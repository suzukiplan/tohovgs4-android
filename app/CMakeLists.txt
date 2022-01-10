cmake_minimum_required(VERSION 3.4.1)

add_library(
        native-lib
        SHARED
        src/main/cpp/jni.cpp
        src/main/cpp/vgs2tone.c
        src/main/cpp/vgsdec.c
        src/main/cpp/vgsmml.c
        src/main/cpp/android_fopen.c
        src/main/cpp/compat/game.c
        src/main/cpp/compat/vgeapi.c
        src/main/cpp/compat/vgs.c
)

include_directories(
        src/main/cpp
        src/main/cpp/compat
)

target_link_libraries(
        native-lib
        android
        jnigraphics
        OpenSLES
        log
)
