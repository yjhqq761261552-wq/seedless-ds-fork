#include "low_latency_audio.h"
#include "audio_rate.h"
#include "audio_speed.h"
#include "hires_runtime.h"
#include "blob_symbols.h"
#include "core/nds_state.h"
#include <stdint.h>
#include <string.h>
#include "platform/android/audio.h"
#include "frontend/audio_out.h"
#include "frontend/mic_default_sample.h"
#include "core_internals.h"
#include "seedlessds/rewind.h"
#include "seedlessds/metrics.h"
#include "seedlessds/platform.h"
#include "mem_access.h"
#include <pthread.h>

typedef uint32_t (*fn_vcall3)(void*, void*, int32_t);

/* The output state belongs to the emulation producer, never the callback. */
static audio_speed_t output_speed;
static audio_rate_t output_rate;
static uint32_t output_epoch;
static uint32_t applied_epoch;
static unsigned fade_remaining;
static int output_initialized;
static int output_was_muted;
static uint64_t output_wait_us;
static pthread_mutex_t queue_mutex = PTHREAD_MUTEX_INITIALIZER;

void platform_audio_queue_lock(void) { pthread_mutex_lock(&queue_mutex); }
void platform_audio_queue_unlock(void) { pthread_mutex_unlock(&queue_mutex); }

void platform_audio_mark_discontinuity(void) {
    __atomic_add_fetch(&output_epoch, 1, __ATOMIC_RELEASE);
}

void platform_audio_wait_for_output(unsigned microseconds) {
    uint64_t before = nds_metrics_now_us();
    nds_platform_default()->threads.sleep_us(microseconds);
    uint64_t after = nds_metrics_now_us();
    if (after >= before) output_wait_us += after - before;
}

static float legacy_buffer_fraction(void) {
    platform_audio_queue_lock();
    platform_audio_t *audio = PLATFORM_AUDIO;
    if (!audio->active || !audio->buffer_count || !audio->buffer_samples ||
        audio->buffer_count > PLATFORM_AUDIO_BUFFERS || audio->buffer_index >= audio->buffer_count) {
        platform_audio_queue_unlock();
        return -1.0f;
    }
    unsigned queued = __atomic_load_n(&audio->queued, __ATOMIC_ACQUIRE);
    if (queued > audio->buffer_count) queued = audio->buffer_count;
    uint64_t filled = (uint64_t)queued * audio->buffer_samples + audio->buffer_fill[audio->buffer_index];
    uint64_t capacity = (uint64_t)audio->buffer_count * audio->buffer_samples;
    float fraction = filled >= capacity ? 1.0f : (float)filled / (float)capacity;
    platform_audio_queue_unlock();
    return fraction;
}

static int legacy_feed(const int16_t *samples, unsigned count, int muted) {
    platform_audio_t *audio = PLATFORM_AUDIO;
    unsigned waits = 0;
    while (count) {
        platform_audio_queue_lock();
        if (!audio->active || !audio->player_queue || !audio->buffer_count ||
            audio->buffer_count > PLATFORM_AUDIO_BUFFERS || !audio->buffer_samples ||
            audio->buffer_samples > PLATFORM_AUDIO_BUFFER_BYTES / sizeof(int16_t) ||
            __atomic_load_n(&audio->closed, __ATOMIC_ACQUIRE) || audio->buffer_index >= audio->buffer_count) {
            platform_audio_queue_unlock();
            return 0;
        }
        if (__atomic_load_n(&audio->queued, __ATOMIC_ACQUIRE) >= audio->buffer_count) {
            platform_audio_queue_unlock();
            if (waits++ >= 150) return 0;
            platform_audio_wait_for_output(1000);
            continue;
        }
        unsigned index = audio->buffer_index;
        unsigned filled = audio->buffer_fill[index];
        if (filled >= audio->buffer_samples) {
            platform_audio_queue_unlock();
            return 0;
        }
        unsigned take = audio->buffer_samples - filled;
        if (take > count) take = count;
        unsigned char *buffer = audio->buffers + (size_t)index * PLATFORM_AUDIO_BUFFER_BYTES;
        if (muted) memset(buffer + (size_t)filled * 2, 0, (size_t)take * 2);
        else memcpy(buffer + (size_t)filled * 2, samples, (size_t)take * 2);
        filled += take;
        audio->buffer_fill[index] = filled;
        samples += take;
        count -= take;
        if (filled == audio->buffer_samples) {
            void *queue = audio->player_queue;
            fn_vcall3 enqueue = (fn_vcall3)rd_ptr(rd_ptr(queue));
            __atomic_fetch_add(&audio->queued, 1, __ATOMIC_RELEASE);
            if (enqueue(queue, buffer, (int32_t)(audio->buffer_samples * 2)) != 0) {
                __atomic_fetch_sub(&audio->queued, 1, __ATOMIC_ACQ_REL);
                audio->buffer_fill[index] = 0;
                platform_audio_mark_discontinuity();
                platform_audio_queue_unlock();
                return 0;
            }
            audio->buffer_fill[index] = 0;
            audio->buffer_index = (index + 1u) % audio->buffer_count;
            waits = 0;
        }
        platform_audio_queue_unlock();
    }
    return 1;
}

static void output_game_pcm(spu_t *spu, unsigned samples, int muted) {
    const unsigned CHUNK_FRAMES = 735;
    int16_t converted[(735 * 2 + 1) * 2];
    unsigned frames = samples / 2;
    uint32_t epoch = __atomic_load_n(&output_epoch, __ATOMIC_ACQUIRE);
    if (!output_initialized || epoch != applied_epoch) {
        audio_speed_init(&output_speed, 1.0f);
        audio_rate_init(&output_rate);
        applied_epoch = epoch;
        fade_remaining = 128;
        output_was_muted = muted;
        output_initialized = 1;
    }
    if (output_was_muted && !muted) fade_remaining = 128;
    output_was_muted = muted;

    const nds_config_t *config = spu->machine ? &spu->machine->config : NULL;
    float buffered = low_latency_audio_buffer_fraction();
    if (buffered < 0.0f) buffered = legacy_buffer_fraction();
    uint64_t now = nds_metrics_now_us();
    uint64_t active_time = now >= output_wait_us ? now - output_wait_us : 0;
    float rate = audio_rate_update(&output_rate, frames, active_time,
                                   config && config->fast_forward,
                                   config ? config->fast_forward_speed : 0, buffered);
    audio_speed_set_rate(&output_speed, rate);

    const int16_t *source = spu->output_samples;
    while (frames) {
        unsigned take = frames < CHUNK_FRAMES ? frames : CHUNK_FRAMES;
        unsigned produced = 0;
        if (!audio_speed_process(&output_speed, source, take, converted,
                                 CHUNK_FRAMES * 2 + 1, &produced)) {
            platform_audio_mark_discontinuity();
            return;
        }
        for (unsigned frame = 0; frame < produced && fade_remaining; ++frame) {
            unsigned gain = 129 - fade_remaining;
            converted[frame * 2] = (int16_t)((int32_t)converted[frame * 2] * (int32_t)gain / 128);
            converted[frame * 2 + 1] = (int16_t)((int32_t)converted[frame * 2 + 1] * (int32_t)gain / 128);
            --fade_remaining;
        }
        unsigned converted_samples = produced * 2;
        if (!low_latency_audio_feed(converted, converted_samples, muted) &&
            !legacy_feed(converted, converted_samples, muted)) {
            platform_audio_mark_discontinuity();
            return;
        }
        source += take * 2;
        frames -= take;
    }
}

void platform_audio_output_feed_frame(spu_t *spu)
{

    static void *(*s_memcpy)(void*, const void*, size_t);
    static void *(*s_memset)(void*, int, size_t);
    if (!s_memcpy) s_memcpy = (void *(*)(void*, const void*, size_t))sym_libc_memcpy;
    if (!s_memset) s_memset = (void *(*)(void*, int, size_t))sym_libc_memset;

    unsigned char *ctx = (unsigned char *)spu;
    if (spu->mixer.muted != 0)      goto L_5;
    if (__atomic_load_n(&PLATFORM_AUDIO->closed, __ATOMIC_ACQUIRE) != 0)          goto L_5;

    if (spu->mixer.mic_enabled != 0 &&
        spu->mixer.mic_init_pending != 0 &&
        spu->mixer.mic_init_failed == 0) {
        spu->mixer.mic_init_pending = 0;
        int32_t r = mic_init_recorder((long)(unsigned long)ctx);
        if (r < 0) spu->mixer.mic_init_failed = 1;
    }

    {
        uint32_t count = spu->output_written;
        platform_audio_t *au = PLATFORM_AUDIO;
        nds_capture_audio(ctx, count & 0x7fffffffu);

        if (spu->mixer.mic_loaded != 0) {

            if (PLATFORM_AUDIO->sound_disabled == 0) goto L_conv;
            {
                uint32_t n = count & 0x7fffffffu;
                if (n != 0) {
                    unsigned char *table = (unsigned char *)PLATFORM_AUDIO->mic_samples;
                    uint32_t idx   = PLATFORM_AUDIO->mic_sample_index;
                    uint32_t half = MIC_DEFAULT_SAMPLE_BYTES >> 1;
                    unsigned char *dst = spu->mic_buffer;
                    for (uint32_t i = 0; i < n; i++) {
                        int16_t sample = rd16s(table + (size_t)idx * 2);
                        uint32_t nidx = idx + 1;
                        idx = (nidx >= half) ? 0u : nidx;

                        int32_t v  = sample;
                        int32_t r1 = v >> 2;
                        int32_t r2 = v << 1;
                        int32_t av = (v < 0) ? -v : v;
                        int32_t sel = (av < 0x2000) ? r1 : r2;
                        if (sel > 32767)  sel = 32767;
                        if (sel < -32767) sel = -32767;

                        PLATFORM_AUDIO->mic_sample_index = idx;
                        wr16(dst, (int16_t)sel);
                        dst += 2;
                    }
                }
            }

        }

    L_1:

        if (PLATFORM_AUDIO->mic_enabled != 0) goto L_4;

    L_2:
        output_game_pcm(spu, count & 0x7fffffffu, (nds_rewind_status() & 1u) != 0);
        goto L_4;

    L_conv:

        {
            void *engine = PLATFORM_AUDIO->record_itf;
            if (engine == NULL) goto L_3;

            uint32_t n = count & 0x7fffffffu;
            if (n != 0) {

                float gain = audio_out_volume;
                unsigned char *source  = (unsigned char *)au->mix;
                unsigned char *dest = spu->mic_buffer;
                for (uint32_t i = 0; i < n; i++) {
                    int16_t sample = rd16s(source + (size_t)i * 2);
                    float normalized = (float)sample * (1.0f / 32768.0f);
                    float mag = normalized * (gain * normalized);
                    float with_sign = (sample < 0) ? -(mag * 32768.0f) : (mag * 32768.0f);
                    int32_t v = (int32_t)with_sign;
                    if (v > 32767)  v = 32767;
                    if (v < -32767) v = -32767;
                    wr16(dest + (size_t)i * 2, (int16_t)v);
                }
            }

            if (audio_out_enabled != 0) {
                uint32_t slot = au->mic_slot;
                unsigned char *src = au->mic_buffers[slot];
                unsigned char *dst = (unsigned char *)au->mix;
                s_memcpy(dst, src, 0x1000);

                uint32_t total = au->mic_total;
                if (total <= 4) {
                    uint32_t updated_slot = ((slot + 1u) & 0xffu) % PLATFORM_AUDIO_MIC_QUEUE_DEPTH;
                    au->mic_slot = (uint8_t)updated_slot;
                    au->mic_total = total + 1u;

                    void *obj2 = au->recorder_queue;
                    unsigned char *arg2 = au->mic_buffers[updated_slot];
                    void *vt2 = rd_ptr(obj2);
                    fn_vcall3 fn2 = (fn_vcall3)rd_ptr(vt2);
                    fn2(obj2, arg2, (int32_t)n);

                    if (PLATFORM_AUDIO->mic_enabled != 0) goto L_4;
                    goto L_2;
                }
            }
            goto L_1;
        }

    L_3:

        s_memset(spu->mic_buffer, 0, (size_t)count << 2);
        if (PLATFORM_AUDIO->mic_enabled == 0) goto L_2;
        goto L_4;

    L_4:

        {
            __atomic_fetch_sub(&PLATFORM_AUDIO->accumulated,
                               (uint64_t)(count & 0x7fffffffu), __ATOMIC_RELAXED);
        }
        spu->output_written = 0;
        return;
    }

L_5:

    spu->output_written = 0;
    return;
}
#undef G1
#undef G2
#undef G3
#undef RD

platform_audio_t platform_audio_state;
