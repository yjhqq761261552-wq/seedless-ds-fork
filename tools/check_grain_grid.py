"""Check both soft DS grain modes against independent pixel-area references.

Covers full-stretch and aspect-fit RP Duo panels, RG DS, fractional origins,
all internal resolutions, small PIP gain safety, atlas isolation and push ABI.
Host image math only. Physical display/GPU behavior requires device testing.
"""
import math
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SHADERS = ROOT / "app/src/main/cpp/vulkan"


def integrated_ridge(output_left, scale):
    center = (output_left + 0.5) / scale
    half_span = math.pi / scale
    return 0.5 + 0.5 * math.cos(2 * math.pi * center) * math.sin(half_span) / half_span


def axis_profile(length, cells, origin, depth):
    scale = length / cells
    first = math.ceil(origin - 0.5)
    last = math.ceil(origin + length - 0.5)
    return [1 - depth * integrated_ridge(absolute - origin, scale)
            for absolute in range(first, last)]


def smoothstep(lo, hi, value):
    t = max(0, min((value - lo) / (hi - lo), 1))
    return t * t * (3 - 2 * t)


def linear_sample(values, coordinate):
    p = coordinate - 0.5
    base = math.floor(p)
    fraction = p - base
    a = values[max(0, min(base, len(values) - 1))]
    b = values[max(0, min(base + 1, len(values) - 1))]
    return a * (1 - fraction) + b * fraction


def area_tap(values, center, footprint):
    lo = center - footprint * 0.5
    edge = math.floor(lo + footprint)
    coordinate = edge - 0.5 + max(0, min((lo + footprint - edge) / footprint, 1))
    return linear_sample(values, coordinate)


def subdivided_area(values, center, footprint):
    parts = math.ceil(footprint)
    assert parts <= 2
    sub = footprint / parts
    return sum(area_tap(values, center - footprint / 2 + (i + 0.5) * sub, sub)
               for i in range(parts)) / parts


def reference_area(values, center, footprint):
    lo = center - footprint / 2
    hi = center + footprint / 2
    result = 0.0
    for i in range(math.floor(lo), math.ceil(hi)):
        covered = max(0, min(hi, i + 1) - max(lo, i))
        result += covered * values[max(0, min(i, len(values) - 1))]
    return result / footprint


def main():
    frag = (SHADERS / "presenter.frag").read_text(encoding="utf-8")
    vert = (SHADERS / "presenter.vert").read_text(encoding="utf-8")
    body = frag.split("vec3 rpDuoDsGridScreen(", 1)[1].split("vec3 lcd3Screen", 1)[0]
    assert "fwidth" not in body
    assert "filter_grid_line" not in frag
    assert "floor(outputPixel) - draw.outputRect.xy" in body
    assert "sin(halfSpan) / halfSpan" in body
    assert "grainDepth * ridge" in body
    assert "rpDuoDsGridScreen(vec2(0.22, 0.18))" in frag
    assert "rpDuoDsGridScreen(vec2(0.30, 0.24))" in frag
    for stage in (vert, frag):
        assert "vec4 outputRect;" in stage and "layout(location=1)" in stage
    presenter = (SHADERS / "vulkan_presenter.cpp").read_text(encoding="utf-8")
    assert "std::copy(r,r+4,p.outputRect);" in presenter
    assert "sizeof(Push)==80" in presenter and "offsetof(Push,outputRect)==64" in presenter

    integrals = 0
    for scale in [2.25, 2.5, 4.296875, 5, 5.625, 7.5]:
        for p in [-1.25, 0, 0.25, 6.5, 137.125, 827.875]:
            # Independent numerical integration of the native periodic aperture.
            numeric = sum(0.5 + 0.5 * math.cos(2 * math.pi * (p + (i + 0.5) / 2048) / scale)
                          for i in range(2048)) / 2048
            exact = integrated_ridge(p, scale)
            assert abs(numeric - exact) < 3e-8
            assert 0 <= exact <= 1
            integrals += 1
    print(f"Soft aperture pixel-area integration, {integrals} comparisons passed")

    panels = [(1920, 1080), (1440, 1080), (1280, 960), (640, 480), (1100, 825)]
    for width, height in panels:
        for dx, dy in [(0.22, 0.18), (0.30, 0.24)]:
            for ox, oy in [(0, 0), (0.25, 0.75), (-19.25, 7.5)]:
                mx = axis_profile(width, 256, ox, dx)
                my = axis_profile(height, 192, oy, dy)
                mean_x, mean_y = 1 - dx / 2, 1 - dy / 2
                bright = 1 + 0.5 * (1 / (mean_x * mean_y) - 1)
                if ox == 0 and oy == 0:
                    assert abs(sum(mx) / len(mx) - mean_x) < 1e-12
                    assert abs(sum(my) / len(my) - mean_y) < 1e-12
                assert min(mx) >= 1 - dx and max(mx) <= 1
                assert min(my) >= 1 - dy and max(my) <= 1
                if min(width / 256, height / 192) >= 5:
                    # Both profiles have shallow, gradual adjacent-pixel changes.
                    assert max(abs(a-b) for a, b in zip(mx, mx[1:])) < 0.17
                    assert max(abs(a-b) for a, b in zip(my, my[1:])) < 0.14
                for gray in [0, 0.05, 0.5, 1]:
                    for x in mx[::max(1, len(mx)//32)]:
                        for y in my[::max(1, len(my)//32)]:
                            value = math.sqrt(min(gray * gray * x * y * bright, 1))
                            assert math.isfinite(value) and 0 <= value <= 1
            soft = axis_profile(width, 256, 0, 0.22)
            plus = axis_profile(width, 256, 0, 0.30)
            assert max(plus)-min(plus) > max(soft)-min(soft)
        print(f"Both grain strengths, {width}x{height}, integral/fractional origins passed")

    patterns = [[0, 1] * 32, [i / 63 for i in range(64)], [0.37] * 64]
    comparisons = 0
    for width, height in panels[:4]:
        for internal in range(1, 9):
            for size, output in [(256 * internal, width), (192 * internal, height)]:
                foot = size / output
                if foot > 2:
                    continue
                for center in [-0.25, 0.01, 0.5, 0.99, 1, 15.333, 31.875, 63.99, 64.1]:
                    for values in patterns:
                        actual = subdivided_area(values, center, foot)
                        expected = reference_area(values, center, foot)
                        assert abs(actual - expected) < 1e-10
                        comparisons += 1
    print(f"Area reconstruction including hires downscales, {comparisons} comparisons passed")
    for sx, sy in [(0.01, 0.01), (0.5, 4), (1, 1), (1.5, 1.5), (2, 2), (2.25, 3), (5, 5)]:
        amount = smoothstep(2, 2.5, min(sx, sy))
        for dx, dy in [(0.22, 0.18), (0.30, 0.24)]:
            bright = 1 + 0.5 * (1 / ((1-dx/2)*(1-dy/2)) - 1)
            gain = 1 + (((1-dx)*(1-dy)*bright)-1)*amount
            assert math.isfinite(gain) and gain > 0
            if min(sx, sy) <= 2:
                assert gain == 1
    print("Small PIP, brightness safety, both modes and push-constant ABI passed")


if __name__ == "__main__":
    main()
