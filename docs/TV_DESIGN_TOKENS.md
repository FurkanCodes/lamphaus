# Lamphaus TV design tokens

The production source of truth is `TvDesignTokens.kt` plus `TvTheme.kt`. Values are measured on the 960×540 mdpi reference canvas.

## Geometry

| Token | Value |
| --- | ---: |
| Horizontal safe margin | 58dp |
| Top navigation origin | 32dp |
| Content origin | 104dp |
| Rail item gap | 20dp |
| Section gap | 40dp |
| Poster | 153×231dp |
| Landscape fallback | 256×144dp |
| Hero | 844×320dp at reference width |
| Settings menu | 268dp |
| Settings content | 452dp |

## Shape and focus

| Token | Value |
| --- | --- |
| Card/button/field/list shape | 4dp |
| Hero shape | 12dp |
| Card focus outline | 3dp, Lamphaus primary `#A8C8FF` |
| Focused artwork scale | 1.02× |
| Focus halo | 7dp elevation, 28% Lamphaus primary |
| Selected navigation beam | 24×2dp |
| Focused container | `#E3E2E6` |
| Focused content | `#2F3033` |
| Default action container | 10% white |
| Focus response | 160ms |
| Delayed hero update | 240ms |
| Hero transition | 220ms crossfade with 1–1.25% horizontal drift |
| Library confirmation | 110ms out + 110ms return |
| Startup sweep | 480ms, once |

## Artwork behavior

Use the portrait poster first. If a portrait poster is unavailable, preserve the supplied landscape artwork at 16:9 instead of cropping it into a portrait slot. Home-rail labels are revealed only for the focused item; grids keep labels visible because they do not have an immersive hero carrying the title. When a valid score is available, show it in a compact neutral scrim centered near the lower poster edge; the artwork remains the dominant surface.

## Lamphaus Beam experiment

The `feature/lamphaus-beam` branch adds a restrained brand layer without changing layout geometry: focused artwork gains a small blue halo and 2% scale, selected navigation receives a short beam, hero artwork drifts slightly through its crossfade, and the text side of the hero receives a faint primary illumination. Continue Watching uses a primary progress track with a bright leading tip. Empty states and profiles use the architectural Lamphaus aperture shape. Android Remove animations replaces these transitions with instant state changes.
