#include "seedlessds/metrics.h"
#include "seedlessds/video_snapshot.h"
#include "seedlessds/platform.h"
#include "frontend/video_out_gl.h"
#include "frontend/frontend.h"
#include "hires_runtime.h"
#include "video_pixels.h"

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

int nds_video_copy_latest(void *rgba, size_t capacity, uint64_t after,
                          nds_video_snapshot_t *info)
{
    video_out_gl_t *g = VIDEO_OUT_GL;
    if (!rgba || !info || !g->page_memory || FRONTEND->frame_state != 0) return 0;
    const nds_platform_t *platform = nds_platform_default();
    uint64_t copy_start = nds_metrics_now_us();
    platform->threads.mutex_lock(g->mutex);
    int ok = 0;
    uint64_t serial = __atomic_load_n(&published_serial, __ATOMIC_RELAXED);
    if (serial != after && serial != 0) {
        unsigned page = (~g->page_parity) & 1u;
        unsigned scale = published_scale;
        size_t stride = published_native ? (size_t)0x30000 * scale * scale
                                     : VIDEO_OUT_GL_SCREEN_PAGE_BYTES;
        unsigned modes[2] = {published_modes[0], published_modes[1]};
        if (scale >= 1 && scale <= 8 && (!published_native || scale <= recon_scale_pages)) {
            ok = nds_video_pack_rgba(rgba, capacity, g->page[page], stride,
                                      scale, modes, published_bpp, info);
            if (ok) { info->serial = serial; info->published_us = published_us; }
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
