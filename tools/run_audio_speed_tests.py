"""Check production pitch-preserving audio on a host or an attached Android device.

Windows uses the Android NDK plus --adb and optionally --serial. Other hosts
can compile locally with --cc. All test PCM is synthesized, with no game data.
"""

import argparse
import os
from pathlib import Path
import subprocess
import tempfile


HARNESS = r'''
#include "audio_speed.h"
#include <algorithm>
#include <cassert>
#include <cmath>
#include <cstdio>
#include <vector>

static constexpr double pi = 3.14159265358979323846;
static constexpr unsigned sample_rate = 44100;

static std::vector<int16_t> tones(unsigned frames) {
    std::vector<int16_t> samples(frames * 2);
    for (unsigned i = 0; i < frames; ++i) {
        samples[i * 2] = (int16_t)std::lround(12000 * std::sin(2 * pi * 440 * i / sample_rate));
        samples[i * 2 + 1] = (int16_t)std::lround(9000 * std::sin(2 * pi * 660 * i / sample_rate));
    }
    return samples;
}

static void drain(audio_speed_t *state, std::vector<int16_t> &output) {
    int16_t buffer[735 * 2 + 2];
    buffer[1470] = 12345;
    buffer[1471] = -23456;
    unsigned frames;
    while ((frames = audio_speed_read(state, buffer, 735)) != 0) {
        assert(frames <= 735 && buffer[1470] == 12345 && buffer[1471] == -23456);
        output.insert(output.end(), buffer, buffer + frames * 2);
    }
}

static void write(audio_speed_t *state, const std::vector<int16_t> &input,
                  float rate, unsigned chunk, std::vector<int16_t> &output) {
    for (unsigned position = 0; position < input.size() / 2;) {
        unsigned frames = std::min(chunk, (unsigned)input.size() / 2 - position);
        assert(audio_speed_write(state, input.data() + position * 2, frames, rate));
        drain(state, output);
        position += frames;
    }
}

static double frequency(const std::vector<int16_t> &samples, unsigned channel) {
    unsigned first = 5000;
    unsigned last = (unsigned)samples.size() / 2 - 1000;
    unsigned crossings = 0;
    for (unsigned i = first + 1; i < last; ++i)
        if (samples[(i - 1) * 2 + channel] <= 0 && samples[i * 2 + channel] > 0) ++crossings;
    return crossings * (double)sample_rate / (last - first - 1);
}

static void check_pitch(const std::vector<int16_t> &samples) {
    assert(samples.size() > 20000);
    assert(std::abs(frequency(samples, 0) - 440) < 8);
    assert(std::abs(frequency(samples, 1) - 660) < 10);
    double power = 0;
    int maximum_jump = 0;
    for (unsigned i = 5001; i < samples.size() / 2 - 1000; ++i) {
        double value = samples[i * 2];
        power += value * value;
        maximum_jump = std::max(maximum_jump,
            std::abs((int)samples[i * 2] - samples[(i - 1) * 2]));
    }
    double rms = std::sqrt(power / (samples.size() / 2 - 6001));
    assert(rms > 6000 && rms < 9000);
    assert(maximum_jump < 2000);
}

int main() {
    audio_speed_t *state = audio_speed_create();
    assert(state);
    for (float rate : {0.5f, 1.0f, 1.5f, 2.0f, 3.0f, 4.0f, 8.0f, 20.0f, 64.0f}) {
        audio_speed_reset(state);
        unsigned frames = (unsigned)(sample_rate * rate * 1.5);
        auto input = tones(frames);
        std::vector<int16_t> output;
        write(state, input, rate, 735, output);
        double expected = frames / (double)rate;
        double received = output.size() / 2;
        assert(std::abs(expected - received) < sample_rate * 0.12);
        check_pitch(output);
        std::printf("rate %.1fx: pitch %.2f/%.2f Hz, duration %.4f s\n", rate,
                    frequency(output, 0), frequency(output, 1), received / sample_rate);
    }

    auto input = tones(sample_rate * 3);
    std::vector<int16_t> large_chunks, small_chunks;
    audio_speed_reset(state);
    write(state, input, 2.0f, 735, large_chunks);
    audio_speed_reset(state);
    write(state, input, 2.0f, 113, small_chunks);
    assert(large_chunks == small_chunks);
    std::puts("arbitrary input blocks preserve identical stereo output");

    // Change tempo repeatedly on one live stream, without resetting its history.
    for (float rate : {3.0f, 0.5f, 4.0f, 1.0f, 20.0f, 2.0f}) {
        auto changing = tones((unsigned)(sample_rate * rate * 1.5));
        std::vector<int16_t> output;
        write(state, changing, rate, 734, output);
        check_pitch(output);
    }
    std::puts("live rate changes retain original pitch");

    // Clearing after pause/rewind cannot replay old notes into new silence.
    audio_speed_reset(state);
    std::vector<int16_t> silence(sample_rate * 4, 0), quiet;
    write(state, silence, 2.0f, 735, quiet);
    assert(!quiet.empty());
    assert(std::all_of(quiet.begin(), quiet.end(), [](int16_t value) { return value == 0; }));
    assert(!audio_speed_write(state, nullptr, 1, 2));
    assert(!audio_speed_write(state, input.data(), 1, NAN));
    assert(audio_speed_write(state, nullptr, 0, 2));
    assert(audio_speed_read(state, nullptr, 735) == 0);
    std::puts("reset clears stale sound; invalid inputs and output guards pass");
    audio_speed_destroy(state);
    std::puts("PASS production SoundTouch 2.3.3 / audio_speed.cpp regression");
}
'''


def run(command, timeout=120):
    result = subprocess.run([str(value) for value in command], capture_output=True,
                            text=True, encoding="utf-8", errors="replace", timeout=timeout)
    if result.returncode:
        raise RuntimeError(result.stdout + result.stderr)
    return result.stdout


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--ndk", type=Path)
    parser.add_argument("--adb", type=Path)
    parser.add_argument("--serial")
    parser.add_argument("--cc", default="c++")
    parser.add_argument("--temp-dir", type=Path)
    args = parser.parse_args()
    if os.name == "nt" and (not args.ndk or not args.adb):
        parser.error("Windows requires --ndk and --adb to test the Android production code")
    root = Path(__file__).resolve().parents[1]
    vendor = root / "core/third_party/soundtouch"
    production = root / "core/src/platform/android"
    with tempfile.TemporaryDirectory(prefix="audio-tempo-", dir=args.temp_dir) as temporary:
        directory = Path(temporary)
        harness = directory / "main.cpp"
        harness.write_text(HARNESS, encoding="utf-8")
        output = directory / "audio_speed_test"
        sources = sorted((vendor / "source").glob("*.cpp"))
        flags = ["-std=c++17", "-O2", "-DSOUNDTOUCH_FLOAT_SAMPLES=1",
                 "-DSOUNDTOUCH_DISABLE_X86_OPTIMIZATIONS=1",
                 "-I", vendor / "include", "-I", production]
        compiler = args.cc
        if args.adb:
            host = "windows-x86_64" if os.name == "nt" else "linux-x86_64"
            toolchain = args.ndk / "toolchains/llvm/prebuilt" / host
            compiler = toolchain / "bin" / ("clang++.exe" if os.name == "nt" else "clang++")
            flags += ["--target=aarch64-linux-android26", "--sysroot=" + str(toolchain / "sysroot"),
                      "-static-libstdc++", "-fPIE", "-pie"]
        run([compiler, *flags, *sources, production / "audio_speed.cpp", harness, "-o", output])
        if args.adb:
            adb = [args.adb] + (["-s", args.serial] if args.serial else [])
            remote = "/data/local/tmp/seedless-audio-speed-test"
            run([*adb, "push", output, remote])
            run([*adb, "shell", "chmod", "700", remote])
            print(run([*adb, "shell", remote]), end="")
        else:
            print(run([output]), end="")


if __name__ == "__main__":
    main()
