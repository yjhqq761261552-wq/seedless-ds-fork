#ifndef SEEDLESS_PCM_RING_H
#define SEEDLESS_PCM_RING_H
#include <stdint.h>
#define PCM_RING_FRAMES 8192u
typedef struct pcm_ring {
    uint32_t read, written, flush;
    uint64_t phase;
    int16_t samples[PCM_RING_FRAMES * 2];
} pcm_ring_t;
/* One emulation producer and one real-time audio consumer. */
unsigned pcm_ring_push(pcm_ring_t *ring, const int16_t *pcm, unsigned frames, unsigned limit);
unsigned pcm_ring_render(pcm_ring_t *ring, int16_t *out, unsigned frames, unsigned rate);
#endif
