// RP Duo adaptation of the appearance of the user's Nova GBA preset.
// Circle/pixel intersections use independent Gauss-Legendre area quadrature.
// Public-domain color profiles by hunterk and Pokefan531 are credited in
// assets/licenses/07-Nova-phosphor.txt.

vec3 novaLinearColor(vec3 rgb) {
    // Preset defaults: black level 2 IRE, exposure 1, temperature 7500 K.
    rgb = clamp((rgb - 0.02) / 0.98, 0.0, 1.0);
    const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);
    vec3 tinted = rgb * vec3(0.9209733279, 0.9322032379, 1.0);
    tinted *= dot(rgb, LUMA) / max(dot(tinted, LUMA), 0.00001);
    return pow(clamp(tinted, 0.0, 1.0), vec3(2.2));
}

vec3 novaDotCoverage(vec2 center, vec2 footprint, vec3 radius) {
    // Integrate each channel's disk over the actual destination pixel area.
    // Radii and coordinates are expressed in native DS cells, independently
    // of internal 3D resolution and the other display's output dimensions.
    vec3 left = max(vec3(center.x - 0.5 * footprint.x), -radius);
    vec3 right = min(vec3(center.x + 0.5 * footprint.x), radius);
    vec3 halfSpan = max(0.5 * (right - left), vec3(0.0));
    vec3 midpoint = 0.5 * (left + right);
    const float NODES[4] = float[4](-0.8611363116, -0.3399810436,
                                   0.3399810436,  0.8611363116);
    const float WEIGHTS[4] = float[4](0.3478548451, 0.6521451549,
                                     0.6521451549, 0.3478548451);
    vec3 area = vec3(0.0);
    for (int i = 0; i < 4; ++i) {
        vec3 x = midpoint + halfSpan * NODES[i];
        vec3 extent = sqrt(max(radius * radius - x * x, vec3(0.0)));
        vec3 bottom = max(vec3(center.y - 0.5 * footprint.y), -extent);
        vec3 top = min(vec3(center.y + 0.5 * footprint.y), extent);
        area += WEIGHTS[i] * max(top - bottom, vec3(0.0));
    }
    return clamp(area * halfSpan / (footprint.x * footprint.y), 0.0, 1.0);
}

vec3 novaPresetColor(vec3 linear) {
    // Phosphor output transfer, followed by the backup's DS Phat profile.
    vec3 rgb = mix(linear * 12.9232102,
                   1.055 * pow(max(linear, vec3(0.0)), vec3(0.4166)) - 0.055,
                   greaterThanEqual(linear, vec3(0.00313)));
    const mat3 PROFILE = mat3(0.815, 0.10, 0.1075,
                              0.275, 0.64, 0.1725,
                             -0.09,  0.26, 0.72);
    rgb = sqrt(clamp(PROFILE * clamp(rgb * rgb * 0.915, 0.0, 1.0), 0.0, 1.0));
    // Exact algebraic reduction of HSV saturation 1.3 and luminance 1.3.
    float value = max(max(rgb.r, rgb.g), rgb.b);
    return clamp(1.3 * (value + 1.3 * (rgb - value)), 0.0, 1.0);
}

vec3 rpDuoNovaScreen() {
    vec2 outputSize = max(draw.outputRect.zw, vec2(0.000001));
    vec2 outputCenter = floor(outputPixel) - draw.outputRect.xy + 0.5;
    vec2 size = sourceSize();
    vec2 sourceFootprint = size / outputSize;
    vec3 linear = novaLinearColor(dsGridAreaColor(outputCenter * sourceFootprint,
                                                 sourceFootprint, size));
    vec2 nativeFootprint = vec2(256.0, 192.0) / outputSize;
    vec2 nativeCenter = outputCenter * nativeFootprint;
    vec3 signal = sqrt(linear);
    vec3 envelope = 0.3 + 0.7 * signal;
    // Saved dot scales are both 0.4, hence radius 0.2 native cells at white.
    vec3 coverage = novaDotCoverage(fract(nativeCenter) - 0.5,
                                    nativeFootprint, 0.2 * envelope);
    vec3 dotLight = coverage * (signal / envelope) * (signal / envelope);
    // Fade the dots as a small PIP approaches its sampling limit.
    float amount = smoothstep(2.0, 3.0, min(1.0 / nativeFootprint.x,
                                          1.0 / nativeFootprint.y));
    return novaPresetColor(mix(linear, dotLight, 0.4 * amount));
}
