#pragma once
#include <memory>
#include <mutex>
#include <vector>
#include "seedlessds/video_snapshot.h"

namespace seedless_vk {
struct FramePacket {
    std::vector<unsigned char> rgba;
    nds_video_snapshot_t info{};
};
/* Only dual-display rendering pays for a CPU staging pool. Pack the live core
 * once; each display holds an immutable packet only while copying its visible
 * screen into its own fenced GPU staging buffer. A slow surface never owns the
 * core page and never prevents primary publication. There is no frame backlog. */
class SharedFrames {
    std::mutex mutex;
    std::shared_ptr<FramePacket> slots[2], published;
    unsigned externalCount = 0;
public:
    void attach() { std::lock_guard<std::mutex> guard(mutex); ++externalCount; }
    void detach() {
        std::lock_guard<std::mutex> guard(mutex);
        if (externalCount && --externalCount == 0) { published.reset(); slots[0].reset(); slots[1].reset(); }
    }
    bool active() { std::lock_guard<std::mutex> guard(mutex); return externalCount != 0; }
    uint64_t serial() {
        std::lock_guard<std::mutex> guard(mutex);
        return published ? published->info.serial : 0;
    }
    std::shared_ptr<const FramePacket> latest(bool external, size_t capacity) {
        std::lock_guard<std::mutex> guard(mutex);
        if (external || (published && published->info.serial == nds_video_latest_serial())) return published;
        for (auto &slot : slots) {
            // The publisher itself accounts for one reference when it is the
            // chosen slot. Any other reference belongs to a display reader.
            if (slot && slot.use_count() > (slot == published ? 2 : 1)) continue;
            if (!slot) slot = std::make_shared<FramePacket>();
            if (slot->rgba.size() < capacity) slot->rgba.resize(capacity);
            nds_video_snapshot_t snapshot{};
            if (nds_video_copy_latest(slot->rgba.data(), slot->rgba.size(), 0, &snapshot)) {
                slot->info = snapshot;
                published = slot;
                nds_video_wake();
            }
            return published;
        }
        return published; // Both readers are briefly copying: reuse, never wait for a GPU.
    }
};
}
