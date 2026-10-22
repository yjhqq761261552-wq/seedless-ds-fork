#ifndef SEEDLESSDS_REWIND_WORKER_H
#define SEEDLESSDS_REWIND_WORKER_H
#include "rewind_ring.h"
typedef int (*rewind_pack_fn)(void *, size_t, void **, size_t *);
/* One raw buffer, one in-flight job and one result: no unbounded queue. */
int rewind_worker_start(rewind_pack_fn pack);
int rewind_worker_idle(void);
void *rewind_worker_buffer(size_t *capacity);
int rewind_worker_submit(void *raw, size_t capacity, size_t size, uint64_t frame, uint64_t epoch);
int rewind_worker_take(rewind_snapshot_t *result, uint64_t *epoch, int *ok);
void rewind_worker_stop(void);
#ifdef SEEDLESS_REWIND_TEST
void rewind_worker_flush_test(void);
#endif
#endif
