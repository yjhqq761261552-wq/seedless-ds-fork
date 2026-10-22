#ifndef SEEDLESSDS_REWIND_CODEC_H
#define SEEDLESSDS_REWIND_CODEC_H
#include <stddef.h>
#include <stdint.h>
typedef struct rewind_header {
    uint32_t magic, raw_size, game_code, flags;
} rewind_header_t;
#define REWIND_MAGIC 0x31575253u
#define REWIND_BACKUP_LIMIT (8u * 1024u * 1024u)
#define REWIND_MACHINE_CAPACITY 0x680000u
/* Mutates only an isolated raw snapshot: fills CRCs, then compresses it. */
int rewind_blob_pack(void *raw, size_t size, void **out, size_t *packed_size);
#endif
