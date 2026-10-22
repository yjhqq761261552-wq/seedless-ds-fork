#include "video_pixels.h"
#include <string.h>

int nds_video_pack_rgba(void *dest, size_t capacity, const void *page,
                        size_t screen_stride, unsigned scale,
                        const unsigned modes[2], unsigned bpp,
                        nds_video_snapshot_t *info)
{
    if (!dest || !page || !info || !modes || scale < 1 || scale > 8
        || (bpp != 16 && bpp != 32)) return 0;
    nds_video_snapshot_t result = {0};
    for (unsigned screen = 0; screen < 2; ++screen) {
        if (modes[screen] > 1) return 0;
        unsigned n = modes[screen] ? scale : 1;
        result.width[screen] = 256u * n;
        result.height[screen] = 192u * n;
        size_t pixels = (size_t)result.width[screen] * result.height[screen];
        if (pixels * (bpp / 8) > screen_stride) return 0;
        result.offset[screen] = result.bytes;
        result.bytes += pixels * 4;
    }
    if (result.bytes > capacity) return 0;
    for (unsigned screen = 0; screen < 2; ++screen) {
        const unsigned char *src = (const unsigned char *)page + screen * screen_stride;
        unsigned char *dst = (unsigned char *)dest + result.offset[screen];
        size_t pixels = (size_t)result.width[screen] * result.height[screen];
        if (bpp == 32) memcpy(dst, src, pixels * 4);
        else for (size_t i = 0; i < pixels; ++i) {
            /* Match GL_UNSIGNED_SHORT_5_6_5, including full-range endpoints. */
            unsigned v = src[i * 2] | ((unsigned)src[i * 2 + 1] << 8);
            unsigned red = v >> 11, green = (v >> 5) & 63, blue = v & 31;
            dst[i * 4] = (unsigned char)((red << 3) | (red >> 2));
            dst[i * 4 + 1] = (unsigned char)((green << 2) | (green >> 4));
            dst[i * 4 + 2] = (unsigned char)((blue << 3) | (blue >> 2));
            dst[i * 4 + 3] = 255;
        }
    }
    *info = result;
    return 1;
}

int nds_video_downsample_base(void *top, void *bottom, const void *page,
    size_t screen_stride, unsigned scale, const unsigned modes[2], unsigned bpp, int argb)
{
    if (!page || !modes || scale < 1 || scale > 8 || (bpp != 16 && bpp != 32)) return 0;
    for (unsigned i = 0; i < 2; ++i) {
        if (modes[i] > 1) return 0;
        unsigned n = modes[i] ? scale : 1;
        if ((size_t)256 * 192 * n * n * (bpp / 8) > screen_stride) return 0;
    }
    for (unsigned i = 0; i < 2; ++i) {
        void *out = i ? bottom : top;
        if (!out) continue;
        unsigned n = modes[i] ? scale : 1;
        const unsigned char *src = (const unsigned char *)page + i * screen_stride;
        for (unsigned y = 0; y < 192; ++y) for (unsigned x = 0; x < 256; ++x) {
            size_t index = ((size_t)y * n * 256 * n + x * n) * (bpp / 8);
            unsigned red, green, blue, value;
            if (bpp == 16) {
                value = src[index] | ((unsigned)src[index + 1] << 8);
                red = value >> 11; green = (value >> 5) & 63; blue = value & 31;
                red = (red << 3) | (red >> 2);
                green = (green << 2) | (green >> 4);
                blue = (blue << 3) | (blue >> 2);
            } else {
                red = src[index]; green = src[index + 1]; blue = src[index + 2];
                // State thumbnail format stores red in the low five bits.
                value = (red >> 3) | ((green & 252) << 3) | ((blue & 248) << 8);
            }
            if (argb) ((uint32_t *)out)[y * 256 + x] = 0xff000000u | (red << 16) | (green << 8) | blue;
            else ((uint16_t *)out)[y * 256 + x] = (uint16_t)value;
        }
    }
    return 1;
}
