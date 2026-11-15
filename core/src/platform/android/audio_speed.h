#ifndef SEEDLESS_AUDIO_SPEED_H
#define SEEDLESS_AUDIO_SPEED_H

#include <stdint.h>

/* One emulation-thread producer. All counts are stereo frames, not samples. */
typedef struct audio_speed {
    uint64_t width;
    uint64_t target_width;
    uint64_t filled;
    int64_t sum_left;
    int64_t sum_right;
    int16_t previous_input[2];
    int16_t previous_output[2];
    unsigned have_input;
    unsigned have_output;
} audio_speed_t;

/* Ratios are input frames per output frame, clamped to 0.5 through 64. */
void audio_speed_init(audio_speed_t *state, float rate);
void audio_speed_set_rate(audio_speed_t *state, float rate);

/* Returns 0 on overflow. A nonempty block needs this many output frames. */
unsigned audio_speed_max_output_frames(unsigned input_frames);

/*
 * The input and output buffers must not overlap. A zero-frame block is valid.
 * For a nonempty block, output_capacity must be at least the value returned by
 * audio_speed_max_output_frames(). On insufficient capacity, state and output
 * are unchanged and the function returns 0. Successful calls return 1 and put
 * the number of generated stereo frames in output_frames.
 */
int audio_speed_process(audio_speed_t *state, const int16_t *input,
                        unsigned input_frames, int16_t *output,
                        unsigned output_capacity, unsigned *output_frames);

#endif
