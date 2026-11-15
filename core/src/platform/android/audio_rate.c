#include "audio_rate.h"

#define SOURCE_RATE 44100.0
#define FIRST_WINDOW_US UINT64_C(20000)
#define MEASURE_WINDOW_US UINT64_C(100000)
#define PAUSE_RESET_US UINT64_C(250000)
#define FEEDBACK_TIME_US 200000.0f
#define FEEDBACK_STRENGTH 0.16f

static float clamp_rate(float value) {
    if (!(value >= 0.5f)) return 0.5f;
    if (value > 64.0f) return 64.0f;
    return value;
}

static float target_rate(unsigned pace_step) {
    if (!pace_step) return 1.0f;
    if (pace_step == 1) return 64.0f;
    return clamp_rate(50000.0f / (float)pace_step);
}

static float buffer_offset(float fraction) {
    if (fraction > 1.0f) fraction = 1.0f;
    return fraction - 0.5f;
}

void audio_rate_init(audio_rate_t *state) {
    if (state) *state = (audio_rate_t){0};
}

float audio_rate_update(audio_rate_t *state, unsigned input_stereo_frames,
                        uint64_t now_us, int fast_forward, unsigned pace_step,
                        float buffer_fraction) {
    if (!state) return 1.0f;
    if (!fast_forward) {
        state->active = 0;
        return 1.0f;
    }

    float limit = target_rate(pace_step);
    int known_buffer = buffer_fraction >= 0.0f;
    int reset = !state->active || state->pace_step != pace_step ||
                now_us < state->last_update_us || now_us - state->last_update_us > PAUSE_RESET_US;
    if (reset) {
        state->active = 1;
        state->pace_step = pace_step;
        state->window_start_us = now_us;
        state->last_update_us = now_us;
        state->window_frames = 0;
        state->measured_rate = pace_step == 1 ? 2.0f : limit;
        state->buffer_error = known_buffer ? buffer_offset(buffer_fraction) : 0.0f;
        state->have_measurement = 0;
    } else {
        uint64_t delta_us = now_us - state->last_update_us;
        state->last_update_us = now_us;
        if (!input_stereo_frames) {
            state->window_start_us = now_us;
            state->window_frames = 0;
            state->have_measurement = 0;
        } else if (UINT64_MAX - state->window_frames < input_stereo_frames) {
            state->window_frames = UINT64_MAX;
        } else {
            state->window_frames += input_stereo_frames;
        }
        uint64_t elapsed = now_us - state->window_start_us;
        uint64_t required = state->have_measurement ? MEASURE_WINDOW_US : FIRST_WINDOW_US;
        if (state->window_frames && elapsed >= required) {
            float observed = (float)((double)state->window_frames * 1000000.0 /
                                     (SOURCE_RATE * (double)elapsed));
            observed = clamp_rate(observed);
            if (observed > limit) observed = limit;
            if (state->have_measurement)
                state->measured_rate += 0.5f * (observed - state->measured_rate);
            else
                state->measured_rate = observed;
            state->have_measurement = 1;
            state->window_frames = 0;
            state->window_start_us = now_us;
        }
        if (known_buffer) {
            float blend = (float)delta_us / FEEDBACK_TIME_US;
            if (blend > 1.0f) blend = 1.0f;
            state->buffer_error += blend * (buffer_offset(buffer_fraction) - state->buffer_error);
        } else {
            state->buffer_error = 0.0f;
        }
    }

    float rate = clamp_rate(state->measured_rate *
                            (1.0f + FEEDBACK_STRENGTH * state->buffer_error));
    return rate > limit ? limit : rate;
}
