# Lamphaus Android Interface Rules

This file is the repository-wide source of truth for designing, implementing, reviewing, and testing Lamphaus interfaces. It consolidates the supplied Android mobile guidance, the existing Lamphaus product and television guidance, and the Android architecture guidance that supports reliable UI.

Normative words have their usual meaning: **must** is required, **should** is the default unless a documented reason applies, and **may** is optional. When rules conflict, use this order:

1. Safety, privacy, accessibility, and explicit product requirements.
2. The platform-specific section in this file: Mobile or TV.
3. Shared Lamphaus rules in this file.
4. Baseline Material 3 behavior.

Existing implementation is not evidence that a conflicting pattern is approved. New work must not copy known drift.

## 1. Instructions for AI agents and contributors

- Read this file before planning, generating, editing, or reviewing Android UI, navigation, presentation state, notifications, onboarding, settings, playback surfaces, or visual resources.
- State whether the work targets **mobile**, **TV**, **playback**, or **shared UI infrastructure**. Do not apply one platform's component tree or input model to the other.
- Reference the applicable rule IDs in implementation plans, code-review findings, and test names. A UI change is incomplete until its relevant quality gates pass.
- Use Jetpack Compose and Material 3 components where they meet the need. Preserve platform behavior instead of recreating it with bespoke chrome.
- Treat mobile and TV as purpose-built experiences sharing product concepts, data, and selected non-visual logic. They must not share navigation components, screen layouts, focus behavior, or geometry merely to reduce code.
- If a requested design conflicts with a must-level rule, flag the conflict before implementation and propose the nearest compliant design.
- Keep this file canonical. Supporting design documents may explain history or exact asset production details, but must not redefine a rule here.

## 2. Product experience

- **SHR-PROD-01 — Content first.** Content and artwork lead; chrome recedes. Avoid noisy streaming-service imitation, promotional clutter, decorative glass, uncontrolled carousels, generic card walls, and affiliation with another media platform.
- **SHR-PROD-02 — Clear next action.** Focus, touch, copy, and state changes must explain what will happen next. Every primary call to action (CTA) is obvious and singular within its context.
- **SHR-PROD-03 — Calm brand.** Lamphaus is precise, cinematic, and calm: a pre-dawn observatory with cold instrument-blue guidance and artwork carrying the emotion.
- **SHR-PROD-04 — Local recovery.** Loading, empty, offline, error, and partial-provider states remain local, understandable, and recoverable. Do not replace usable content with a whole-screen failure when only one row or pane failed.
- **SHR-PROD-05 — Honest product.** Lamphaus is provider-neutral and supplies no content source. Copy and visuals must not imply ownership of provider content or affiliation with another service.
- **SHR-PROD-06 — Privacy by default.** Communal-room and lock-screen surfaces reveal the minimum necessary information. Provider credentials, URLs, queries, tokens, stream locations, and sensitive account data never enter diagnostics or analytics.
- **SHR-PROD-07 — Localizable copy.** User-facing text comes from string resources, supports pluralization and RTL, and remains clear with longer translations. Do not embed copy in code or imagery.
- **SHR-PROD-08 — Color-managed art.** Product fixture artwork and authored assets use sRGB. Runtime content accents are contrast-clamped before they affect text, controls, or focus indicators.

## 3. Shared vocabulary

- **Activity:** An Android application component that hosts UI and makes actions available. Lamphaus uses activities as platform/form-factor hosts, not as data stores.
- **Back stack:** The ordered stack of destinations or activities a user traverses. Back reverses the user's navigation history.
- **Canonical layout:** A common adaptive composition such as list-detail, feed, or supporting pane.
- **Chroma:** A color's colorfulness, from neutral gray to full vibrancy.
- **Containment:** Grouping related content or actions with whitespace, typography, dividers, surfaces, or panes.
- **CTA:** Call to action; the primary goal the interface asks the user to complete.
- **Density-independent pixel (dp):** A layout unit relative to a 160 dpi screen; approximately one pixel at mdpi.
- **Display cutout:** A display area reserved for physical hardware such as a camera or sensor.
- **Hue:** The perceived family or description of a color.
- **Intent:** Android's mechanism for asking another component or app to perform an action, such as sharing or opening a browser.
- **Navigation bar:** Android's bottom system bar for gesture or button device navigation. It is distinct from the app's Material navigation bar.
- **Scalable pixel (sp):** A text unit that combines density scaling with the user's font-size preference.
- **Status bar:** Android's top system bar containing time, status, and notification icons.
- **System bars:** The status and system navigation bars collectively.
- **Task:** A back stack of activities used to complete a user goal.
- **Tone:** A color's luminance or brightness.

## 4. Architecture and UI state

- **SHR-ARC-01 — Host architecture.** Keep the existing separate mobile, TV, and playback activity hosts where their form-factor or system responsibilities require them. Within each host, use a single-activity destination model rather than creating an activity per screen. Use Navigation 3 for new multi-screen navigation and deep links; document any staged migration needed by existing navigation.
- **SHR-ARC-02 — Layering.** Maintain a clearly defined UI layer and data layer. Add a domain layer only when reusable or complex business logic justifies it.
- **SHR-ARC-03 — Separation of concerns.** Activities, services, receivers, and composables coordinate and render; they do not own durable application data or business rules.
- **SHR-ARC-04 — Data access.** UI and ViewModels access application data through repositories, never directly through databases, DataStore, SharedPreferences, network clients, location providers, or other data sources.
- **SHR-ARC-05 — Single source of truth.** Give every mutable data type one owner. Expose immutable state and explicit mutation functions.
- **SHR-ARC-06 — Unidirectional data flow.** State flows from data/domain layers through a screen-level state holder to UI; user actions flow back toward the owner. Render UI from data models rather than imperatively mutating views.
- **SHR-ARC-07 — ViewModels.** Use AAC `ViewModel` at screen or navigation-graph scope. Do not use ViewModels in reusable components, hold `Activity`, `Context`, `Resources`, or views in them, or use `AndroidViewModel` without a documented exception.
- **SHR-ARC-08 — StateFlow.** Expose screen UI state as immutable `StateFlow` where appropriate, collect it with `collectAsStateWithLifecycle`, and use `stateIn` plus `SharingStarted.WhileSubscribed(5_000)` for derived streams unless another lifecycle is required.
- **SHR-ARC-09 — Events become state.** Handle actions in the state holder and represent results as durable UI state. Do not use fire-and-forget ViewModel-to-UI event streams for outcomes that can be lost during lifecycle changes.
- **SHR-ARC-10 — Lifecycle.** Use lifecycle-aware Compose effects and collection instead of Activity lifecycle overrides for UI work. Preserve relevant navigation, input, scroll, selection, and playback state across rotation, resizing, folding, backgrounding, and process recreation.
- **SHR-ARC-11 — Concurrency.** Use coroutines and Flow between layers. Types that perform blocking or long-running work own their dispatcher policy and are safe to call from the main thread.
- **SHR-ARC-12 — Dependencies.** Prefer constructor injection. Use Hilt when navigation-scoped ViewModels, WorkManager, or project complexity warrant it; manual dependency injection remains acceptable where simpler.
- **SHR-ARC-13 — Offline resilience.** Persist relevant fresh data so browsing and recovery remain useful with intermittent connectivity. A database is normally the source of truth for offline-first application data.
- **SHR-ARC-14 — Reuse.** Build reusable, stateless or state-hoisted composables. Use plain state-holder classes for complex reusable UI behavior. Share models and behavior across form factors, not entire screens.
- **SHR-ARC-15 — Testability.** Keep boundaries explicit, expose as little as possible, and prefer fakes over mocks. At minimum test ViewModels and Flows, repositories/data sources, and navigation regressions.
- **SHR-ARC-16 — Models.** In complex features, map remote, persistence, domain, and presentation models at layer boundaries when doing so reduces coupling or exposes only what the consumer needs.
- **SHR-ARC-17 — Naming.** Name methods with verb phrases and properties with noun phrases. Name Flow-returning functions `get{Model}Stream`/`get{Models}Stream`; give implementations meaningful behavior-based names, use `Default` only as a fallback, and prefix test fakes with `Fake`.

## 5. Mobile app anatomy and system UI

- **MOB-SYS-01 — Anatomy.** Design each screen as system bars, an app navigation region, and a body. Body backgrounds and scroll content may continue behind system/navigation regions; interactive body content must remain usable and legible.
- **MOB-SYS-02 — Edge to edge.** Call `enableEdgeToEdge()` in mobile and playback hosts. Draw backgrounds, dividers, imagery, and scrollable content behind system bars.
- **MOB-SYS-03 — Insets.** Apply system-bar, system-gesture, IME, display-cutout, and waterfall/edge-display insets according to what each element needs. Critical text, controls, tap targets, and drag targets never sit under obscuring hardware or gesture-conflict zones.
- **MOB-SYS-04 — Scroll protection.** Use Material 3 app-bar protection or one custom, background-matched gradient protection when content scrolls beneath a translucent status bar. Never stack protections. Give panes and drawers separate matching protection when their surfaces differ.
- **MOB-SYS-05 — Navigation bar treatment.** Keep gesture navigation transparent. When a bottom app bar animates away under three-button navigation, provide the appropriate system-bar scrim.
- **MOB-SYS-06 — Cutouts.** Let solid backgrounds and horizontal carousels extend into cutouts, while insetting important content. Test portrait, landscape, and cover displays with cutouts.
- **MOB-SYS-07 — Keyboard.** When the IME appears, keep the active input visible and focused. Reflow, pan, or pin the field to the keyboard; never hide an input or assume the user can discover an offscreen scroll.
- **MOB-SYS-08 — Quick access.** Use a Quick Settings tile or two to four app shortcuts only for genuinely frequent, safe actions. Shortcuts have simple recognizable icons, work immediately, and deep-link into a coherent back stack.
- **MOB-WGT-01 — Purpose and placement.** A widget serves one primary, glanceable use case and has a useful default without configuration. Declare `targetCellWidth` and `targetCellHeight` (with sensible `minResizeWidth`/`minResizeHeight`) so the picker offers an appropriate default on phones and tablets, and add resizable sizes only when the content benefits. Verify the recommended size bands (handheld `2×1` through `4×3`, tablet bands) and Auto (a `2×2` grid works best).
- **MOB-WGT-02 — Fill and shape.** The container fills its entire grid cell at every size with no custom outer padding and uses the system corner radius. An expressive non-rectangular shape must touch the grid on at least one axis; never use a fixed square container.
- **MOB-WGT-03 — Responsive content.** Define breakpoints where the layout changes—show or hide supplemental content and switch between the text, toolbar, list, and grid canonical layouts—instead of stretching one layout. Build hierarchy with weight, size, and line height rather than decorative color.
- **MOB-WGT-04 — Theming.** Use Material color roles and tokens, dynamic color on Android 12+, correct light and dark rendering, type from Material roles, and Glance as the implementation layer.
- **MOB-WGT-05 — Configuration.** Open configuration during placement only when the widget is empty without a choice or customization is central; otherwise let the user configure after placement. Guide the user to adding the widget: never dead-end and never cancel the addition because configuration was abandoned—provide an in-widget empty or configure state instead. Show a live preview while customizing and disclose advanced controls progressively.
- **MOB-WGT-06 — Picker and discovery.** Picker previews must match the shipped size, layout, and colors (use `android:previewLayout` where available), never fall back to an icon-only preview, and never change size or shape when dropped. Provide a clear value description, and do not publish multiple widgets that differ only in color or shape. Offer pinning from the app only at contextually relevant moments and never block a primary action.
- **MOB-SYS-10 — Links and sharing.** Use verified Android App Links for public Lamphaus URLs. Use the system share sheet through `Intent.createChooser`, filter incoming content types, and provide safe title/description/thumbnail previews without leaking provider-sensitive data.

## 6. Mobile adaptive layout and content structure

- **MOB-LAY-01 — Adaptive by default.** Decide layout from the current window, not a device name. Use width size class first, then height, posture, aspect ratio, display features, viewing distance, and input capabilities as needed.
- **MOB-LAY-02 — Size classes.** Support compact `<600dp`, medium `600–839dp`, and expanded `>=840dp` widths. At minimum every feature must have intentional compact and expanded behavior; medium must never be an accidental stretched compact layout.
- **MOB-LAY-03 — Orientation and resizing.** Support portrait, landscape, split screen, freeform/desktop resizing, fold/unfold, and configuration changes. Do not lock orientation except where a playback experience has a specific, justified requirement.
- **MOB-LAY-04 — Panes.** Compact normally uses one pane, medium one or two panes, and expanded multiple panes. Think in movable, revealable, hideable, constrainable panes rather than separate screens for every device.
- **MOB-LAY-05 — Canonical layouts.** Prefer list-detail for a list with supplementary item detail, feed for equivalent card/tile content, and supporting-pane layouts for contextual controls or information. Bottom sheets may become side sheets or supporting panes at larger widths.
- **MOB-LAY-06 — Max width.** Do not stretch reading content, buttons, forms, settings rows, or single-pane layouts across large screens. Use a sensible maximum width, additional columns, or a second pane; approximately `840dp` is the default maximum reading/content width unless the composition requires another value.
- **MOB-LAY-07 — Grid.** Use a responsive column grid: compact 4 columns with `16dp` margins and `8dp` gutters; medium 8 columns with `24dp` margins and `16dp` gutters; expanded 12 columns with `24dp` margins and `24dp` gutters. Adapt when a hinge or display feature requires a wider gutter.
- **MOB-LAY-08 — Baseline rhythm.** Use an `8dp` grid for layout, component dimensions, and spacing, with `4dp` increments for typography, icons, and small internal alignment. Exceptions must preserve visual rhythm.
- **MOB-LAY-09 — Containment.** Group related content and actions with implicit containment first—whitespace, alignment, and type hierarchy—and explicit surfaces/dividers when a visible boundary adds meaning. Do not put every item in a card.
- **MOB-LAY-10 — Hierarchy.** Use hierarchical grids for unequal/emphasized content, modular grids for equivalent collections, and column grids for flexible one-directional content. Choose from content hierarchy, not decoration.
- **MOB-LAY-11 — Flow.** Use rows/columns and lazy lists/grids for one- or two-dimensional content. Keep consistent spacing and logic in regular or staggered grids. Avoid nested scrolling or crossing interaction hierarchies that make content hard to reach.
- **MOB-LAY-12 — Ergonomics.** Keep primary navigation and high-value actions within comfortable reach. Use a FAB only when a screen has one unmistakable primary action.
- **MOB-LAY-13 — Essential actions.** Do not overwhelm a view with actions. Put the highest-value action in the primary position, secondary contextual actions near their content or in the top bar, and infrequent actions in overflow.
- **MOB-LAY-14 — Spec behavior.** Every custom layout or handoff states alignment, constraints, gravity/arrangement, image scale/crop, reflow, max width, and inset behavior.
- **MOB-LAY-15 — Foldables and covers.** Avoid hinges for text and controls, preserve continuity across folds, and consider tabletop media controls and focused cover-screen experiences. Cover UI stays edge-to-edge, cutout-safe, and intentionally reduced to its essential use case.
- **MOB-LAY-16 — Landscape continuity.** Reflow or change density/component presentation rather than merely rotate or stretch. Do not introduce scrolling only in landscape when the equivalent portrait content does not scroll.

## 7. Mobile navigation and back behavior

- **MOB-NAV-01 — Primary navigation.** Lamphaus mobile has four labeled top-level destinations: Home, Discover, Library, and Search. Use Material 3 `NavigationBar` on compact widths and a labeled `NavigationRail` from medium widths. Do not implement a custom bottom-navigation component.
- **MOB-NAV-02 — Destination limits.** A navigation bar is for three to five peer destinations. Keep primary navigation present on parent views; do not add secondary settings or contextual actions merely to fill it.
- **MOB-NAV-03 — Selected state.** Always show destination labels. Use filled icons for selected items, outlined icons for unselected items, and the Material `secondaryContainer` selected indicator with its matching foreground role. Selection is conveyed by semantics and more than color alone.
- **MOB-NAV-04 — Expanded navigation.** Use a permanent drawer only when an expanded hierarchy truly exceeds rail capacity. Use a modal drawer on compact/medium only for a complex hierarchy, not as a substitute for the four primary destinations.
- **MOB-NAV-05 — Secondary navigation.** Use tabs for sibling content. Use top/bottom app bars for related actions and secondary navigation. Do not use lateral slide motion between unrelated top-level destinations.
- **MOB-NAV-06 — App bars.** Parent views normally have a left-aligned title and no Up icon when primary navigation is visible. Child views use Up to move within the app hierarchy. Full-screen modal surfaces use Close. Limit top-bar actions to two or three, then use overflow.
- **MOB-NAV-07 — Back versus Up.** System Back reverses user history and can leave the app; Up moves one level in the app hierarchy. They may produce different destinations. Never replace Back with an app-defined double-press-to-exit gate.
- **MOB-NAV-08 — Predictive Back.** Opt into predictive back and use AndroidX/Navigation handling. Do not override deprecated `onBackPressed()`, suppress system previews, or intercept Back for confirmation unless unsaved user work would be lost.
- **MOB-NAV-09 — Custom predictive motion.** Prefer system and Material transitions. If a custom full-screen preview is justified, derive it from predictive-back progress, respect the initiating edge, use standard deceleration, scale no smaller than 90%, preserve an `8dp` edge margin, and restore state cleanly on cancellation. In a custom full-screen preview, swap the pre-commit and destination content through a fade-through at the ~35% progress threshold. Reduced motion still applies.
- **MOB-NAV-10 — Context preservation.** Returning from details, playback, sheets, or external activities restores the originating destination, selected item, scroll position, and relevant input state.
- **MOB-NAV-11 — Intents and WebViews.** Prefer a browser Intent/Custom Tab for web content. Use WebView only when in-app web rendering is essential; make it follow light/dark theme and normal navigation/privacy rules.

## 8. Mobile theme, color, shape, and elevation

- **MOB-CLR-01 — Material color.** Use Material 3 HCT-derived tonal palettes and semantic roles. Components consume `MaterialTheme.colorScheme` or named product semantic tokens, never scattered hardcoded colors.
- **MOB-CLR-02 — Schemes.** Support System, Light, and Dark modes. Android 12+ dynamic color is the default personalization path unless a product requirement explicitly disables it. Always provide complete branded static light and dark fallbacks generated from the Lamphaus seed with Material Color Utilities/Material Theme Builder.
- **MOB-CLR-03 — User preferences.** Respect system theme, font, contrast, bold-text, animation, and accessibility preferences. An app override may add System/Light/Dark choice but must not remove system-respecting behavior.
- **MOB-CLR-04 — Role pairing.** Pair foregrounds with matching `on*` roles. Use surface roles for most backgrounds, primary for high-priority actions/active states, secondary for support, tertiary sparingly, and error only for error/destructive meaning.
- **MOB-CLR-05 — Semantic consistency.** Once a color meaning is established, repeat it. Do not encode status using color alone, change error color between fields, or confuse decorative and semantic palettes.
- **MOB-CLR-06 — Contrast.** Normal text reaches at least `4.5:1`; large text reaches `3:1`; non-text controls, icons, focus boundaries, and meaningful surface boundaries reach `3:1`. Avoid equal/similar tones for foreground and background.
- **MOB-CLR-07 — Restraint.** Keep one dominant theme source per view. Content-derived color may come from one primary artwork source; do not mix extracted palettes from multiple cards. Provide sufficient scrims behind text and controls.
- **MOB-CLR-08 — Dark surfaces.** Use tonal dark surfaces rather than pure black as the default reading/background system. Large reading areas use surface roles, not saturated accents.
- **MOB-CLR-09 — Brand fallback.** Lamphaus instrument blue remains the branded static fallback and semantic guidance color. Artwork supplies emotional color. Exact palette values live in the theme source, not components.
- **MOB-SHP-01 — Shape.** Express brand shape through theme/component roles consistently. Do not create one-off radii without a named token and a component rationale.
- **MOB-ELE-01 — Elevation.** Use tonal surface/elevation behavior to communicate hierarchy. Do not add decorative shadows, blur, or glass effects that reduce contrast or performance.

## 9. Typography, icons, and imagery

- **MOB-TYP-01 — Scalable type.** Specify all text in `sp` through Material typography roles. Body text is never below `12sp`; labels are never below `11sp`.
- **MOB-TYP-02 — Type roles.** Reference `MaterialTheme.typography` rather than hardcoded sizes in components. The mobile type system may use Inter for Lamphaus branding, but each of all 15 Material roles must retain a clear hierarchy and readable metrics.
- **MOB-TYP-03 — Font scaling.** Support at least `200%` font size without clipping, overlap, missing controls, or essential horizontal scrolling. Allow wrapping and reflow; do not cap font scaling to preserve a screenshot.
- **MOB-TYP-04 — Readability.** Use appropriate line heights, normally about `1.2–1.5×` font size, concise card labels, and logical heading structure. Support bold-text/high-contrast settings.
- **MOB-ICO-01 — Icons.** Use one coherent Material icon style. Icons communicate meaning, not decoration; they do not replace labels where recognition is uncertain.
- **MOB-GFX-01 — Asset choice.** Prefer Vector Drawables for icons and simple illustrations. Use WebP/PNG/JPEG for complex raster artwork and provide appropriate `mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, and `xxxhdpi` assets when runtime/provider artwork is not supplying resolution variants. Icon and small-asset sources embed intrinsic padding so touch targets and optical size stay consistent.
- **MOB-GFX-02 — Naming.** Drawable names are lowercase and follow a consistent prefix such as `ic_`. Avoid immutable text inside images; localize text in the UI.
- **MOB-GFX-03 — Aspect ratios.** Use intentional, consistent ratios—especially Lamphaus `2:3` posters and `16:9` backdrops/video. Other valid content ratios include `3:2`, `4:3`, `1:1`, `3:4`, and `2:3`.
- **MOB-GFX-04 — Crop contract.** Specify `Fit`, `Crop`, `FillHeight`, `FillWidth`, `FillBounds`, `Inside`, or equivalent behavior for each image container. Protect the subject/focal point and adapt ratios or fixed heights by breakpoint instead of allowing accidental crops.
- **MOB-GFX-05 — Performance.** Use gradients, blur, blend modes, and large animated images only when they improve comprehension. Blur requires a performant fallback below Android 12. Prefer Animated Vector Drawables or programmatic motion for small UI animations. Tint and blend modes recolor a single source asset instead of producing per-color variants.
- **MOB-GFX-06 — App surfaces.** Provide adaptive foreground/background and monochrome launcher assets, a compatible Android 12+ splash experience, and meaningful notification/media artwork. Do not use large notification icons merely for branding.

## 10. Components, actions, feedback, and motion

- **MOB-CMP-01 — Material first.** Use Material 3 code-backed components for actions, communication, containment, navigation, selection, and text input. Customize through theme/tokens and supported slots before building a replacement.
- **MOB-CMP-02 — FAB.** Use at most one FAB on a screen and only for its single highest-value action. Place it bottom-end above navigation/insets; prefer an extended labeled FAB when clarity benefits, use `primaryContainer` by default, and use `Scaffold` to keep it visible.
- **MOB-CMP-03 — Sheets.** Use modal bottom sheets for non-critical supplementary tasks, standard sheets for persistent support, and side/supporting panes on larger widths. Provide a drag handle where dismissal by drag is available and scroll content that can exceed the viewport.
- **MOB-CMP-04 — Dialogs.** Reserve dialogs for critical decisions requiring immediate attention. Use concise titles, contextual body text, and standard confirm/dismiss placement. Do not use dialogs for brief status or minor education.
- **MOB-CMP-05 — Snackbar.** Use snackbars for brief, non-critical feedback. Include one `Undo` action for reversible/destructive operations where practical. Never put critical or durable information only in an auto-dismissed snackbar.
- **MOB-CMP-06 — Selection.** Use switches/checkboxes for binary settings, radio buttons for one choice among two or more, sliders for ranges, segmented buttons for small peer option sets, and the correct chip type for filters, user input, assistance, or suggestions.
- **MOB-CMP-07 — Lists and cards.** Use lists for row-based collections and cards when explicit containment is meaningful. Use dividers sparingly and avoid redundant disclosure indicators.
- **MOB-CMP-08 — Immediate response.** Every action acknowledges input immediately through ripple, state change, progress, or feedback. Do not leave the UI apparently inert during work.
- **MOB-CMP-09 — Stable states.** Loading skeletons preserve the final content geometry. Empty, offline, and provider-error states occupy the affected component or pane and expose a relevant recovery action without displacing unrelated content.
- **MOB-MOT-01 — Purposeful motion.** Motion explains hierarchy, continuity, state, or result. Use Android/Material patterns such as container transform, shared axis, fade-through, and fade based on the relationship between destinations.
- **MOB-MOT-02 — Timing.** Ordinary state transitions are `150–250ms` with appropriate Material easing. Never auto-advance content while the user may be reading.
- **MOB-MOT-03 — Reduced motion.** Honor animator duration scale/remove-animations. Replace decorative transitions with instant state changes and keep essential state understandable without motion.
- **MOB-MOT-04 — Feedback.** Use Material ripple on tappable surfaces and appropriate haptics for meaningful confirmation or long press. Haptics supplement rather than replace visual/audible state.

## 11. Accessibility and input

- **SHR-A11Y-01 — Accessibility is foundational.** Accessibility requirements apply during design, implementation, and verification, not as a final polish pass.
- **MOB-A11Y-01 — Semantics.** Every meaningful or interactive element exposes an accurate role, name, value, selected/checked/disabled state, and action. Describe meaning or action, not appearance; decorative graphics use a null description.
- **MOB-A11Y-02 — Grouping.** Merge related descendants into a useful TalkBack focus unit when separate stops add noise. Preserve headings and enough granularity to skip between blocks of content and actions.
- **MOB-A11Y-03 — Custom actions.** Swipe, drag, long-press, and contextual actions have visible and accessibility-action alternatives. Never make a gesture the only route through a core flow.
- **MOB-A11Y-04 — Targets.** Every touch target is at least `48×48dp`, even when the visual icon is smaller. Do not shrink targets to fit a dense layout.
- **MOB-A11Y-05 — Focus order.** Reading and focus order follow top-to-bottom, start-to-end structure unless the layout requires an explicit correction. After navigation or dismissal, move accessibility/keyboard focus to the logical destination.
- **MOB-A11Y-06 — Input methods.** Every mobile flow works with TalkBack, Switch Access, Voice Access where applicable, touch, external keyboard, and pointer. Hover/focus states and keyboard shortcuts must not hide touch behavior.
- **MOB-A11Y-07 — Canvas.** Custom canvas-drawn controls expose virtual accessibility nodes and actions using `ExploreByTouchHelper` or Compose semantics with equivalent coverage.
- **MOB-A11Y-08 — Sensory redundancy.** Do not rely only on color, sound, motion, or haptics. Links and states have a second affordance such as icon, text, weight, shape, or pattern.
- **MOB-INP-01 — Gesture zones.** Do not place interactive elements wholly in system gesture insets. Use `WindowInsets.systemGestures` and display-cutout insets for custom positioning.
- **MOB-INP-02 — Destructive gestures.** Swipe-to-dismiss/delete is undoable or confirmed and always has a non-gesture action.
- **MOB-INP-03 — Long press.** Use long press only for context menus or multi-select, provide haptic feedback, and expose the same feature another way.

## 12. Settings, help, and feedback

- **MOB-SET-01 — Scope.** Settings contain infrequently changed preferences, not frequent actions, account management, version/license content, or duplicates of device settings. Frequently changed playback options such as captions belong near playback.
- **MOB-SET-02 — Defaults.** Defaults suit most users, are neutral and low-risk, conserve data/battery, and interrupt only when important. Persist user choices.
- **MOB-SET-03 — Placement.** Settings is normally secondary navigation: overflow, top app bar, or after primary items in a drawer. If it is essential to the product journey it may be top-level. Label it **Settings**, not Options or Preferences, and keep it accessible while signed out when applicable.
- **MOB-SET-04 — Structure.** Use a list overview and list-detail at larger sizes. Group related preferences under headings. For 15 or more settings, introduce category subscreens; the opening label and destination title must match.
- **MOB-SET-05 — Dependencies.** Put dependent settings below their parent, disable them when unavailable, explain why, and deep-link to the relevant device setting when the dependency is system-owned.
- **MOB-SET-06 — Copy.** Labels start with the important concept, use direct neutral language, avoid first-person phrasing and generic verbs, and do not repeat section titles. Supporting text shows status or consequence only when the label is insufficient.
- **MOB-SET-07 — Filters.** Filters modify the current content context. Do not hide contextual filtering among durable app settings.
- **MOB-HELP-01 — Placement.** Help and Send feedback are secondary: overflow, the bottom of a drawer, or Settings. Use consistent standard labels across platforms.
- **MOB-HELP-02 — Hierarchy.** Put common task help and direct links to relevant features/settings first. Place Privacy and Terms of service less prominently but accessibly.
- **MOB-HELP-03 — Support.** Provide issue-reporting/feedback channels and use relevant images, icons, video, or animation when they materially clarify a task. Review requests use the Google In-App Reviews API at an appropriate successful moment, never a notification.

## 13. Onboarding, authentication, and permissions

- **MOB-ONB-01 — Minimize upfront work.** Separate what is truly required before use from what can be taught or requested in context. Prefer previewing real value and just-in-time education to long introductory slides. Teach in context with rich tooltips, dialogs, or sheets rather than interruptive walkthroughs when possible.
- **MOB-ONB-02 — Value before action.** Explain the user benefit before asking for account creation, provider setup, or permission. Ask only at the moment the capability is needed.
- **MOB-ONB-03 — Minimal data.** Collect only critical registration information. Combine related fields, split genuinely long flows into sensible steps, and avoid both long scrolling forms and one trivial field per screen.
- **MOB-ONB-04 — Authentication.** Prefer Credential Manager, passkeys, SSO, biometrics, and autofill where appropriate. Sign-in is fast and unobtrusive; sign-up and sign-in may share a clear entry when the identity method supports both.
- **MOB-ONB-05 — Recovery.** Keep password/account recovery visible and empathetic. State requirements before submission, preserve non-sensitive progress, mask sensitive input, and never prefill secrets in recovery.
- **MOB-ONB-06 — Progress and escape.** Longer flows show unambiguous step/progress state, allow Skip or Sign in where the product permits, and preserve a clear resume checkpoint.
- **MOB-ONB-07 — Forms.** Group related fields, use max-width containment on large screens, keep the active input visible above the IME, and provide inline assistive validation focused on resolution rather than blame.
- **MOB-PERM-01 — Contextual permission.** Explain why a permission is required immediately before the system request. Do not bulk-request at launch or ask for permissions the current feature cannot justify.
- **MOB-PERM-02 — Denial.** A denial leaves a usable degraded path and clear recovery. Do not block the whole app for an optional permission.
- **MOB-PERM-03 — Least privilege.** Use Photo Picker rather than broad media access, approximate location unless precision is essential, one-time camera/microphone access where appropriate, and visible privacy indicators for active sensors.
- **MOB-ONB-08 — Cross-device handoff.** Mobile may complete account/provider setup initiated on TV. Make the originating TV state, transfer status, success, failure, expiration, and safe return path explicit.

## 14. Notifications and live updates

- **MOB-NOT-01 — Purpose.** Notifications are brief, timely, relevant, and valuable while the app is not in use. Never use them for advertising/cross-promotion, empty re-engagement, review requests, recoverable background errors, silent sync, greetings, or as the primary communication channel.
- **MOB-NOT-02 — Permission.** For non-exempt notifications, first show dismissible contextual UI explaining benefit and consequence. Do not open the system prompt after the user dismisses that explanation. Media-session exemptions do not justify unrelated notifications.
- **MOB-NOT-03 — Templates.** Use the platform template matching the content: standard, big text, big picture, progress, MediaStyle, MessagingStyle, or CallStyle. Do not build bespoke visuals when a system template communicates the same information.
- **MOB-NOT-04 — Content.** Titles put the most important information first, omit the app name, and target 30 characters or fewer. Supporting text avoids repetition and targets 40 characters or fewer. Imagery reinforces meaning. Set the notification color so Android 12+ derives the status-bar icon tint for foreground-service and media notifications, and use the large icon only when imagery reinforces meaning—circular for people, square otherwise.
- **MOB-NOT-05 — Actions.** Tapping opens the exact relevant destination with its back stack. Provide up to three useful expanded actions, direct reply only for short input, and no action that duplicates tapping the body.
- **MOB-NOT-06 — Channels.** Give each distinct notification type a user-configurable channel and appropriate predefined category. Choose importance conservatively: High only for immediate/time-critical action, Default for timely non-interruptive attention, Low for optional updates, and Min for nonessential status.
- **MOB-NOT-07 — Foreground services.** Accurately describe ongoing work and expose a Stop action when stoppable. Do not create a foreground service notification for work that cannot justify persistent user awareness.
- **MOB-NOT-08 — Lifecycle.** Group multiple notifications of the same type, keep children understandable alone, and remove stale notifications. Set public/private/secret lock-screen visibility from content sensitivity; Lamphaus account/provider details default to private or secret.
- **MOB-NOT-09 — Media.** Use MediaStyle with a valid media session and system transport controls. Artwork and metadata must remain safe for lock-screen/communal visibility settings.
- **MOB-LIVE-01 — Live updates.** Use Live Updates only for a user-initiated, finite, trackable experience with a clear endpoint, such as a delivery-like or active progress task. Do not use them for bundled multi-app information, recommendations, indefinite status, or bespoke animation/data structures.
- **MOB-LIVE-02 — Alerts.** Alert only for critical status changes and immediately show why; do not alert for small ETA/progress adjustments.
- **MOB-LIVE-03 — Progress.** A progress bar corresponds truthfully to remaining time/distance or clearly labeled discrete phases. Use the same timestamp/duration format in collapsed status and expanded card views.
- **MOB-LIVE-04 — Familiar templates.** Keep fields predictable for a use-case vertical so users can scan title, state, time, and destination consistently.

## 15. Playback, immersive mode, and picture-in-picture

- **PLY-IMM-01 — Appropriate immersion.** Hide system bars only when uninterrupted media genuinely benefits—video, games, images, books, presentation, or managed kiosk/enterprise use—not simply to gain space.
- **PLY-IMM-02 — Escape.** Never permanently hide system bars on a personal device. A simple tap during playback reveals playback controls and system bars; Back and system gestures remain predictable.
- **PLY-IMM-03 — Legibility.** Overlay text and controls on a sufficient scrim. Full-screen media keeps essential controls reachable, labeled, and out of gesture/cutout zones.
- **PLY-IMM-04 — Continuity.** Combine immersive playback with PiP and Cast where they preserve the user's experience. Preserve position, selected source, subtitle/audio state, and return destination.
- **PLY-PIP-01 — Video only.** PiP shows video with minimal or no app chrome. Hide ordinary controls while in PiP and use system media/PiP actions.
- **PLY-PIP-02 — Entry.** Enter PiP when leaving active playback only if continued watching matches user intent. Do not force PiP at an episode end or when it creates extra work to stop playback.
- **PLY-PIP-03 — One player.** Selecting new content while PiP is active reuses the existing playback activity/session and returns it fullscreen rather than launching a competing player.
- **PLY-PIP-04 — Smooth transition.** On Android 12+, use auto-enter for qualifying video playback and keep `sourceRectHint` synchronized with the visible video bounds. Disable seamless resizing for non-video content.
- **PLY-PIP-05 — Controls.** Maintain an active media session so play/pause/next/previous appear where applicable. Add custom actions only when essential and recognizable at PiP size.

## 16. TV baseline and mobile/TV boundary

These rules preserve the approved TV experience while making the overlap explicit. Exact TV token values below remain normative.

- **TV-FND-01 — Ten-foot context.** TV is content-first, dark-surface by default, readable from ten feet, and safe for shared-room viewing.
- **TV-FND-02 — Input.** Every TV flow works with D-pad, Back, Home, play/pause, and seek. Touch is never required and no on-screen Back button is shown.
- **TV-LAY-01 — Canvas.** Design against a `960×540dp` mdpi 16:9 reference, `58dp` horizontal safe margins, `32dp` top-navigation origin, `104dp` content origin, `20dp` rail gaps, and no critical UI near overscan edges.
- **TV-FOC-01 — Focus.** Exactly one actionable item visibly owns focus. Focus uses an inverse/high-contrast container or outline plus content/reveal change, never color alone. Directional traversal is spatial, rows restore prior focus, and focused scale never clips.
- **TV-FOC-02 — Approved treatment.** Cards use a `3dp` Lamphaus-blue outline, `1.02×` artwork scale, restrained `7dp`/28% blue halo, focused container `#E3E2E6`, focused content `#2F3033`, and `160ms` response. Titles, supporting labels, icons, settings toggles, and control labels all switch to the focused-content role on that pale surface. This treatment is TV-only.
- **TV-NAV-01 — Top navigation.** TV uses overscan-safe Home, Discover, Search, Library, and Settings/Profile navigation with explicit D-pad entry targets. It does not use mobile bottom navigation or a touch navigation rail.
- **TV-NAV-02 — Back.** Back reverses the latest layer, restores origin focus, never loops or gates ordinary exit behind confirmation, and eventually reaches Home then exits. Page-content Back first returns to the active top-navigation item according to the approved TV flow.
- **TV-NAV-03 — Text entry.** A TV text field can receive browse focus without opening the IME; Select enters edit mode and Back/IME action returns to browse mode while keeping field focus.
- **TV-NAV-04 — Cancel and destructive flows.** Do not gate Back behind confirmation. A visible Cancel action accompanies screens whose only other actions are confirming, destructive, purchasing, or otherwise committing.
- **TV-NAV-05 — Spatial traversal.** Prefer framework spatial navigation. Add explicit directional overrides only where tested default traversal fails; overrides form complete, non-trapping paths. Rows/categories traverse vertically and items within a row horizontally, with a straight D-pad path to every visible control and no crossing/nested focus hierarchy.
- **TV-NAV-06 — Back-stack construction.** Loading and splash states never enter the back stack. Signed-in Home is the fixed start destination, and deep links construct the same Back path a manual journey would have created.
- **TV-NAV-07 — Page return.** Returning from details or playback restores the originating item and row. Back from scrolled page content follows the approved scroll-to-top/active-navigation behavior before leaving the destination.
- **TV-CNT-01 — Content.** Home has a non-auto-rotating immersive feature region followed by continuation/provider rows. Details order title, metadata, primary action, summary, then episodes. Provider failures remain attached to their row.
- **TV-CNT-02 — Stable loading.** Loading preserves final row/card geometry. Empty and failed content does not steal focus from usable content or move the user's previously focused target unexpectedly.
- **TV-TOK-01 — Geometry.** Posters are `153×231dp`, landscape fallback is `256×144dp`, hero is `844×320dp`, settings menu is `268dp`, and settings content is `452dp` on the reference canvas.
- **TV-TOK-02 — Shape and surface.** Card/button/field/list shape is `4dp`; hero shape is `12dp`; selected navigation beam is `24×2dp`; the default action container is 10% white.
- **TV-MOT-01 — Approved motion.** Focus responds in `160ms`; focused labels lift `6dp` while fading in; the hero updates after `240ms` and crossfades in `220ms` with `1–1.25%` horizontal drift; library confirmation is `110ms` out plus `110ms` return; startup sweep is `480ms` once. Nothing auto-advances while the user reads. Remove animations makes all of these transitions instant.
- **TV-TYP-01 — Type.** TV uses Inter with the approved Material type ramp and short, safely wrapped copy. Text never scrolls horizontally.
- **TV-CLR-01 — Fixed scheme.** TV does not use wallpaper dynamic color. Its approved semantic dark roles are background `#1A1C1E`, surface `#121316`, primary `#A8C8FF`, on-primary `#003062`, secondary `#BDC7DC`, primary ink `#E3E2E6`, surface ink `#C7C6CA`, and muted ink `#C4C6CF`. Saturated color is never a large reading surface.
- **TV-ART-01 — Artwork.** Prefer `2:3` portrait posters. If unavailable, preserve landscape art at `16:9` rather than cropping it into a poster. Home-row labels appear for the focused item; grids keep labels visible. Ratings use a small neutral lower-poster scrim.

### Shared principles that overlap

| Shared on mobile and TV               | Mobile interpretation                                  | TV interpretation                                         |
| ------------------------------------- | ------------------------------------------------------ | --------------------------------------------------------- |
| Content first, calm Lamphaus identity | Touch-first Material 3 chrome, adaptive panes          | Ten-foot dark cinematic canvas, artwork-led focus         |
| Semantic roles and contrast           | Light, dark, dynamic, and contrast-aware schemes       | Fixed approved dark semantic scheme                       |
| Accessible names, states, and order   | TalkBack, Switch Access, keyboard, pointer, 48dp touch | D-pad focus, screen reader/state labels, overscan safety  |
| Adaptive content and stable state     | Window classes, rotation, folds, split screen          | 720p/1080p/4K scaling within 16:9 safe geometry           |
| Reduced motion                        | Ripple/Material transitions become instant as needed   | Focus, hero, beam, and confirmation motion become instant |
| Local loading/error recovery          | Pane/row/form-level recovery                           | Row/provider-level recovery without losing focus          |
| Privacy                               | Permission minimization and lock-screen sensitivity    | Shared-room masking and safe pairing/account surfaces     |
| Shared data, progress, and playback   | Touch ergonomics and mobile system integrations        | Remote ergonomics and focus restoration                   |

### Rules that must not cross platforms

- Do not apply TV overscan margins, fixed `960×540` geometry, D-pad focus scale/halo, top-navigation rail, dark-only theme, or ten-foot type sizing to mobile.
- Do not apply mobile bottom navigation, touch gestures, IME-on-focus, compact phone margins, dynamic wallpaper color, or phone dialogs/sheets directly to TV.
- Mobile may use visible Up/Close affordances; TV has no on-screen Back affordance.
- Mobile adapts navigation bar to rail/drawer from window size; TV uses the approved top navigation and spatial focus model.
- Shared business/data components may be reused. Platform UI components may share small visual primitives only when semantics, input, and geometry remain correct for both.

## 17. Quality gates

- **QA-01 — Rule mapping.** Every interface requirement changed by a feature maps to a Compose test, screenshot test, lint/static check, or named manual test.
- **QA-02 — Mobile matrix.** Test at `360dp` and `412dp` compact widths, representative medium and expanded widths, portrait and landscape, split screen, at least one fold/hinge posture where relevant, gesture and three-button navigation, light/dark/dynamic schemes, and at least three dynamic-color wallpapers.
- **QA-03 — Accessibility matrix.** Test `200%` font size, bold text/high contrast where supported, RTL, TalkBack, Switch Access, external keyboard, pointer, touch-target size, color contrast, remove animations, and logical traversal.
- **QA-04 — System UI.** Test status/navigation bars, IME, cutouts, gesture insets, edge-to-edge scrolling, predictive Back commit/cancel, process recreation, and state restoration.
- **QA-05 — Content states.** Verify loading, empty, partial, stale, offline, permission denied, authentication recovery, provider failure, missing art, long/localized copy, and large collections.
- **QA-06 — Playback.** Verify media-session controls, notifications/channels, lock-screen privacy, immersive reveal/exit, PiP enter/exit/source bounds, Cast handoff where available, subtitles, audio tracks, and playback return state.
- **QA-07 — TV matrix.** Test 720p, 1080p, and 4K, physical/emulator D-pad reachability for every control, Back paths with `adb shell input keyevent`, focus restoration, overscan safety, reduced motion, and text-field browse/edit behavior. `TV-NAV-03` remains covered by `TvNavigationBehaviorTest` plus the named emulator check **TV text-field browse/edit flow**.
- **QA-08 — Performance.** Check lazy collection behavior, recomposition, image loading/cropping, blur/gradient fallbacks, animation smoothness, startup, and resource use on a representative low-end device.

## 18. Review checklist

Before approving mobile UI work, answer yes to all applicable questions:

- Is the UI driven by lifecycle-aware immutable state with recoverable loading/error states?
- Does it use the correct compact/medium/expanded composition without orientation locking or stretched content?
- Are system bars, IME, gestures, cutouts, and hinges handled edge-to-edge?
- Does compact use the labeled Material `NavigationBar` and larger width use the labeled rail?
- Are Back, Up, Close, predictive Back, deep links, and return-state behavior distinct and correct?
- Are color and type supplied by semantic Material theme roles with light/dark/dynamic fallbacks?
- Do all text, controls, images, grids, and custom layouts have explicit scaling/reflow behavior?
- Are all targets at least `48×48dp`, all semantics accurate, and every gesture available another way?
- Does the feature work at 200% font scale, RTL, TalkBack, Switch Access, keyboard, and reduced motion?
- Are settings, onboarding, permission, notification, immersive, and PiP patterns used only for their intended purpose?
- Does failure stay local, privacy remain visible, and no provider-sensitive data leak into UI outside its intended surface?
- Have platform-specific mobile and TV rules remained separate?

## 19. Source baseline

This consolidation is based on the Android guidance supplied with the request and these repository sources: `PRODUCT.md`, `DESIGN.md`, `docs/ANDROID_DESIGN_REQUIREMENTS.md`, and `docs/TV_DESIGN_TOKENS.md`.

Primary external guidance:

- Android mobile design: <https://developer.android.com/design/ui/mobile>
- Accessibility: <https://developer.android.com/design/ui/mobile/guides/foundations/accessibility>
- Adaptive apps: <https://developer.android.com/develop/adaptive-apps>
- Edge to edge: <https://developer.android.com/design/ui/mobile/guides/layout-and-content/edge-to-edge>
- Material 3: <https://m3.material.io/>
- Android architecture: <https://developer.android.com/topic/architecture>
- Predictive Back: <https://developer.android.com/guide/navigation/predictive-back-gesture>
- Picture-in-picture: <https://developer.android.com/develop/ui/views/picture-in-picture>
- TV design: <https://developer.android.com/design/ui/tv>
