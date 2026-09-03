cmake_minimum_required(VERSION 3.22)
project(lamphaus_mpv C)

# Thin dlopen glue: builds with no libmpv dependency and no C++ runtime.
add_library(lamphaus_mpv SHARED mpv_jni.c)

target_link_libraries(lamphaus_mpv
    android
    log
)
