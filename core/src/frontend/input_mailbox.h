#ifndef SEEDLESSDS_INPUT_MAILBOX_H
#define SEEDLESSDS_INPUT_MAILBOX_H

#include "seedlessds/nds.h"

/* Independent of frontend_t so the reconstructed layout stays unchanged.
 * Latest movement/held state plus one consumable press edge per key/pen.
 * reset is cancellation, not an ordinary button release. */
void input_mailbox_publish(const nds_input_t *input);
nds_input_t input_mailbox_read(void);
void input_mailbox_reset(void);
void input_mailbox_cancel_pen(void);

#endif
