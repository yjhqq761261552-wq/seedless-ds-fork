#include "seedlessds/video_snapshot.h"
#include <pthread.h>
#include <time.h>

static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t changed = PTHREAD_COND_INITIALIZER;
static unsigned epoch;
void nds_video_wake(void) {
    pthread_mutex_lock(&lock);
    ++epoch;
    pthread_cond_broadcast(&changed);
    pthread_mutex_unlock(&lock);
}
void nds_video_wait_new_frame(uint64_t after, unsigned timeout_us) {
    if (timeout_us > 20000) timeout_us = 20000;
    struct timespec deadline;
    clock_gettime(CLOCK_REALTIME, &deadline);
    deadline.tv_nsec += (long)timeout_us * 1000;
    if (deadline.tv_nsec >= 1000000000) { ++deadline.tv_sec; deadline.tv_nsec -= 1000000000; }
    pthread_mutex_lock(&lock);
    unsigned generation = epoch;
    while (nds_video_latest_serial() == after && generation == epoch) {
        if (pthread_cond_timedwait(&changed, &lock, &deadline) != 0) break;
    }
    pthread_mutex_unlock(&lock);
}
