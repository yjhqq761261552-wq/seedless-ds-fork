#pragma once
namespace seedless_vk {
// IDs 0..6 are unchanged; 7/8 Sharp Bilinear, 9/10 Sharp Shimmerless,
// 11 Pixel AA Fast (manual gamma-correct taps), 12/13 LCD3x, 14/15 Quilez.
inline bool linear_filter(int mode) {
    return mode == 1 || mode == 2 || mode == 7 || mode == 8 ||
        mode == 9 || mode == 10 || mode == 14 || mode == 15;
}
}
