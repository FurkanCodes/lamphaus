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

## AndroidX Media3

- Artifacts: androidx.media3 (ExoPlayer, media3-session, media3-ui), Apache
  License 2.0. https://github.com/androidx/media

## Behavioral references

- NuvioTV (https://github.com/NuvioMedia/NuvioTV) was consulted as a
  behavioral reference only; no GPL source is copied.
