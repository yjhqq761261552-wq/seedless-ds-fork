// Scalar coordinate maps shared by the fragment shader and host conformance tests.
// Adapted from public-domain / CC0 algorithms by Themaister, zadpos, fishku,
// and hunterk's public-domain Quilez port. See assets/licenses/04-Vulkan-curated.txt.
float filter_sharp_bilinear(float pixel, float outputPerInput) {
    float scale = max(floor(outputPerInput + 0.01), 1.0);
    float phase = pixel - floor(pixel);
    float range = 0.5 - 0.5 / scale;
    float centered = phase - 0.5;
    return floor(pixel) + (centered - clamp(centered, -range, range)) * scale + 0.5;
}
float filter_shimmerless(float pixel, float footprint) {
    // Exact box coverage for upscaling, including fractional destination origins.
    // Caller uses a bounded approximation instead when footprint exceeds one.
    float right = pixel + 0.5 * footprint;
    float edge = floor(right);
    return edge - 0.5 + clamp((right - edge) / max(footprint, 0.000001), 0.0, 1.0);
}
float filter_pixel_aa(float pixel, float outputPerInput) {
    float period = floor(pixel - 0.5);
    float phase = pixel - 0.5 - period;
    float t = clamp((phase - 0.5) * outputPerInput + 0.5, 0.0, 1.0);
    return period + 0.5 + t * t * (3.0 - 2.0 * t);
}
float filter_quilez(float pixel) {
    float p = pixel + 0.5;
    float base = floor(p);
    float f = p - base;
    f = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    return base + f - 0.5;
}
