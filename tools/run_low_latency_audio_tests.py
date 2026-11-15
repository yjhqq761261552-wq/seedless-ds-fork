"""Run production AAudio lifecycle and PCM code against deterministic host APIs.

Windows uses NDK clang and the real Win32 heap/SRW mutex. Android/AAudio calls,
time and OpenSL vtables are mocked. The production OpenSL restart function is
extracted from nds.c. No Android audio hardware is used. --source-root is
optional when this script is copied into repo/tools/.
"""
from pathlib import Path
import argparse
import ctypes as C
import json
import os
import subprocess
import tempfile


parser = argparse.ArgumentParser()
parser.add_argument('--source-root', type=Path, default=Path(__file__).resolve().parents[1])
parser.add_argument('--ndk', type=Path, default=os.environ.get('ANDROID_NDK_HOME'))
parser.add_argument('--cc', default='cc')
args = parser.parse_args()
root = args.source_root.resolve()
windows = os.name == 'nt'

AAUDIO_HEADER = r'''
#ifndef HOST_AAUDIO_H
#define HOST_AAUDIO_H
#include <stdint.h>
typedef int32_t aaudio_result_t;
typedef int32_t aaudio_stream_state_t;
typedef int32_t aaudio_data_callback_result_t;
typedef int32_t aaudio_usage_t;
typedef struct AAudioStream AAudioStream;
typedef struct AAudioStreamBuilder AAudioStreamBuilder;
typedef aaudio_data_callback_result_t (*DataCallback)(AAudioStream *, void *, void *, int32_t);
typedef void (*ErrorCallback)(AAudioStream *, void *, aaudio_result_t);
enum { AAUDIO_OK=0, AAUDIO_ERROR_TIMEOUT=-885, AAUDIO_ERROR_DISCONNECTED=-899 };
enum { AAUDIO_CALLBACK_RESULT_CONTINUE=0, AAUDIO_DIRECTION_OUTPUT=0,
       AAUDIO_FORMAT_PCM_I16=1, AAUDIO_SHARING_MODE_SHARED=1,
       AAUDIO_PERFORMANCE_MODE_LOW_LATENCY=12, AAUDIO_USAGE_GAME=14 };
enum { AAUDIO_STREAM_STATE_UNINITIALIZED=0, AAUDIO_STREAM_STATE_UNKNOWN=1,
       AAUDIO_STREAM_STATE_OPEN=2, AAUDIO_STREAM_STATE_STARTING=3,
       AAUDIO_STREAM_STATE_STARTED=4, AAUDIO_STREAM_STATE_PAUSING=5,
       AAUDIO_STREAM_STATE_PAUSED=6, AAUDIO_STREAM_STATE_FLUSHING=7,
       AAUDIO_STREAM_STATE_FLUSHED=8, AAUDIO_STREAM_STATE_STOPPING=9,
       AAUDIO_STREAM_STATE_STOPPED=10, AAUDIO_STREAM_STATE_CLOSING=11,
       AAUDIO_STREAM_STATE_CLOSED=12, AAUDIO_STREAM_STATE_DISCONNECTED=13 };
aaudio_result_t AAudio_createStreamBuilder(AAudioStreamBuilder **);
aaudio_result_t AAudioStreamBuilder_delete(AAudioStreamBuilder *);
void AAudioStreamBuilder_setDirection(AAudioStreamBuilder *, int32_t);
void AAudioStreamBuilder_setChannelCount(AAudioStreamBuilder *, int32_t);
void AAudioStreamBuilder_setFormat(AAudioStreamBuilder *, int32_t);
void AAudioStreamBuilder_setPerformanceMode(AAudioStreamBuilder *, int32_t);
void AAudioStreamBuilder_setSharingMode(AAudioStreamBuilder *, int32_t);
void AAudioStreamBuilder_setUsage(AAudioStreamBuilder *, aaudio_usage_t);
void AAudioStreamBuilder_setDataCallback(AAudioStreamBuilder *, DataCallback, void *);
void AAudioStreamBuilder_setErrorCallback(AAudioStreamBuilder *, ErrorCallback, void *);
aaudio_result_t AAudioStreamBuilder_openStream(AAudioStreamBuilder *, AAudioStream **);
aaudio_result_t AAudioStream_requestStart(AAudioStream *);
aaudio_result_t AAudioStream_requestPause(AAudioStream *);
aaudio_result_t AAudioStream_close(AAudioStream *);
int32_t AAudioStream_getSampleRate(AAudioStream *);
int32_t AAudioStream_getChannelCount(AAudioStream *);
int32_t AAudioStream_getFormat(AAudioStream *);
int32_t AAudioStream_getFramesPerBurst(AAudioStream *);
aaudio_result_t AAudioStream_setBufferSizeInFrames(AAudioStream *, int32_t);
int32_t AAudioStream_getBufferSizeInFrames(AAudioStream *);
int32_t AAudioStream_getBufferCapacityInFrames(AAudioStream *);
int32_t AAudioStream_getXRunCount(AAudioStream *);
aaudio_stream_state_t AAudioStream_getState(AAudioStream *);
aaudio_result_t AAudioStream_waitForStateChange(AAudioStream *, aaudio_stream_state_t,
                                               aaudio_stream_state_t *, int64_t);
#endif
'''

MOCK = r'''
#include <stddef.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <aaudio/AAudio.h>
#include "low_latency_audio.h"

struct AAudioStreamBuilder { DataCallback data; ErrorCallback error; void *data_user, *error_user; };
struct AAudioStream { DataCallback data; ErrorCallback error; void *data_user, *error_user;
    int state, buffer; };
static AAudioStream *current;
static uint64_t clock_us=1000;
static int pause_mode, start_failure, open_failure;
static int starts, closes, opens, restarts, legacy_playing, xruns;
int32_t audio_out_mode;
int _fltused;

uint64_t nds_metrics_now_us(void) { return clock_us; }
void *memcpy(void *d, const void *s, size_t n) {
    unsigned char *a=d; const unsigned char *b=s;
    for (size_t i=0;i<n;++i) a[i]=b[i]; return d;
}
void *memset(void *d, int value, size_t n) {
    unsigned char *a=d; for (size_t i=0;i<n;++i) a[i]=(unsigned char)value; return d;
}
static int same(const char *a, const char *b) {
    while (*a && *a==*b) { ++a; ++b; } return *a==*b;
}
int __android_log_print(int priority, const char *tag, const char *format, ...) {
    (void)priority; (void)tag; (void)format; return 0;
}
void *dlopen(const char *name, int flags) { (void)name; (void)flags; return (void *)1; }
int dlclose(void *handle) { (void)handle; return 0; }
void *dlsym(void *handle, const char *name) {
    (void)handle;
#define MATCH(function) if (same(name,#function)) return (void *)function;
    MATCH(AAudio_createStreamBuilder)
    MATCH(AAudioStreamBuilder_delete)
    MATCH(AAudioStreamBuilder_setDirection)
    MATCH(AAudioStreamBuilder_setChannelCount)
    MATCH(AAudioStreamBuilder_setFormat)
    MATCH(AAudioStreamBuilder_setPerformanceMode)
    MATCH(AAudioStreamBuilder_setSharingMode)
    MATCH(AAudioStreamBuilder_setUsage)
    MATCH(AAudioStreamBuilder_setDataCallback)
    MATCH(AAudioStreamBuilder_setErrorCallback)
    MATCH(AAudioStreamBuilder_openStream)
    MATCH(AAudioStream_requestStart)
    MATCH(AAudioStream_requestPause)
    MATCH(AAudioStream_close)
    MATCH(AAudioStream_getSampleRate)
    MATCH(AAudioStream_getChannelCount)
    MATCH(AAudioStream_getFormat)
    MATCH(AAudioStream_getFramesPerBurst)
    MATCH(AAudioStream_setBufferSizeInFrames)
    MATCH(AAudioStream_getBufferSizeInFrames)
    MATCH(AAudioStream_getBufferCapacityInFrames)
    MATCH(AAudioStream_getXRunCount)
    MATCH(AAudioStream_getState)
    MATCH(AAudioStream_waitForStateChange)
#undef MATCH
    return 0;
}
aaudio_result_t AAudio_createStreamBuilder(AAudioStreamBuilder **builder) {
    *builder=calloc(1,sizeof **builder); return *builder?0:-1;
}
aaudio_result_t AAudioStreamBuilder_delete(AAudioStreamBuilder *builder) { free(builder); return 0; }
#define SETTER(name) void name(AAudioStreamBuilder *b, int32_t v) { (void)b; (void)v; }
SETTER(AAudioStreamBuilder_setDirection)
SETTER(AAudioStreamBuilder_setChannelCount)
SETTER(AAudioStreamBuilder_setFormat)
SETTER(AAudioStreamBuilder_setPerformanceMode)
SETTER(AAudioStreamBuilder_setSharingMode)
SETTER(AAudioStreamBuilder_setUsage)
#undef SETTER
void AAudioStreamBuilder_setDataCallback(AAudioStreamBuilder *b, DataCallback cb, void *user) {
    b->data=cb; b->data_user=user;
}
void AAudioStreamBuilder_setErrorCallback(AAudioStreamBuilder *b, ErrorCallback cb, void *user) {
    b->error=cb; b->error_user=user;
}
aaudio_result_t AAudioStreamBuilder_openStream(AAudioStreamBuilder *b, AAudioStream **stream) {
    if (open_failure) return -1;
    AAudioStream *s=calloc(1,sizeof *s);
    if (!s) return -1;
    s->data=b->data; s->error=b->error; s->data_user=b->data_user; s->error_user=b->error_user;
    s->state=AAUDIO_STREAM_STATE_OPEN; s->buffer=192;
    *stream=s; current=s; ++opens; return 0;
}
aaudio_result_t AAudioStream_requestStart(AAudioStream *s) {
    ++starts; if (start_failure) return -1;
    s->state=AAUDIO_STREAM_STATE_STARTED; return 0;
}
aaudio_result_t AAudioStream_requestPause(AAudioStream *s) {
    if (pause_mode==1) return -1;  /* Pause rejected, state stays STARTED/STARTING. */
    s->state=pause_mode==2?AAUDIO_STREAM_STATE_PAUSING:AAUDIO_STREAM_STATE_PAUSED;
    return 0;
}
aaudio_result_t AAudioStream_close(AAudioStream *s) {
    if (current==s) current=0; ++closes; free(s); return 0;
}
int32_t AAudioStream_getSampleRate(AAudioStream *s) { (void)s; return 48000; }
int32_t AAudioStream_getChannelCount(AAudioStream *s) { (void)s; return 2; }
int32_t AAudioStream_getFormat(AAudioStream *s) { (void)s; return AAUDIO_FORMAT_PCM_I16; }
int32_t AAudioStream_getFramesPerBurst(AAudioStream *s) { (void)s; return 192; }
aaudio_result_t AAudioStream_setBufferSizeInFrames(AAudioStream *s, int32_t frames) {
    s->buffer=frames; return frames;
}
int32_t AAudioStream_getBufferSizeInFrames(AAudioStream *s) { return s->buffer; }
int32_t AAudioStream_getBufferCapacityInFrames(AAudioStream *s) { (void)s; return 8192; }
int32_t AAudioStream_getXRunCount(AAudioStream *s) { (void)s; return xruns; }
aaudio_stream_state_t AAudioStream_getState(AAudioStream *s) { return s->state; }
aaudio_result_t AAudioStream_waitForStateChange(AAudioStream *s, aaudio_stream_state_t before,
                                               aaudio_stream_state_t *after, int64_t timeout) {
    (void)before; (void)timeout; *after=s->state;
    return pause_mode==2?AAUDIO_ERROR_TIMEOUT:AAUDIO_OK;
}
/* Count handoff requests and execute the extracted production restart body. */
void audio_out_restart_playback(void) {
    ++restarts;
    extern void production_audio_out_restart_playback(void);
    production_audio_out_restart_playback();
}
void test_reset(int latency) {
    extern void mock_legacy_init(void);
    low_latency_audio_close();
    pause_mode=start_failure=open_failure=0;
    starts=closes=opens=restarts=legacy_playing=xruns=0;
    clock_us=1000; audio_out_mode=latency;
    mock_legacy_init();
    low_latency_audio_configure(1); low_latency_audio_sound(1); low_latency_audio_volume(100);
}
void test_pause_mode(int value) { pause_mode=value; }
void test_start_failure(int value) { start_failure=value; }
void test_open_failure(int value) { open_failure=value; }
void test_state(int value) { if (current) current->state=value; }
void test_latency(int value) { __atomic_store_n(&audio_out_mode,value,__ATOMIC_RELEASE); }
void test_advance(uint64_t delta) { clock_us+=delta; }
void test_xruns(int value) { xruns=value; }
int test_starts(void) { return starts; }
int test_closes(void) { return closes; }
int test_opens(void) { return opens; }
int test_restarts(void) { return restarts; }
int test_legacy_playing(void) { return legacy_playing; }
int test_buffer(void) { return current?current->buffer:0; }
void test_disconnect(void) {
    if (current) { current->state=AAUDIO_STREAM_STATE_DISCONNECTED;
        current->error(current,current->error_user,AAUDIO_ERROR_DISCONNECTED); }
}
int test_callback(unsigned frames) {
    static int16_t data[4096];
    if (!current || frames>2048) return -1;
    memset(data,0x5a,(size_t)frames*4);
    current->data(current,current->data_user,data,(int32_t)frames);
    int nonzero=0;
    for (unsigned i=0;i<frames*2;++i) if (data[i]) ++nonzero;
    return nonzero;
}
'''

FIXTURES = {
    'aaudio/AAudio.h': AAUDIO_HEADER,
    'android/log.h': '#define ANDROID_LOG_INFO 4\n#define ANDROID_LOG_WARN 5\nint __android_log_print(int,const char *,const char *,...);\n',
    'dlfcn.h': '#define RTLD_NOW 2\n#define RTLD_LOCAL 0\nvoid *dlopen(const char *,int);\nvoid *dlsym(void *,const char *);\nint dlclose(void *);\n',
    'seedlessds/metrics.h': '#include <stdint.h>\nuint64_t nds_metrics_now_us(void);\n',
    'frontend/audio_out.h': '#include <stdint.h>\nextern int32_t audio_out_mode;\n',
}

LEGACY_MOCK = r'''
#include "platform/android/audio.h"
platform_audio_t platform_audio_state;
static int physical_queued, enqueue_attempts, enqueue_fail_every;
static long mock_play(void *object, long state) {
    (void)object; legacy_playing=state==3; return 0;
}
static long mock_enqueue(void *object, void *data, uint32_t bytes) {
    (void)object; (void)data; (void)bytes;
    ++enqueue_attempts;
    if ((enqueue_fail_every && enqueue_attempts%enqueue_fail_every==0) ||
        physical_queued >= (int)PLATFORM_AUDIO->buffer_count) return 1;
    ++physical_queued; return 0;
}
static void mock_clear(void *object) { (void)object; physical_queued=0; }
static long mock_volume(void *object, uint32_t volume) { (void)object; (void)volume; return 0; }
static void *play_methods[]={(void *)mock_play};
static void *queue_methods[]={(void *)mock_enqueue,(void *)mock_clear};
static void *volume_methods[]={(void *)mock_volume};
static void **play_object=play_methods;
static void **queue_object=queue_methods;
static void **volume_object=volume_methods;
void mock_legacy_init(void) {
    memset(PLATFORM_AUDIO,0,sizeof *PLATFORM_AUDIO);
    PLATFORM_AUDIO->active=1;
    PLATFORM_AUDIO->buffer_count=4;
    PLATFORM_AUDIO->buffer_samples=1470;
    PLATFORM_AUDIO->play_itf=&play_object;
    PLATFORM_AUDIO->player_queue=&queue_object;
    PLATFORM_AUDIO->volume_itf=&volume_object;
    physical_queued=enqueue_attempts=enqueue_fail_every=0;
}
void test_queue_failure(int every) { enqueue_fail_every=every; enqueue_attempts=0; }
int test_physical_queued(void) { return physical_queued; }
int test_queued(void) { return (int)__atomic_load_n(&PLATFORM_AUDIO->queued,__ATOMIC_ACQUIRE); }
void test_restart(void) { audio_out_restart_playback(); }
'''


def run(command):
    result = subprocess.run([str(x) for x in command], capture_output=True, text=True, timeout=60)
    if result.returncode:
        raise RuntimeError(result.stdout + result.stderr)


with tempfile.TemporaryDirectory(prefix='seedless-audio-host-') as temp_dir:
    temp = Path(temp_dir)
    include = temp / 'include'
    mock = MOCK
    nds_source = (root / 'core/src/core/nds.c').read_text(encoding='utf-8')
    restart_start = nds_source.index('static void *slot0(void *obj) {')
    restart_end = nds_source.index('static void *(*core_memset)', restart_start)
    restart_source = nds_source[restart_start:restart_end].replace(
        'void audio_out_restart_playback(void)',
        'void production_audio_out_restart_playback(void)')
    mock += LEGACY_MOCK + '\n' + restart_source
    if windows:
        if not args.ndk:
            parser.error('--ndk is required on Windows')
        toolchain = args.ndk / 'toolchains/llvm/prebuilt/windows-x86_64/bin'
        compiler = toolchain / 'clang.exe'
        linker = toolchain / 'ld.lld.exe'
        FIXTURES['stdlib.h'] = '#include <stddef.h>\nvoid *calloc(size_t,size_t);\nvoid free(void *);\n'
        FIXTURES['string.h'] = '#include <stddef.h>\nvoid *memcpy(void *,const void *,size_t);\nvoid *memset(void *,int,size_t);\n'
        FIXTURES['pthread.h'] = '''#ifndef HOST_MUTEX_H
#define HOST_MUTEX_H
typedef struct { void *value; } pthread_mutex_t;
#define PTHREAD_MUTEX_INITIALIZER {0}
__declspec(dllimport) void AcquireSRWLockExclusive(void *);
__declspec(dllimport) void ReleaseSRWLockExclusive(void *);
static inline int pthread_mutex_lock(pthread_mutex_t *m) { AcquireSRWLockExclusive(m); return 0; }
static inline int pthread_mutex_unlock(pthread_mutex_t *m) { ReleaseSRWLockExclusive(m); return 0; }
#endif
'''
        mock += '''
__declspec(dllimport) void *GetProcessHeap(void);
__declspec(dllimport) void *HeapAlloc(void *,unsigned long,size_t);
__declspec(dllimport) int HeapFree(void *,unsigned long,void *);
void *calloc(size_t count,size_t size) { return HeapAlloc(GetProcessHeap(),8,count*size); }
void free(void *p) { if (p) HeapFree(GetProcessHeap(),0,p); }
'''
    else:
        compiler = args.cc
    for name, content in FIXTURES.items():
        p = include / name
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(content, encoding='utf-8')
    (temp / 'mock.c').write_text(mock, encoding='utf-8')
    production = root / 'core/src/platform/android'
    flags = ['-O2', '-fno-builtin', '-I', include, '-I', production, '-I', root / 'core/src']
    sources = [production / 'low_latency_audio.c', production / 'pcm_ring.c', temp / 'mock.c']
    objects = []
    for index, source in enumerate(sources):
        obj = temp / (str(index) + ('.obj' if windows else '.o'))
        target = ['--target=x86_64-pc-windows-msvc', '-ffreestanding', '-fno-stack-protector'] if windows else ['-fPIC']
        run([compiler, *target, *flags, '-c', source, '-o', obj])
        objects.append(obj)
    exported = ['low_latency_audio_start', 'low_latency_audio_active', 'low_latency_audio_close',
                'low_latency_audio_sound', 'low_latency_audio_pause', 'low_latency_audio_resume',
                'low_latency_audio_configure', 'low_latency_audio_feed', 'low_latency_audio_stats',
                'low_latency_audio_volume', 'test_reset', 'test_pause_mode', 'test_start_failure',
                'test_open_failure', 'test_state', 'test_latency', 'test_advance', 'test_xruns',
                'test_starts', 'test_closes', 'test_opens', 'test_restarts', 'test_legacy_playing',
                'test_buffer', 'test_disconnect', 'test_callback', 'test_queue_failure',
                'test_physical_queued', 'test_queued', 'test_restart']
    if windows:
        definition = temp / 'kernel.def'
        definition.write_text('LIBRARY KERNEL32.dll\nEXPORTS\nAcquireSRWLockExclusive\nReleaseSRWLockExclusive\nGetProcessHeap\nHeapAlloc\nHeapFree\n', encoding='utf-8')
        imports = temp / 'kernel.lib'
        run([linker, '-flavor', 'link', '/dll', '/noentry', '/nodefaultlib', '/force:unresolved',
             '/def:' + str(definition), '/machine:x64', '/implib:' + str(imports),
             '/out:' + str(temp / 'kernel32.dll')])
        library = temp / 'audio_test.dll'
        run([linker, '-flavor', 'link', '/dll', '/noentry', '/nodefaultlib',
             '/out:' + str(library), *['/export:' + name for name in exported], *objects, imports])
    else:
        library = temp / 'audio_test.so'
        run([compiler, '-shared', *objects, '-pthread', '-Wl,-Bsymbolic', '-o', library])
    dll = C.CDLL(str(library))
    for name in ['test_reset', 'test_pause_mode', 'test_start_failure', 'test_open_failure',
                 'test_state', 'test_latency', 'test_xruns', 'low_latency_audio_sound',
                 'low_latency_audio_configure', 'low_latency_audio_volume', 'low_latency_audio_start',
                 'test_queue_failure']:
        getattr(dll, name).argtypes = [C.c_int]
    dll.test_advance.argtypes = [C.c_uint64]
    dll.test_callback.argtypes = [C.c_uint]
    dll.low_latency_audio_feed.argtypes = [C.POINTER(C.c_int16), C.c_uint, C.c_int]
    dll.low_latency_audio_stats.argtypes = [C.POINTER(C.c_uint64)]
    pcm = (C.c_int16 * 4096)(*[12000 if i % 2 == 0 else -8000 for i in range(4096)])

    def feed(frames=735, muted=False):
        return dll.low_latency_audio_feed(pcm, frames * 2, int(muted))

    def begin(latency=0):
        dll.test_reset(latency)
        assert dll.low_latency_audio_start(latency) == 1

    def prime():
        assert feed(734) == 1
        assert feed(735) == 1
        if not dll.test_starts():
            assert feed(735) == 1
        assert dll.test_starts() >= 1

    passed = []

    begin()
    assert feed(734) == 1 and dll.test_starts() == 0
    assert feed(735) == 1 and dll.test_starts() == 1
    assert dll.test_callback(256) > 0
    passed.append('minimumLatency1469FramePriming')

    begin()
    prime()
    dll.test_pause_mode(1)
    dll.low_latency_audio_pause()
    assert dll.test_callback(128) == 0
    dll.low_latency_audio_resume()
    # The first callback may consume the explicit stale-data flush.
    dll.test_callback(128)
    feed()
    assert dll.test_callback(128) > 0
    assert dll.test_restarts() == 0 and dll.low_latency_audio_active() == 1
    passed.append('pauseRejectedWhileStartedRestoresOutputGate')

    begin()
    prime()
    dll.test_state(3)
    dll.test_pause_mode(1)
    dll.low_latency_audio_pause()
    dll.low_latency_audio_resume()
    dll.test_callback(128)
    feed()
    assert dll.test_callback(128) > 0
    passed.append('pauseRejectedWhileStartingRestoresOutputGate')

    begin()
    prime()
    dll.test_pause_mode(2)
    dll.low_latency_audio_pause()
    dll.low_latency_audio_resume()
    assert dll.low_latency_audio_active() == 1  # Producer owns the handoff.
    assert feed() == 0
    assert dll.low_latency_audio_active() == 0
    assert dll.test_legacy_playing() == 1 and dll.test_restarts() == 1
    passed.append('pauseTimeoutFallsBackOnProducer')

    begin()
    prime()
    dll.low_latency_audio_sound(0)
    assert dll.test_callback(128) == 0
    dll.low_latency_audio_sound(1)
    prime()
    assert dll.test_callback(128) > 0
    passed.append('soundToggleReprimesAndResumes')

    begin()
    prime()
    dll.low_latency_audio_sound(0)
    dll.test_state(13)
    dll.low_latency_audio_sound(1)
    assert feed() == 0 and dll.test_legacy_playing() == 1
    passed.append('soundToggleWithDisconnectedStreamRecoversLegacy')

    begin()
    prime()
    dll.test_disconnect()
    assert feed() == 0
    assert dll.test_legacy_playing() == 1 and dll.test_closes() == 1
    passed.append('errorCallbackFallsBackToOpenSl')

    begin()
    dll.test_start_failure(1)
    feed(734)
    feed(735)
    assert feed() == 0 and dll.test_legacy_playing() == 1
    passed.append('requestStartFailureFallsBackToOpenSl')

    dll.test_reset(0)
    dll.test_open_failure(1)
    assert dll.low_latency_audio_start(0) == 0
    assert dll.low_latency_audio_active() == 0 and feed() == 0
    dll.test_open_failure(0)
    passed.append('openFailureLeavesLegacyOwnership')

    begin()
    prime()
    dll.test_advance(1000001)
    assert feed() == 1  # Detect first, hand over on the next producer block.
    assert feed() == 0
    assert dll.test_legacy_playing() == 1 and dll.test_restarts() == 1
    passed.append('stalledCallbacksRecoverWithinTwoBlocks')

    begin()
    prime()
    for _ in range(15):
        dll.test_advance(100000)
        assert dll.test_callback(512) >= 0
        assert feed() == 1
    assert dll.low_latency_audio_active() == 1 and dll.test_restarts() == 0
    passed.append('healthyCallbacksAvoidFalseStallRecovery')

    begin()
    prime()
    dll.low_latency_audio_pause()
    dll.test_advance(5000000)
    dll.low_latency_audio_resume()
    prime()
    assert dll.test_callback(128) > 0
    assert feed() == 1 and dll.test_restarts() == 0
    passed.append('longPauseDoesNotTriggerStallRecovery')

    begin()
    prime()
    dll.low_latency_audio_configure(0)
    assert feed() == 0 and dll.test_legacy_playing() == 1
    dll.low_latency_audio_configure(1)
    assert feed(734) == 1 and dll.test_opens() == 2
    assert dll.test_legacy_playing() == 0
    feed(735)
    assert dll.test_callback(128) > 0
    passed.append('configureToggleTransfersBackendOwnership')

    begin(0)
    prime()
    assert dll.test_buffer() == 192 * 4
    dll.test_latency(2)
    assert feed(734) == 1
    assert dll.test_buffer() == 192 * 8 and dll.test_opens() == 2
    feed(735)
    feed(735)
    assert dll.test_callback(128) > 0
    passed.append('latencyChangeRecreatesBufferOnProducer')

    begin()
    prime()
    for _ in range(100):
        dll.low_latency_audio_pause()
        dll.low_latency_audio_resume()
        prime()
        assert dll.test_callback(128) > 0
        dll.test_advance(17000)
    assert dll.low_latency_audio_active() == 1 and dll.test_restarts() == 0
    passed.append('oneHundredPauseResumeCycles')

    begin()
    prime()
    dll.low_latency_audio_volume(0)
    assert dll.test_callback(128) == 0
    dll.low_latency_audio_volume(100)
    feed()
    assert dll.test_callback(128) > 0
    passed.append('volumeZeroAndRestore')

    begin()
    prime()
    feed(muted=True)
    assert dll.test_callback(128) == 0
    feed()
    assert dll.test_callback(128) > 0
    passed.append('rewindMuteFlushAndRestore')

    for latency in range(4):
        begin(latency)
        prime()
        assert dll.test_callback(128) > 0
    passed.append('allFourLatencyModesProduceAudio')

    dll.test_reset(0)
    dll.low_latency_audio_configure(0)
    assert dll.low_latency_audio_start(0) == 0
    dll.test_restart()
    assert dll.test_queued() == 4 and dll.test_physical_queued() == 4
    dll.test_queue_failure(2)
    dll.test_restart()
    assert dll.test_queued() == 2 and dll.test_physical_queued() == 2
    assert dll.test_legacy_playing() == 1
    passed.append('realOpenSlRestartCountsOnlySuccessfulEnqueues')

    dll.test_queue_failure(1)
    dll.test_restart()
    assert dll.test_queued() == 0 and dll.test_physical_queued() == 0
    assert dll.test_legacy_playing() == 1
    dll.test_queue_failure(0)
    dll.test_restart()
    assert dll.test_queued() == 4 and dll.test_physical_queued() == 4
    dll.test_restart()
    assert dll.test_queued() == 4 and dll.test_physical_queued() == 4
    passed.append('realOpenSlRestartClearsOldQueueAndRecoversFromEnqueueFailures')

    begin()
    dll.test_restart()
    assert dll.test_queued() == 0 and dll.test_physical_queued() == 0
    assert dll.test_legacy_playing() == 0
    prime()
    assert dll.test_callback(128) > 0
    passed.append('realOpenSlRestartTransfersExclusiveOwnershipToAAudio')

    dll.low_latency_audio_close()
    print(json.dumps({'productionSources': ['core/src/platform/android/low_latency_audio.c',
                                          'core/src/platform/android/pcm_ring.c',
                                          'core/src/core/nds.c audio_out_restart_playback extracted verbatim'],
                      'testsPassed': len(passed), 'tests': {name: 'passed' for name in passed},
                      'scope': 'Production controller/PCM/restart with mocked AAudio/time/OpenSL vtables; no Android driver or acoustic validation'}, indent=2))
    if windows:
        kernel = C.WinDLL('kernel32', use_last_error=True)
        kernel.FreeLibrary.argtypes = [C.c_void_p]
        assert kernel.FreeLibrary(dll._handle)
