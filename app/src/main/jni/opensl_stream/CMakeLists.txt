project( opensl_stream
    LANGUAGES C
)

set(SOURCES
    opensl_stream.c
    opensl_stream.h
)

add_library( opensl_stream STATIC ${SOURCES} )

target_include_directories( opensl_stream PUBLIC ${CMAKE_CURRENT_SOURCE_DIR} )
