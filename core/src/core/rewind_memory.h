#ifndef SEEDLESSDS_REWIND_MEMORY_H
#define SEEDLESSDS_REWIND_MEMORY_H
#include <stddef.h>
#include <stdint.h>
int state_rewind_capture_raw(void *machine, void **buffer, size_t *capacity, size_t *used);
int state_rewind_restore(void *machine, const void *data, size_t size);
#endif
