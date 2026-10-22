/* Matches the standard zlib Windows DLL ABI; not used by Android. */
typedef unsigned long uLong;
typedef unsigned long uLongf;
#define Z_OK 0
#define Z_BEST_SPEED 1
__declspec(dllimport) uLong crc32(uLong, const unsigned char *, unsigned int);
__declspec(dllimport) uLong compressBound(uLong);
__declspec(dllimport) int compress2(unsigned char *,uLongf *,const unsigned char *,uLong,int);
__declspec(dllimport) int uncompress(unsigned char *,uLongf *,const unsigned char *,uLong);
