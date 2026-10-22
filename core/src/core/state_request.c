#include "seedlessds/state_request.h"
#include "frontend/frontend.h"
#include "core_internals.h"
#include <pthread.h>

extern void input_pause_notify(void);
static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;
static uint64_t sequence, current;
static unsigned slot;
static int load, phase, result;

uint64_t nds_state_request(unsigned requested_slot, int requested_load) {
    pthread_mutex_lock(&lock);
    uint64_t ticket = 0;
    if (requested_slot <= 9 && !phase && !state_write_pending()
        && FRONTEND->machine && !FRONTEND->quit_request) {
        current = ticket = ++sequence;
        slot = requested_slot;
        load = requested_load != 0;
        result = 0;
        phase = 1;
    }
    pthread_mutex_unlock(&lock);
    if (ticket) input_pause_notify();
    return ticket;
}

int nds_state_request_status(uint64_t ticket) {
    pthread_mutex_lock(&lock);
    int status = ticket && ticket == current ? (phase ? 0 : result) : -1;
    pthread_mutex_unlock(&lock);
    return status;
}

void nds_state_write_completed(int status) {
    pthread_mutex_lock(&lock);
    if (phase == 2 && !load) {
        result = status == 0 ? 1 : -1;
        phase = 0;
    }
    pthread_mutex_unlock(&lock);
    input_pause_notify();
}

void nds_state_request_poll(void *machine) {
    pthread_mutex_lock(&lock);
    if (phase != 1 || state_write_pending()) {
        pthread_mutex_unlock(&lock);
        return;
    }
    const unsigned requested_slot = slot;
    const int requested_load = load;
    phase = 2;
    pthread_mutex_unlock(&lock);
    if (requested_load) {
        extern void input_state_reset(void);
        input_state_reset();
        FRONTEND->lid_closed = 0;
    }
    int status = requested_load
        ? state_load_slot(machine, requested_slot, 0, 0, 0)
        : state_save_slot_with_screens(requested_slot);
    if (requested_load || status != 0) {
        pthread_mutex_lock(&lock);
        result = status == 0 ? 1 : -1;
        phase = 0;
        pthread_mutex_unlock(&lock);
    }
}

void nds_state_requests_cancel(void) {
    pthread_mutex_lock(&lock);
    nds_state_hold(0);
    nds_boot_state_complete(-1);
    /* An admitted writer owns its memory until it finishes. */
    if (phase == 1) { phase = 0; result = -1; }
    pthread_mutex_unlock(&lock);
}

static int hold_requested, hold_ack;
void nds_state_hold(int held) {
    __atomic_store_n(&hold_requested, held != 0, __ATOMIC_RELEASE);
    input_pause_notify();
}
int nds_state_hold_requested(void) { return __atomic_load_n(&hold_requested, __ATOMIC_ACQUIRE); }
int nds_state_held(void) { return __atomic_load_n(&hold_ack, __ATOMIC_ACQUIRE); }
void nds_state_hold_acknowledge(int held) { __atomic_store_n(&hold_ack, held, __ATOMIC_RELEASE); }

static int boot_result = 2;
void nds_boot_state_begin(int requested_slot) {
    __atomic_store_n(&boot_result, requested_slot >= 0 && requested_slot <= 9 ? 0 : 2, __ATOMIC_RELEASE);
}
void nds_boot_state_complete(int result) {
    int pending = 0;
    __atomic_compare_exchange_n(&boot_result, &pending, result == 0 ? 1 : -1,
                               0, __ATOMIC_RELEASE, __ATOMIC_RELAXED);
}
int nds_boot_state_status(void) { return __atomic_load_n(&boot_result, __ATOMIC_ACQUIRE); }
