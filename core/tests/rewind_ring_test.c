#include "core/rewind_ring.h"
#include <stdlib.h>
#include <stdint.h>
#define CHECK(x) do { if (!(x)) return __LINE__; } while (0)
static rewind_snapshot_t sample(size_t size, uint64_t frame)
{
    void *data = malloc(1); /* Ring never inspects payload; tests can model large states cheaply. */
    return (rewind_snapshot_t){ data, size, frame };
}
/* Returns zero on success; otherwise returns the failing source line. */
int rewind_ring_test(void)
{
    rewind_ring_t ring = {0};
    rewind_snapshot_t got = rewind_ring_take(&ring, 0);
    CHECK(!got.data && ring.count == 0 && ring.bytes == 0);
    CHECK(rewind_ring_push(&ring, sample(100, 10), 3));
    CHECK(rewind_ring_push(&ring, sample(200, 20), 3));
    got = rewind_ring_take(&ring, 21);
    CHECK(got.frame == 20 && got.size == 200 && ring.count == 1 && ring.bytes == 100);
    free(got.data);
    got = rewind_ring_take(&ring, 10); /* Equal and future frames must never replay. */
    CHECK(!got.data && !ring.count && !ring.bytes);
    for (unsigned i = 1; i <= 10; ++i) CHECK(rewind_ring_push(&ring, sample(i, i), 3));
    CHECK(ring.count == 3 && ring.bytes == 8 + 9 + 10 && ring.items[0].frame == 8);
    rewind_ring_expire(&ring, 9);
    CHECK(ring.count == 2 && ring.bytes == 19 && ring.items[0].frame == 9);
    /* Restore chronological order for the take/discard scenario below. */
    rewind_ring_clear(&ring);
    CHECK(rewind_ring_push(&ring, sample(8, 8), 3));
    CHECK(rewind_ring_push(&ring, sample(9, 9), 3));
    CHECK(rewind_ring_push(&ring, sample(10, 10), 3));
    got = rewind_ring_take(&ring, 9);
    CHECK(got.frame == 8 && !ring.count && !ring.bytes);
    free(got.data);
    CHECK(!rewind_ring_push(&ring, sample(1, 1), 0));
    CHECK(!rewind_ring_push(&ring, sample(REWIND_MEMORY_BUDGET + 1, 1), 60));
    CHECK(!rewind_ring_push(&ring, sample(0, 1), 60));
    CHECK(!rewind_ring_push(&ring, (rewind_snapshot_t){0}, 60));
    CHECK(!ring.count && !ring.bytes);
    CHECK(rewind_ring_push(&ring, sample(REWIND_MEMORY_BUDGET, 1), 60));
    CHECK(rewind_ring_push(&ring, sample(1, 2), 60));
    CHECK(ring.count == 1 && ring.bytes == 1 && ring.items[0].frame == 2);
    rewind_ring_clear(&ring);
    for (unsigned i = 0; i < 100; ++i) CHECK(rewind_ring_push(&ring, sample(5, i), 1000));
    CHECK(ring.count == 60 && ring.bytes == 300 && ring.items[0].frame == 40);
    rewind_ring_clear(&ring);
    rewind_ring_clear(&ring); /* Idempotent lifecycle cleanup. */
    uint32_t random = 1;
    for (unsigned i = 0; i < 10000; ++i) {
        random = random * 1664525u + 1013904223u;
        if (random & 4) {
            CHECK(rewind_ring_push(&ring, sample((random % 4000000) + 1, i), (random % 60) + 1));
        } else {
            got = rewind_ring_take(&ring, i > 10 ? i - 10 : 0);
            if (got.data) { CHECK(got.frame < (i > 10 ? i - 10 : 0)); free(got.data); }
        }
        size_t total = 0;
        CHECK(ring.count <= 60);
        for (unsigned j = 0; j < ring.count; ++j) {
            CHECK(ring.items[j].data);
            if (j) CHECK(ring.items[j-1].frame < ring.items[j].frame);
            total += ring.items[j].size;
        }
        CHECK(total == ring.bytes && total <= REWIND_MEMORY_BUDGET);
    }
    rewind_ring_clear(&ring);
    CHECK(!ring.count && !ring.bytes);
    return 0;
}
