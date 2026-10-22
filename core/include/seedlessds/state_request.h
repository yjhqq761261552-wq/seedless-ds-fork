#ifndef SEEDLESS_STATE_REQUEST_H
#define SEEDLESS_STATE_REQUEST_H
#ifdef __cplusplus
extern "C" {
#endif
#include <stdint.h>
/* One operation owns its slot until the actual load or file commit completes.
 * Status: 0 pending, 1 committed, -1 failed/stale. Ticket 0 means rejected. */
uint64_t nds_state_request(unsigned slot, int load);
int nds_state_request_status(uint64_t ticket);
void nds_state_request_poll(void *machine);
void nds_state_write_completed(int result);
void nds_state_requests_cancel(void);
void nds_boot_state_begin(int slot);
void nds_boot_state_complete(int result);
int nds_boot_state_status(void);
void nds_state_hold(int held);
int nds_state_hold_requested(void);
int nds_state_held(void);
void nds_state_hold_acknowledge(int held);
#ifdef __cplusplus
}
#endif
#endif
