#include "audio_speed.h"

#include <limits.h>
#include <stddef.h>

#define SAMPLE_UNIT (UINT64_C(1) << 32)
#define SMOOTH_OUTPUT_FRAMES 220

static uint64_t speed_width(float rate) {
    /* The first comparison also maps NaN to the minimum rate. */
    if (!(rate >= 0.5f)) rate = 0.5f;
    if (rate > 64.0f) rate = 64.0f;
    return (uint64_t)((double)rate * (double)SAMPLE_UNIT + 0.5);
}

void audio_speed_init(audio_speed_t *state, float rate) {
    if (!state) return;
    *state = (audio_speed_t){0};
    state->width = speed_width(rate);
    state->target_width = state->width;
}

void audio_speed_set_rate(audio_speed_t *state, float rate) {
    if (state) state->target_width = speed_width(rate);
}

unsigned audio_speed_max_output_frames(unsigned input_frames) {
    if (!input_frames || input_frames > (UINT_MAX - 1u) / 2u) return 0;
    return input_frames * 2u + 1u;
}

static void advance_width(audio_speed_t *state) {
    if (state->width == state->target_width) return;
    if (state->width < state->target_width) {
        uint64_t difference = state->target_width - state->width;
        state->width += (difference + SMOOTH_OUTPUT_FRAMES - 1u) / SMOOTH_OUTPUT_FRAMES;
    } else {
        uint64_t difference = state->width - state->target_width;
        state->width -= (difference + SMOOTH_OUTPUT_FRAMES - 1u) / SMOOTH_OUTPUT_FRAMES;
    }
}

static int16_t interpolate(int16_t previous, int16_t current, uint64_t fraction) {
    int64_t delta = (int64_t)current - previous;
    return (int16_t)(previous + delta * (int64_t)fraction / (int64_t)SAMPLE_UNIT);
}

static int16_t smooth_output(int16_t current, int16_t previous, uint64_t width) {
    /* Additional attenuation near the new Nyquist frequency when speeding up. */
    if (width <= SAMPLE_UNIT) return current;
    uint64_t amount = width - SAMPLE_UNIT;
    if (amount > SAMPLE_UNIT) amount = SAMPLE_UNIT;
    int64_t change = (int64_t)previous - current;
    return (int16_t)(current + change * (int64_t)amount / (int64_t)(2u * SAMPLE_UNIT));
}

int audio_speed_process(audio_speed_t *state, const int16_t *input,
                        unsigned input_frames, int16_t *output,
                        unsigned output_capacity, unsigned *output_frames) {
    if (!state || !output_frames) return 0;
    *output_frames = 0;
    if (!input_frames) return 1;
    unsigned maximum = audio_speed_max_output_frames(input_frames);
    if (!maximum || output_capacity < maximum || !input || !output || !state->width) return 0;

    if (state->width == SAMPLE_UNIT && state->target_width == SAMPLE_UNIT && !state->filled) {
        for (unsigned sample = 0; sample < input_frames * 2u; ++sample)
            output[sample] = input[sample];
        state->previous_input[0] = state->previous_output[0] = input[(input_frames - 1u) * 2u];
        state->previous_input[1] = state->previous_output[1] = input[(input_frames - 1u) * 2u + 1u];
        state->have_input = state->have_output = 1;
        *output_frames = input_frames;
        return 1;
    }

    unsigned written = 0;
    for (unsigned frame = 0; frame < input_frames; ++frame) {
        int16_t left = input[frame * 2u];
        int16_t right = input[frame * 2u + 1u];
        uint64_t position = 0;
        while (position < SAMPLE_UNIT) {
            uint64_t remaining_sample = SAMPLE_UNIT - position;
            uint64_t remaining_window = state->width - state->filled;
            uint64_t used = remaining_sample < remaining_window ? remaining_sample : remaining_window;
            state->sum_left += (int64_t)left * (int64_t)used;
            state->sum_right += (int64_t)right * (int64_t)used;
            state->filled += used;
            position += used;
            if (state->filled == state->width) {
                int16_t sample_left = (int16_t)(state->sum_left / (int64_t)state->width);
                int16_t sample_right = (int16_t)(state->sum_right / (int64_t)state->width);
                if (state->width < SAMPLE_UNIT && state->have_input) {
                    sample_left = interpolate(state->previous_input[0], left, position);
                    sample_right = interpolate(state->previous_input[1], right, position);
                }
                output[written * 2u] = state->have_output
                    ? smooth_output(sample_left, state->previous_output[0], state->width) : sample_left;
                output[written * 2u + 1u] = state->have_output
                    ? smooth_output(sample_right, state->previous_output[1], state->width) : sample_right;
                state->previous_output[0] = sample_left;
                state->previous_output[1] = sample_right;
                state->have_output = 1;
                ++written;
                state->filled = 0;
                state->sum_left = state->sum_right = 0;
                advance_width(state);
            }
        }
        state->previous_input[0] = left;
        state->previous_input[1] = right;
        state->have_input = 1;
    }
    *output_frames = written;
    return 1;
}
