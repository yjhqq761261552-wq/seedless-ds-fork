"""Run production rewind ring/codec/worker/scheduler tests without Android or a ROM.

Windows: --ndk <NDK root> uses clang and Windows heap/pthread adapters;
--zlib-dll selects a real x64 zlib DLL (defaults to Git for Windows' copy).
Other hosts: --cc cc requires pthread and the zlib development library.
All outputs are temporary. Machine/clock are fixtures; the codec uses real zlib.
"""
from pathlib import Path
import argparse, ctypes, json, os, subprocess, tempfile

parser = argparse.ArgumentParser()
parser.add_argument('--ndk', default=os.environ.get('ANDROID_NDK_HOME'))
parser.add_argument('--cc', default=os.environ.get('CC', 'cc'))
parser.add_argument('--zlib-dll', default=os.environ.get('ZLIB_DLL', 'C:/Program Files/Git/mingw64/bin/zlib1.dll'))
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
fixtures = {
    'seedlessds/metrics.h': '#include <stdint.h>\nenum { NDS_METRIC_COPY, NDS_METRIC_REWIND };\nstatic uint64_t nds_metrics_now_us(void) {return 0;}\nstatic void nds_metrics_record(unsigned kind,uint64_t us) {(void)kind;(void)us;}\n',

    'core/nds_state.h': '#include <stdint.h>\ntypedef struct { struct { uint64_t frame; } sched; } nds_t;\n',
    'frontend/frontend.h': '''typedef struct { unsigned quit_request, pause_request, rom_load_request, reset_request, state_load_request, state_saving; } frontend_t;
extern frontend_t test_frontend;
#define FRONTEND (&test_frontend)
''',
    'core_internals.h': '#include <stdint.h>\nvoid time_now_microseconds(uint64_t *out);\nint state_write_pending(void);\n',
    'seedlessds/platform.h': '''typedef struct { struct { void (*sleep_us)(unsigned); } threads; } nds_platform_t;
const nds_platform_t *nds_platform_default(void);
''',
}
sources = [root / 'core/src/core/rewind_ring.c', root / 'core/src/core/rewind.c',
           root / 'core/tests/rewind_ring_test.c', root / 'core/tests/rewind_manager_test.c',
           root / 'core/src/core/rewind_worker.c', root / 'core/src/core/rewind_codec.c',
           root / 'core/tests/rewind_worker_test.c', root / 'core/tests/rewind_codec_test.c']
def run(command):
    subprocess.run([str(x) for x in command], check=True, capture_output=True, text=True, timeout=60)

with tempfile.TemporaryDirectory(prefix='seedless-rewind-test-') as folder:
    temp = Path(folder)
    include = temp / 'include'
    windows = os.name == 'nt'
    if windows:
        if not args.ndk: parser.error('Windows requires --ndk or ANDROID_NDK_HOME')
        bin_dir = Path(args.ndk) / 'toolchains/llvm/prebuilt/windows-x86_64/bin'
        cc, linker = bin_dir / 'clang.exe', bin_dir / 'ld.lld.exe'
        fixtures['stdlib.h'] = '#include <stddef.h>\nvoid *malloc(size_t n);\nvoid *realloc(void *p,size_t n);\nvoid free(void *p);\n'
        fixtures['string.h'] = '#include <stddef.h>\nvoid *memmove(void *,const void *,size_t);\nvoid *memcpy(void *,const void *,size_t);\nvoid *memset(void *,int,size_t);\nint memcmp(const void *,const void *,size_t);\n'
        fixtures['pthread.h'] = (root / 'tools/host/pthread_win.h').read_text()
        fixtures['zlib.h'] = (root / 'tools/host/zlib_win.h').read_text()
        adapter = temp / 'windows_heap.c'
        adapter.write_text((root / 'tools/host/windows_heap.c').read_text(), encoding='utf-8')
        sources.append(adapter)
    for name, contents in fixtures.items():
        target = include / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(contents, encoding='utf-8')
    include_flags = ['-DSEEDLESS_REWIND_TEST=1', '-I', include, '-I', root / 'core/include', '-I', root / 'core/src', '-I', root / 'core/src/core']
    if windows:
        objects = []
        for i, source in enumerate(sources):
            obj = temp / f'{i}.obj'
            run([cc, '--target=x86_64-pc-windows-msvc', '-ffreestanding', '-fno-builtin', '-O1', '-fno-stack-protector', *include_flags, '-c', source, '-o', obj])
            objects.append(obj)
        exports = temp / 'kernel32.def'
        exports.write_text('LIBRARY KERNEL32.dll\nEXPORTS\nGetProcessHeap\nHeapAlloc\nHeapReAlloc\nHeapFree\nAcquireSRWLockExclusive\nReleaseSRWLockExclusive\nWakeConditionVariable\nWakeAllConditionVariable\nSleepConditionVariableSRW\nCreateThread\nWaitForSingleObject\nCloseHandle\n', encoding='utf-8')
        imp = temp / 'kernel32.lib'
        run([linker, '-flavor', 'link', '/dll', '/noentry', '/nodefaultlib', '/force:unresolved', '/def:' + str(exports), '/machine:x64', '/implib:' + str(imp), '/out:' + str(temp / 'KERNEL32.dll')])
        zdef = temp / 'zlib.def'
        zdef.write_text('LIBRARY zlib1.dll\nEXPORTS\ncrc32\ncompressBound\ncompress2\nuncompress\n', encoding='utf-8')
        zimp = temp / 'zlib.lib'
        run([linker, '-flavor', 'link', '/dll', '/noentry', '/nodefaultlib', '/force:unresolved', '/def:' + str(zdef), '/machine:x64', '/implib:' + str(zimp), '/out:' + str(temp / 'zlib1.dll')])
        if not Path(args.zlib_dll).is_file(): parser.error('Provide a standard Windows x64 zlib DLL with --zlib-dll')
        zlib_library = ctypes.CDLL(str(Path(args.zlib_dll).resolve()))
        library = temp / 'rewind_test.dll'
        run([linker, '-flavor', 'link', '/dll', '/noentry', '/nodefaultlib', '/out:' + str(library), '/export:rewind_ring_test', '/export:rewind_manager_test', '/export:rewind_manager_async_test', '/export:rewind_worker_test', '/export:rewind_codec_test', '/export:test_outstanding_allocations', *objects, imp, zimp])
    else:
        library = temp / 'rewind_test.so'
        run([args.cc, '-shared', '-fPIC', '-O1', *include_flags, *sources, '-pthread', '-lz', '-o', library])
    dll = ctypes.CDLL(str(library))
    results = {}
    for name in ('rewind_ring_test', 'rewind_codec_test', 'rewind_worker_test', 'rewind_manager_test', 'rewind_manager_async_test'):
        test = getattr(dll, name); test.argtypes = []; test.restype = ctypes.c_int
        line = test()
        assert line == 0, f'{name} failed at source line {line}'
        results[name] = 'passed'
    if windows:
        assert dll.test_outstanding_allocations() == 0, 'Native history allocation leak'
        results['outstandingAllocations'] = 0
    print(json.dumps({'tests': results, 'ringRandomOperations': 10000, 'workerReuseCycles': 512,
                      'scope': 'Production ring, codec, background worker and scheduler; fake machine and clock; real host zlib; not Android gameplay'}, indent=2))
    # Release the DLL before Windows removes the temporary directory.
    if windows:
        kernel = ctypes.WinDLL('kernel32', use_last_error=True)
        kernel.FreeLibrary.argtypes = [ctypes.c_void_p]
        kernel.FreeLibrary.restype = ctypes.c_int
        assert kernel.FreeLibrary(dll._handle)
        assert kernel.FreeLibrary(zlib_library._handle)
