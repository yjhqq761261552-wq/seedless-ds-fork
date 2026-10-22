#include <stdint.h>
#include <string.h>
#include "hires_runtime.h"
#include "blob_symbols.h"
#include "core/nds_state.h"
#include "present_hook.h"
#include "seedlessds/platform.h"
#include "core_internals.h"
#include "mem_access.h"




void pacer_init(unsigned char *ctx)
{

    nds_runtime_t *g = &((nds_t *)ctx)->runtime;

    gpu2d_worker_hook_noop();

    uint64_t now;
    time_now_microseconds(&now);

    g->pacer_flag = 0;
    wr32(&g->skip_frame, 0);

    g->pacer_base = now * 3u;
    g->pacer_accumulated = 0;
}

#define DEFAULT_STEP  50000
#define LATE_THRESHOLD  (-50001)



extern void time_now_microseconds(uint64_t *dest);

void pacer_run(uint8_t *ctx) {

    nds_config_t *p = &((nds_t *)ctx)->config;
    nds_runtime_t *g = &((nds_t *)ctx)->runtime;

    uint32_t step = p->fast_forward_speed;

    uint32_t state  = nds_frame_limiter_measure_returns_zero();
    uint32_t mode = p->frameskip_type;
    uint32_t lim  = p->frameskip_value;

    uint64_t clock;

    time_now_microseconds(&clock);
    uint64_t ref = clock * 3u - g->pacer_base;

    if (step == 0) step = DEFAULT_STEP;
    uint64_t acc = g->pacer_accumulated + step;
    g->pacer_accumulated = acc;

    uint32_t w20 = 0, w21 = 0, w8;

    if (p->fast_forward != 0 && (step >> 4) <= 0xc34u && ((nds_t *)ctx)->benchmark.enabled == 0) {
        /* Fast forward pacing.
         *
         * The original reconstruction forced a fixed frameskip pattern here
         * (mode 1 with lim 6): one rendered frame followed by six skipped
         * frames, regardless of how much headroom the device had. At 2x
         * speed that presents roughly 17 frames per second, which made fast
         * forward visibly choppy.
         *
         * Pace adaptively instead. The accumulator keeps an absolute
         * schedule (advance one step per frame, sleep off the difference
         * when ahead), so the requested speed is held exactly. Rendering is
         * only skipped when the emulator falls more than one frame period
         * behind the schedule, and then at most six frames in a row. A
         * device that merely renders slower than the requested speed keeps
         * presenting every frame and runs marginally slow, which is exactly
         * what the normal speed limiter does. */
        if (step <= 1u) {
            /* Unlimited fast forward: run flat out and render every frame. */
            g->pacer_accumulated = ref;
            g->pacer_flag = 0;
            g->skip_count = 0;
            g->skip_frame = 0;
            recon_pacer_behind = 1u;
            return;
        }

        int32_t diff = (int32_t)((uint32_t)acc - (uint32_t)ref);

        if (diff > 0x40000000 || diff < -0x40000000) {
            /* Clock jump (pause, hibernation): resynchronize the schedule
             * and render this frame instead of sleeping or skipping. */
            g->pacer_accumulated = ref;
            g->pacer_flag = 0;
            g->skip_count = 0;
            g->skip_frame = 0;
            recon_pacer_behind = 1u;
            return;
        }

        if (diff >= 1) {
            /* Ahead of the schedule: sleep off the difference to hold the
             * requested speed and present this frame. */
            nds_platform_default()->threads.sleep_us((uint32_t)diff / 3u);
            g->skip_count = 0;
            g->skip_frame = 0;
            recon_pacer_behind = 0u;
            return;
        }

        recon_pacer_behind = 1u;

        uint32_t deficit = (uint32_t)(-(int64_t)diff);

        if (deficit <= step) {
            /* At most one frame period behind: keep presenting every frame
             * and run marginally slow, like the normal speed limiter. */
            g->skip_count = 0;
            g->skip_frame = 0;
            return;
        }

        /* More than one frame period behind. Forgive the backlog beyond a
         * single frame period; with the deficit clamped there, the next
         * frame's pre-forgiveness deficit equals its wall time, so a
         * marginally slow device stays in the smooth path while a device
         * that cannot render near the target speed falls through. */
        g->pacer_accumulated = ref - (uint64_t)step;

        if (deficit <= 2u * step) {
            /* The device renders slower than the requested speed but is
             * within one extra frame period: present every frame. */
            g->skip_count = 0;
            g->skip_frame = 0;
            return;
        }

        /* The device cannot render anywhere near the requested speed: skip
         * up to six frames in a row, then render one, so weak devices still
         * gain most of the raw skip speed. */
        if (g->skip_count >= 6u) {
            g->skip_count = 0;
            g->skip_frame = 0;
        } else {
            g->skip_count = (uint8_t)(g->skip_count + 1u);
            g->skip_frame = 1;
        }
        return;
    }

    recon_pacer_behind = ((int32_t)((uint32_t)acc - (uint32_t)ref) < 1) ? 1u : 0u;
    if (g->pacer_flag == 0) goto La34;

    if (g->pacer_count != 0x14) {
        if (state == 0) goto L8f8;
        g->pacer_count = (uint8_t)(g->pacer_count + 1);
        w20 = 1;
        w21 = (uint32_t)acc - (uint32_t)ref;
        if ((int32_t)w21 >= 1) goto L928;
        goto L8c8;
    }
    nds_subsystem_hook_noop();
    acc = g->pacer_accumulated;

L8f8:
    {
        uint32_t d = (uint32_t)acc - (uint32_t)ref;
        d = (step + d - 1u) / step;
        acc = acc - (uint64_t)(uint32_t)(d * step);
        w20 = 0;
        g->pacer_flag = 0;
        g->pacer_accumulated = acc;
        w21 = (uint32_t)acc - (uint32_t)ref;
        if ((int32_t)w21 < 1) goto L8c8;
    }

L928:
    if (w20 == 0) {
        nds_platform_default()->threads.sleep_us(w21 / 3u);
        if (mode != 2) goto L8dc;
        goto L998;
    }
L92c:
    nds_platform_default()->threads.sleep_us(0);
    w20 = 1;
    if (mode != 2) goto L8dc;
    goto L998;

L8c8:
    if ((int32_t)w21 <= LATE_THRESHOLD) goto L92c;
    if (mode == 2) goto L998;

L8dc:
    if (mode == 1) goto L944;
    if (mode != 0) return;
    g->skip_frame = 0;
    if (w20 == 0) return;
    w8 = step + w21;
    goto L9f4;

L944:
    {
        uint32_t v = g->skip_count;
        uint32_t n = lim + 1u;
        uint32_t mark = (v != 0);

        uint32_t updated = (n == ((v + 1u) & 0xffu)) ? 0u : (v + 1u);
        g->skip_frame = (uint8_t)mark;
        g->skip_count = (uint8_t)updated;
        return;
    }

L998:
    {
        uint32_t v = g->skip_parity;
        uint32_t drop = rd32(&((nds_t *)ctx)->bus.io_mirror[0].dispcapcnt);
        uint32_t inv = v ^ 1u;
        g->skip_parity = (uint8_t)inv;

        if ((int32_t)drop < 0) {
            if ((inv & 0xffu) != 0) return;
            uint32_t c = g->skip_run;
            g->skip_run = 0;
            if (c >= 0x79u) { g->skip_frame = 0; return; }
            goto L9cc;
        }
        if (g->skip_run != 0xff) g->skip_run = (uint8_t)(g->skip_run + 1);
    }

L9cc:
    g->skip_frame = 0;
    if (w20 == 0) { g->skip_count = 0; return; }
    {
        uint32_t v = g->skip_count + 1u;
        uint32_t n = lim + 1u;
        g->skip_count = (uint8_t)v;
        if (n != (v & 0xffu)) { g->skip_frame = 1; return; }
        w8 = step + w21;
        g->skip_count = 0;
    }

L9f4:
    {
        uint64_t a = g->pacer_accumulated;
        uint32_t k = ((w8 - 1u) / step) * step;
        g->pacer_flag = 0;
        g->pacer_accumulated = a - (uint64_t)k;
    }
    return;

La34:

    if (state == 2) {
        w20 = 1;
        g->pacer_flag = 1;
        g->pacer_count = 0;
    } else {
        w20 = 0;
    }
    w21 = (uint32_t)acc - (uint32_t)ref;
    if ((int32_t)w21 >= 1) goto L928;
    goto L8c8;
}
#undef DEFAULT_STEP
#undef LATE_THRESHOLD
