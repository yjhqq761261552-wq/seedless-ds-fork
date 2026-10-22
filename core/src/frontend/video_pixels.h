#ifndef SEEDLESS_VIDEO_PIXELS_H
#define SEEDLESS_VIDEO_PIXELS_H
#include "seedlessds/video_snapshot.h"
#ifdef __cplusplus
extern "C" {
#endif
int nds_video_pack_rgba(void *dest, size_t capacity, const void *page,
                        size_t screen_stride, unsigned scale,
                        const unsigned modes[2], unsigned bpp,
                        nds_video_snapshot_t *info);
int nds_video_downsample_base(void *top, void *bottom, const void *page,
    size_t screen_stride, unsigned scale, const unsigned modes[2], unsigned bpp, int argb);
#ifdef __cplusplus
}
#endif
#endif
