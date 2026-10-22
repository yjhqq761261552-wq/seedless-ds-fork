"""Compile the production GLSL scalar maps as C and compare with area coverage.
No GPU/device is used. This checks coordinate math, not texture derivatives,
driver execution, color/gamma output, or the bounded downscaling approximation.
Windows: --ndk <NDK>; other hosts: --cc cc.
"""
from pathlib import Path
import argparse,ctypes as C,json,math,os,subprocess,tempfile

p=argparse.ArgumentParser();p.add_argument('--ndk');p.add_argument('--cc',default='cc');a=p.parse_args()
r=Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory(prefix='seedless-filter-math-') as folder:
 t=Path(folder);source=t/'filter_math.c'
 # GLSL/C adapters implement floor/max/clamp over the tested image coordinate range.
 source.write_text('''static float host_floor(float x) {int i=(int)x;return (float)(i-(x<(float)i));}
static float host_max(float a,float b) {return a>b?a:b;}
static float host_clamp(float x,float a,float b) {return x<a?a:(x>b?b:x);}
#define floor host_floor
#define max host_max
#define clamp host_clamp
#include "filter_math.glsl"
int _fltused=0;
''',encoding='utf8')
 def run(args):
  x=subprocess.run([str(v) for v in args],capture_output=True,text=True,timeout=60)
  if x.returncode:raise RuntimeError(x.stdout+x.stderr)
 names=['filter_sharp_bilinear','filter_shimmerless','filter_pixel_aa','filter_quilez']
 if os.name=='nt':
  assert a.ndk,'--ndk required on Windows'
  b=Path(a.ndk)/'toolchains/llvm/prebuilt/windows-x86_64/bin'
  run([b/'clang.exe','--target=x86_64-pc-windows-msvc','-ffreestanding','-fno-builtin','-fno-stack-protector','-O2','-I',r/'app/src/main/cpp/vulkan','-c',source,'-o',t/'math.obj'])
  library=t/'filter_math.dll'
  run([b/'ld.lld.exe','-flavor','link','/dll','/noentry','/nodefaultlib','/out:'+str(library),*['/export:'+n for n in names],t/'math.obj'])
 else:
  library=t/'filter_math.so'
  run([a.cc,'-shared','-fPIC','-O2','-I',r/'app/src/main/cpp/vulkan',source,'-o',library])
 dll=C.CDLL(str(library))
 for n in names:
  fn=getattr(dll,n);fn.argtypes=[C.c_float] if n=='filter_quilez' else [C.c_float,C.c_float];fn.restype=C.c_float
 def sample(values,coord):
  pos=coord-.5;i=math.floor(pos);f=pos-i
  at=lambda k:values[min(max(k,0),len(values)-1)]
  return at(i)*(1-f)+at(i+1)*f
 def area(values,center,footprint):
  left=center-footprint/2;right=center+footprint/2;total=0
  for i in range(math.floor(left),math.ceil(right)):
   overlap=max(0,min(right,i+1)-max(left,i))
   total+=overlap*values[min(max(i,0),len(values)-1)]
  return total/footprint
 patterns=[[0.,1.]*32,[float(i==31) for i in range(64)],[i/63 for i in range(64)],[.37]*64]
 count=0;max_error=0.
 for footprint in [1.,.99,.8,2/3,.5,.4,.25,.125]:
  for center in [-.2,.01,.5,.999,1.125,15.0,15.125,15.333,31.5,31.875,32.1,63.9,64.1]:
   mapped=dll.filter_shimmerless(center,footprint)
   for values in patterns:
    error=abs(sample(values,mapped)-area(values,center,footprint));max_error=max(max_error,error)
    assert error<.0001,(center,footprint,error)
    count+=1
 # Integer magnification: coverage never mixes neighboring texels.
 integer_samples=0
 for scale in [1,2,3,4,5,7,8]:
  for x in range(16*scale):
   coord=dll.filter_shimmerless((x+.5)/scale,1/scale)
   assert abs(sample(patterns[0],coord)-patterns[0][x//scale])<.0001
   integer_samples+=1
 checks=0
 for period in [-2,0,7,63,1023]:
  for scale in [1.,1.25,2.,2.5,4.,8.]:
   last_sharp=last_aa=last_quilez=-float('inf')
   for step in range(101):
    frac=step/100;p0=period+frac
    sb=dll.filter_sharp_bilinear(p0,scale)
    assert period-.0002<=sb<=period+1.0002 and sb>=last_sharp-.0002
    if scale<2:assert abs(sb-p0)<.0002
    pa=dll.filter_pixel_aa(p0+.5,scale)
    assert period+.4998<=pa<=period+1.5002 and pa>=last_aa-.0002
    q=dll.filter_quilez(p0-.5)
    assert period-.5002<=q<=period+.5002 and q>=last_quilez-.0002
    last_sharp,last_aa,last_quilez=sb,pa,q;checks+=1
 print(json.dumps({'source':'production filter_math.glsl compiled verbatim with C scalar adapters','exactBoxCoverageComparisons':count,'maximumAbsoluteCoverageError':max_error,'integerScaleSamples':integer_samples,'coordinateRangeAndMonotonicityChecks':checks,'tests':{'fractionalOriginBoxCoverage':'passed','integerPixelPreservation':'passed','sharpBilinearLinearLimit':'passed','pixelAaTransitionBounds':'passed','quilezMonotonicity':'passed'},'scope':'Host coordinate math; no GPU derivatives, sampler/color output or physical display verification'},indent=2))
 if os.name=='nt':
  kernel=C.WinDLL('kernel32');kernel.FreeLibrary.argtypes=[C.c_void_p];assert kernel.FreeLibrary(dll._handle)
