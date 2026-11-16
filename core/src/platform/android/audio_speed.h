#ifndef SEEDLESS_AUDIO_SPEED_H
#define SEEDLESS_AUDIO_SPEED_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct audio_speed audio_speed_t;

/* Owned by the emulation producer. Counts are stereo frames at 44100 Hz. */
audio_speed_t *audio_speed_create(void);
void audio_speed_destroy(audio_speed_t *state);
void audio_speed_reset(audio_speed_t *state);

/* Changes tempo while retaining pitch. Accepts 0.5 through 64 times speed.
 * Drain all available output after each write. Returns 0 on failure. */
int audio_speed_write(audio_speed_t *state, const int16_t *input,
                      unsigned frames, float rate);
unsigned audio_speed_read(audio_speed_t *state, int16_t *output, unsigned capacity);

#ifdef __cplusplus
}
#endif

#endif
