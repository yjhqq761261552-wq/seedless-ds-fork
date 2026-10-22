#define __ANDROID_UNAVAILABLE_SYMBOLS_ARE_WEAK__ 1
#include "low_latency_audio.h"
#include "pcm_ring.h"
#include "seedlessds/metrics.h"
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
typedef struct {
    AAudioStream *stream;
    pcm_ring_t ring;
    unsigned rate, burst, limit;
    int running, started, failed, xruns, target;
    uint64_t underruns, overruns, callbacks, checked_at;
} output_t;
/* Lifetime is stable until stream close has joined all callbacks. Control APIs
 * and the producer share a short lock; the real-time callback never takes it. */
static output_t *output;
static uint64_t last_stats[6];
static aaudio_data_callback_result_t on_audio(AAudioStream *stream, void *user, void *data, int32_t frames) {
    (void)stream;
    output_t *o = user;
    if (!__atomic_load_n(&o->running, __ATOMIC_ACQUIRE)) { memset(data, 0, (size_t)frames * 4); return AAUDIO_CALLBACK_RESULT_CONTINUE; }
    unsigned got = pcm_ring_render(&o->ring, data, (unsigned)frames, o->rate);
    unsigned gain = __atomic_load_n(&gain_q15, __ATOMIC_RELAXED);
    if (gain != 32768) {
        int16_t *samples = data;
        for (int32_t i = 0; i < frames * 2; ++i) samples[i] = (int16_t)(((int32_t)samples[i] * (int32_t)gain) / 32768);
    }
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
    if (!__atomic_load_n(&configured, __ATOMIC_ACQUIRE) || !load_api()) goto done;
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
    if (latency > 3) latency = 3;
    o->limit = 1470 + 735 * latency;
    o->target = api_Stream_setBufferSizeInFrames(o->stream, (int32_t)(o->burst * bursts[latency]));
    if (o->target <= 0) goto fail;
    __atomic_store_n(&o->running, 1, __ATOMIC_RELEASE);
    /* Start after two emulation blocks have primed the ring, not during ROM boot. */
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
        if (state != AAUDIO_STREAM_STATE_STARTING && state != AAUDIO_STREAM_STATE_STARTED) {
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
            } else close_locked();
        }
    }
    pthread_mutex_unlock(&control);
}
int low_latency_audio_feed(const int16_t *pcm, unsigned samples, int muted) {
    pthread_mutex_lock(&control);
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
        if (muted) __atomic_store_n(&o->ring.flush, 1, __ATOMIC_RELEASE);
        else if (!pcm_ring_push(&o->ring, pcm, frames, o->limit))
            __atomic_fetch_add(&o->overruns, 1, __ATOMIC_RELAXED);
        if (!o->started && __atomic_load_n(&o->ring.written,__ATOMIC_ACQUIRE)
            - __atomic_load_n(&o->ring.read,__ATOMIC_ACQUIRE) >= 1470) {
            if (api_Stream_requestStart(o->stream) == AAUDIO_OK) o->started=1;
            else __atomic_store_n(&o->failed,1,__ATOMIC_RELEASE);
        }
        uint64_t now = nds_metrics_now_us();
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
