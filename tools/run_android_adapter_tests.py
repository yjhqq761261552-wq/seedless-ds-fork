"""Build isolated CPU/driver-adapter tests and run them through ADB.
No ROM or application data is opened; binaries are pushed to a fixed test folder
under /data/local/tmp. The fake audio library is scoped to its test child process.
"""
from pathlib import Path
import argparse,json,os,subprocess,tempfile

p=argparse.ArgumentParser()
p.add_argument('--ndk',required=True)
p.add_argument('--adb',required=True)
p.add_argument('--serial',required=True)
a=p.parse_args()
r=Path(__file__).resolve().parents[1]
bin=Path(a.ndk)/'toolchains/llvm/prebuilt'/('windows-x86_64' if os.name=='nt' else 'linux-x86_64')/'bin'
cc=bin/('clang.exe' if os.name=='nt' else 'clang')
cxx=bin/('clang++.exe' if os.name=='nt' else 'clang++')
def run(args):
    result=subprocess.run([str(v) for v in args],capture_output=True,text=True,timeout=60)
    if result.returncode:raise RuntimeError(result.stdout+result.stderr)
    return result.stdout.strip()
def adb(*args):return run([a.adb,'-s',a.serial,*args])
with tempfile.TemporaryDirectory(prefix='seedless-android-adapters-') as folder:
    t=Path(folder)
    include=['-I',r/'core/include','-I',r/'core/src/platform/android','-I',r/'app/src/main/cpp/vulkan']
    flags=['--target=aarch64-linux-android21','-O2',*include]
    run([cc,'--target=aarch64-linux-android28','-O2','-Werror=incompatible-pointer-types','-shared','-fPIC','-Wl,-soname,libaaudio.so',r/'core/tests/audio_driver_fixture.c','-o',t/'libaaudio.so'])
    run([cc,*flags,r/'core/tests/audio_adapter_test.c',r/'core/src/platform/android/low_latency_audio.c',r/'core/src/platform/android/pcm_ring.c',r/'core/src/core/metrics.c','-ldl','-llog','-o',t/'audio_adapter_test'])
    run([cxx,*flags,'-std=c++17','-static-libstdc++',r/'core/tests/frame_packet_test.cpp','-o',t/'frame_packet_test'])
    run([cc,*flags,r/'core/tests/video_wait_test.c',r/'core/src/frontend/video_wait.c','-o',t/'video_wait_test'])
    remote='/data/local/tmp/seedless108tests'
    adb('shell','mkdir','-p',remote)
    adb('push',t/'libaaudio.so',t/'audio_adapter_test',t/'frame_packet_test',t/'video_wait_test',remote+'/')
    for name in ['audio_adapter_test','frame_packet_test','video_wait_test']:adb('shell','chmod','700',remote+'/'+name)
    results=[]
    results.append(json.loads(adb('shell',f'LD_LIBRARY_PATH={remote} {remote}/audio_adapter_test {remote}/libaaudio.so')))
    for name in ['frame_packet_test','video_wait_test']:results.append(json.loads(adb('shell',remote+'/'+name)))
    print(json.dumps({'serial':a.serial,'tests':results},indent=2))
