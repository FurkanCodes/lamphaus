# Media3 FFmpeg extension JNI (Apache-2.0), linked against the LGPL FFmpeg
# static libraries built by scripts/build-ffmpeg-audio.sh.
cmake_minimum_required(VERSION 3.21.0 FATAL_ERROR)

set(CMAKE_CXX_STANDARD 11)

project(libffmpegJNI C CXX)

set(ffmpeg_prefix "${CMAKE_CURRENT_SOURCE_DIR}/prebuilt/${ANDROID_ABI}")

foreach(ffmpeg_lib avutil swresample avcodec)
    add_library(${ffmpeg_lib} STATIC IMPORTED)
    set_target_properties(${ffmpeg_lib} PROPERTIES
            IMPORTED_LOCATION ${ffmpeg_prefix}/lib/lib${ffmpeg_lib}.a)
endforeach()

# Headers are shared across ABIs except libavutil/avconfig.h, which comes first.
include_directories(${ffmpeg_prefix}/avconfig ${CMAKE_CURRENT_SOURCE_DIR}/prebuilt/include)
find_library(android_log_lib log)

add_library(ffmpegJNI SHARED ffmpeg_jni.cc)

target_link_libraries(ffmpegJNI
                      PRIVATE android
                      PRIVATE swresample
                      PRIVATE avcodec
                      PRIVATE avutil
                      PRIVATE ${android_log_lib})

# 16 KB page alignment for Android 15+ devices.
target_link_options(ffmpegJNI PRIVATE "-Wl,-z,max-page-size=16384")

if(ANDROID_ABI STREQUAL "arm64-v8a")
    target_link_options(ffmpegJNI PRIVATE "-Wl,-Bsymbolic")
endif()
