#define __ANDROID_UNAVAILABLE_SYMBOLS_ARE_WEAK__ 1
#include "low_latency_audio.h"
#include "pcm_ring.h"
#include "seedlessds/metrics.h"
#include "frontend/audio_out.h"
#include <aaudio/AAudio.h>
#include <android/log.h>
#include <dlfcn.h>
#include <pthread.h>
#include <stdlib.h>
#include <string.h>

/* Resolve at runtime: API 21-25 retain OpenSL without a load-time dependency. */
#define REQUIRED(X) \
 X(_createStreamBuilder) X(StreamBuilder_delete) X(StreamBuilder_setDirection) \
 X(StreamBuilder_setChannelCount) X(StreamBuilder_setFormat) X(StreamBuilder_setPerformanceMode) \
 X(StreamBuilder_setSharingMode) X(StreamBuilder_setDataCallback) X(StreamBuilder_setErrorCallback) \
 X(StreamBuilder_openStream) X(Stream_requestStart) X(Stream_requestPause) \
 X(Stream_close) X(Stream_getSampleRate) X(Stream_getChannelCount) X(Stream_getFormat) \
 X(Stream_getFramesPerBurst) X(Stream_setBufferSizeInFrames) X(Stream_getBufferSizeInFrames) \
 X(Stream_getBufferCapacityInFrames) X(Stream_getXRunCount) X(Stream_getState) X(Stream_waitForStateChange)
#define DECLARE(name) static __typeof__(&AAudio##name) api_##name;
REQUIRED(DECLARE)
#undef DECLARE
static void *library;
static pthread_mutex_t control = PTHREAD_MUTEX_INITIALIZER;
static unsigned configured = 1;
static unsigned sound_enabled = 1;
static unsigned gain_q15 = 32768;
static int applied_config = -1;
static unsigned applied_latency;
typedef struct {
    AAudioStream *stream;
    pcm_ring_t ring;
    unsigned rate, burst, limit;
    int running, started, failed, xruns, target;
    uint64_t underruns, overruns, callbacks, checked_at;
    uint64_t observed_callbacks, progress_at;
    int reset_envelope;
    int16_t last_output[2], fade_in_start[2], fade_out_start[2];
    unsigned fade_in_remaining, fade_out_remaining, needs_fade_in;
} output_t;
/* Lifetime is stable until stream close has joined all callbacks. Control APIs
 * and the producer share a short lock; the real-time callback never takes it. */
static output_t *output;
static uint64_t last_stats[6];
/* Protected by control; prevents pointer reuse from confusing a waiting feed. */
static uint64_t output_generation;

/* Implemented by the platform audio producer. The callback never waits. */
extern void platform_audio_wait_for_output(unsigned microseconds);

/* Callback-owned state. Pause/mute only request a reset atomically. */
static void reset_output_envelope(output_t *o, unsigned fade_in) {
    o->last_output[0] = o->last_output[1] = 0;
    o->fade_in_start[0] = o->fade_in_start[1] = 0;
    o->fade_out_start[0] = o->fade_out_start[1] = 0;
    o->fade_in_remaining = o->fade_out_remaining = 0;
    o->needs_fade_in = fade_in;
}

#define UNDERRUN_FADE_FRAMES 64u
static void smooth_underrun(output_t *o, int16_t *samples, unsigned frames, unsigned got) {
    const unsigned span = UNDERRUN_FADE_FRAMES - 1;
    if (got) {
        o->fade_out_remaining = 0;
        if (o->needs_fade_in) {
            o->fade_in_start[0] = o->last_output[0];
            o->fade_in_start[1] = o->last_output[1];
            o->fade_in_remaining = UNDERRUN_FADE_FRAMES;
            o->needs_fade_in = 0;
        }
        for (unsigned i = 0; i < got && o->fade_in_remaining; ++i) {
            unsigned position = UNDERRUN_FADE_FRAMES - o->fade_in_remaining;
            for (unsigned channel = 0; channel < 2; ++channel) {
                int32_t value = (int32_t)o->fade_in_start[channel] * (int32_t)(span - position)
                              + (int32_t)samples[i * 2 + channel] * (int32_t)position;
                samples[i * 2 + channel] = (int16_t)(value / (int32_t)span);
            }
            --o->fade_in_remaining;
        }
    }
    if (got < frames) {
        o->needs_fade_in = 1;
        o->fade_in_remaining = 0;
        if (got >= UNDERRUN_FADE_FRAMES) {
            unsigned first = got - UNDERRUN_FADE_FRAMES;
            for (unsigned i = 0; i < UNDERRUN_FADE_FRAMES; ++i) {
                for (unsigned channel = 0; channel < 2; ++channel) {
                    unsigned index = (first + i) * 2 + channel;
                    samples[index] = (int16_t)((int32_t)samples[index] * (int32_t)(span - i) / (int32_t)span);
                }
            }
            o->fade_out_remaining = 0;
        } else if (got) {
            o->fade_out_start[0] = samples[(got - 1) * 2];
            o->fade_out_start[1] = samples[(got - 1) * 2 + 1];
            o->fade_out_remaining = UNDERRUN_FADE_FRAMES;
        } else if (!o->fade_out_remaining && (o->last_output[0] || o->last_output[1])) {
            o->fade_out_start[0] = o->last_output[0];
            o->fade_out_start[1] = o->last_output[1];
            o->fade_out_remaining = UNDERRUN_FADE_FRAMES;
        }
        for (unsigned i = got; i < frames && o->fade_out_remaining; ++i) {
            unsigned remaining = o->fade_out_remaining - 1;
            for (unsigned channel = 0; channel < 2; ++channel)
                samples[i * 2 + channel] = (int16_t)((int32_t)o->fade_out_start[channel] * (int32_t)remaining / (int32_t)span);
            --o->fade_out_remaining;
        }
    }
    if (frames) {
        o->last_output[0] = samples[(frames - 1) * 2];
        o->last_output[1] = samples[(frames - 1) * 2 + 1];
    }
}

static aaudio_data_callback_result_t on_audio(AAudioStream *stream, void *user, void *data, int32_t frames) {
    (void)stream;
    output_t *o = user;
    if (__atomic_exchange_n(&o->reset_envelope, 0, __ATOMIC_ACQ_REL)) reset_output_envelope(o, 1);
    if (!__atomic_load_n(&o->running, __ATOMIC_ACQUIRE)) {
        reset_output_envelope(o, 1);
        memset(data, 0, (size_t)frames * 4);
        return AAUDIO_CALLBACK_RESULT_CONTINUE;
    }
    unsigned got = pcm_ring_render(&o->ring, data, (unsigned)frames, o->rate);
    unsigned gain = __atomic_load_n(&gain_q15, __ATOMIC_RELAXED);
    if (gain != 32768) {
        int16_t *samples = data;
        for (int32_t i = 0; i < frames * 2; ++i) samples[i] = (int16_t)(((int32_t)samples[i] * (int32_t)gain) / 32768);
    }
    if (gain == 0) reset_output_envelope(o, got < (unsigned)frames);
    else smooth_underrun(o, data, (unsigned)frames, got);
    __atomic_fetch_add(&o->callbacks, 1, __ATOMIC_RELAXED);
    if (got < (unsigned)frames) __atomic_fetch_add(&o->underruns, 1, __ATOMIC_RELAXED);
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}
static void on_error(AAudioStream *stream, void *user, aaudio_result_t error) {
    (void)stream; (void)error;
    output_t *o = user;
    __atomic_store_n(&o->failed, 1, __ATOMIC_RELEASE);
    __atomic_store_n(&o->running, 0, __ATOMIC_RELEASE);
}
static int load_api(void) {
    if (library) return 1;
    library = dlopen("libaaudio.so", RTLD_NOW | RTLD_LOCAL);
    if (!library) return 0;
#define LOAD(name) api_##name = dlsym(library, "AAudio" #name); if (!api_##name) goto fail;
    REQUIRED(LOAD)
#undef LOAD
    return 1;
fail:
    dlclose(library); library = NULL; return 0;
}
static void stats_locked(uint64_t values[6]) {
    output_t *o = output;
    if (!o) { memcpy(values, last_stats, sizeof last_stats); values[0] = 0; return; }
    values[0] = o->rate; values[1] = (uint64_t)o->target;
    values[2] = __atomic_load_n(&o->underruns, __ATOMIC_RELAXED);
    values[3] = __atomic_load_n(&o->overruns, __ATOMIC_RELAXED);
    values[4] = (uint64_t)o->xruns; values[5] = __atomic_load_n(&o->callbacks, __ATOMIC_RELAXED);
}
static void close_locked(void) {
    if (!output) return;
    ++output_generation;
    __atomic_store_n(&output->running, 0, __ATOMIC_RELEASE);
    stats_locked(last_stats);
    api_Stream_close(output->stream);
    free(output); output = NULL;
}
void low_latency_audio_volume(unsigned volume) {
    if (volume > 100) volume = 100;
    __atomic_store_n(&gain_q15, volume * 32768 / 100, __ATOMIC_RELAXED);
}
void low_latency_audio_sound(int enabled) {
    unsigned previous = __atomic_exchange_n(&sound_enabled, enabled != 0, __ATOMIC_ACQ_REL);
    if (previous == (unsigned)(enabled != 0)) return;
    if (enabled) low_latency_audio_resume(); else low_latency_audio_pause();
}
void low_latency_audio_configure(int enabled) { __atomic_store_n(&configured, enabled != 0, __ATOMIC_RELEASE); }
int low_latency_audio_start(unsigned latency) {
    pthread_mutex_lock(&control);
    close_locked();
    int ok = 0;
    AAudioStreamBuilder *builder = NULL;
    output_t *o = NULL;
    if (latency > 3) latency = 3;
    applied_config = (int)__atomic_load_n(&configured, __ATOMIC_ACQUIRE);
    applied_latency = latency;
    if (!applied_config || !load_api()) goto done;
    o = calloc(1, sizeof *o);
    if (!o || api__createStreamBuilder(&builder) != AAUDIO_OK) goto fail;
    api_StreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
    api_StreamBuilder_setChannelCount(builder, 2);
    api_StreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
    api_StreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
    api_StreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    /* Usage was introduced in API 28; native sample rate is left unspecified. */
    void (*usage)(AAudioStreamBuilder *, aaudio_usage_t) = dlsym(library, "AAudioStreamBuilder_setUsage");
    if (usage) usage(builder, AAUDIO_USAGE_GAME);
    api_StreamBuilder_setDataCallback(builder, on_audio, o);
    api_StreamBuilder_setErrorCallback(builder, on_error, o);
    if (api_StreamBuilder_openStream(builder, &o->stream) != AAUDIO_OK) goto fail;
    o->rate = (unsigned)api_Stream_getSampleRate(o->stream);
    if (o->rate < 8000 || o->rate > 192000 || api_Stream_getChannelCount(o->stream) != 2
        || api_Stream_getFormat(o->stream) != AAUDIO_FORMAT_PCM_I16) goto fail;
    o->burst = (unsigned)api_Stream_getFramesPerBurst(o->stream);
    if (!o->burst || o->burst > 4096) goto fail;
    static const unsigned bursts[] = {4, 6, 8, 12};
    o->limit = 1470 + 735 * latency;
    o->target = api_Stream_setBufferSizeInFrames(o->stream, (int32_t)(o->burst * bursts[latency]));
    if (o->target <= 0) goto fail;
    __atomic_store_n(&o->running, 1, __ATOMIC_RELEASE);
    /* The producer primes the stream after ROM boot. */
    output = o; ok = 1;
    __android_log_print(ANDROID_LOG_INFO, "SeedlessAudio", "AAudio native=%u Hz, burst=%u, buffer=%d frames, source=44100 stereo", o->rate, o->burst, o->target);
    goto done;
fail:
    if (o) { if (o->stream) api_Stream_close(o->stream); free(o); }
done:
    if (builder) api_StreamBuilder_delete(builder);
    pthread_mutex_unlock(&control);
    return ok;
}
int low_latency_audio_active(void) {
    pthread_mutex_lock(&control); int active = output != NULL; pthread_mutex_unlock(&control); return active;
}
void low_latency_audio_close(void) { pthread_mutex_lock(&control); close_locked(); pthread_mutex_unlock(&control); }
void low_latency_audio_pause(void) {
    pthread_mutex_lock(&control);
    if (output) {
        __atomic_store_n(&output->running, 0, __ATOMIC_RELEASE);
        __atomic_store_n(&output->reset_envelope, 1, __ATOMIC_RELEASE);
        __atomic_store_n(&output->ring.flush, 1, __ATOMIC_RELEASE);
        aaudio_stream_state_t state = api_Stream_getState(output->stream), next;
        if (state == AAUDIO_STREAM_STATE_STARTING || state == AAUDIO_STREAM_STATE_STARTED) {
            api_Stream_requestPause(output->stream);
            state = api_Stream_getState(output->stream);
        }
        if (state == AAUDIO_STREAM_STATE_PAUSING)
            api_Stream_waitForStateChange(output->stream, state, &next, 100000000);
    }
    pthread_mutex_unlock(&control);
}
void low_latency_audio_resume(void) {
    pthread_mutex_lock(&control);
    if (output) {
        aaudio_stream_state_t state = api_Stream_getState(output->stream);
        if (state == AAUDIO_STREAM_STATE_STARTING || state == AAUDIO_STREAM_STATE_STARTED) {
            // Pause is asynchronous and can fail while a stream is starting.
            // An already-running stream must regain its callback output gate.
            output->started = 1;
            output->progress_at = nds_metrics_now_us();
            output->observed_callbacks = __atomic_load_n(&output->callbacks, __ATOMIC_ACQUIRE);
            __atomic_store_n(&output->running, 1, __ATOMIC_RELEASE);
        } else {
            if (state == AAUDIO_STREAM_STATE_PAUSING) {
                aaudio_stream_state_t next;
                api_Stream_waitForStateChange(output->stream,state,&next,100000000);
                state=api_Stream_getState(output->stream);
            }
            if (state == AAUDIO_STREAM_STATE_OPEN || state == AAUDIO_STREAM_STATE_PAUSED || state == AAUDIO_STREAM_STATE_STOPPED) {
                // Callbacks are quiescent here. Reset both sides before arming
                // the producer, so the first callback does not discard priming.
                uint32_t written=__atomic_load_n(&output->ring.written,__ATOMIC_ACQUIRE);
                __atomic_store_n(&output->ring.read,written,__ATOMIC_RELEASE);
                __atomic_store_n(&output->ring.flush,0,__ATOMIC_RELEASE);
                output->ring.phase=0;
                output->started=0;
                __atomic_store_n(&output->running,1,__ATOMIC_RELEASE);
            } else {
                // The producer performs the complete OpenSL handoff. Closing
                // here would strand the legacy player in its stopped state.
                __atomic_store_n(&output->failed, 1, __ATOMIC_RELEASE);
            }
        }
    }
    pthread_mutex_unlock(&control);
}

static void start_primed_output(output_t *o, unsigned available, unsigned frames, uint64_t now) {
    // Fractional DS timing can prime 1469 frames into a 1470-frame ring.
    // Start before the next block would be rejected indefinitely.
    if (o->started || __atomic_load_n(&o->failed, __ATOMIC_ACQUIRE) || !available ||
        (available < 1470 && (uint64_t)available + frames <= o->limit)) return;
    if (api_Stream_requestStart(o->stream) == AAUDIO_OK) {
        o->started = 1;
        o->progress_at = now;
        o->observed_callbacks = __atomic_load_n(&o->callbacks, __ATOMIC_ACQUIRE);
    } else {
        __atomic_store_n(&o->failed, 1, __ATOMIC_RELEASE);
    }
}

int low_latency_audio_feed(const int16_t *pcm, unsigned samples, int muted) {
    pthread_mutex_lock(&control);
    unsigned wanted = __atomic_load_n(&configured, __ATOMIC_ACQUIRE);
    unsigned latency = (unsigned)__atomic_load_n(&audio_out_mode, __ATOMIC_ACQUIRE);
    if (latency > 3) latency = 3;
    if (applied_config >= 0 && ((unsigned)applied_config != wanted || applied_latency != latency)) {
        // Apply UI requests on the emulation producer. Callback ownership and
        // the legacy queue are transferred before feeding this audio block.
        pthread_mutex_unlock(&control);
        low_latency_audio_start(latency);
        extern void audio_out_restart_playback(void);
        audio_out_restart_playback();
        pthread_mutex_lock(&control);
    }
    output_t *o = output;
    if (o && __atomic_load_n(&o->failed, __ATOMIC_ACQUIRE)) {
        __android_log_print(ANDROID_LOG_WARN, "SeedlessAudio", "AAudio disconnected, reverting to OpenSL");
        close_locked(); o = NULL;
        /* Caller restarts the already-created OpenSL player. */
        extern void audio_out_restart_playback(void);
        pthread_mutex_unlock(&control);
        audio_out_restart_playback();
        return 0;
    }
    int owned = o != NULL;
    if (o && __atomic_load_n(&sound_enabled, __ATOMIC_ACQUIRE) && __atomic_load_n(&o->running, __ATOMIC_ACQUIRE)) {
        unsigned frames = samples / 2;
        uint64_t now = nds_metrics_now_us();
        if (muted) {
            __atomic_store_n(&o->reset_envelope, 1, __ATOMIC_RELEASE);
            __atomic_store_n(&o->ring.flush, 1, __ATOMIC_RELEASE);
        } else {
            unsigned written = 0;
            unsigned waits = 0;
            uint64_t generation = output_generation;
            while (written < frames) {
                if (output != o || output_generation != generation) {
                    // Another control operation owns the replacement backend.
                    // Do not send this partially handled block to OpenSL.
                    pthread_mutex_unlock(&control);
                    return 1;
                }
                if (__atomic_load_n(&o->failed, __ATOMIC_ACQUIRE)) {
                    __android_log_print(ANDROID_LOG_WARN, "SeedlessAudio", "AAudio disconnected, reverting to OpenSL");
                    close_locked();
                    extern void audio_out_restart_playback(void);
                    pthread_mutex_unlock(&control);
                    audio_out_restart_playback();
                    return 0;
                }
                unsigned current_latency = (unsigned)__atomic_load_n(&audio_out_mode, __ATOMIC_ACQUIRE);
                if (current_latency > 3) current_latency = 3;
                if (!__atomic_load_n(&sound_enabled, __ATOMIC_ACQUIRE) ||
                    !__atomic_load_n(&o->running, __ATOMIC_ACQUIRE) ||
                    __atomic_load_n(&configured, __ATOMIC_ACQUIRE) != (unsigned)applied_config ||
                    current_latency != applied_latency) {
                    pthread_mutex_unlock(&control);
                    return 1;
                }

                unsigned flushing = __atomic_load_n(&o->ring.flush, __ATOMIC_ACQUIRE);
                if (flushing && !o->started) {
                    // Before the first callback the consumer is quiescent.
                    // Clear stale priming data so new input survives unmute.
                    unsigned at = __atomic_load_n(&o->ring.written, __ATOMIC_ACQUIRE);
                    __atomic_store_n(&o->ring.read, at, __ATOMIC_RELEASE);
                    __atomic_store_n(&o->ring.flush, 0, __ATOMIC_RELEASE);
                    o->ring.phase = 0;
                    flushing = 0;
                }
                unsigned available = __atomic_load_n(&o->ring.written, __ATOMIC_ACQUIRE)
                                   - __atomic_load_n(&o->ring.read, __ATOMIC_ACQUIRE);
                unsigned space = !flushing && available < o->limit ? o->limit - available : 0;
                unsigned part = frames - written;
                if (part > space) part = space;
                if (part) written += pcm_ring_push(&o->ring, pcm + written * 2, part, o->limit);

                available = __atomic_load_n(&o->ring.written, __ATOMIC_ACQUIRE)
                          - __atomic_load_n(&o->ring.read, __ATOMIC_ACQUIRE);
                start_primed_output(o, available, frames, now);
                if (__atomic_load_n(&o->failed, __ATOMIC_ACQUIRE)) break;
                if (written == frames) break;
                if (waits++ >= 32) {
                    __atomic_fetch_add(&o->overruns, 1, __ATOMIC_RELAXED);
                    break;
                }
                // The consumer must run, and pause/close must acquire control.
                pthread_mutex_unlock(&control);
                platform_audio_wait_for_output(1000);
                pthread_mutex_lock(&control);
            }
        }
        unsigned available = __atomic_load_n(&o->ring.written, __ATOMIC_ACQUIRE)
                           - __atomic_load_n(&o->ring.read, __ATOMIC_ACQUIRE);
        start_primed_output(o, available, frames, now);
        if (o->started) {
            uint64_t callbacks = __atomic_load_n(&o->callbacks, __ATOMIC_ACQUIRE);
            if (callbacks != o->observed_callbacks) {
                o->observed_callbacks = callbacks;
                o->progress_at = now;
            } else if (now - o->progress_at >= 1000000) {
                __android_log_print(ANDROID_LOG_WARN, "SeedlessAudio", "AAudio callback stalled, scheduling OpenSL recovery");
                __atomic_store_n(&o->failed, 1, __ATOMIC_RELEASE);
            }
        }
        if (now - o->checked_at >= 1000000) {
            int xruns = api_Stream_getXRunCount(o->stream);
            if (xruns > o->xruns) {
                int cap = api_Stream_getBufferCapacityInFrames(o->stream);
                int desired = o->target + (int)o->burst;
                if (desired <= cap && desired <= (int)o->rate / 10) {
                    int actual = api_Stream_setBufferSizeInFrames(o->stream, desired);
                    if (actual > 0) o->target = actual;
                }
            }
            if (xruns >= 0) o->xruns = xruns;
            o->checked_at = now;
        }
    }
    pthread_mutex_unlock(&control);
    return owned;
}
void low_latency_audio_stats(uint64_t values[6]) {
    pthread_mutex_lock(&control); stats_locked(values); pthread_mutex_unlock(&control);
}
float low_latency_audio_buffer_fraction(void) {
    pthread_mutex_lock(&control);
    output_t *o = output;
    float fraction = -1.0f;
    unsigned latency = (unsigned)__atomic_load_n(&audio_out_mode, __ATOMIC_ACQUIRE);
    if (latency > 3) latency = 3;
    if (o && o->started && __atomic_load_n(&o->running, __ATOMIC_ACQUIRE) &&
        __atomic_load_n(&sound_enabled, __ATOMIC_ACQUIRE) &&
        __atomic_load_n(&configured, __ATOMIC_ACQUIRE) == (unsigned)applied_config &&
        latency == applied_latency &&
        !__atomic_load_n(&o->failed, __ATOMIC_ACQUIRE) &&
        !__atomic_load_n(&o->ring.flush, __ATOMIC_ACQUIRE)) {
        unsigned available = __atomic_load_n(&o->ring.written, __ATOMIC_ACQUIRE)
                           - __atomic_load_n(&o->ring.read, __ATOMIC_ACQUIRE);
        if (available > o->limit) available = o->limit;
        fraction = (float)available / (float)o->limit;
    }
    pthread_mutex_unlock(&control);
    return fraction;
}
