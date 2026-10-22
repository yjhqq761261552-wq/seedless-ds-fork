#include "rewind_ring.h"
#include <stdlib.h>
#include <string.h>

static void drop_oldest(rewind_ring_t *ring)
{
    ring->bytes -= ring->items[0].size;
    free(ring->items[0].data);
    --ring->count;
    memmove(ring->items, ring->items + 1, ring->count * sizeof ring->items[0]);
}

void rewind_ring_clear(rewind_ring_t *ring)
{
    while (ring->count) drop_oldest(ring);
    ring->bytes = 0;
}

void rewind_ring_expire(rewind_ring_t *ring, uint64_t oldest_frame)
{
    while (ring->count && ring->items[0].frame < oldest_frame) drop_oldest(ring);
}

int rewind_ring_push(rewind_ring_t *ring, rewind_snapshot_t item, unsigned limit)
{
    if (!item.data || !item.size || item.size > REWIND_MEMORY_BUDGET || !limit) {
        free(item.data);
        return 0;
    }
    if (limit > REWIND_MAX_SNAPSHOTS) limit = REWIND_MAX_SNAPSHOTS;
    while (ring->count && (ring->count >= limit || ring->bytes > REWIND_MEMORY_BUDGET - item.size))
        drop_oldest(ring);
    ring->items[ring->count++] = item;
    ring->bytes += item.size;
    return 1;
}

rewind_snapshot_t rewind_ring_take(rewind_ring_t *ring, uint64_t current_frame)
{
    while (ring->count) {
        rewind_snapshot_t item = ring->items[--ring->count];
        ring->bytes -= item.size;
        if (item.frame < current_frame) return item;
        free(item.data);
    }
    return (rewind_snapshot_t){0};
}
