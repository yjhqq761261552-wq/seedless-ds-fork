/* Host scheduling test. Emulator/clock are fixtures; rewind.c and ring.c are
   production sources. Does not claim to validate Android game-state fidelity. */
#include "seedlessds/rewind.h"
#include "seedlessds/platform.h"
#include "core/nds_state.h"
#include "frontend/frontend.h"
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <zlib.h>
#include "core/rewind_worker.h"
#include "core/rewind_codec.h"
#include "core/state_format.h"
#define CHECK(x) do { if (!(x)) return __LINE__; } while (0)
frontend_t test_frontend;
static nds_t machine;
static uint64_t clock_us;
static unsigned saves, loads, fail_save, fail_load, manual_busy, control_pause, sleeps;
static unsigned fail_pack;

void time_now_microseconds(uint64_t *value) { *value = clock_us; }
int state_write_pending(void) { return manual_busy; }
int nds_control_pause_requested(void) { return control_pause; }
static void sleep_fixture(unsigned us) { (void)us; ++sleeps; nds_rewind_cancel(); }
static nds_platform_t platform = { .threads = { .sleep_us = sleep_fixture } };
const nds_platform_t *nds_platform_default(void) { return &platform; }
int state_rewind_capture_raw(void *object, void **data, size_t *capacity, size_t *size)
{
    ++saves;
    if (fail_save) return 0;
    uint32_t ids[STATE_SECTION_COUNT_MAX];
    uint32_t count = state_expected_sections(0, ids);
    size_t total = sizeof(rewind_header_t) + count * (sizeof(state_section_header_t) + sizeof(uint64_t));
    if (*capacity < total) {
        void *grown = realloc(*data, total);
        if (!grown) return 0;
        *data = grown; *capacity = total;
    }
    rewind_header_t header = { REWIND_MAGIC, total - sizeof header, 0x12345678, 0 };
    if (fail_pack) header.magic = 0;
    memcpy(*data, &header, sizeof header);
    unsigned char *cursor = (unsigned char *)*data + sizeof header;
    for (unsigned i = 0; i < count; ++i) {
        state_section_header_t section = { ids[i], sizeof(uint64_t), 0, 0 };
        memcpy(cursor, &section, sizeof section);
        memcpy(cursor + sizeof section, &((nds_t *)object)->sched.frame, sizeof(uint64_t));
        cursor += sizeof section + sizeof(uint64_t);
    }
    *size = total;
    return 1;
}
int state_rewind_restore(void *object, const void *data, size_t size)
{
    ++loads;
    if (fail_load || size <= sizeof(rewind_header_t)) return 0;
    unsigned char raw[1024];
    uLongf raw_size = sizeof raw;
    if (uncompress(raw, &raw_size, (const unsigned char *)data + sizeof(rewind_header_t), size - sizeof(rewind_header_t)) != Z_OK) return 0;
    memcpy(&((nds_t *)object)->sched.frame, raw + sizeof(state_section_header_t), sizeof(uint64_t));
    return 1;
}
static void poll(uint64_t frame) {
    unsigned before = saves;
    machine.sched.frame = frame; nds_rewind_poll(&machine);
    if (saves != before) { rewind_worker_flush_test(); nds_rewind_poll(&machine); }
}
static unsigned count(void) { return (nds_rewind_status() >> 1) & 0x7fff; }
int rewind_manager_test(void)
{
    nds_rewind_shutdown();
    nds_rewind_configure(0, 30);
    poll(0);
    CHECK(nds_rewind_hold(1) == -1 && saves == 0 && count() == 0);
    nds_rewind_configure(1, 30);
    CHECK(nds_rewind_hold(1) == 0);
    poll(0);
    CHECK(saves == 1 && count() == 1);
    poll(59); CHECK(saves == 1);
    poll(60); CHECK(saves == 2 && count() == 2);
    uint64_t revision = nds_rewind_status() >> 16;
    CHECK(nds_rewind_hold(1) == 1);
    nds_rewind_hold(0); /* Tap UP arrives before frame boundary. */
    poll(61);
    CHECK(loads == 1 && machine.sched.frame == 60 && count() == 1);
    CHECK((nds_rewind_status() >> 16) == revision + 1);
    CHECK(!(nds_rewind_status() & 1));
    poll(120); CHECK(count() == 2);
    nds_rewind_hold(1);
    poll(121); CHECK(machine.sched.frame == 120 && count() == 1);
    clock_us = 100000;
    poll(121); CHECK(loads == 2); /* Hold repeats are throttled by real time. */
    clock_us = 250000;
    poll(122); CHECK(machine.sched.frame == 0 && loads == 3 && count() == 0);
    CHECK(sleeps == 0); /* Last restored frame must render before the oldest-point wait. */
    poll(1);
    CHECK(sleeps == 1 && !(nds_rewind_status() & 1));
    poll(60);
    CHECK(count() == 1);
    nds_rewind_configure(1, 30); /* Applying unchanged settings preserves history. */
    poll(61); CHECK(count() == 1);
    nds_rewind_hold(1);
    nds_rewind_cancel(); /* Menu/pause/disconnect cancels pending quick taps too. */
    unsigned old_loads = loads;
    poll(62); CHECK(loads == old_loads);
    nds_rewind_invalidate();
    CHECK(count() == 0 && !(nds_rewind_status() & 1));
    manual_busy = 1;
    poll(63); CHECK(count() == 0);
    manual_busy = 0;
    poll(64); CHECK(count() == 1);
    nds_rewind_invalidate();
    control_pause = 1;
    poll(65); CHECK(count() == 0);
    control_pause = 0;
    poll(66); CHECK(count() == 1);
    fail_load = 1;
    nds_rewind_hold(1);
    poll(67);
    CHECK(count() == 0 && nds_rewind_hold(1) == -2 && !(nds_rewind_status() & 1));
    fail_load = 0;
    nds_rewind_configure(0, 30); poll(68);
    nds_rewind_configure(1, 30);
    fail_save = 1;
    poll(69); CHECK(count() == 0 && nds_rewind_hold(1) == -2);
    fail_save = 0;
    nds_rewind_invalidate(); poll(70);
    CHECK(count() == 1);
    nds_rewind_hold(1);
    nds_rewind_invalidate();
    CHECK(count() == 0 && !(nds_rewind_status() & 1));
    poll(71); CHECK(count() == 1);
    nds_rewind_shutdown();
    CHECK(count() == 0 && !(nds_rewind_status() & 1));
    return 0;
}

int rewind_manager_async_test(void)
{
    nds_rewind_shutdown(); nds_rewind_configure(1, 30);
    machine.sched.frame = 10;
    nds_rewind_poll(&machine); /* starts a background job without collecting it */
    nds_rewind_invalidate();
    rewind_worker_flush_test();
    machine.sched.frame = 20;
    nds_rewind_poll(&machine); /* result from frame 10 must be rejected */
    CHECK(count() == 0);
    rewind_worker_flush_test();
    nds_rewind_poll(&machine);
    CHECK(count() == 1);
    nds_rewind_hold(1); nds_rewind_hold(0);
    poll(21);
    CHECK(machine.sched.frame == 20 && count() == 0);
    nds_rewind_invalidate(); poll(0); /* establish a history point at frame zero */
    extern void rewind_codec_block_test(int);
    extern void rewind_codec_wait_test(void);
    rewind_codec_block_test(1);
    machine.sched.frame = 60; nds_rewind_poll(&machine);
    rewind_codec_wait_test();
    unsigned before = saves;
    machine.sched.frame = 120; nds_rewind_poll(&machine);
    int skipped_without_wait = saves == before && machine.sched.frame == 120;
    nds_rewind_hold(1); nds_rewind_hold(0);
    machine.sched.frame = 121; nds_rewind_poll(&machine);
    int restored_before_worker_finished = machine.sched.frame == 0;
    rewind_codec_block_test(0);
    rewind_worker_flush_test();
    machine.sched.frame = 1; nds_rewind_poll(&machine);
    CHECK(skipped_without_wait && restored_before_worker_finished);
    CHECK(count() == 0); /* late frame-60 result may not reinsert the abandoned future */
    nds_rewind_invalidate(); fail_pack = 1; poll(70);
    CHECK(count() == 0 && nds_rewind_hold(1) == -2);
    fail_pack = 0; nds_rewind_invalidate(); poll(71);
    CHECK(count() == 1);
    machine.sched.frame = 131; nds_rewind_poll(&machine); /* quit while compression may still run */
    nds_rewind_shutdown();
    nds_rewind_configure(0, 30); poll(22);
    CHECK(count() == 0 && rewind_worker_idle() == 0);
    return 0;
}
