#include "seedlessds/metrics.h"
#include "seedlessds/video_snapshot.h"
#include "seedlessds/platform.h"
#include "frontend/video_out_gl.h"
#include "frontend/frontend.h"
#include "hires_runtime.h"
#include "video_pixels.h"
#include <stdlib.h>
#include <string.h>

static uint64_t published_serial, published_us;
static unsigned published_scale, published_bpp, published_native, published_modes[2];

void nds_video_published_locked(void) {
    const video_out_gl_t *g = VIDEO_OUT_GL;
    unsigned page = (~g->page_parity) & 1u;
    published_native = recon_native;
    published_scale = recon_native ? recon_page_scale_tag[page] : 2u;
    published_bpp = (unsigned)g->bits_per_pixel;
    published_modes[0] = g->screen_mode[0];
    published_modes[1] = g->screen_mode[1];
    published_us = nds_metrics_now_us();
    __atomic_add_fetch(&published_serial, 1, __ATOMIC_RELEASE);
    nds_video_wake();
}
uint64_t nds_video_latest_serial(void) {
    return __atomic_load_n(&published_serial, __ATOMIC_ACQUIRE);
}

/* Private staging for the raw published page. The per-pixel RGB565->RGBA
 * conversion used to run while holding the core video mutex, stalling the
 * core's own page operations behind a full-frame conversion (worst at high
 * internal scales, and it starved everything else sharing those cores).
 * Now the mutex only covers a streaming memcpy of the raw page; the
 * conversion runs unlocked on the private copy. Callers are the Vulkan
 * presenters, which never pack concurrently (the shared-frame path
 * serializes on its own mutex; the direct path is a single presenter) —
 * and if one ever did, the busy flag falls back to the original in-lock
 * conversion for that call. */
static unsigned char *copy_scratch;
static size_t copy_scratch_cap;
static int copy_scratch_busy;

int nds_video_copy_latest(void *rgba, size_t capacity, uint64_t after,
                          nds_video_snapshot_t *info)
{
    video_out_gl_t *g = VIDEO_OUT_GL;
    if (!rgba || !info || !g->page_memory || FRONTEND->frame_state != 0) return 0;
    const nds_platform_t *platform = nds_platform_default();
    uint64_t copy_start = nds_metrics_now_us();
    int ok = 0;
    platform->threads.mutex_lock(g->mutex);
    uint64_t serial = __atomic_load_n(&published_serial, __ATOMIC_RELAXED);
    if (serial != after && serial != 0) {
        unsigned page = (~g->page_parity) & 1u;
        unsigned scale = published_scale;
        size_t stride = published_native ? (size_t)0x30000 * scale * scale
                                     : VIDEO_OUT_GL_SCREEN_PAGE_BYTES;
        unsigned modes[2] = {published_modes[0], published_modes[1]};
        unsigned bpp = published_bpp;
        uint64_t published = published_us;
        if (scale >= 1 && scale <= 8 && (!published_native || scale <= recon_scale_pages)) {
            int expected = 0;
            if (__atomic_compare_exchange_n(&copy_scratch_busy, &expected, 1, 0,
                                            __ATOMIC_ACQUIRE, __ATOMIC_RELAXED)) {
                size_t span = stride * 2;
                if (copy_scratch_cap < span) {
                    unsigned char *grown = (unsigned char *)malloc(span);
                    if (grown) { free(copy_scratch); copy_scratch = grown; copy_scratch_cap = span; }
                }
                if (copy_scratch && copy_scratch_cap >= span) {
                    memcpy(copy_scratch, g->page[page], span);
                    platform->threads.mutex_unlock(g->mutex);
                    ok = nds_video_pack_rgba(rgba, capacity, copy_scratch, stride,
                                             scale, modes, bpp, info);
                    if (ok) { info->serial = serial; info->published_us = published; }
                    __atomic_store_n(&copy_scratch_busy, 0, __ATOMIC_RELEASE);
                    if (ok) nds_metrics_record(NDS_METRIC_COPY, nds_metrics_now_us() - copy_start);
                    return ok;
                }
                __atomic_store_n(&copy_scratch_busy, 0, __ATOMIC_RELEASE);
            }
            /* Scratch busy or unavailable: original in-lock conversion. */
            ok = nds_video_pack_rgba(rgba, capacity, g->page[page], stride,
                                     scale, modes, bpp, info);
            if (ok) { info->serial = serial; info->published_us = published; }
        }
    }
    platform->threads.mutex_unlock(g->mutex);
    if (ok) nds_metrics_record(NDS_METRIC_COPY, nds_metrics_now_us() - copy_start);
    return ok;
}

int nds_video_copy_base(void *top, void *bottom, int argb) {
    video_out_gl_t *g = VIDEO_OUT_GL;
    if (!g->page_memory) return 0;
    const nds_platform_t *p = nds_platform_default();
    p->threads.mutex_lock(g->mutex);
    unsigned page = (~g->page_parity) & 1u;
    unsigned scale = published_scale;
    size_t stride = published_native ? (size_t)0x30000 * scale * scale : VIDEO_OUT_GL_SCREEN_PAGE_BYTES;
    int ok = nds_video_latest_serial() && nds_video_downsample_base(top, bottom, g->page[page],
        stride, scale, published_modes, published_bpp, argb);
    p->threads.mutex_unlock(g->mutex);
    return ok;
}
