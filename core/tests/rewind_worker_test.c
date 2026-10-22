#include "core/rewind_worker.h"
#include <pthread.h>
#include <stdlib.h>
#include <string.h>
static pthread_mutex_t gate_mutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t gate_cv = PTHREAD_COND_INITIALIZER;
static int blocked, entered;
static int codec_blocked, codec_entered;
void rewind_codec_block_test(int on)
{
    pthread_mutex_lock(&gate_mutex);
    codec_blocked = on;
    if (on) codec_entered = 0;
    pthread_cond_broadcast(&gate_cv);
    pthread_mutex_unlock(&gate_mutex);
}
void rewind_codec_wait_test(void)
{
    pthread_mutex_lock(&gate_mutex);
    while (!codec_entered) pthread_cond_wait(&gate_cv, &gate_mutex);
    pthread_mutex_unlock(&gate_mutex);
}
void rewind_codec_test_before_pack(void)
{
    pthread_mutex_lock(&gate_mutex);
    if (codec_blocked) {
        codec_entered = 1; pthread_cond_broadcast(&gate_cv);
        while (codec_blocked) pthread_cond_wait(&gate_cv, &gate_mutex);
    }
    pthread_mutex_unlock(&gate_mutex);
}
static int pack(void *raw, size_t size, void **out, size_t *out_size)
{
    pthread_mutex_lock(&gate_mutex);
    entered = 1; pthread_cond_broadcast(&gate_cv);
    while (blocked) pthread_cond_wait(&gate_cv, &gate_mutex);
    pthread_mutex_unlock(&gate_mutex);
    *out = malloc(size); *out_size = size;
    if (!*out) return 0;
    memcpy(*out, raw, size);
    return 1;
}
#define CHECK(x) do { if (!(x)) { failure = __LINE__; goto cleanup; } } while (0)
int rewind_worker_test(void)
{
    int failure = 0;
    rewind_snapshot_t result = {0};
    uint64_t epoch = 0;
    int ok = 0;
    void *raw = NULL;
    rewind_worker_stop();
    CHECK(rewind_worker_start(pack));
    CHECK(rewind_worker_idle());
    size_t capacity = 0;
    CHECK(!rewind_worker_buffer(&capacity) && capacity == 0);
    raw = malloc(8); CHECK(raw);
    memset(raw, 0x5a, 8);
    pthread_mutex_lock(&gate_mutex); blocked = 1; entered = 0; pthread_mutex_unlock(&gate_mutex);
    CHECK(rewind_worker_submit(raw, 8, 8, 60, 7)); raw = NULL;
    pthread_mutex_lock(&gate_mutex);
    while (!entered) pthread_cond_wait(&gate_cv, &gate_mutex);
    pthread_mutex_unlock(&gate_mutex);
    /* Worker is deliberately blocked. These operations must return immediately. */
    CHECK(!rewind_worker_idle());
    CHECK(!rewind_worker_buffer(&capacity) && capacity == 0);
    CHECK(!rewind_worker_submit((void *)1, 8, 8, 61, 7));
    CHECK(!rewind_worker_take(&result, &epoch, &ok));
    pthread_mutex_lock(&gate_mutex); blocked = 0; pthread_cond_broadcast(&gate_cv); pthread_mutex_unlock(&gate_mutex);
    rewind_worker_flush_test();
    CHECK(!rewind_worker_idle()); /* completed result must be collected before reuse */
    CHECK(rewind_worker_take(&result, &epoch, &ok));
    CHECK(ok && result.frame == 60 && result.size == 8 && epoch == 7);
    CHECK(((unsigned char *)result.data)[0] == 0x5a);
    free(result.data); result.data = NULL;
    CHECK(rewind_worker_idle());
    raw = rewind_worker_buffer(&capacity); CHECK(raw && capacity == 8);
    memset(raw, 0x33, 8);
    CHECK(rewind_worker_submit(raw, capacity, 8, 120, 8)); raw = NULL;
    rewind_worker_flush_test();
    CHECK(rewind_worker_take(&result, &epoch, &ok) && ok && epoch == 8 && result.frame == 120);
    CHECK(((unsigned char *)result.data)[0] == 0x33);
    free(result.data); result.data = NULL;
    for (unsigned i = 0; i < 512; ++i) {
        if (i % 128 == 0) { rewind_worker_stop(); CHECK(rewind_worker_start(pack)); }
        raw = rewind_worker_buffer(&capacity);
        if (!raw) { raw = malloc(8); capacity = 8; }
        CHECK(raw);
        memset(raw, (int)(i & 255), 8);
        CHECK(rewind_worker_submit(raw, capacity, 8, 400 + i, 100 + i)); raw = NULL;
        rewind_worker_flush_test();
        CHECK(rewind_worker_take(&result, &epoch, &ok) && ok && epoch == 100 + i && result.frame == 400 + i);
        CHECK(((unsigned char *)result.data)[0] == (i & 255));
        free(result.data); result.data = NULL;
    }
cleanup:
    pthread_mutex_lock(&gate_mutex); blocked = 0; pthread_cond_broadcast(&gate_cv); pthread_mutex_unlock(&gate_mutex);
    free(raw); free(result.data);
    rewind_worker_stop(); rewind_worker_stop();
    return failure;
}
