#include "seedlessds/video_snapshot.h"
#include <assert.h>
#include <pthread.h>
#include <stdint.h>
#include <stdio.h>
#include <time.h>
#include <unistd.h>
static uint64_t serial = 1;
static unsigned returned;
uint64_t nds_video_latest_serial(void) { return __atomic_load_n(&serial,__ATOMIC_ACQUIRE); }
static uint64_t now_us(void) { struct timespec t;clock_gettime(CLOCK_MONOTONIC,&t);return (uint64_t)t.tv_sec*1000000+t.tv_nsec/1000; }
static void *wait_frame(void *arg) {
    uint64_t after=(uint64_t)(uintptr_t)arg;
    nds_video_wait_new_frame(after,20000);
    __atomic_fetch_add(&returned,1,__ATOMIC_RELEASE);return NULL;
}
int main(void) {
    pthread_t readers[8];
    for(unsigned i=0;i<8;i++)assert(pthread_create(&readers[i],NULL,wait_frame,(void*)1)==0);
    usleep(3000); uint64_t woke=now_us(); __atomic_store_n(&serial,2,__ATOMIC_RELEASE); nds_video_wake();
    for(unsigned i=0;i<8;i++)pthread_join(readers[i],NULL);
    assert(returned==8 && now_us()-woke<10000);
    returned=0;
    for(unsigned i=0;i<8;i++)assert(pthread_create(&readers[i],NULL,wait_frame,(void*)2)==0);
    usleep(3000); woke=now_us(); nds_video_wake(); // Cancel a surface epoch without publishing a frame.
    for(unsigned i=0;i<8;i++)pthread_join(readers[i],NULL);
    assert(returned==8 && now_us()-woke<10000);
    uint64_t begin=now_us();nds_video_wait_new_frame(1,20000);assert(now_us()-begin<10000);
    begin=now_us();nds_video_wait_new_frame(2,5000);uint64_t elapsed=now_us()-begin;
    assert(elapsed>=4000 && elapsed<100000);
    puts("{\"newFrameBroadcast\":\"passed\",\"lifecycleCancellation\":\"passed\",\"alreadyPublished\":\"passed\",\"boundedTimeout\":\"passed\",\"waiters\":8}");
}
