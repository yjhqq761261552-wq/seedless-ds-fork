#include "rewind_codec.h"
#include "state_format.h"
#include <stdlib.h>
#include <string.h>
#include <zlib.h>

int rewind_blob_pack(void *data, size_t size, void **out, size_t *packed_size)
{
#ifdef SEEDLESS_REWIND_TEST
    extern void rewind_codec_test_before_pack(void);
    rewind_codec_test_before_pack();
#endif
    *out = NULL; *packed_size = 0;
    if (!data || size <= sizeof(rewind_header_t)) return 0;
    rewind_header_t header;
    memcpy(&header, data, sizeof header);
    if (header.magic != REWIND_MAGIC || header.raw_size != size - sizeof header
        || header.raw_size > REWIND_MACHINE_CAPACITY + REWIND_BACKUP_LIMIT) return 0;
    uint32_t ids[STATE_SECTION_COUNT_MAX];
    uint32_t count = state_expected_sections(header.flags, ids);
    uint8_t *raw = (uint8_t *)data + sizeof header;
    uint8_t *cursor = raw, *end = (uint8_t *)data + size;
    for (uint32_t i = 0; i < count; ++i) {
        if ((size_t)(end - cursor) < sizeof(state_section_header_t)) return 0;
        state_section_header_t section;
        memcpy(&section, cursor, sizeof section);
        if (section.id != ids[i] || section.size > (size_t)(end - cursor - sizeof section)) return 0;
        section.crc = (uint32_t)crc32(0, cursor + sizeof section, section.size);
        memcpy(cursor, &section, sizeof section);
        cursor += sizeof section + section.size;
    }
    if (cursor != end) return 0;
    uLongf bound = compressBound(header.raw_size);
    uint8_t *packed = malloc(sizeof header + bound);
    if (!packed) return 0;
    if (compress2(packed + sizeof header, &bound, raw, header.raw_size, Z_BEST_SPEED) != Z_OK) {
        free(packed); return 0;
    }
    memcpy(packed, &header, sizeof header);
    size_t actual = sizeof header + bound;
    uint8_t *shrunk = realloc(packed, actual);
    if (!shrunk) { free(packed); return 0; }
    *out = shrunk; *packed_size = actual;
    return 1;
}
