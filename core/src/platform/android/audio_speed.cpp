#include "audio_speed.h"
#include "SoundTouch.h"

#include <algorithm>
#include <cmath>

struct audio_speed {
    soundtouch::SoundTouch processor;

    audio_speed() {
        processor.setSampleRate(44100);
        processor.setChannels(2);
        processor.setPitch(1.0);
        processor.setRate(1.0);
        processor.setSetting(SETTING_USE_QUICKSEEK, 0);
        // 24 ms output batches fit even the smallest 33 ms playback ring.
        processor.setSetting(SETTING_SEQUENCE_MS, 32);
        processor.setSetting(SETTING_SEEKWINDOW_MS, 8);
        processor.setSetting(SETTING_OVERLAP_MS, 8);
    }
};

audio_speed_t *audio_speed_create(void) {
    try {
        return new audio_speed;
    } catch (...) {
        return nullptr;
    }
}

void audio_speed_destroy(audio_speed_t *state) { delete state; }

void audio_speed_reset(audio_speed_t *state) {
    if (state) state->processor.clear();
}

int audio_speed_write(audio_speed_t *state, const int16_t *input,
                      unsigned frames, float rate) {
    if (!state || (frames && !input) || !std::isfinite(rate)) return 0;
    rate = std::clamp(rate, 0.5f, 64.0f);
    try {
        state->processor.setTempo(rate);
        float converted[735 * 2];
        while (frames) {
            unsigned count = std::min(frames, 735u);
            for (unsigned i = 0; i < count * 2; ++i)
                converted[i] = static_cast<float>(input[i]) / 32768.0f;
            state->processor.putSamples(converted, count);
            input += count * 2;
            frames -= count;
        }
        return 1;
    } catch (...) {
        state->processor.clear();
        return 0;
    }
}

unsigned audio_speed_read(audio_speed_t *state, int16_t *output, unsigned capacity) {
    if (!state || !output || !capacity) return 0;
    float converted[1470 * 2];
    unsigned frames = state->processor.receiveSamples(converted, std::min(capacity, 1470u));
    for (unsigned i = 0; i < frames * 2; ++i) {
        float sample = std::clamp(converted[i] * 32768.0f, -32768.0f, 32767.0f);
        output[i] = static_cast<int16_t>(std::lround(sample));
    }
    return frames;
}
