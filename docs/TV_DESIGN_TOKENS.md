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
| Focused container | `#E3E2E6` |
| Focused content | `#2F3033` |
| Default action container | 10% white |
| Focus response | 160ms |
| Delayed hero update | 240ms |

## Artwork behavior

Use the portrait poster first. If a portrait poster is unavailable, preserve the supplied landscape artwork at 16:9 instead of cropping it into a portrait slot. Home-rail labels are revealed only for the focused item; grids keep labels visible because they do not have an immersive hero carrying the title. When a valid score is available, show it in a compact neutral scrim centered near the lower poster edge; the artwork remains the dominant surface.
