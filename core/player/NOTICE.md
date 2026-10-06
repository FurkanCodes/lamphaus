# Lamphaus Player — Third-Party Notices

The player ships libmpv as a second, optional engine, a Kotlin port of
libdovi, and a patched Media3 module. libmpv is loaded at runtime; the
application functions fully without it (Media3/ExoPlayer remains the default
engine).

## libmpv (second playback engine)

libmpv and its libraries are loaded at runtime with dlopen; Lamphaus links
none of them. Each is LGPL or permissively licensed, and none is built with
GPL components. They are built from pinned tags by `scripts/build-mpv-libs.sh`,
which records each resolved commit and every library's SHA-256 in its build
manifest; the same script reproduces them.

| Library | Pinned source | License |
| --- | --- | --- |
| mpv | https://github.com/mpv-player/mpv `v0.40.0`, `-Dgpl=false`, libmpv only | LGPL-2.1-or-later |
| FFmpeg | https://github.com/FFmpeg/FFmpeg `n7.1`, `--disable-gpl --enable-version3`, JNI and MediaCodec, no encoders or muxers | LGPL-3.0-or-later |
| libplacebo | https://code.videolan.org/videolan/libplacebo `v7.351.0`, OpenGL ES only | LGPL-2.1-or-later |
| Mbed TLS | https://github.com/Mbed-TLS/mbedtls `mbedtls-3.6.2`, linked into FFmpeg | Apache-2.0 |

libmpv renders subtitles with the `libass.so` that ass-media
(`io.github.peerless2012:ass-media`, already used for ExoPlayer's styled
subtitles) ships, so no second libass is packaged; the script builds libass
0.17.3 and its font libraries only to compile mpv against. libplacebo uses
the `libc++_shared.so` the app already ships.

mpv is Copyright (c) mpv-player developers; FFmpeg is Copyright (c) the
FFmpeg developers; the other libraries are copyright their respective
authors. Their license texts ship with the respective source trees and
accompany any distribution of the built libraries. Because the libraries are
separate shared objects, a user may replace any of them with a compatible
build of their own.

## libdovi (ported, Dolby Vision profile 7 → 8.1)

- Source: https://github.com/quietvoid/dovi_tool, `dolby_vision` crate 3.4.0
  (commit `614c816b6446dcd1dbaf433403d499a6026fbb5a`)
- License: MIT. The RPU header, mapping, and display-management syntax and the
  mode 2 (to 8.1) conversion in `core/player/.../dolbyvision/` are a Kotlin
  port of that crate; no native library is shipped.

```
MIT License

Copyright (c) 2026 quietvoid

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## AndroidX Media3

- Artifacts: androidx.media3 (ExoPlayer, media3-session, media3-ui), Apache
  License 2.0. https://github.com/androidx/media
- `media3-exoplayer` is a patched build of release 1.11.0
  (`com.lamphaus.media3:media3-exoplayer:1.11.0-lamphaus.1` in
  `third_party/maven/`), made by `scripts/build-media3-exoplayer.sh` from the
  upstream tag plus `third_party/media3/patches/`. The patch, Lamphaus's own
  work under the same Apache License 2.0, keeps buffered samples in off-heap
  memory: `Allocation`, `DefaultAllocator`, and `SampleDataQueue` gain a
  direct-buffer path, and the new `NativeBuffers` class with
  `src/main/jni/native_buffers.c` allocates and frees that memory. Every
  other Media3 module is the stock 1.11.0 artifact.

## Behavioral references

- NuvioTV (https://github.com/NuvioMedia/NuvioTV) was consulted as a
  behavioral reference only; no GPL source or binaries are copied. Its
  streaming behaviour (parallel range downloads, native memory buffering,
  recovery), Dolby Vision modes, audio options, engine choice, and external
  player hand-off are re-implemented from observed settings and constants.
