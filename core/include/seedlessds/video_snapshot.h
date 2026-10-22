#ifndef SEEDLESS_VIDEO_SNAPSHOT_H
#define SEEDLESS_VIDEO_SNAPSHOT_H
#include <stddef.h>
#include <stdint.h>
#ifdef __cplusplus
extern "C" {
#endif
typedef struct nds_video_snapshot {
    uint64_t serial;
    uint32_t width[2], height[2];
    size_t offset[2], bytes;
    uint64_t published_us;
} nds_video_snapshot_t;
/* Single consumer-owned RGBA snapshot of the last completed frame. No GL,
 * GPU wait, heap allocation or ownership of a live emulation page escapes. */
int nds_video_copy_latest(void *rgba, size_t capacity, uint64_t after,
                          nds_video_snapshot_t *info);
int nds_video_copy_base(void *top, void *bottom, int argb);
/* Called inside the existing completed-page flip mutex. */
void nds_video_published_locked(void);
uint64_t nds_video_latest_serial(void);
void nds_video_wake(void);
void nds_video_wait_new_frame(uint64_t after, unsigned timeout_us);
#ifdef __cplusplus
}
#endif
#endif
