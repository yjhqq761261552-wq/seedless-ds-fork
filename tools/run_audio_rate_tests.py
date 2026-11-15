"""Compile and test the production fast-forward audio-rate estimator.

On Windows, pass --ndk with an installed Android NDK. Other hosts may select a
C compiler with --cc. --temp-dir selects the temporary build directory.
"""

import argparse
import ctypes
import json
import math
import os
from pathlib import Path
import subprocess
import tempfile


class RateState(ctypes.Structure):
    _fields_ = [
        ("window_start_us", ctypes.c_uint64),
        ("last_update_us", ctypes.c_uint64),
        ("window_frames", ctypes.c_uint64),
        ("pace_step", ctypes.c_uint),
        ("active", ctypes.c_uint),
        ("measured_rate", ctypes.c_float),
        ("buffer_error", ctypes.c_float),
        ("have_measurement", ctypes.c_uint),
    ]


def run(command):
    result = subprocess.run([str(part) for part in command], capture_output=True, text=True, timeout=60)
    if result.returncode:
        raise RuntimeError(result.stdout + result.stderr)


def make_library(root, temporary, ndk, compiler):
    source = root / "core/src/platform/android/audio_rate.c"
    if os.name != "nt":
        library = temporary / "audio_rate.so"
        run([compiler, "-std=c11", "-shared", "-fPIC", "-O2", source, "-o", library])
        return library
    if not ndk:
        raise SystemExit("--ndk is required on Windows")
    bin_dir = Path(ndk) / "toolchains/llvm/prebuilt/windows-x86_64/bin"
    support = temporary / "support.c"
    support.write_text(
        """#include <stddef.h>
int _fltused = 0;
void *memset(void *dest, int value, size_t count) {
    unsigned char *p = dest;
    for (size_t i = 0; i < count; ++i) p[i] = (unsigned char)value;
    return dest;
}
""", encoding="utf-8")
    objects = []
    for name, src in [("rate", source), ("support", support)]:
        obj = temporary / (name + ".obj")
        run([bin_dir / "clang.exe", "--target=x86_64-pc-windows-msvc", "-std=c11",
             "-ffreestanding", "-fno-builtin", "-fno-stack-protector", "-O2", "-c", src, "-o", obj])
        objects.append(obj)
    library = temporary / "audio_rate.dll"
    run([bin_dir / "ld.lld.exe", "-flavor", "link", "/dll", "/noentry", "/nodefaultlib",
         "/out:" + str(library), "/export:audio_rate_init", "/export:audio_rate_update", *objects])
    return library


def configure_library(path):
    library = ctypes.CDLL(str(path))
    library.audio_rate_init.argtypes = [ctypes.POINTER(RateState)]
    library.audio_rate_update.argtypes = [ctypes.POINTER(RateState), ctypes.c_uint,
                                          ctypes.c_uint64, ctypes.c_int,
                                          ctypes.c_uint, ctypes.c_float]
    library.audio_rate_update.restype = ctypes.c_float
    return library


def update(library, state, frames, timestamp, fast, step, fraction):
    rate = library.audio_rate_update(ctypes.byref(state), frames, timestamp, fast, step, fraction)
    assert math.isfinite(rate) and .5 <= rate <= 64, rate
    return rate


def simulate(library, target_step, actual_rate, seconds=3, buffer=-1):
    state = RateState()
    library.audio_rate_init(ctypes.byref(state))
    frames = 735
    interval = round(frames * 1_000_000 / (44100 * actual_rate))
    values = []
    for timestamp in range(0, seconds * 1_000_000, interval):
        values.append(update(library, state, frames, timestamp, 1, target_step, buffer))
    return state, values


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--ndk")
    parser.add_argument("--cc", default="cc")
    parser.add_argument("--temp-dir")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    if args.temp_dir:
        Path(args.temp_dir).mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory(prefix="audio-rate-", dir=args.temp_dir) as folder:
        library = configure_library(make_library(root, Path(folder), args.ndk, args.cc))
        results = {}
        for target_step, actual, expected in [(25000, 2, 2), (12500, 4, 4),
                                               (12500, 2, 2), (1, 6, 6),
                                               (100000, .5, .5)]:
            state, values = simulate(library, target_step, actual)
            assert abs(values[-1] - expected) < .03, (target_step, actual, values[-1])
            if target_step != 1:
                assert max(values) <= max(.5, min(64, 50000 / target_step)) + .00001
            results[f"step={target_step}, actual={actual}x"] = round(values[-1], 5)

        # The first window uses the finite target, and unlimited speed starts at 2x.
        state = RateState()
        library.audio_rate_init(ctypes.byref(state))
        assert update(library, state, 735, 0, 1, 12500, -1) == 4
        assert update(library, state, 735, 1000, 1, 1, -1) == 2
        assert state.window_frames == 0
        assert update(library, state, 735, 2000, 1, 25000, -1) == 2
        assert state.window_frames == 0

        # First observation uses about 20 ms; later updates use 100 ms and smoothing.
        library.audio_rate_init(ctypes.byref(state))
        assert update(library, state, 735, 0, 1, 1, -1) == 2
        for index in range(1, 25):
            assert update(library, state, 735, index * 833, 1, 1, -1) == 2
        first = update(library, state, 735, 25 * 833, 1, 1, -1)
        assert 19.9 < first < 20.1 and state.have_measurement == 1, first
        transition_start = 25 * 833
        for index in range(1, 60):
            after = update(library, state, 735, transition_start + index * 1667, 1, 1, -1)
            assert after == first
        after = update(library, state, 735, transition_start + 60 * 1667, 1, 1, -1)
        assert abs(after - (first + 10) / 2) < .02, after

        library.audio_rate_init(ctypes.byref(state))
        assert update(library, state, 735, 0, 1, 12500, -1) == 4
        assert update(library, state, 735, 8333, 1, 12500, -1) == 4
        assert update(library, state, 735, 16666, 1, 12500, -1) == 4
        first = update(library, state, 735, 24999, 1, 12500, -1)
        assert abs(first - 2) < .01 and state.have_measurement == 1, first

        # Half speed emits one DS frame every 33 ms, so the first completed
        # window contains exactly one interval and yields the correct rate.
        library.audio_rate_init(ctypes.byref(state))
        assert update(library, state, 735, 0, 1, 100000, -1) == .5
        assert update(library, state, 735, 33333, 1, 100000, -1) == .5
        assert state.have_measurement == 1

        # An empty block resets the window rather than treating silence as a
        # zero-speed observation or folding a long gap into the first window.
        library.audio_rate_init(ctypes.byref(state))
        assert update(library, state, 0, 0, 1, 12500, -1) == 4
        assert update(library, state, 0, 50000, 1, 12500, -1) == 4
        assert not state.have_measurement and not state.window_frames
        for index in range(1, 4):
            value = update(library, state, 735, 50000 + index * 8333, 1, 12500, -1)
        assert abs(value - 2) < .01 and state.have_measurement == 1, value

        # A malformed exceptionally large frame count cannot overflow the sum.
        library.audio_rate_init(ctypes.byref(state))
        update(library, state, 735, 0, 1, 12500, -1)
        state.window_frames = 2**64 - 2
        value = update(library, state, 2**32 - 1, 20000, 1, 12500, -1)
        assert value == 4 and state.have_measurement == 1

        # Pauses, time rollback and returning from normal speed start new windows.
        library.audio_rate_init(ctypes.byref(state))
        update(library, state, 735, 2000, 1, 25000, -1)
        update(library, state, 735, 11000, 1, 25000, -1)
        assert state.window_frames == 735
        assert update(library, state, 735, 400000, 1, 25000, -1) == 2
        assert state.window_start_us == 400000 and state.window_frames == 0
        update(library, state, 735, 1000, 1, 25000, -1)
        assert state.window_start_us == 1000 and state.window_frames == 0
        assert update(library, state, 735, 2000, 0, 12500, 1) == 1
        assert update(library, state, 735, 3000, 1, 12500, -1) == 4
        assert state.window_start_us == 3000 and state.window_frames == 0

        _, low = simulate(library, 12500, 2, buffer=0)
        _, neutral = simulate(library, 12500, 2, buffer=.5)
        _, high = simulate(library, 12500, 2, buffer=1)
        assert abs(low[-1] - 1.84) < .04, low[-1]
        assert abs(neutral[-1] - 2.0) < .04, neutral[-1]
        assert abs(high[-1] - 2.16) < .04, high[-1]
        assert low[-1] < neutral[-1] < high[-1]
        _, capped = simulate(library, 25000, 2, buffer=1)
        assert max(capped) <= 2.0
        results["buffer low/neutral/high"] = [round(low[-1], 4), round(neutral[-1], 4), round(high[-1], 4)]

        # Unknown and non-finite fractions cannot leak NaNs into the converter.
        for fraction in [-1, float("nan"), float("-inf"), float("inf"), 1.5]:
            value = update(library, state, 735, 12000, 1, 12500, fraction)
            assert math.isfinite(value) and .5 <= value <= 4
        assert update(library, state, 735, 2**64 - 1, 1, 1, float("nan")) == 2
        assert update(library, state, 735, 0, 1, 1, -1) == 2
        assert update(library, state, 735, 0, 0, 0, float("nan")) == 1

        print(json.dumps({"test": "production audio_rate.c", "settled_rates": results,
                          "tests": ["nominal and underperforming finite speeds", "unlimited speed",
                                    "half speed", "20ms first measurement", "empty and large frame counts",
                                    "100ms later smoothing", "speed changes", "pause and clock rollback",
                                    "smoothed buffer feedback", "finite limits", "NaN guards"]}, indent=2))
        if os.name == "nt":
            kernel = ctypes.WinDLL("kernel32")
            kernel.FreeLibrary.argtypes = [ctypes.c_void_p]
            assert kernel.FreeLibrary(library._handle)


if __name__ == "__main__":
    main()
