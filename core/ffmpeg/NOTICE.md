# core/ffmpeg notices

- `src/main/java` and `src/main/jni/ffmpeg_jni.cc` are the AndroidX Media3
  FFmpeg decoder extension, release 1.11.0, © The Android Open Source
  Project, licensed under the Apache License 2.0. Unmodified except for one
  Javadoc link to a class this module does not bundle.
- `src/main/jni/prebuilt/` holds FFmpeg n7.1 static libraries (libavcodec,
  libavutil, libswresample) built by `scripts/build-ffmpeg-audio.sh` with
  `--disable-gpl` and only the AC-3, E-AC-3, TrueHD, MLP, and DTS decoders.
  FFmpeg is licensed under the GNU LGPL 2.1 or later; source:
  https://github.com/FFmpeg/FFmpeg/tree/n7.1. Rebuild with the script to
  replace them.
