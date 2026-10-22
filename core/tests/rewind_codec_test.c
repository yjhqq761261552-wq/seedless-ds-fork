#include "core/rewind_codec.h"
#include "core/state_format.h"
#include <stdlib.h>
#include <string.h>
#include <zlib.h>
#define CHECK(x) do { if (!(x)) { failure = __LINE__; goto cleanup; } } while (0)
int rewind_codec_test(void)
{
    int failure = 0;
    void *packed = NULL;
    size_t packed_size = 0;
    unsigned char blob[1024], decoded[1024];
    for (unsigned flags = 0; flags <= STATE_FLAG_SPI_MEMORY; flags += STATE_FLAG_SPI_MEMORY) {
        uint32_t ids[STATE_SECTION_COUNT_MAX];
        unsigned count = state_expected_sections(flags, ids);
        rewind_header_t header = { REWIND_MAGIC, count * 32u, 0x12345678, flags };
        memcpy(blob, &header, sizeof header);
        unsigned char *cursor = blob + sizeof header;
        for (unsigned i = 0; i < count; ++i) {
            state_section_header_t section = { ids[i], 16, 0, 0 };
            memcpy(cursor, &section, sizeof section);
            memset(cursor + sizeof section, (int)i + 1, 16);
            cursor += 32;
        }
        size_t size = (size_t)(cursor - blob);
        CHECK(rewind_blob_pack(blob, size, &packed, &packed_size));
        CHECK(memcmp(packed, blob, sizeof header) == 0);
        uLongf decoded_size = sizeof decoded;
        CHECK(uncompress(decoded, &decoded_size, (unsigned char *)packed + sizeof header, packed_size - sizeof header) == Z_OK);
        CHECK(decoded_size == header.raw_size && memcmp(decoded, blob + sizeof header, decoded_size) == 0);
        for (unsigned i = 0; i < count; ++i) {
            state_section_header_t section;
            memcpy(&section, decoded + 32 * i, sizeof section);
            CHECK(section.id == ids[i] && section.crc == (uint32_t)crc32(0, decoded + 32 * i + sizeof section, 16));
        }
        free(packed); packed = NULL;
        CHECK(!rewind_blob_pack(blob, size - 1, &packed, &packed_size));
        ((rewind_header_t *)blob)->magic ^= 1;
        CHECK(!rewind_blob_pack(blob, size, &packed, &packed_size));
        ((rewind_header_t *)blob)->magic ^= 1;
        ((state_section_header_t *)(blob + sizeof header))->size = 0xffffffffu;
        CHECK(!rewind_blob_pack(blob, size, &packed, &packed_size));
    }
    CHECK(!rewind_blob_pack(NULL, 0, &packed, &packed_size));
cleanup:
    free(packed);
    return failure;
}
