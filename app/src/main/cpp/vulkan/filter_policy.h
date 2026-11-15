#pragma once
namespace seedless_vk {
// IDs 0..6 are unchanged; 7/8 Sharp Bilinear, 9/10 Sharp Shimmerless,
// 11 Pixel AA Fast (manual gamma-correct taps), 12/13 LCD3x, 14/15 Quilez,
// 16 RP Duo LCD Sharp (manual exact box-coverage taps + LCD grid).
// 17 RP Duo DS Grid (hardware-linear area reconstruction + integrated soft DS grain).
// 18 RP Duo DS Grid Soft+ (stronger grain aperture with the same reconstruction).
inline bool linear_filter(int mode) {
    return mode == 1 || mode == 2 || mode == 7 || mode == 8 ||
        mode == 9 || mode == 10 || mode == 14 || mode == 15 || mode == 17 || mode == 18;
}
}
