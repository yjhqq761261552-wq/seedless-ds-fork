#include "rewind_worker.h"
#include <pthread.h>
#include <stdlib.h>
#if defined(__ANDROID__)
#include <sys/resource.h>
#endif

static pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t condition = PTHREAD_COND_INITIALIZER;
static pthread_t thread;
static int started, stopping, busy, ready, success;
static rewind_pack_fn pack_function;
static void *raw_buffer;
static size_t raw_capacity, raw_size;
static uint64_t job_frame, job_epoch, completed_epoch;
static rewind_snapshot_t completed;

static void *run(void *unused)
{
    (void)unused;
#if defined(__ANDROID__)
    pthread_setname_np(pthread_self(), "seedless-rewind");
    setpriority(PRIO_PROCESS, 0, 5); /* Do not compete at emulation-thread priority. */
#endif
    pthread_mutex_lock(&mutex);
    for (;;) {
        while (!busy && !stopping) pthread_cond_wait(&condition, &mutex);
        if (stopping) break;
        void *raw = raw_buffer;
        size_t size = raw_size;
        uint64_t frame = job_frame, epoch = job_epoch;
        pthread_mutex_unlock(&mutex);
        void *packed = NULL;
        size_t packed_size = 0;
        int ok = pack_function(raw, size, &packed, &packed_size);
        pthread_mutex_lock(&mutex);
        completed = (rewind_snapshot_t){packed, packed_size, frame};
        completed_epoch = epoch;
        success = ok;
        ready = 1;
        busy = 0;
        pthread_cond_broadcast(&condition);
    }
    busy = 0;
    pthread_cond_broadcast(&condition);
    pthread_mutex_unlock(&mutex);
    return NULL;
}

int rewind_worker_start(rewind_pack_fn pack)
{
    /* Lifecycle/start/submit/take are called only by the emulation thread. */
    if (started) return 1;
    stopping = busy = ready = 0;
    pack_function = pack;
    if (!pack || pthread_create(&thread, NULL, run, NULL) != 0) return 0;
    started = 1;
    return 1;
}

int rewind_worker_idle(void)
{
    pthread_mutex_lock(&mutex);
    int idle = started && !busy && !ready && !stopping;
    pthread_mutex_unlock(&mutex);
    return idle;
}

void *rewind_worker_buffer(size_t *capacity)
{
    pthread_mutex_lock(&mutex);
    void *result = NULL;
    *capacity = 0;
    if (!busy && !ready) {
        result = raw_buffer;
        *capacity = raw_capacity;
        raw_buffer = NULL;
        raw_capacity = 0;
    }
    pthread_mutex_unlock(&mutex);
    return result;
}

int rewind_worker_submit(void *raw, size_t capacity, size_t size, uint64_t frame, uint64_t epoch)
{
    pthread_mutex_lock(&mutex);
    int accepted = started && !stopping && !busy && !ready && raw && size && size <= capacity;
    if (accepted) {
        free(raw_buffer);
        raw_buffer = raw;
        raw_capacity = capacity;
        raw_size = size;
        job_frame = frame;
        job_epoch = epoch;
        busy = 1;
        pthread_cond_signal(&condition);
    }
    pthread_mutex_unlock(&mutex);
    return accepted; /* caller retains ownership on rejection */
}

int rewind_worker_take(rewind_snapshot_t *result, uint64_t *epoch, int *ok)
{
    pthread_mutex_lock(&mutex);
    int have = ready;
    if (have) {
        *result = completed;
        *epoch = completed_epoch;
        *ok = success;
        completed = (rewind_snapshot_t){0};
        ready = 0;
    }
    pthread_mutex_unlock(&mutex);
    return have;
}

void rewind_worker_stop(void)
{
    if (!started) return;
    if (started) {
        pthread_mutex_lock(&mutex);
        stopping = 1;
        pthread_cond_broadcast(&condition);
        pthread_mutex_unlock(&mutex);
        pthread_join(thread, NULL);
        started = 0;
    }
    free(raw_buffer); raw_buffer = NULL; raw_capacity = 0;
    free(completed.data); completed = (rewind_snapshot_t){0};
    stopping = busy = ready = 0;
}

#ifdef SEEDLESS_REWIND_TEST
void rewind_worker_flush_test(void)
{
    pthread_mutex_lock(&mutex);
    while (busy) pthread_cond_wait(&condition, &mutex);
    pthread_mutex_unlock(&mutex);
}
#endif
