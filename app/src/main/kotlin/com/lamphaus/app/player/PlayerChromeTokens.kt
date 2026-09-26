package com.lamphaus.app.player

import androidx.compose.ui.unit.dp

/**
 * Named player chrome geometry and timing (PLY-CHR). Icon-only controls keep a
 * 48dp touch target on mobile while the glyph stays smaller (MOB-A11Y-04), and
 * the TV row uses the approved ten-foot container size (TV-LAY-01).
 */
internal object PlayerChromeTokens {
    val ControlContainer = 48.dp
    val ControlGlyph = 24.dp
    val TvControlContainer = 52.dp
    val TvControlGlyph = 28.dp
    val ControlGap = 8.dp
    val AutoHideMillis = 4_000L

    /**
     * Buffering must persist this long before the compact indicator appears, so
     * an in-buffer seek or a sub-second stall never flashes chrome
     * (SHR-PROD-02, TV-MOT-01).
     */
    const val RebufferIndicatorDelayMillis = 600L

    /** Longer than the engine's automatic re-prepare, so a recovered error never flashes. */
    const val ErrorRevealDelayMillis = 2_500L

    /** Sustained TV rebuffering during clean viewing yields to the artwork surface (TV-CNT-02). */
    const val RebufferSurfaceDelayMillis = 1_500L

    /** Subtitles lift above the chrome by this fraction of the video frame (PLY-IMM-03). */
    const val SubtitleLiftFraction = 0.10f
}
