#ifndef SEEDLESSDS_REWIND_RING_H
#define SEEDLESSDS_REWIND_RING_H
#include <stddef.h>
#include <stdint.h>
#define REWIND_MAX_SNAPSHOTS 60
#define REWIND_MEMORY_BUDGET (64u * 1024u * 1024u)
typedef struct rewind_snapshot {
    void *data;
    size_t size;
    uint64_t frame;
} rewind_snapshot_t;
typedef struct rewind_ring {
    rewind_snapshot_t items[REWIND_MAX_SNAPSHOTS];
    size_t bytes;
    unsigned count;
} rewind_ring_t;
void rewind_ring_clear(rewind_ring_t *ring);
void rewind_ring_expire(rewind_ring_t *ring, uint64_t oldest_frame);
/* Takes ownership, including when the snapshot exceeds the budget. */
int rewind_ring_push(rewind_ring_t *ring, rewind_snapshot_t item, unsigned limit);
/* Removes the newest past snapshot; future/equal snapshots are discarded. */
rewind_snapshot_t rewind_ring_take(rewind_ring_t *ring, uint64_t current_frame);
#endif
