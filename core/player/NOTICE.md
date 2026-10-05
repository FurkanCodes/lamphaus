# Lamphaus Player — Third-Party Notices

The player ships two optional native components and one build toolchain. Both
are dynamically loaded at runtime; the application functions fully without
them (Media3/ExoPlayer remains the primary engine).

## libmpv (optional fallback engine)

- Source: https://github.com/mpv-player/mpv (pinned `v0.40.0` via
  `scripts/build-mpv-libs.sh`)
- License: LGPL-2.1-or-later. Built with `-Dgpl=false` (no GPL components) and
  linked dynamically by dlopen; no GPL code is compiled into the application.
- Built FFmpeg dependency: https://github.com/FFmpeg/FFmpeg (pinned `n7.1`),
  configured `--enable-shared --enable-lgpl --disable-static` (LGPL 2.1+).
- Built libass dependency: https://github.com/libass/libass (pinned `0.17.3`),
  ISC license.
- Reproducible source instructions: `scripts/build-mpv-libs.sh` (clones the
  pinned refs, verifies, builds per-ABI, strips, and reports sha256 for each
  produced `libmpv.so`).

mpv is Copyright (c) mpv-player developers; FFmpeg is Copyright (c) the FFmpeg
developers; libass is Copyright (c) the libass developers. Their license texts
ship with the respective source trees and must accompany any distribution of
the built libraries.

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

## Behavioral references

- NuvioTV (https://github.com/NuvioMedia/NuvioTV) was consulted as a
  behavioral reference only; no GPL source is copied.
