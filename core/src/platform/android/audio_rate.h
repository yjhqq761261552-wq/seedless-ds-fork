#ifndef SEEDLESS_AUDIO_RATE_H
#define SEEDLESS_AUDIO_RATE_H

#include <stdint.h>

/* One emulation-thread producer. Duration is measured using a monotonic clock. */
typedef struct audio_rate {
    uint64_t window_start_us;
    uint64_t last_update_us;
    uint64_t window_frames;
    unsigned pace_step;
    unsigned active;
    float measured_rate;
    float buffer_error;
    unsigned have_measurement;
} audio_rate_t;

void audio_rate_init(audio_rate_t *state);

/*
 * Estimate source stereo frames produced per second divided by 44100.
 * During fast-forward, pace_step uses the core's 50000-per-normal-frame
 * timebase and a value of 1 means unlimited speed. A negative or NaN buffer
 * fraction means unknown; otherwise 0 is empty and 1 is full.
 * The result is between 0.5 and 64, or exactly 1 outside fast-forward.
 */
float audio_rate_update(audio_rate_t *state, unsigned input_stereo_frames,
                        uint64_t now_us, int fast_forward, unsigned pace_step,
                        float buffer_fraction);

#endif
