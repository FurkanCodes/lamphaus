# Lamphaus Design System

## Scene and strategy

Someone settles into a dim living room after sunset and needs to recognize content and focus from across the room without the interface becoming another light source. The TV system uses neutral Material surfaces, a pale inverse focus treatment, and media artwork for emotional color.

## Color source

Mobile supports light and dark schemes plus Android dynamic color. TV uses the fixed semantic dark scheme extracted from the approved reference file.

| Role | Light | Dark / TV |
| --- | --- | --- |
| Background | `#FFFFFF` | `#1A1C1E` |
| Surface | `#F6F7FA` | `#121316` |
| Primary | `#40588D` | `#A8C8FF` |
| On primary | `#FFFFFF` | `#003062` |
| Secondary | `#006878` | `#BDC7DC` |
| Ink | `#191B22` | `#E3E2E6` |
| Surface ink | `#505563` | `#C7C6CA` |
| Muted ink | `#505563` | `#C4C6CF` |

Components consume semantic color roles, never raw values. Normal text must reach 4.5:1 contrast and large text/focus boundaries 3:1.

## Typography

Use Inter through Material type roles on TV. The complete scale is defined once in `TvTheme.kt`: 57/64, 45/52, 36/44 display; 32/40, 28/36, 24/32 headline; 22/28, 16/24, 14/20 title; 16/24, 14/20, 12/16 body; and 14/20, 12/16, 11/16 label. Text wraps safely and card labels remain short.

## Layout

- Mobile: 16dp compact margins; 24dp medium/expanded margins; 4/8/12-column thinking; bottom navigation under 600dp and rail from 600dp.
- TV: 960×540 mdpi reference canvas, 58dp horizontal safe margins, 32dp top navigation origin, 20dp rail gutters, a 320dp hero, and no critical element near overscan edges.
- Touch targets are at least 48×48dp. TV targets leave enough surrounding space for focused scale without clipping.

## Shape and elevation

Poster cards, buttons, fields, and list rows use 4dp corners. Immersive hero cards use 12dp. Focused TV cards use a stable-size 3dp Lamphaus-blue high-contrast outline; buttons and navigation invert surface/content colors without scaling. Available ratings appear as small neutral poster overlays rather than additional card chrome.

## Motion

State transitions last 150–250ms with fast-out-slow-in or linear-out-slow-in easing. TV focus responds immediately; hero artwork crossfades after a short focus dwell. Remove-animations changes transitions to instant state changes or a minimal crossfade. Nothing auto-advances while the user is reading.

## Artwork

Posters are 2:3, backdrops 16:9, TV banners 16:9, and icons use adaptive foreground/background layers. Generated fixture artwork is fictional, unbranded, sRGB, and never substitutes for provider-supplied runtime art.
