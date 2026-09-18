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
    val TvControlContainer = 56.dp
    val TvControlGlyph = 32.dp
    val ControlGap = 8.dp
    val AutoHideMillis = 4_000L

    /** Subtitles lift above the chrome by this fraction of the video frame (PLY-IMM-03). */
    const val SubtitleLiftFraction = 0.10f
}
