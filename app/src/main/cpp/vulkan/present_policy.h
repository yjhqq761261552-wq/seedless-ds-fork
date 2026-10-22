#pragma once
#include <vulkan/vulkan.h>
#include <stdint.h>

namespace seedless_vk {
struct PresentModes {
    VkPresentModeKHR modes[4];
    unsigned count;
    const VkPresentModeKHR *begin() const { return modes; }
    const VkPresentModeKHR *end() const { return modes + count; }
};
inline PresentModes preferred_modes(bool lowLatency) {
    if (!lowLatency) return {{VK_PRESENT_MODE_FIFO_KHR}, 1};
    return {{VK_PRESENT_MODE_MAILBOX_KHR, VK_PRESENT_MODE_IMMEDIATE_KHR,
        VK_PRESENT_MODE_FIFO_RELAXED_KHR, VK_PRESENT_MODE_FIFO_KHR}, 4};
}
inline VkPresentModeKHR choose_mode(const VkPresentModeKHR *modes, uint32_t count, bool lowLatency = true) {
    for (auto p : preferred_modes(lowLatency)) for (uint32_t i=0; i<count; ++i) if (modes[i]==p) return p;
    return VK_PRESENT_MODE_FIFO_KHR;
}
inline uint32_t image_count(uint32_t min, uint32_t max, VkPresentModeKHR mode) {
    uint32_t n = min > 2 ? min : 2;
    if (mode == VK_PRESENT_MODE_MAILBOX_KHR && n < 3) n = 3;
    if (max && n > max) n = max;
    return n;
}
inline int rotation(VkSurfaceTransformFlagBitsKHR transform) {
    switch (transform) {
    case VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR: return 0;
    case VK_SURFACE_TRANSFORM_ROTATE_90_BIT_KHR: return 1;
    case VK_SURFACE_TRANSFORM_ROTATE_180_BIT_KHR: return 2;
    case VK_SURFACE_TRANSFORM_ROTATE_270_BIT_KHR: return 3;
    default: return -1;
    }
}
inline void rotate_point(float &x, float &y, int rotation) {
    float old = x;
    if (rotation == 1) { x=-y; y=old; }
    else if (rotation == 2) { x=-x; y=-y; }
    else if (rotation == 3) { x=y; y=-old; }
}
}
