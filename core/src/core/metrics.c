#include "seedlessds/metrics.h"
#include <time.h>
/* 128 us bins, saturating at 524.288 ms. No allocation or contended lock in
 * input/audio/render paths. Counts describe this process session. */
#define BINS 4096
static uint64_t histogram[NDS_METRICS][BINS], count[NDS_METRICS];
uint64_t nds_metrics_now_us(void) {
    struct timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);
    return (uint64_t)now.tv_sec * 1000000 + now.tv_nsec / 1000;
}
void nds_metrics_record(unsigned metric, uint64_t us) {
    if (metric >= NDS_METRICS) return;
    unsigned bucket = us / 128 >= BINS ? BINS - 1 : (unsigned)(us / 128);
    __atomic_fetch_add(&histogram[metric][bucket], 1, __ATOMIC_RELAXED);
    __atomic_fetch_add(&count[metric], 1, __ATOMIC_RELEASE);
}
uint64_t nds_metrics_count(unsigned metric) {
    return metric < NDS_METRICS ? __atomic_load_n(&count[metric], __ATOMIC_ACQUIRE) : 0;
}
uint64_t nds_metrics_percentile(unsigned metric, unsigned percentile) {
    uint64_t total = nds_metrics_count(metric);
    if (!total || !percentile || percentile > 100) return 0;
    uint64_t wanted = (total * percentile + 99) / 100, sum = 0;
    for (unsigned i = 0; i < BINS; ++i) {
        sum += __atomic_load_n(&histogram[metric][i], __ATOMIC_RELAXED);
        if (sum >= wanted) return (uint64_t)(i + 1) * 128;
    }
    return BINS * 128;
}

static uint64_t previous_frame;
static unsigned frame_gap;
void nds_metrics_frame_gap(void) { __atomic_store_n(&frame_gap, 1, __ATOMIC_RELEASE); }
void nds_metrics_frame_boundary(void) {
    uint64_t now = nds_metrics_now_us();
    if (!__atomic_exchange_n(&frame_gap, 0, __ATOMIC_ACQ_REL) && previous_frame && now - previous_frame < 500000)
        nds_metrics_record(NDS_METRIC_FRAME, now - previous_frame);
    previous_frame = now;
}
