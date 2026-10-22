#include "seedlessds/video_snapshot.h"
#include <stdint.h>
#include <stddef.h>
#include <string.h>
#include "hires_runtime.h"
#include "blob_symbols.h"
#include "seedlessds/nds.h"
#include "core/nds_state.h"
#include "frontend/frontend.h"
#include "core_internals.h"
#include "mem_access.h"

#define SCREEN_WIDTH 256
#define SCREEN_HEIGHT 192
#define SCREEN_PIXELS (SCREEN_WIDTH * SCREEN_HEIGHT)
#define SCREEN_RGBA_BYTES (SCREEN_PIXELS * 4)
#define SCREEN_RGB16_BYTES (SCREEN_PIXELS * 2)
#define SCREEN_ROW_RGBA 0x400
#define SCREEN_ROW_16 0x800
#define SCREEN_ROW_32 0x1000
#define SCREEN_STEP_16 4
#define SCREEN_STEP_32 8
#define SCREEN_MODE_WIDE 2
#define PIXEL_OPAQUE 0xff000000u
#define HUD_SCALE 16.0
#define HUD_LIMIT 0xffffu
#define HUD_SHIFT 16


void nds_screen_buffers(nds_t *nds, const void **top, const void **bottom)
{
    (void)nds;
    *top = video_out_screen_buffer(0);
    *bottom = video_out_screen_buffer(1);
}

int nds_screens_rgba(nds_t *nds, void *top, void *bottom)
{
    (void)nds;
    return nds_video_copy_base(top, bottom, 1);
}

int nds_screenshot(nds_t *nds, void *rgba, size_t size)
{
    (void)nds;
    if (!rgba || size < SCREEN_RGBA_BYTES * 2) return 0;
    return nds_video_copy_base(rgba, (uint8_t *)rgba + SCREEN_RGBA_BYTES, 1);
}

int nds_snapshot_slot(nds_t *nds, int slot, void *top16, void *bottom16)
{
    (void)nds;
    return state_load_slot((unsigned char *)(FRONTEND->machine), (uint32_t)slot,
                                                top16, bottom16, 1);
}

int nds_snapshot_file(nds_t *nds, const char *path, void *top16, void *bottom16)
{
    (void)nds;
    return state_load_file((unsigned char *)(FRONTEND->machine), path,
                                                top16, bottom16, 1);
}

static uint32_t hud_word(float value)
{
    double scaled = (double)value * HUD_SCALE;

    if (scaled != scaled || scaled < 0.0)
        return 0;
    if (scaled >= 4294967296.0)
        return UINT32_MAX;
    return (uint32_t)scaled;
}

uint32_t nds_perf_word(nds_t *nds)
{
    frontend_t *g = FRONTEND;
    const gpu_capture_t *capture;
    uint32_t speed;
    uint32_t composed;

    (void)nds;

    if (g->frame_state != 0)
        return UINT32_MAX;

    capture = &g->machine->output.capture;
    speed = hud_word(capture->hud_fps);
    composed = hud_word(capture->hud_percent);

    if (speed > HUD_LIMIT)
        speed = HUD_LIMIT;
    if (composed > HUD_LIMIT)
        composed = HUD_LIMIT;

    return composed | (speed << HUD_SHIFT);
}

void nds_clear_screens(nds_t *nds, uint32_t texture_top, uint32_t texture_bottom)
{
    (void)nds;
    video_out_gl_clear_screens(texture_top, texture_bottom);
}

void nds_render_frame(nds_t *nds, uint32_t first, uint32_t second, int flag)
{
    (void)nds;
    video_out_upload_screen_textures(first, second, (uint32_t)(flag & 0xff));
}

void nds_draw_screen(nds_t *nds, uint32_t texture, uint32_t index)
{
    (void)nds;
    video_out_gl_draw_screen_rect4(texture, index);
}

void nds_draw_screen_ext(nds_t *nds, uint32_t texture, uint32_t index)
{
    (void)nds;
    video_out_gl_draw_screen_rect2(texture, index);
}
