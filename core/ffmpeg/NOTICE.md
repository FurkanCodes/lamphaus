# core/ffmpeg notices

- `src/main/java` and `src/main/jni/ffmpeg_jni.cc` are the AndroidX Media3
  FFmpeg decoder extension, release 1.11.0, © The Android Open Source
  Project, licensed under the Apache License 2.0. Modified by Lamphaus under
  the same license: one Javadoc link to a class this module does not bundle,
  and Force AC-3 for optical (`FfmpegAudioRenderer`, `FfmpegAudioDecoder`,
  and `ffmpeg_jni.cc` re-encode surround to AC-3 through FFmpeg's AC-3
  encoder).
- `src/main/jni/prebuilt/` holds FFmpeg n7.1 static libraries (libavcodec,
  libavutil, libswresample) built by `scripts/build-ffmpeg-audio.sh` with
  `--disable-gpl`, only the AC-3, E-AC-3, TrueHD, MLP, and DTS decoders, and
  the AC-3 encoder.
  FFmpeg is licensed under the GNU LGPL 2.1 or later; source:
  https://github.com/FFmpeg/FFmpeg/tree/n7.1. Rebuild with the script to
  replace them.
