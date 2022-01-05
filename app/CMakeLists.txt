cmake_minimum_required(VERSION 3.4.1)

add_library(
        native-lib
        SHARED
        src/main/cpp/jni.cpp
        src/main/cpp/vgs2tone.c
        src/main/cpp/vgsdec.c
        src/main/cpp/vgsmml.c
)

include_directories(
        src/main/cpp
)

target_link_libraries(
        native-lib
        log
)
