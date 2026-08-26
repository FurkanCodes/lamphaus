# Android Design Requirements

This is the implementation checklist for Lamphaus. It summarizes the supplied product brief and current Android platform guidance; it does not reproduce external documentation.

## Shared Android requirements

- **AND-FND-01:** Mobile and TV share product concepts and data, not component trees.
- **AND-FND-02:** All layouts use semantic Material roles, resource-backed strings, scalable type, and accessible state labels.
- **AND-A11Y-01:** Normal text contrast is at least 4.5:1; large text and visible focus boundaries are at least 3:1.
- **AND-A11Y-02:** Interactive elements expose roles, names, selected/disabled states, and logical traversal.
- **AND-MOT-01:** Motion communicates state in 150–250ms and honors the system remove-animations preference.
- **AND-PRIV-01:** Provider credentials, URLs, queries, tokens, and stream locations never enter diagnostics or analytics.

## Mobile requirements

- **MOB-NAV-01:** Compact widths use four labeled bottom destinations; medium and expanded widths use a labeled rail.
- **MOB-LAY-01:** Layout responds to window size and posture, preserves system/IME insets, supports split screen, and never locks orientation outside playback.
- **MOB-INP-01:** Every target is at least 48dp and every gesture has a visible alternative.
- **MOB-TYP-01:** All text uses Material roles and remains usable at 200% font scale.
- **MOB-THM-01:** System, light, and dark preferences are supported; Android 12+ dynamic color can be enabled without losing brand fallback.

## Television foundations

- **TV-FND-01:** The experience is content-first, readable from ten feet, dark-surface by default, and safe for shared-room viewing.
- **TV-FND-02:** All critical flows work with D-pad, Back, Home, play/pause, and seek keys; touch is never required.
- **TV-FND-03:** No on-screen Back affordance is shown. Back reverses the most recent navigation layer and never traps focus.
- **TV-FND-04:** Screens assume 16:9, use the 960×540 mdpi reference canvas, and keep critical content inside overscan-safe margins.
- **TV-FND-05:** Background imagery may be full bleed; text and controls may not occupy unsafe edges.

## Television focus and navigation

- **TV-FOC-01:** Exactly one actionable element presents visible focus whenever the screen is interactive.
- **TV-FOC-02:** Focus uses a high-contrast outline or inverse container plus an accompanying content/reveal state; it never relies on color alone.
- **TV-FOC-03:** Directional movement is spatially predictable and every row restores its previously focused item.
- **TV-FOC-04:** Focused scaling has room to render without cropping siblings or leaving the safe region.
- **TV-NAV-01:** The overscan-safe top navigation exposes Home, Discover, Search, Library, and Settings/Profile; each destination has an explicit D-pad entry target.
- **TV-NAV-02:** Returning from details or playback restores focus to the originating item.
- **TV-NAV-03:** Back from page content scrolls that page to its top and activates the currently active top-navigation item; back restores the previous view instead of switching destinations implicitly.
- **TV-NAV-04:** Back is never gated by confirmation screens and never loops; repeated Back reaches the start destination and then exits. No on-screen Back affordance is shown; a Cancel action accompanies screens whose only other actions are confirming, destructive, or purchase actions.
- **TV-NAV-05:** Framework spatial navigation is preferred; explicit directional overrides are added only where default traversal demonstrably fails, and overridden orders form closed loops.
- **TV-NAV-06:** Loading and splash states never enter the back stack; the signed-in Home screen is the fixed start destination, and deep links simulate manual navigation back to it.
- **TV-NAV-07:** Rows and categories traverse on the vertical axis while items within a row traverse on the horizontal axis; layouts keep a straight D-pad path to every visible control and avoid nested or crossing focus hierarchies.
- **TV-INP-01:** TV text fields accept browse focus without opening the IME, Select enters edit mode, and Back/IME action returns to browse mode while retaining field focus.

## Television content and type

- **TV-CNT-01:** Home uses an immersive feature region followed by continuation and provider rows; it never auto-rotates.
- **TV-CNT-02:** Details prioritize title, metadata, primary action, summary, and episodes in that order.
- **TV-CNT-03:** Loading uses stable skeleton geometry; provider failures remain attached to their affected row.
- **TV-TYP-01:** Inter and the approved TV Material type scale are used. Card copy is short, wraps safely, and never scrolls horizontally.
- **TV-CLR-01:** Artwork and content accents are sRGB and contrast-clamped; saturated color is not used as a large reading surface.

## Quality gates

Each requirement ID must map to a Compose test, screenshot test, lint/static check, or named manual test before release. Validation covers 720p, 1080p, and 4K TV; compact, medium, and expanded mobile widths; 200% font scale; RTL; TalkBack; keyboard; Switch Access; and physical D-pad navigation. D-pad traversal (every visible control reachable) and Back-path behavior are exercised on the TV emulator with `adb shell input keyevent` before each release.

**Validation mapping:** TV-INP-01 is covered by `TvNavigationBehaviorTest` and the named emulator check `TV-INP-01 — TV text-field browse/edit flow` in the release verification checklist.

## Source baseline

- Android TV design foundations: <https://developer.android.com/design/ui/tv/guides/foundations/design-for-tv>
- TV layouts: <https://developer.android.com/design/ui/tv/guides/styles/layouts>
- TV focus system: <https://developer.android.com/design/ui/tv/guides/styles/focus-system>
- TV navigation training (D-pad, focus, Back): <https://developer.android.com/training/tv/get-started/navigation>
- TV app quality: <https://developer.android.com/develop/adaptive-apps/quality-guidelines/tv-app-quality>
- Mobile adaptive apps: <https://developer.android.com/develop/adaptive-apps/guides/get-started-with-adaptive-apps>
