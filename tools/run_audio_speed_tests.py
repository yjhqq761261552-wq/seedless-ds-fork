"""Exercise the production streaming speed converter with deterministic PCM.

On Windows, pass --ndk with the installed Android NDK directory. Other hosts
may provide a C compiler with --cc. --temp-dir selects the temporary build
directory. This host test does not exercise an Android audio driver.
"""

import argparse
import ctypes
import json
import math
import os
from pathlib import Path
import random
import subprocess
import tempfile


class SpeedState(ctypes.Structure):
    _fields_ = [
        ("width", ctypes.c_uint64),
        ("target_width", ctypes.c_uint64),
        ("filled", ctypes.c_uint64),
        ("sum_left", ctypes.c_int64),
        ("sum_right", ctypes.c_int64),
        ("previous_input", ctypes.c_int16 * 2),
        ("previous_output", ctypes.c_int16 * 2),
        ("have_input", ctypes.c_uint),
        ("have_output", ctypes.c_uint),
    ]


def run(command):
    result = subprocess.run([str(part) for part in command], capture_output=True, text=True, timeout=60)
    if result.returncode:
        raise RuntimeError(result.stdout + result.stderr)


def make_library(root, temporary, ndk, compiler):
    source = root / "core/src/platform/android/audio_speed.c"
    if os.name != "nt":
        library = temporary / "audio_speed.so"
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
void *memcpy(void *dest, const void *source, size_t count) {
    unsigned char *out = dest;
    const unsigned char *in = source;
    for (size_t i = 0; i < count; ++i) out[i] = in[i];
    return dest;
}
""", encoding="utf-8")
    objects = []
    for name, src in [("speed", source), ("support", support)]:
        obj = temporary / (name + ".obj")
        run([bin_dir / "clang.exe", "--target=x86_64-pc-windows-msvc", "-std=c11",
             "-ffreestanding", "-fno-builtin", "-fno-stack-protector", "-O2", "-c", src, "-o", obj])
        objects.append(obj)
    library = temporary / "audio_speed.dll"
    names = ["audio_speed_init", "audio_speed_set_rate", "audio_speed_max_output_frames", "audio_speed_process"]
    run([bin_dir / "ld.lld.exe", "-flavor", "link", "/dll", "/noentry", "/nodefaultlib",
         "/out:" + str(library), *["/export:" + name for name in names], *objects])
    return library


def configure_library(path):
    library = ctypes.CDLL(str(path))
    pointer = ctypes.POINTER(ctypes.c_int16)
    library.audio_speed_init.argtypes = [ctypes.POINTER(SpeedState), ctypes.c_float]
    library.audio_speed_set_rate.argtypes = [ctypes.POINTER(SpeedState), ctypes.c_float]
    library.audio_speed_max_output_frames.argtypes = [ctypes.c_uint]
    library.audio_speed_max_output_frames.restype = ctypes.c_uint
    library.audio_speed_process.argtypes = [ctypes.POINTER(SpeedState), pointer, ctypes.c_uint,
                                             pointer, ctypes.c_uint, ctypes.POINTER(ctypes.c_uint)]
    library.audio_speed_process.restype = ctypes.c_int
    return library


def process(library, state, samples):
    count = len(samples) // 2
    assert len(samples) == count * 2
    capacity = library.audio_speed_max_output_frames(count)
    source = (ctypes.c_int16 * len(samples))(*samples)
    output = (ctypes.c_int16 * (capacity * 2 + 2))(*([23456] * (capacity * 2 + 2)))
    frames = ctypes.c_uint(0)
    assert library.audio_speed_process(ctypes.byref(state), source, count, output,
                                       capacity, ctypes.byref(frames)) == 1
    assert frames.value <= capacity
    assert output[capacity * 2] == output[capacity * 2 + 1] == 23456
    return list(output[:frames.value * 2])


def convert(library, samples, rate, chunks):
    state = SpeedState()
    library.audio_speed_init(ctypes.byref(state), rate)
    converted = []
    position = 0
    for size in chunks:
        converted.extend(process(library, state, samples[position * 2:(position + size) * 2]))
        position += size
    assert position * 2 == len(samples)
    return converted, state


def random_chunks(count, seed):
    randomizer = random.Random(seed)
    result = []
    while count:
        size = min(count, randomizer.randint(1, 901))
        result.append(size)
        count -= size
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--ndk")
    parser.add_argument("--cc", default="cc")
    parser.add_argument("--temp-dir")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    if args.temp_dir:
        Path(args.temp_dir).mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory(prefix="audio-speed-", dir=args.temp_dir) as folder:
        library = configure_library(make_library(root, Path(folder), args.ndk, args.cc))
        sample_count = 20000
        samples = []
        for frame in range(sample_count):
            samples.extend((round(26000 * math.sin(2 * math.pi * 400 * frame / 44100)),
                            round(19000 * math.cos(2 * math.pi * 733 * frame / 44100))))

        rates = [0.5, 1.0, 1.5, 2.0, 4.0, 20.0, 64.0]
        counts = {}
        for rate in rates:
            whole, _ = convert(library, samples, rate, [sample_count])
            split, _ = convert(library, samples, rate, random_chunks(sample_count, 390 + int(rate * 10)))
            assert whole == split, ("block-boundary mismatch", rate)
            assert abs(len(whole) // 2 - int(sample_count / rate)) <= 1, (rate, len(whole))
            if rate == 1.0:
                assert whole == samples, "1x must preserve all original samples"
            counts[str(rate)] = len(whole) // 2

        slow_input = [1000, -1000, 3000, -3000, 5000, -5000]
        slow, _ = convert(library, slow_input, .5, [1, 2])
        assert slow == [1000, -1000, 1000, -1000, 2000, -2000,
                        3000, -3000, 4000, -4000, 5000, -5000], slow

        alternating = [component for frame in range(8000)
                       for component in ((30000 if frame % 2 else -30000),
                                         (-27000 if frame % 2 else 27000))]
        for rate in [2.0, 4.0, 20.0]:
            filtered, _ = convert(library, alternating, rate, random_chunks(8000, int(rate)))
            assert max(map(abs, filtered), default=0) <= 1, ("Nyquist alias", rate)

        fast, _ = convert(library, samples, 2.0, random_chunks(sample_count, 84))
        maximum_jump = max(abs(fast[index] - fast[index - 2]) for index in range(2, len(fast), 2))
        assert maximum_jump < 3500, maximum_jump

        # Abrupt requested-rate changes must keep a continuous constant signal.
        state = SpeedState()
        library.audio_speed_init(ctypes.byref(state), 1.0)
        for rate in [1.0, 2.0, 20.0, 0.5, 4.0, 1.0]:
            library.audio_speed_set_rate(ctypes.byref(state), rate)
            chunk = process(library, state, [12000, -19000] * 6000)
            assert all(chunk[index:index + 2] == [12000, -19000]
                       for index in range(0, len(chunk), 2)), ("DC transition", rate)

        # The same changes at the same input boundaries are invariant to block cuts.
        def sequence(partition):
            state = SpeedState()
            library.audio_speed_init(ctypes.byref(state), 1.0)
            result = []
            position = 0
            for rate, length in [(1, 3000), (1.5, 4000), (4, 5000), (.5, 3000), (2, 5000)]:
                library.audio_speed_set_rate(ctypes.byref(state), rate)
                sizes = random_chunks(length, length) if partition else [length]
                for size in sizes:
                    result.extend(process(library, state, samples[position * 2:(position + size) * 2]))
                    position += size
            return result
        assert sequence(False) == sequence(True)

        state = SpeedState()
        library.audio_speed_init(ctypes.byref(state), 1.5)
        process(library, state, [1, 2] * 2)
        before = ctypes.string_at(ctypes.byref(state), ctypes.sizeof(state))
        input_buffer = (ctypes.c_int16 * 4)(1, 2, 3, 4)
        output_buffer = (ctypes.c_int16 * 12)(*([23456] * 12))
        frames = ctypes.c_uint(99)
        assert library.audio_speed_process(ctypes.byref(state), input_buffer, 2, output_buffer,
                                           4, ctypes.byref(frames)) == 0
        assert frames.value == 0 and list(output_buffer) == [23456] * 12
        assert ctypes.string_at(ctypes.byref(state), ctypes.sizeof(state)) == before
        assert library.audio_speed_max_output_frames(0) == 0
        assert library.audio_speed_max_output_frames(2**32 - 1) == 0
        assert library.audio_speed_process(ctypes.byref(state), None, 0, None, 0, ctypes.byref(frames)) == 1

        print(json.dumps({"test": "production audio_speed.c", "rates_and_output_frames": counts,
                          "maximum_400hz_2x_sample_jump": maximum_jump,
                          "tests": ["normal speed bit-exact", "independent stereo channels",
                                    "linear half-speed", "fractional phase across arbitrary blocks",
                                    "high-frequency attenuation", "continuous rate changes",
                                    "undersized output atomicity", "capacity guards"]}, indent=2))
        if os.name == "nt":
            kernel = ctypes.WinDLL("kernel32")
            kernel.FreeLibrary.argtypes = [ctypes.c_void_p]
            assert kernel.FreeLibrary(library._handle)


if __name__ == "__main__":
    main()
