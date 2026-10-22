#ifndef REWIND_HOST_PTHREAD_H
#define REWIND_HOST_PTHREAD_H
#include <stddef.h>
#include <stdlib.h>
/* Test adapter only: production Android uses the NDK pthread implementation. */
typedef struct { void *ptr; } pthread_mutex_t;
typedef struct { void *ptr; } pthread_cond_t;
typedef void *pthread_t;
#define PTHREAD_MUTEX_INITIALIZER {0}
#define PTHREAD_COND_INITIALIZER {0}
__declspec(dllimport) void AcquireSRWLockExclusive(void *);
__declspec(dllimport) void ReleaseSRWLockExclusive(void *);
__declspec(dllimport) void WakeConditionVariable(void *);
__declspec(dllimport) void WakeAllConditionVariable(void *);
__declspec(dllimport) int SleepConditionVariableSRW(void *, void *, unsigned long, unsigned long);
__declspec(dllimport) void *CreateThread(void *, size_t, unsigned long (*)(void *), void *, unsigned long, unsigned long *);
__declspec(dllimport) unsigned long WaitForSingleObject(void *, unsigned long);
__declspec(dllimport) int CloseHandle(void *);
static inline int pthread_mutex_lock(pthread_mutex_t *m) { AcquireSRWLockExclusive(m); return 0; }
static inline int pthread_mutex_unlock(pthread_mutex_t *m) { ReleaseSRWLockExclusive(m); return 0; }
static inline int pthread_cond_wait(pthread_cond_t *c, pthread_mutex_t *m) { return SleepConditionVariableSRW(c,m,0xffffffffu,0) ? 0 : -1; }
static inline int pthread_cond_signal(pthread_cond_t *c) { WakeConditionVariable(c); return 0; }
static inline int pthread_cond_broadcast(pthread_cond_t *c) { WakeAllConditionVariable(c); return 0; }
typedef struct { void *(*run)(void *); void *arg; } host_start_t;
static unsigned long host_thread(void *p) { host_start_t start=*(host_start_t *)p; free(p); start.run(start.arg); return 0; }
static inline int pthread_create(pthread_t *t, const void *attrs, void *(*run)(void *), void *arg) {
    (void)attrs;
    host_start_t *start=malloc(sizeof *start);
    if(!start)return -1;
    start->run=run;start->arg=arg;
    *t=CreateThread(NULL,0,host_thread,start,0,NULL);
    if(!*t){free(start);return -1;}return 0;
}
static inline int pthread_join(pthread_t t, void **out) { (void)out; WaitForSingleObject(t,0xffffffffu);CloseHandle(t);return 0; }
#endif
