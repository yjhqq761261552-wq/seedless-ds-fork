#ifndef SEEDLESS_LOW_LATENCY_AUDIO_H
#define SEEDLESS_LOW_LATENCY_AUDIO_H
#include <stdint.h>
void low_latency_audio_sound(int enabled);
void low_latency_audio_volume(unsigned volume);
void low_latency_audio_configure(int enabled);
int low_latency_audio_start(unsigned latency);
int low_latency_audio_active(void);
void low_latency_audio_pause(void);
void low_latency_audio_resume(void);
void low_latency_audio_close(void);
/* Returns 1 when the adapter owns output, 0 for legacy fallback. */
int low_latency_audio_feed(const int16_t *pcm, unsigned samples, int muted);
void low_latency_audio_stats(uint64_t values[6]);
#endif
