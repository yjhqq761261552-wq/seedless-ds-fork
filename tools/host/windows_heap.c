#include <stddef.h>
__declspec(dllimport) void *GetProcessHeap(void);
__declspec(dllimport) void *HeapAlloc(void *, unsigned long, size_t);
__declspec(dllimport) void *HeapReAlloc(void *, unsigned long, void *, size_t);
__declspec(dllimport) int HeapFree(void *, unsigned long, void *);
static int outstanding;
void *malloc(size_t n) { void *p=HeapAlloc(GetProcessHeap(),0,n);if(p)__atomic_add_fetch(&outstanding,1,__ATOMIC_RELAXED);return p; }
void free(void *p) { if(p){__atomic_sub_fetch(&outstanding,1,__ATOMIC_RELAXED);HeapFree(GetProcessHeap(),0,p);} }
void *realloc(void *p,size_t n) { if(!p)return malloc(n);return HeapReAlloc(GetProcessHeap(),0,p,n); }
void *memmove(void *out,const void *in,size_t n) { unsigned char *d=out;const unsigned char *s=in;if(d<s){for(size_t i=0;i<n;++i)d[i]=s[i];}else{while(n){--n;d[n]=s[n];}}return out; }
void *memcpy(void *out,const void *in,size_t n) { unsigned char *d=out;const unsigned char *s=in;for(size_t i=0;i<n;++i)d[i]=s[i];return out; }
void *memset(void *out,int v,size_t n) { unsigned char *d=out;for(size_t i=0;i<n;++i)d[i]=(unsigned char)v;return out; }
int memcmp(const void *a,const void *b,size_t n) { const unsigned char *x=a,*y=b;for(size_t i=0;i<n;++i)if(x[i]!=y[i])return x[i]<y[i]?-1:1;return 0; }
int test_outstanding_allocations(void) { return __atomic_load_n(&outstanding,__ATOMIC_RELAXED); }
