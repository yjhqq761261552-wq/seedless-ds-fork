#include "pcm_ring.h"
#include <string.h>
unsigned pcm_ring_push(pcm_ring_t *r, const int16_t *pcm, unsigned frames, unsigned limit) {
    uint32_t write = __atomic_load_n(&r->written, __ATOMIC_RELAXED);
    uint32_t read = __atomic_load_n(&r->read, __ATOMIC_ACQUIRE);
    if (limit > PCM_RING_FRAMES) limit = PCM_RING_FRAMES;
    if (frames > limit || write - read > limit - frames) return 0;
    for (unsigned i = 0; i < frames; ++i) {
        unsigned at = ((write + i) & (PCM_RING_FRAMES - 1)) * 2;
        r->samples[at] = pcm[i * 2]; r->samples[at + 1] = pcm[i * 2 + 1];
    }
    __atomic_store_n(&r->written, write + frames, __ATOMIC_RELEASE);
    return frames;
}
unsigned pcm_ring_render(pcm_ring_t *r, int16_t *out, unsigned frames, unsigned rate) {
    if (!rate) { memset(out, 0, frames * 4); return 0; }
    uint32_t read = __atomic_load_n(&r->read, __ATOMIC_RELAXED);
    uint32_t write = __atomic_load_n(&r->written, __ATOMIC_ACQUIRE);
    if (__atomic_exchange_n(&r->flush, 0, __ATOMIC_ACQ_REL)) {
        read = write; r->phase = 0;
    }
    uint64_t phase = r->phase;
    uint64_t step = (UINT64_C(44100) << 32) / rate;
    unsigned rendered = 0;
    for (unsigned i = 0; i < frames; ++i) {
        unsigned offset = (unsigned)(phase >> 32);
        if (offset + 1 >= write - read) {
            out[i * 2] = out[i * 2 + 1] = 0;
            continue;
        }
        unsigned a = ((read + offset) & (PCM_RING_FRAMES - 1)) * 2;
        unsigned b = ((read + offset + 1) & (PCM_RING_FRAMES - 1)) * 2;
        uint32_t fraction = (uint32_t)phase;
        for (unsigned c = 0; c < 2; ++c) {
            int64_t value = (int64_t)r->samples[a + c] * (INT64_C(4294967296) - fraction)
                          + (int64_t)r->samples[b + c] * fraction;
            out[i * 2 + c] = (int16_t)(value >> 32);
        }
        phase += step;
        ++rendered;
    }
    unsigned consumed = (unsigned)(phase >> 32);
    if (consumed > write - read) { consumed = write - read; phase = 0; }
    r->phase = phase & UINT32_MAX;
    __atomic_store_n(&r->read, read + consumed, __ATOMIC_RELEASE);
    return rendered;
}
