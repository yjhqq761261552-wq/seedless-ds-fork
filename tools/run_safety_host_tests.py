"""Production save writer/admission, mailbox, resampler and histogram regression tests.
Windows: --ndk <NDK>. POSIX: --cc cc. File I/O, serialization and clocks are
fault-injection adapters. No ROM, save folder, device or Android audio driver is used.
"""
from pathlib import Path
import argparse,ctypes as C,json,os,subprocess,tempfile,threading,math
p=argparse.ArgumentParser();p.add_argument('--ndk',default=os.environ.get('ANDROID_NDK_HOME'));p.add_argument('--cc',default='cc');args=p.parse_args()
r=Path(__file__).resolve().parents[1]
def function(file,marker):
 s=file.read_text(encoding='utf8');a=s.index(marker);b=s.index('{',a)+1;depth=1
 while depth:
  if s[b]=='{':depth+=1
  elif s[b]=='}':depth-=1
  b+=1
 return s[a:b]
pre=r'''
#include <stdint.h>
#include <stddef.h>
#include <string.h>
#include "core/state_format.h"
#include "frontend/frontend.h"
#include "seedlessds/state_request.h"
#include "seedlessds/metrics.h"
#include "time.h"
state_write_work_t state_write_work;
static void *rd_ptr(const void *p){return *(void *const*)p;}
static const uint8_t *rd_ptr_u8(const void *p){return *(const uint8_t *const*)p;}
static uint8_t rd8(const void *p){return *(const uint8_t*)p;}
static uint64_t rd64(const void *p){return *(const uint64_t*)p;}
static void wr_ptr(void *p,const void *v){*(const void**)p=v;}
static void wr32(void *p,uint32_t v){*(uint32_t*)p=v;}
static void wr64(void *p,uint64_t v){*(uint64_t*)p=v;}
void *memcpy(void *d,const void *s,size_t n){unsigned char *a=d;const unsigned char *b=s;for(size_t i=0;i<n;i++)a[i]=b[i];return d;}
void *memset(void *d,int c,size_t n){unsigned char *a=d;for(size_t i=0;i<n;i++)a[i]=(unsigned char)c;return d;}
static unsigned failures,write_at,writes,renames,opens,closes;
static unsigned char raw[0x680000],compressed[0x680100];
static size_t fake_write(const void *p,size_t n,size_t count,void *f){(void)p;(void)n;(void)f;++writes;return (failures==1&&(!write_at||writes==write_at))?0:count;}
static int fake_close(void *f){(void)f;++closes;return failures==2?-1:0;}
int fflush(void *f){(void)f;return failures==3?-1:0;}
int ferror(void *f){(void)f;return failures==4?1:0;}
static void fake_free(void *v){(void)v;}
static void *fake_malloc(size_t n){return failures==5?NULL:(n<=sizeof raw?raw:compressed);}
static unsigned long fake_bound(unsigned long n){return n+64;}
static int fake_compress(void *d,unsigned long *n,const void *s,unsigned long size){(void)d;(void)s;(void)size;*n=32;return failures==6?-1:0;}
static int fake_sleep(unsigned usec){(void)usec;return 0;}
static char *fake_strncpy(char *d,const char *s,size_t n){size_t i=0;while(i<n&&s[i]){d[i]=s[i];++i;}while(i<n)d[i++]=0;return d;}
static void *sym_libc_fwrite=fake_write,*sym_libc_free=fake_free,*sym_libc_fclose=fake_close,*sym_libc_compressBound=fake_bound,*sym_libc_malloc=fake_malloc,*sym_libc_compress=fake_compress,*sym_libc_memcpy=memcpy,*sym_libc_usleep=fake_sleep,*sym_libc_strncpy=fake_strncpy;
typedef size_t(*fn_fwrite)(const void*,size_t,size_t,void*);
typedef void(*fn_free_26)(void*);typedef int(*fn_fclose)(void*);
typedef unsigned long(*fn_compress_bound)(unsigned long);typedef void*(*fn_malloc_26)(size_t);typedef int(*fn_compress)(void*,unsigned long*,const void*,unsigned long);
typedef void*(*fn_malloc_29)(size_t);typedef void(*fn_free_29)(void*);typedef void*(*fn_memcpy_29)(void*,const void*,size_t);typedef int(*fn_usleep)(unsigned);typedef char*(*fn_strncpy)(char*,const char*,size_t);typedef void*(*fn_start_thread)(void*);typedef int(*fn_pthread_create)(void*,const void*,fn_start_thread,void*);
int str_vsprintf_caller_limit_4(char *d,size_t n,const char *fmt,...){(void)n;(void)fmt;d[0]='x';d[1]=0;return 1;}
int files_jni_env_invoke_and_release(const char *a,const char *b){(void)a;(void)b;++renames;return failures==7?-1:0;}
int platform_thread_create(void *t,const void *attr,void *(*run)(void*),void *arg){(void)attr;(void)run;(void)arg;*(uint64_t*)t=1;return failures==9?-1:0;}
int pthread_detach(uint64_t t){(void)t;return 0;}
typedef struct{uint64_t frame;struct{unsigned savestates_enabled,savestate_copies,savestate_extra;}config;struct{struct{unsigned mask;}backup;unsigned game_code;}cart;struct{unsigned bios_flags;}bus;}nds_t;
static nds_t machine;
frontend_t mock_frontend={&machine,0,0};
void input_pause_notify(void){}
void input_state_reset(void){}
#define SS(p) ((state_stream_t*)(p))
static void copy_bytes(void *d,const void *s,size_t n){memcpy(d,s,n);}
static uint8_t *state_section_open(void *stream,unsigned id){(void)id;uint8_t *p=SS(stream)->cursor;SS(stream)->cursor+=16;return p;}
static void state_section_close(void *stream,void *section){(void)stream;(void)section;}
static void state_write_spi_memory_data(void *spi,void *stream){(void)spi;SS(stream)->cursor+=32;}
static void state_write_machine_blocks(void *ctx,void *stream,unsigned version){(void)ctx;(void)version;SS(stream)->cursor+=128;}
static void *fake_open(void *user,const char *name,const char *mode){(void)user;(void)name;(void)mode;++opens;return failures==8?NULL:(void*)1;}
typedef struct{void *user;struct{void*(*open)(void*,const char*,const char*);}files;}nds_platform_t;
static const nds_platform_t platform={0,{fake_open}};
const nds_platform_t *nds_platform_default(void){return &platform;}
static uint64_t fake_clock;
int clock_gettime(int id,struct timespec *out){(void)id;out->tv_sec=(long)(fake_clock/1000000);out->tv_nsec=(long)(fake_clock%1000000)*1000;return 0;}
'''
post=r'''
int state_save_slot_with_screens(unsigned slot){(void)slot;return state_save_file((uint8_t*)&machine,"test","slot",NULL,NULL);}
int state_load_slot(void *ctx,unsigned slot,void *a,void *b,unsigned look){(void)ctx;(void)slot;(void)a;(void)b;(void)look;return failures==10?-1:0;}
void fixture_setup(unsigned fail,unsigned at,unsigned compressed_mode){
 failures=fail;write_at=at;writes=renames=opens=closes=0;
 memset(&machine,0,sizeof machine);machine.config.savestates_enabled=compressed_mode;
 __atomic_store_n(&state_write_work.pending,0,__ATOMIC_RELEASE);
 mock_frontend.quit_request=0;
}
int fixture_save(void){return state_save_slot_with_screens(0);}
void fixture_finish(void){state_write_finish(&state_write_work);}
unsigned fixture_stat(unsigned which){return which==0?opens:which==1?renames:which==2?closes:writes;}
void fixture_poll(void){nds_state_request_poll(&machine);}
void fixture_quit(unsigned quit){mock_frontend.quit_request=quit;}
void fixture_clock(uint64_t now){fake_clock=now;}
'''
fixtures={
'frontend/frontend.h':'#ifndef MOCK_FRONTEND\n#define MOCK_FRONTEND\ntypedef struct{void *machine;unsigned quit_request,lid_closed;}frontend_t;extern frontend_t mock_frontend;\n#define FRONTEND (&mock_frontend)\n#endif\n',
'core_internals.h':'#include <stdint.h>\nuint32_t state_write_pending(void);\nint state_load_slot(void*,unsigned,void*,void*,unsigned);\nint state_save_slot_with_screens(unsigned);\n',
'string.h':'#include <stddef.h>\nvoid *memcpy(void*,const void*,size_t);void *memset(void*,int,size_t);\n',
# Only the monotonic clock is replaced; histogram implementation is production.
'time.h':'#ifndef MOCK_TIME\n#define MOCK_TIME\nstruct timespec{long tv_sec,tv_nsec;};\n#define CLOCK_MONOTONIC 1\nint clock_gettime(int,struct timespec*);\n#endif\n'}
def run(cmd):
 out=subprocess.run([str(v) for v in cmd],capture_output=True,text=True,timeout=60)
 if out.returncode:raise RuntimeError(out.stdout+out.stderr)
with tempfile.TemporaryDirectory(prefix='seedless-safety-') as folder:
 t=Path(folder);inc=t/'include';win=os.name=='nt'
 if win:
  if not args.ndk:p.error('--ndk required on Windows')
  bin=Path(args.ndk)/'toolchains/llvm/prebuilt/windows-x86_64/bin';cc=bin/'clang.exe';link=bin/'ld.lld.exe'
  fixtures['pthread.h']='#ifndef TEST_MUTEX\n#define TEST_MUTEX\ntypedef struct{void *value;}pthread_mutex_t;\n#define PTHREAD_MUTEX_INITIALIZER {0}\n__declspec(dllimport) void AcquireSRWLockExclusive(void*);\n__declspec(dllimport) void ReleaseSRWLockExclusive(void*);\nstatic inline int pthread_mutex_lock(pthread_mutex_t*m){AcquireSRWLockExclusive(m);return 0;}\nstatic inline int pthread_mutex_unlock(pthread_mutex_t*m){ReleaseSRWLockExclusive(m);return 0;}\n#endif\n'
 for name,text in fixtures.items():
  f=inc/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(text,encoding='utf8')
 state=r/'core/src/core/state.c'
 (t/'fixture.c').write_text(pre+'\n'+function(state,'void *state_write_finish(')+'\n'+function(state,'uint32_t state_write_pending(')+'\n'+function(state,'int32_t state_save_file(')+'\n'+post,encoding='utf8')
 sources=[t/'fixture.c',r/'core/src/core/state_request.c',r/'core/src/frontend/input_mailbox.c',r/'core/src/platform/android/pcm_ring.c',r/'core/src/core/metrics.c',r/'core/src/core/frame_observer.c']
 names=['nds_boot_state_begin','nds_boot_state_complete','nds_boot_state_status','fixture_setup','fixture_save','fixture_finish','fixture_stat','fixture_poll','fixture_quit','fixture_clock','state_write_pending','nds_state_request','nds_state_request_status','nds_state_requests_cancel','nds_state_hold','nds_state_held','nds_state_hold_acknowledge','input_mailbox_publish','input_mailbox_read','input_mailbox_reset','pcm_ring_push','pcm_ring_render','nds_metrics_record','nds_metrics_count','nds_metrics_percentile','nds_frame_observer_set','nds_frame_observer_emit']
 flags=['-I',inc,'-I',r/'core/include','-I',r/'core/src','-I',r/'core/src/platform/android']
 if win:
  objects=[]
  for i,source in enumerate(sources):
   obj=t/f'{i}.obj';run([cc,'--target=x86_64-pc-windows-msvc','-ffreestanding','-fno-builtin','-fno-stack-protector','-O2',*flags,'-c',source,'-o',obj]);objects.append(obj)
  d=t/'kernel.def';d.write_text('LIBRARY KERNEL32.dll\nEXPORTS\nAcquireSRWLockExclusive\nReleaseSRWLockExclusive\n');imp=t/'kernel.lib'
  run([link,'-flavor','link','/dll','/noentry','/nodefaultlib','/force:unresolved','/def:'+str(d),'/machine:x64','/implib:'+str(imp),'/out:'+str(t/'kernel32.dll')])
  lib=t/'safety.dll';run([link,'-flavor','link','/dll','/noentry','/nodefaultlib','/out:'+str(lib),*['/export:'+name for name in names],*objects,imp])
 else:
  lib=t/'safety.so';run([args.cc,'-shared','-fPIC','-fno-builtin','-O2',*flags,*sources,'-pthread','-o',lib])
 dll=C.CDLL(str(lib));dll.nds_state_request.restype=C.c_uint64;dll.nds_state_request_status.argtypes=[C.c_uint64]
 saves=[]
 for compressed in [0,1]:
  for fail in range(10):
   if fail==6 and not compressed:continue
   for at in ([1,2,3] if fail==1 and compressed else [0]):
    dll.fixture_setup(fail,at,compressed);ticket=dll.nds_state_request(0,0);assert ticket
    assert dll.nds_state_request(1,0)==0
    assert dll.nds_state_request_status(ticket)==0
    dll.fixture_poll()
    start_failed=fail in [5,8,9]
    assert dll.fixture_stat(1)==0
    if not start_failed:
     assert dll.nds_state_request_status(ticket)==0
     assert dll.state_write_pending()==1
     before=dll.fixture_stat(0);assert dll.fixture_save()==-1;assert dll.fixture_stat(0)==before
     dll.fixture_finish()
    expected=1 if fail==0 else -1
    assert dll.nds_state_request_status(ticket)==expected,(compressed,fail,at)
    assert dll.fixture_stat(1)==(1 if fail in [0,7] else 0),(compressed,fail,at)
    assert dll.state_write_pending()==0
    saves.append([compressed,fail,at])
 dll.nds_boot_state_begin(-1);assert dll.nds_boot_state_status()==2
 dll.nds_boot_state_begin(0);assert dll.nds_boot_state_status()==0
 dll.nds_boot_state_complete(0);assert dll.nds_boot_state_status()==1
 dll.nds_boot_state_begin(0);dll.nds_boot_state_complete(-1);assert dll.nds_boot_state_status()==-1
 # Actual load outcome, cancellation, invalid slot, lifecycle and stale tickets.
 for fail in [0,10]:
  dll.fixture_setup(fail,0,0);ticket=dll.nds_state_request(2,1);assert ticket;dll.fixture_poll();assert dll.nds_state_request_status(ticket)==(1 if fail==0 else -1)
 dll.fixture_setup(0,0,0);ticket=dll.nds_state_request(0,0);dll.nds_state_requests_cancel();assert dll.nds_state_request_status(ticket)==-1
 assert not dll.nds_state_request(10,0);dll.fixture_quit(1);assert not dll.nds_state_request(0,0)
 class Input(C.Structure):_fields_=[('keys',C.c_uint32),('mask',C.c_uint32),('down',C.c_int),('x',C.c_int),('y',C.c_int)]
 dll.input_mailbox_publish.argtypes=[C.POINTER(Input)];dll.input_mailbox_read.restype=Input
 def publish(keys=0,down=0,x=0,y=0,mask=0):dll.input_mailbox_publish(C.byref(Input(keys,mask,down,x,y)))
 dll.input_mailbox_reset();publish(16,1,120,90);publish();v=dll.input_mailbox_read();assert(v.keys,v.down,v.x,v.y)==(16,1,120,90);v=dll.input_mailbox_read();assert(v.keys,v.down)==(0,0)
 publish(16,1,2,3);publish(16,1,240,180);v=dll.input_mailbox_read();assert(v.keys,v.down,v.x,v.y)==(16,1,240,180);assert dll.input_mailbox_read().keys==16
 publish();publish(32,0,mask=32);publish();v=dll.input_mailbox_read();assert v.keys==32 and v.mask==32
 publish(16,1,2,3);dll.input_mailbox_reset();v=dll.input_mailbox_read();assert v.keys==0 and v.down==0
 errors=[]
 def writer():
  try:
   for i in range(5000):publish(i&1023,i&1,i%256,i%192)
  except BaseException as e:errors.append(repr(e))
 def reader():
  try:
   for i in range(5000):
    v=dll.input_mailbox_read();assert 0<=v.x<256 and 0<=v.y<192 and v.down in [0,1]
  except BaseException as e:errors.append(repr(e))
 threads=[threading.Thread(target=writer),threading.Thread(target=reader)]
 for thread in threads:thread.start()
 for thread in threads:thread.join()
 assert not errors,errors
 class Ring(C.Structure):_fields_=[('read',C.c_uint32),('written',C.c_uint32),('flush',C.c_uint32),('phase',C.c_uint64),('samples',C.c_int16*(8192*2))]
 dll.pcm_ring_push.argtypes=[C.POINTER(Ring),C.POINTER(C.c_int16),C.c_uint,C.c_uint];dll.pcm_ring_render.argtypes=[C.POINTER(Ring),C.POINTER(C.c_int16),C.c_uint,C.c_uint]
 rates=[8000,44100,48000,96000,192000]
 for rate in rates:
  ring=Ring();source=(C.c_int16*(4000*2))()
  for i in range(4000):source[i*2]=12000;source[i*2+1]=-12000
  assert dll.pcm_ring_push(C.byref(ring),source,4000,8192)==4000
  frames=math.ceil(4000*rate/44100)+100;out=(C.c_int16*(frames*2))();got=dll.pcm_ring_render(C.byref(ring),out,frames,rate)
  assert abs(got-(3999*rate/44100))<2,(rate,got)
  assert ring.read<=ring.written,(rate,ring.read,ring.written)
  assert all(out[i*2]==12000 and out[i*2+1]==-12000 for i in range(got))
  assert all(v==0 for v in out[got*2:])
 ring=Ring();ring.read=ring.written=0xfffffff0;source=(C.c_int16*200)(*range(200));assert dll.pcm_ring_push(C.byref(ring),source,100,100)==100
 assert dll.pcm_ring_push(C.byref(ring),source,1,100)==0
 out=(C.c_int16*100)();assert dll.pcm_ring_render(C.byref(ring),out,50,44100)==50;assert list(out)==list(range(100))
 ring.flush=1;assert dll.pcm_ring_render(C.byref(ring),out,50,48000)==0;assert all(v==0 for v in out)
 dll.nds_metrics_count.restype=C.c_uint64;dll.nds_metrics_percentile.restype=C.c_uint64
 for i in range(100):dll.nds_metrics_record(2,1000 if i<95 else 10000)
 assert dll.nds_metrics_count(2)==100;assert dll.nds_metrics_percentile(2,95)==1024;assert dll.nds_metrics_percentile(2,99)==10112
 callback=C.CFUNCTYPE(None);observations=[]
 @callback
 def observe():observations.append(1)
 dll.nds_frame_observer_set.argtypes=[callback];dll.nds_frame_observer_set(observe)
 for i in range(120):dll.nds_frame_observer_emit()
 assert len(observations)==120
 print(json.dumps({'saveFaultCases':len(saves),'saveWriterAndAdmission':'passed','loadCompletionAndCancellation':'passed','mailboxEdgesMovesCancelAndConcurrentAccess':'passed','resamplerRates':rates,'ringOwnershipWrapOverflowAndFlush':'passed','histogramPercentiles':'passed','emulatedFrameObserverCount':len(observations),'scope':'Production code, fault-injection file/serialization/clock adapters; no Android driver or live saves'},indent=2))
 if win:
  kernel=C.WinDLL('kernel32');kernel.FreeLibrary.argtypes=[C.c_void_p];assert kernel.FreeLibrary(dll._handle)
