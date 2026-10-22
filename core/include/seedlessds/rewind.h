#ifndef SEEDLESSDS_REWIND_H
#define SEEDLESSDS_REWIND_H
#include <stdint.h>
#ifdef __cplusplus
extern "C" {
#endif
/* Frontend calls only publish requests. Snapshot work stays on the emulation thread. */
void nds_rewind_interval(unsigned seconds);
void nds_rewind_configure(int enabled, unsigned seconds);
int nds_rewind_hold(int pressed); /* 1 accepted, 0 no history, -1 disabled, -2 failed */
void nds_rewind_cancel(void);
uint64_t nds_rewind_status(void); /* revision << 16 | snapshot count << 1 | held/pending */
void nds_rewind_invalidate(void);
void nds_rewind_poll(void *machine);
void nds_rewind_shutdown(void); /* after the emulation thread has stopped */
#ifdef __cplusplus
}
#endif
#endif
