add_library(seedless_soundtouch STATIC
    source/AAFilter.cpp
    source/FIFOSampleBuffer.cpp
    source/FIRFilter.cpp
    source/InterpolateCubic.cpp
    source/InterpolateLinear.cpp
    source/InterpolateShannon.cpp
    source/RateTransposer.cpp
    source/SoundTouch.cpp
    source/TDStretch.cpp
    source/cpu_detect_x86.cpp
    source/mmx_optimized.cpp
    source/sse_optimized.cpp
)
target_include_directories(seedless_soundtouch PUBLIC include)
target_compile_definitions(seedless_soundtouch PUBLIC
    SOUNDTOUCH_FLOAT_SAMPLES=1 SOUNDTOUCH_DISABLE_X86_OPTIMIZATIONS=1)
target_compile_features(seedless_soundtouch PRIVATE cxx_std_17)
target_compile_options(seedless_soundtouch PRIVATE -O2 -fPIC)
set_target_properties(seedless_soundtouch PROPERTIES CXX_VISIBILITY_PRESET hidden)
