"""Exercise production OpenSL queue filling against a deterministic callback.

Windows requires --ndk with the Android NDK root. Other platforms use --cc.
The actual legacy_feed function and platform_audio_t are compiled with a mock
OpenSL vtable. No Android audio driver or device is involved.
"""

import argparse
import ctypes
import json
import os
import subprocess
import tempfile
from pathlib import Path


parser = argparse.ArgumentParser()
parser.add_argument('--ndk', type=Path, default=os.environ.get('ANDROID_NDK_HOME'))
parser.add_argument('--cc', default='cc')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
windows = os.name == 'nt'

MOCK = r'''
#include <stddef.h>
#include <stdint.h>
#include "platform/android/audio.h"

void *memcpy(void *dest, const void *src, size_t n) {
    unsigned char *d = dest; const unsigned char *s = src;
    for (size_t i = 0; i < n; ++i) d[i] = s[i];
    return dest;
}
void *memset(void *dest, int value, size_t n) {
    unsigned char *d = dest;
    for (size_t i = 0; i < n; ++i) d[i] = (unsigned char)value;
    return dest;
}
typedef uint32_t (*fn_vcall3)(void*, void*, int32_t);
static inline void *rd_ptr(const void *p) {
    void *value; memcpy(&value, p, sizeof value); return value;
}

platform_audio_t platform_audio_state;
static int16_t captured[65536];
static unsigned captured_count, enqueue_count, wait_calls, release_on_wait;
static unsigned close_on_wait, fail_next, discontinuities;
static int lock_depth, lock_error;
void platform_audio_queue_lock(void) { if (lock_depth++) lock_error = 1; }
void platform_audio_queue_unlock(void) { if (--lock_depth) lock_error = 1; }

void platform_audio_mark_discontinuity(void) { ++discontinuities; }
void platform_audio_wait_for_output(unsigned microseconds) {
    if (lock_depth) lock_error = 1;
    if (microseconds != 1000) return;
    ++wait_calls;
    if (wait_calls == release_on_wait)
        __atomic_fetch_sub(&PLATFORM_AUDIO->queued, 1, __ATOMIC_ACQ_REL);
    if (wait_calls == close_on_wait)
        __atomic_store_n(&PLATFORM_AUDIO->closed, 1, __ATOMIC_RELEASE);
}
static uint32_t mock_enqueue(void *object, void *data, int32_t bytes) {
    if (lock_depth != 1) lock_error = 1;
    (void)object;
    if (fail_next) { fail_next = 0; return 1; }
    unsigned samples = (unsigned)bytes / 2;
    if (captured_count + samples > sizeof captured / sizeof captured[0]) return 1;
    memcpy(captured + captured_count, data, (size_t)bytes);
    captured_count += samples;
    ++enqueue_count;
    return 0;
}
static void *queue_methods[] = {(void *)mock_enqueue};
static void **queue_object = queue_methods;

void test_reset(unsigned buffers, unsigned samples) {
    memset(&platform_audio_state, 0, sizeof platform_audio_state);
    memset(platform_audio_state.buffers, 0xa5, sizeof platform_audio_state.buffers);
    memset(platform_audio_state.mic_buffers, 0x5a, sizeof platform_audio_state.mic_buffers);
    platform_audio_state.active = 1;
    platform_audio_state.player_queue = &queue_object;
    platform_audio_state.buffer_count = buffers;
    platform_audio_state.buffer_samples = samples;
    captured_count = enqueue_count = wait_calls = release_on_wait = 0;
    close_on_wait = fail_next = discontinuities = 0;
}
void test_queued_set(unsigned value) { __atomic_store_n(&PLATFORM_AUDIO->queued, value, __ATOMIC_RELEASE); }
void test_release_at(unsigned value) { release_on_wait = value; }
void test_close_at(unsigned value) { close_on_wait = value; }
void test_closed_set(unsigned value) { __atomic_store_n(&PLATFORM_AUDIO->closed, value, __ATOMIC_RELEASE); }
void test_fail_next(void) { fail_next = 1; }
unsigned test_enqueued(void) { return enqueue_count; }
unsigned test_waits(void) { return wait_calls; }
unsigned test_queued(void) { return __atomic_load_n(&PLATFORM_AUDIO->queued, __ATOMIC_ACQUIRE); }
unsigned test_index(void) { return PLATFORM_AUDIO->buffer_index; }
unsigned test_fill(unsigned index) { return PLATFORM_AUDIO->buffer_fill[index]; }
unsigned test_discontinuities(void) { return discontinuities; }
unsigned test_captured_count(void) { return captured_count; }
int test_captured(unsigned index) { return captured[index]; }
int test_buffer_sample(unsigned index, unsigned sample) {
    int16_t value;
    memcpy(&value, PLATFORM_AUDIO->buffers + (size_t)index * PLATFORM_AUDIO_BUFFER_BYTES + (size_t)sample * 2, 2);
    return value;
}
int test_guards(void) {
    unsigned used_bytes = PLATFORM_AUDIO->buffer_samples * 2;
    if (used_bytes > PLATFORM_AUDIO_BUFFER_BYTES) return 1;
    for (unsigned buffer = 0; buffer < PLATFORM_AUDIO_BUFFERS; ++buffer)
        for (unsigned offset = used_bytes; offset < PLATFORM_AUDIO_BUFFER_BYTES; ++offset)
            if (PLATFORM_AUDIO->buffers[buffer * PLATFORM_AUDIO_BUFFER_BYTES + offset] != 0xa5) return 0;
    const unsigned char *mic = (const unsigned char *)PLATFORM_AUDIO->mic_buffers;
    for (size_t i = 0; i < sizeof PLATFORM_AUDIO->mic_buffers; ++i)
        if (mic[i] != 0x5a) return 0;
    return 1;
}
'''


def run(command):
    completed = subprocess.run([str(value) for value in command], capture_output=True, text=True, timeout=60)
    if completed.returncode:
        raise RuntimeError(completed.stdout + completed.stderr)


with tempfile.TemporaryDirectory(prefix='seedless-opensl-queue-') as directory:
    temp = Path(directory)
    include = temp / 'include'
    include.mkdir()
    (include / 'string.h').write_text(
        '#include <stddef.h>\nvoid *memcpy(void *,const void *,size_t);\nvoid *memset(void *,int,size_t);\n', encoding='utf-8')
    source = (root / 'core/src/platform/android/audio.c').read_text(encoding='utf-8')
    start = source.index('static int legacy_feed(')
    end = source.index('\nstatic int send_game_pcm(', start)
    function = source[start:end]
    (temp / 'queue.c').write_text(MOCK + '\n' + function + '''
int test_feed(const int16_t *samples, unsigned count, int muted) {
    int result = legacy_feed(samples, count, muted);
    return lock_depth || lock_error ? -99 : result;
}
''', encoding='utf-8')
    if windows:
        if not args.ndk:
            parser.error('--ndk is required on Windows')
        toolchain = args.ndk / 'toolchains/llvm/prebuilt/windows-x86_64/bin'
        compiler, linker = toolchain / 'clang.exe', toolchain / 'ld.lld.exe'
        obj, library = temp / 'queue.obj', temp / 'queue.dll'
        run([compiler, '--target=x86_64-pc-windows-msvc', '-ffreestanding', '-fno-stack-protector',
             '-fno-builtin', '-O2', '-I', include, '-I', root / 'core/src', '-c', temp / 'queue.c', '-o', obj])
        exports = ['test_reset', 'test_queued_set', 'test_release_at', 'test_close_at', 'test_closed_set',
                   'test_fail_next', 'test_enqueued', 'test_waits', 'test_queued', 'test_index', 'test_fill',
                   'test_discontinuities', 'test_captured_count', 'test_captured', 'test_buffer_sample', 'test_guards', 'test_feed']
        run([linker, '-flavor', 'link', '/dll', '/noentry', '/nodefaultlib', '/out:' + str(library),
             *['/export:' + name for name in exports], obj])
    else:
        library = temp / 'queue.so'
        run([args.cc, '-shared', '-fPIC', '-fno-builtin', '-O2', '-I', include, '-I', root / 'core/src', temp / 'queue.c', '-o', library])

    dll = ctypes.CDLL(str(library))
    for name in ['test_enqueued', 'test_waits', 'test_queued', 'test_index', 'test_fill',
                 'test_discontinuities', 'test_captured_count', 'test_guards']:
        getattr(dll, name).restype = ctypes.c_uint
    dll.test_reset.argtypes = [ctypes.c_uint, ctypes.c_uint]
    for name in ['test_queued_set', 'test_release_at', 'test_close_at', 'test_closed_set', 'test_fill', 'test_captured']:
        getattr(dll, name).argtypes = [ctypes.c_uint]
    dll.test_buffer_sample.argtypes = [ctypes.c_uint, ctypes.c_uint]
    dll.test_feed.argtypes = [ctypes.POINTER(ctypes.c_int16), ctypes.c_uint, ctypes.c_int]
    dll.test_feed.restype = ctypes.c_int

    def feed(values, muted=False):
        samples = (ctypes.c_int16 * len(values))(*values)
        return dll.test_feed(samples, len(values), int(muted))

    def captured():
        return [dll.test_captured(index) for index in range(dll.test_captured_count())]

    passed = []
    stereo = [value for index in range(14) for value in (1000 + index, -2000 - index)]
    dll.test_reset(4, 8)
    assert feed(stereo[:6]) == 1 and dll.test_fill(0) == 6 and dll.test_enqueued() == 0
    assert feed(stereo[6:]) == 1
    assert dll.test_enqueued() == 3 and dll.test_queued() == 3
    assert captured() == stereo[:24] and dll.test_index() == 3 and dll.test_fill(3) == 4
    assert [dll.test_buffer_sample(3, n) for n in range(4)] == stereo[24:]
    assert dll.test_guards()
    passed.append('variableStereoChunksCrossThreeBuffersWithoutOverflow')

    dll.test_reset(4, 1470)
    long_stereo = [value for index in range(1471) for value in (index + 1, -index - 1)]
    assert feed(long_stereo) == 1
    assert captured() == long_stereo[:2940] and dll.test_queued() == 2
    assert dll.test_index() == 2 and dll.test_fill(2) == 2
    assert [dll.test_buffer_sample(2, n) for n in range(2)] == long_stereo[2940:]
    assert dll.test_guards()
    passed.append('slowMotionBlockSpansTwoFullBuffersAndOneStereoFrame')

    dll.test_reset(2, 8)
    dll.test_release_at(2)
    assert feed(stereo[:20]) == 1
    assert dll.test_enqueued() == 2 and dll.test_waits() == 2 and dll.test_queued() == 1
    assert captured() == stereo[:16] and dll.test_index() == 0 and dll.test_fill(0) == 4
    assert [dll.test_buffer_sample(0, n) for n in range(4)] == stereo[16:20]
    assert dll.test_guards()
    passed.append('oneCallWaitsAndResumesAfterCrossingAFullQueue')

    dll.test_reset(4, 8)
    dll.test_queued_set(4)
    dll.test_release_at(3)
    assert feed(stereo[:4]) == 1
    assert dll.test_waits() == 3 and dll.test_queued() == 3 and dll.test_fill(0) == 4
    assert [dll.test_buffer_sample(0, n) for n in range(4)] == stereo[:4]
    assert dll.test_guards()
    passed.append('fullQueueWaitsForCallbackWithoutDroppingPcm')

    dll.test_reset(4, 8)
    dll.test_queued_set(4)
    assert feed(stereo[:4]) == 0
    assert dll.test_waits() == 150 and dll.test_queued() == 4 and dll.test_fill(0) == 0
    assert dll.test_guards()
    passed.append('fullQueueHasBounded150MillisecondWait')

    dll.test_reset(4, 8)
    dll.test_queued_set(4)
    dll.test_close_at(2)
    assert feed(stereo[:4]) == 0 and dll.test_waits() == 2
    assert dll.test_fill(0) == 0 and dll.test_enqueued() == 0 and dll.test_guards()
    dll.test_reset(4, 8)
    dll.test_closed_set(1)
    assert feed(stereo[:4]) == 0 and dll.test_waits() == 0 and dll.test_guards()
    passed.append('pauseInterruptsWaitingAndPreventsWrite')

    dll.test_reset(4, 8)
    assert feed(stereo[:4]) == 1 and feed(stereo[4:8], muted=True) == 1
    assert captured() == stereo[:4] + [0] * 4 and dll.test_guards()
    passed.append('muteZerosOnlyNewSamplesInPartialBuffer')

    dll.test_reset(4, 8)
    dll.test_fail_next()
    assert feed(stereo[:8]) == 0
    assert dll.test_queued() == 0 and dll.test_fill(0) == 0 and dll.test_discontinuities() == 1
    assert dll.test_enqueued() == 0 and dll.test_guards()
    passed.append('failedEnqueueRestoresCountAndMarksDiscontinuity')

    for buffers, samples in [(0, 8), (9, 8), (4, 0), (4, 5881)]:
        dll.test_reset(buffers, samples)
        assert feed(stereo[:8]) == 0 and dll.test_enqueued() == 0
    passed.append('invalidQueueGeometryIsRejected')

    print(json.dumps({'source': 'production legacy_feed and platform_audio_t',
                      'tests': passed, 'count': len(passed),
                      'scope': 'deterministic callback and enqueue mock, no Android driver'}, indent=2))

    if windows:
        kernel = ctypes.WinDLL('kernel32')
        kernel.FreeLibrary.argtypes = [ctypes.c_void_p]
        assert kernel.FreeLibrary(dll._handle)
