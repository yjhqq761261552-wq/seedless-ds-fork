#include "seedlessds/metrics.h"
#include "seedlessds/rewind.h"
#include "seedlessds/platform.h"
#include "seedlessds/control.h"
#include "core/nds_state.h"
#include "frontend/frontend.h"
#include "core_internals.h"
#include "rewind_memory.h"
#include "rewind_ring.h"
#include "rewind_worker.h"
#include "rewind_codec.h"
#include <stdlib.h>

static rewind_ring_t history;
static unsigned interval_seconds = 1;
static unsigned config, generation, applied_generation;
static unsigned requests, working, available, failed;
static uint64_t revision;
/* Emulation-thread owned scheduling state. NDS runs at approximately 60 fps. */
static uint64_t last_capture, next_step_us;
static int captured;
static uint64_t capture_epoch;

void nds_rewind_cancel(void)
{
    __atomic_store_n(&requests, 0, __ATOMIC_RELEASE);
}

void nds_rewind_invalidate(void)
{
    nds_rewind_cancel();
    __atomic_store_n(&available, 0, __ATOMIC_RELEASE);
    __atomic_add_fetch(&generation, 1, __ATOMIC_ACQ_REL);
}

void nds_rewind_configure(int enabled, unsigned seconds)
{
    if (seconds < 10) seconds = 10;
    if (seconds > REWIND_MAX_SNAPSHOTS) seconds = REWIND_MAX_SNAPSHOTS;
    unsigned value = enabled ? seconds : 0;
    if (__atomic_exchange_n(&config, value, __ATOMIC_ACQ_REL) != value)
        nds_rewind_invalidate();
}

void nds_rewind_interval(unsigned seconds) {
    if (seconds < 1) seconds = 1;
    if (seconds > 3) seconds = 3;
    if (__atomic_exchange_n(&interval_seconds, seconds, __ATOMIC_ACQ_REL) != seconds)
        nds_rewind_invalidate();
}

int nds_rewind_hold(int pressed)
{
    if (!pressed) { __atomic_fetch_and(&requests, ~1u, __ATOMIC_ACQ_REL); return 1; }
    if (!__atomic_load_n(&config, __ATOMIC_ACQUIRE)) return -1;
    if (__atomic_load_n(&failed, __ATOMIC_ACQUIRE)) return -2;
    if (!__atomic_load_n(&available, __ATOMIC_ACQUIRE)) return 0;
    /* A quick tap must still work if UP arrives before the next frame boundary. */
    __atomic_fetch_or(&requests, 3u, __ATOMIC_ACQ_REL);
    return 1;
}

uint64_t nds_rewind_status(void)
{
    return (__atomic_load_n(&revision, __ATOMIC_ACQUIRE) << 16)
        | ((uint64_t)__atomic_load_n(&available, __ATOMIC_ACQUIRE) << 1)
        | (__atomic_load_n(&requests, __ATOMIC_ACQUIRE) != 0 || __atomic_load_n(&working, __ATOMIC_ACQUIRE));
}

void nds_rewind_shutdown(void)
{
    nds_rewind_invalidate();
    rewind_worker_stop();
    rewind_ring_clear(&history);
    captured = 0;
    __atomic_store_n(&working, 0, __ATOMIC_RELEASE);
    __atomic_store_n(&failed, 0, __ATOMIC_RELEASE);
}

void nds_rewind_poll(void *machine)
{
    unsigned gen = __atomic_load_n(&generation, __ATOMIC_ACQUIRE);
    if (gen != applied_generation) {
        rewind_ring_clear(&history);
        applied_generation = gen;
        captured = 0;
        next_step_us = 0;
        __atomic_store_n(&available, 0, __ATOMIC_RELEASE);
        __atomic_store_n(&failed, 0, __ATOMIC_RELEASE);
        ++capture_epoch;
    }
    unsigned limit = __atomic_load_n(&config, __ATOMIC_ACQUIRE);
    if (!limit) { rewind_worker_stop(); return; }
    rewind_snapshot_t result = {0};
    uint64_t epoch = 0;
    int packed_ok = 0;
    if (rewind_worker_take(&result, &epoch, &packed_ok)) {
        if (epoch != capture_epoch) {
            free(result.data); /* from before reset/load/rewind: never reinsert the future */
        } else if (!packed_ok || !rewind_ring_push(&history, result, limit)) {
            if (!packed_ok) free(result.data);
            rewind_ring_clear(&history);
            nds_rewind_cancel();
            __atomic_store_n(&failed, 1, __ATOMIC_RELEASE);
        }
        __atomic_store_n(&available, history.count, __ATOMIC_RELEASE);
    }
    if (__atomic_load_n(&failed, __ATOMIC_ACQUIRE)) return;
    frontend_t *g = FRONTEND;
    if (__atomic_load_n(&g->quit_request, __ATOMIC_ACQUIRE) || __atomic_load_n(&g->pause_request, __ATOMIC_ACQUIRE) || nds_control_pause_requested()
        || g->rom_load_request || g->reset_request || g->state_load_request) return;
    /* Manual state writes may own a compression buffer; avoid competing allocations. */
    if (g->state_saving || state_write_pending()) return;
    uint64_t frame = ((nds_t *)machine)->sched.frame;
    rewind_ring_expire(&history, frame > limit * 60u ? frame - limit * 60u : 0);
    __atomic_store_n(&available, history.count, __ATOMIC_RELEASE);
    /* Keep restoration visible to audio/achievement readers even after a tap's
       pending bit is consumed and before its new revision is published. */
    __atomic_store_n(&working, 1, __ATOMIC_RELEASE);
    unsigned request = __atomic_fetch_and(&requests, ~2u, __ATOMIC_ACQ_REL);
    unsigned step = request & 2u;
    unsigned holding = request & 1u;
    uint64_t now = 0;
    if (step || holding) {
        int restored = 0;
        time_now_microseconds(&now);
        if (step || now >= next_step_us) {
            rewind_snapshot_t item = rewind_ring_take(&history, frame);
            if (item.data) {
                int ok = state_rewind_restore(machine, item.data, item.size);
                free(item.data);
                if (!ok) {
                    rewind_ring_clear(&history);
                    nds_rewind_cancel();
                    __atomic_store_n(&failed, 1, __ATOMIC_RELEASE);
                } else {
                    restored = 1;
                    ++capture_epoch;
                    __atomic_add_fetch(&revision, 1, __ATOMIC_RELEASE);
                    last_capture = ((nds_t *)machine)->sched.frame;
                    captured = 1;
                    next_step_us = now + 250000;
                }
            }
            __atomic_store_n(&available, history.count, __ATOMIC_RELEASE);
        }
        /* At the oldest point, wait for release without advancing the game.
           Pause/quit also break the wait, so lifecycle transitions cannot deadlock. */
        while (!restored && !history.count && (__atomic_load_n(&requests, __ATOMIC_ACQUIRE) & 1u)
               && !__atomic_load_n(&g->quit_request, __ATOMIC_ACQUIRE) && !__atomic_load_n(&g->pause_request, __ATOMIC_ACQUIRE)
               && !nds_control_pause_requested()
               && gen == __atomic_load_n(&generation, __ATOMIC_ACQUIRE))
            nds_platform_default()->threads.sleep_us(10000);
        __atomic_store_n(&working, 0, __ATOMIC_RELEASE);
        return;
    }
    __atomic_store_n(&working, 0, __ATOMIC_RELEASE);
    if (!captured || frame < last_capture || frame - last_capture >= 60u * __atomic_load_n(&interval_seconds, __ATOMIC_ACQUIRE)) {
        if (!rewind_worker_start(rewind_blob_pack)) {
            __atomic_store_n(&failed, 1, __ATOMIC_RELEASE);
            return;
        }
        /* A slow compressor causes a missed history point, never a blocking
           wait or a growing queue on the emulation thread. */
        if (!rewind_worker_idle()) return;
        size_t capacity = 0, used = 0;
        void *raw = rewind_worker_buffer(&capacity);
        uint64_t capture_start = nds_metrics_now_us();
        if (!state_rewind_capture_raw(machine, &raw, &capacity, &used)
            || !rewind_worker_submit(raw, capacity, used, frame, capture_epoch)) {
            free(raw);
            rewind_ring_clear(&history);
            __atomic_store_n(&failed, 1, __ATOMIC_RELEASE);
        } else {
            last_capture = frame;
            captured = 1;
        }
        nds_metrics_record(NDS_METRIC_REWIND, nds_metrics_now_us() - capture_start);
        __atomic_store_n(&available, history.count, __ATOMIC_RELEASE);
    }
}
