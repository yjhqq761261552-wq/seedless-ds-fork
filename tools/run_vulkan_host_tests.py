"""Test production frame copying and WSI selection without an Android GPU.
Windows: --ndk <NDK 25.2 root>; other hosts: --cc/--cxx and --vulkan-include.
The core layout is a fixture; snapshot/pixel code and present_policy.h are real.
Tests cover pixel order, scaling, RGB565, capacity guards, concurrent publishing,
metadata latching, present modes/image bounds and pre-rotation. Not a WSI test.
"""
from pathlib import Path
import argparse, ctypes as C, json, os, subprocess, tempfile, threading, time

parser=argparse.ArgumentParser()
parser.add_argument('--ndk',default=os.environ.get('ANDROID_NDK_HOME'))
parser.add_argument('--cc',default='cc')
parser.add_argument('--cxx',default='c++')
parser.add_argument('--vulkan-include')
args=parser.parse_args()
root=Path(__file__).resolve().parents[1]
windows=os.name=='nt'

fixtures={
    'seedlessds/metrics.h': '#include <stdint.h>\nenum { NDS_METRIC_COPY, NDS_METRIC_REWIND };\nstatic uint64_t nds_metrics_now_us(void) {return 0;}\nstatic void nds_metrics_record(unsigned kind,uint64_t us) {(void)kind;(void)us;}\n',

'frontend/video_out_gl.h':'''#include <stdint.h>
#include <pthread.h>
typedef struct { void *page_memory; void *page[2]; unsigned page_parity, screen_mode[2], bits_per_pixel; pthread_mutex_t mutex[1]; } video_out_gl_t;
extern video_out_gl_t mock_video;
#define VIDEO_OUT_GL (&mock_video)
#define VIDEO_OUT_GL_SCREEN_PAGE_BYTES 0xc0000u
''',
'frontend/frontend.h':'''typedef struct { unsigned frame_state; } frontend_t;
extern frontend_t mock_frontend;
#define FRONTEND (&mock_frontend)
''',
'hires_runtime.h':'''extern unsigned recon_native, recon_scale_pages, recon_page_scale_tag[2];''',
'seedlessds/platform.h':'''typedef struct { struct { int (*mutex_lock)(void *); int (*mutex_unlock)(void *); } threads; } nds_platform_t;
const nds_platform_t *nds_platform_default(void);
''',
'string.h':'''#include <stddef.h>
void *memcpy(void *, const void *, size_t);
void *memset(void *, int, size_t);
''',
}
mock='''#include <stddef.h>
#include <stdint.h>
#include <string.h>
#include "seedlessds/platform.h"
#include "seedlessds/video_snapshot.h"
#include "frontend/video_out_gl.h"
#include "frontend/frontend.h"
unsigned recon_native=1, recon_scale_pages=2, recon_page_scale_tag[2]={2,2};
frontend_t mock_frontend;
static unsigned char pages[2][2*0xc0000];
video_out_gl_t mock_video={pages,{pages[0],pages[1]},0,{1,1},32,{PTHREAD_MUTEX_INITIALIZER}};
static int lock(void *m) { return pthread_mutex_lock(m); }
static int unlock(void *m) { return pthread_mutex_unlock(m); }
static const nds_platform_t platform={{lock,unlock}};
const nds_platform_t *nds_platform_default(void) { return &platform; }
void *memcpy(void *d,const void *s,size_t n) { unsigned char *a=d; const unsigned char *b=s;for(size_t i=0;i<n;++i)a[i]=b[i];return d; }
void *memset(void *d,int v,size_t n) { unsigned char *a=d;for(size_t i=0;i<n;++i)a[i]=(unsigned char)v;return d; }
void nds_video_wake(void) {}
void test_loading(unsigned loading) { mock_frontend.frame_state=loading; }
uint64_t test_publish(void) {
    uint64_t next=nds_video_latest_serial()+1;
    unsigned scale=1+(unsigned)(next&1u), page=mock_video.page_parity;
    // Emulation writes the active page outside the flip mutex, like the core.
    memset(pages[page],(int)(next&255u),2*0xc0000);
    lock(mock_video.mutex);
    mock_video.screen_mode[0]=(next%3)!=0; mock_video.screen_mode[1]=(next%5)!=0;
    mock_video.bits_per_pixel=32;
    recon_page_scale_tag[page]=scale;
    mock_video.page_parity=page^1;
    nds_video_published_locked();
    unlock(mock_video.mutex);
    return next;
}
void test_disturb_live_metadata(void) {
    lock(mock_video.mutex);
    mock_video.screen_mode[0]=mock_video.screen_mode[1]=99;
    mock_video.bits_per_pixel=7;
    unlock(mock_video.mutex);
}
'''
policy='''#include "present_policy.h"
#include "filter_policy.h"
extern "C" {
int _fltused=0;
int test_linear_filter(int mode) {return seedless_vk::linear_filter(mode);}
int test_choose_normal(const int *m, unsigned n) {return seedless_vk::choose_mode((const VkPresentModeKHR *)m,n,false);}
int test_choose(const int *m, unsigned n) {return seedless_vk::choose_mode((const VkPresentModeKHR *)m,n);}
unsigned test_count(unsigned min,unsigned max,int mode) {return seedless_vk::image_count(min,max,(VkPresentModeKHR)mode);}
void test_rotate(float *x,float *y,int rotate) {seedless_vk::rotate_point(*x,*y,rotate);}
int test_rotation(unsigned transform) {return seedless_vk::rotation((VkSurfaceTransformFlagBitsKHR)transform);}
}
'''

def run(command):
    p=subprocess.run([str(x) for x in command],capture_output=True,text=True,timeout=60)
    if p.returncode:raise RuntimeError(p.stdout+p.stderr)

class Snapshot(C.Structure):
    _fields_=[('serial',C.c_uint64),('width',C.c_uint32*2),('height',C.c_uint32*2),
              ('offset',C.c_size_t*2),('bytes',C.c_size_t),('published_us',C.c_uint64)]

with tempfile.TemporaryDirectory(prefix='seedless-vulkan-test-') as tmp:
    temp=Path(tmp); inc=temp/'include'
    if windows:
        if not args.ndk:parser.error('Windows requires --ndk')
        ndk=Path(args.ndk); bin_dir=ndk/'toolchains/llvm/prebuilt/windows-x86_64/bin'
        cc,cxx,linker=bin_dir/'clang.exe',bin_dir/'clang++.exe',bin_dir/'ld.lld.exe'
        vkinc=ndk/'sources/third_party/vulkan/src/include'
        fixtures['pthread.h']='#ifndef VK_HOST_MUTEX\n#define VK_HOST_MUTEX\ntypedef struct { void *value; } pthread_mutex_t;\n#define PTHREAD_MUTEX_INITIALIZER {0}\n__declspec(dllimport) void AcquireSRWLockExclusive(void *);\n__declspec(dllimport) void ReleaseSRWLockExclusive(void *);\nstatic inline int pthread_mutex_lock(pthread_mutex_t *m) { AcquireSRWLockExclusive(m);return 0; }\nstatic inline int pthread_mutex_unlock(pthread_mutex_t *m) { ReleaseSRWLockExclusive(m);return 0; }\n#endif\n'
    else:
        cc,cxx=args.cc,args.cxx
        vkinc=Path(args.vulkan_include or '/usr/include')
    for name,text in fixtures.items():
        p=inc/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text)
    (temp/'mock.c').write_text(mock);(temp/'policy.cpp').write_text(policy)
    flags=['-I',inc,'-I',root/'core/include','-I',root/'core/src/frontend',
           '-I',root/'app/src/main/cpp/vulkan','-I',vkinc]
    sources=[root/'core/src/frontend/video_pixels.c',root/'core/src/frontend/video_snapshot.c',
             temp/'mock.c',temp/'policy.cpp']
    if windows:
        objects=[]
        for i,p in enumerate(sources):
            obj=temp/f'{i}.obj'
            run([cxx if p.suffix=='.cpp' else cc,'--target=x86_64-pc-windows-msvc',
                 '-ffreestanding','-fno-builtin','-fno-stack-protector','-fno-exceptions','-fno-rtti','-O2',*flags,'-c',p,'-o',obj])
            objects.append(obj)
        definition=temp/'kernel.def'
        definition.write_text('LIBRARY KERNEL32.dll\nEXPORTS\nAcquireSRWLockExclusive\nReleaseSRWLockExclusive\n')
        imp=temp/'kernel.lib'
        run([linker,'-flavor','link','/dll','/noentry','/nodefaultlib','/force:unresolved',
             '/def:'+str(definition),'/machine:x64','/implib:'+str(imp),'/out:'+str(temp/'kernel32.dll')])
        library=temp/'vulkan_test.dll'
        names=['nds_video_pack_rgba','nds_video_downsample_base','nds_video_copy_latest','nds_video_latest_serial',
               'test_publish','test_loading','test_disturb_live_metadata','test_linear_filter','test_choose_normal','test_choose','test_count','test_rotate','test_rotation']
        run([linker,'-flavor','link','/dll','/noentry','/nodefaultlib','/out:'+str(library),
             *['/export:'+n for n in names],*objects,imp])
    else:
        objects=[]
        for i,p in enumerate(sources):
            obj=temp/f'{i}.o'
            run([cxx if p.suffix=='.cpp' else cc,'-fPIC','-fno-builtin','-O2',*flags,'-c',p,'-o',obj]);objects.append(obj)
        library=temp/'vulkan_test.so';run([cxx,'-shared',*objects,'-pthread','-o',library])
    dll=C.CDLL(str(library))
    dll.nds_video_pack_rgba.argtypes=[C.c_void_p,C.c_size_t,C.c_void_p,C.c_size_t,C.c_uint,C.POINTER(C.c_uint),C.c_uint,C.POINTER(Snapshot)]
    dll.nds_video_pack_rgba.restype=C.c_int
    dll.nds_video_copy_latest.argtypes=[C.c_void_p,C.c_size_t,C.c_uint64,C.POINTER(Snapshot)]
    dll.nds_video_copy_latest.restype=C.c_int
    dll.nds_video_latest_serial.restype=C.c_uint64;dll.test_publish.restype=C.c_uint64
    mode=(C.c_uint*2)(1,1)
    dll.nds_video_downsample_base.argtypes=[C.c_void_p,C.c_void_p,C.c_void_p,C.c_size_t,C.c_uint,C.POINTER(C.c_uint),C.c_uint,C.c_int]
    for scale in range(1,9):
        stride=256*192*scale*scale*4
        # Packed source dimensions are per-screen, including mixed 1x/hires.
        for mode_values in [(0,0),(0,1),(1,0),(1,1)]:
            modes=(C.c_uint*2)(*mode_values)
            for bpp in [16,32]:
                page=(C.c_ubyte*(stride*2))()
                for screen in range(2):
                    n=scale if mode_values[screen] else 1
                    for y in range(192):
                        for x in range(256):
                            off=screen*stride+(y*n*256*n+x*n)*(bpp//8)
                            if bpp==32:
                                page[off]=x;page[off+1]=y;page[off+2]=screen*255;page[off+3]=255
                            else:
                                value=((x>>3)<<11)|((y>>2)<<5)|(31 if screen else 0)
                                page[off]=value&255;page[off+1]=value>>8
                a=(C.c_uint32*(256*192))();b=(C.c_uint32*(256*192))()
                assert dll.nds_video_downsample_base(a,b,page,stride,scale,modes,bpp,1)==1
                for screen,out in enumerate([a,b]):
                    for y in [0,1,95,191]:
                        for x in range(256):
                            red=x if bpp==32 else ((x>>3)<<3)|((x>>3)>>2)
                            green=y if bpp==32 else ((y>>2)<<2)|((y>>2)>>4)
                            expected=0xff000000|(red<<16)|(green<<8)|(screen*255)
                            assert out[y*256+x]==expected,(scale,mode_values,bpp,screen,x,y,hex(out[y*256+x]),hex(expected))
                a16=(C.c_uint16*(256*192))();b16=(C.c_uint16*(256*192))()
                assert dll.nds_video_downsample_base(a16,b16,page,stride,scale,modes,bpp,0)==1
                for screen,out in enumerate([a16,b16]):
                    for x in range(256):
                        expected=((x>>3)<<11)|(31 if screen else 0) if bpp==16 else (x>>3)|(screen*31<<11)
                        assert out[x]==expected
    assert dll.nds_video_downsample_base(a,b,page,stride,9,modes,32,1)==0
    assert dll.nds_video_downsample_base(a,b,page,100,8,modes,32,1)==0
    # Exercise every supported scale with independent top/bottom byte patterns.
    for scale in range(1,9):
        stride=0x30000*scale*scale
        src=C.create_string_buffer(bytes([17])*stride+bytes([231])*stride)
        dest=C.create_string_buffer(2*stride+32);C.memset(dest,0x5a,C.sizeof(dest));info=Snapshot()
        assert dll.nds_video_pack_rgba(dest,2*stride,src,stride,scale,mode,32,C.byref(info))==1
        assert list(info.width)==[256*scale]*2 and list(info.height)==[192*scale]*2
        assert info.bytes==2*stride and list(info.offset)==[0,stride]
        data=dest.raw;assert data[:stride]==bytes([17])*stride and data[stride:2*stride]==bytes([231])*stride
        assert data[-32:]==bytes([0x5a])*32
    # All 65,536 RGB565 values, with full-range RGB endpoints.
    pixels=256*192;raw=bytearray(pixels*4)
    for i in range(pixels*2):raw[2*i:2*i+2]=(i&65535).to_bytes(2,'little')
    src=C.create_string_buffer(bytes(raw));dest=C.create_string_buffer(pixels*8);info=Snapshot()
    assert dll.nds_video_pack_rgba(dest,C.sizeof(dest),src,pixels*2,1,mode,16,C.byref(info))==1
    data=dest.raw
    for i in range(pixels*2):
        value=i&65535;r=value>>11;g=(value>>5)&63;b=value&31
        assert data[i*4:i*4+4]==bytes([(r<<3)|(r>>2),(g<<2)|(g>>4),(b<<3)|(b>>2),255])
    # Invalid input must not write the destination or result metadata.
    dest=C.create_string_buffer(bytes([0x63])*128);info=Snapshot();info.serial=1234
    for scale,bpp,stride,capacity in [(0,32,0xc0000,128),(9,32,0xc0000,128),(1,24,0xc0000,128),(1,32,3,128),(1,32,0xc0000,127)]:
        assert dll.nds_video_pack_rgba(dest,capacity,src,stride,scale,mode,bpp,C.byref(info))==0
        assert dest.raw[:128]==bytes([0x63])*128 and info.serial==1234
    # Real production copy API selects the completed page and its latched metadata.
    capacity=0x180000;dest=C.create_string_buffer(capacity);info=Snapshot()
    assert dll.nds_video_copy_latest(dest,capacity,0,C.byref(info))==0
    serial=dll.test_publish();dll.test_disturb_live_metadata()
    assert dll.nds_video_copy_latest(dest,capacity,0,C.byref(info))==1 and info.serial==serial
    assert dll.nds_video_copy_latest(dest,capacity,serial,C.byref(info))==0
    dll.test_loading(1);assert dll.nds_video_copy_latest(dest,capacity,0,C.byref(info))==0;dll.test_loading(0)
    failures=[];counts=[0,0];gate=threading.Barrier(3)
    def writer():
        gate.wait()
        for _ in range(300):dll.test_publish();time.sleep(0)
    def reader(index):
        try:
            buf=C.create_string_buffer(capacity);item=Snapshot();after=0;gate.wait()
            for _ in range(1000):
                if not dll.nds_video_copy_latest(buf,capacity,after,C.byref(item)):continue
                after=item.serial;scale=1+(after&1)
                expected=[256*(scale if after%3 else 1),256*(scale if after%5 else 1)]
                assert list(item.width)==expected
                assert list(item.height)==[v//256*192 for v in expected]
                data=C.string_at(buf,item.bytes);assert data.count(bytes([after&255]))==len(data)
                counts[index]+=1
        except BaseException as error:failures.append(repr(error))
    jobs=[threading.Thread(target=writer),threading.Thread(target=reader,args=(0,)),threading.Thread(target=reader,args=(1,))]
    for job in jobs:job.start()
    for job in jobs:job.join(timeout=30);assert not job.is_alive(),'snapshot publication deadlock'
    assert not failures,failures
    assert min(counts)>0
    dll.test_choose.argtypes=[C.POINTER(C.c_int),C.c_uint];dll.test_choose.restype=C.c_int
    for modes,expected in [([2,0,1,3],1),([2,0],0),([2,3],3),([2],2),([],2)]:
        assert dll.test_choose((C.c_int*len(modes))(*modes),len(modes))==expected
    dll.test_linear_filter.argtypes=[C.c_int];dll.test_linear_filter.restype=C.c_int
    for mode in range(16):assert dll.test_linear_filter(mode)==int(mode in [1,2,7,8,9,10,14,15])
    dll.test_choose_normal.argtypes=[C.POINTER(C.c_int),C.c_uint];dll.test_choose_normal.restype=C.c_int
    for modes in [[0,1,2,3],[1,2],[2],[3,2]]:
        assert dll.test_choose_normal((C.c_int*len(modes))(*modes),len(modes))==2
    for minimum,maximum,mode,expected in [(1,0,1,3),(2,0,2,2),(3,3,1,3),(1,2,1,2),(1,1,2,1),(4,0,1,4)]:
        assert dll.test_count(minimum,maximum,mode)==expected
    dll.test_rotate.argtypes=[C.POINTER(C.c_float),C.POINTER(C.c_float),C.c_int]
    for rotate,expected in [(0,(-1,-1)),(1,(1,-1)),(2,(1,1)),(3,(-1,1))]:
        x=C.c_float(-1);y=C.c_float(-1);dll.test_rotate(C.byref(x),C.byref(y),rotate)
        assert (x.value,y.value)==expected
    for transform,expected in [(1,0),(2,1),(4,2),(8,3),(16,-1),(256,-1)]:assert dll.test_rotation(transform)==expected
    print(json.dumps({'scalesTested':list(range(1,9)),'rgb565Values':65536,
        'concurrentPublications':300,'concurrentReaders':2,'coherentSnapshots':counts,
        'tests':{'baseScreenshotAllScalesModesAndFormats':'passed','rgbaPacking':'passed','rgb565Conversion':'passed','capacityGuards':'passed',
                 'completedPageAndMetadata':'passed','loadingAndNoNewFrame':'passed',
                 'concurrentSnapshots':'passed','presentModeAndImageCount':'passed','normalVulkanUsesFifo':'passed','lcdUsesNearestSampler':'passed','interpolationSamplerPolicy':'passed','preRotation':'passed'},
        'scope':'Production pixel/snapshot code and WSI policy; fixture core state and host mutex; no Android Vulkan driver execution'},indent=2))
    if windows:
        kernel=C.WinDLL('kernel32',use_last_error=True);kernel.FreeLibrary.argtypes=[C.c_void_p]
        assert kernel.FreeLibrary(dll._handle)
