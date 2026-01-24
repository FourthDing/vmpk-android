cmake_minimum_required(VERSION 3.21)

project( vmpk-android
    LANGUAGES C
)

set(BUILD_SHARED_LIBS OFF)
set(EAS_FM_SYNTH ON)
add_subdirectory(sonivox)
add_subdirectory(opensl_stream)
add_subdirectory(midisynth)
