#include "seedlessds/frame_observer.h"
static void (*observer)(void);
void nds_frame_observer_set(void (*callback)(void)) {
    __atomic_store_n(&observer, callback, __ATOMIC_RELEASE);
}
void nds_frame_observer_emit(void) {
    void (*callback)(void) = __atomic_load_n(&observer, __ATOMIC_ACQUIRE);
    if (callback) callback();
}
