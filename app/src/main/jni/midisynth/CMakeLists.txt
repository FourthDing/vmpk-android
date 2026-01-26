project( midisynth
    LANGUAGES C
)

add_library(midisynth SHARED midisynth.c)

target_link_libraries(midisynth PRIVATE
    opensl_stream
    sonivox
    OpenSLES
    log
)

target_link_options(midisynth PRIVATE
    "-Wl,--build-id=none"
)
