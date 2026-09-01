package com.lamphaus.app.mobile

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Lamphaus Mobile tokens. Single source of truth for the dark streaming UI;
 * components consume these semantic values instead of scattering visual constants.
 */
internal object MobileTokens {
    val ink = Color(0xFF08090D) // app background, near-black
    val surface = Color(0xFF101218) // cards, fields, bars
    val surfaceRaised = Color(0xFF181B23) // raised rows, inputs, episode thumbs
    val hairline = Color(0x14FFFFFF) // dividers, borders
    val textPrimary = Color(0xFFF2F4F8)
    val textMuted = Color(0xFF9AA1AF)
    val accent = Color(0xFFA8C8FF) // instrument blue, matches TV beam (TV-CLR-01)

    val radiusCard = 8.dp // posters, thumbnails
    val radiusResume = 12.dp // continue-watching landscape cards
    val radiusField = 12.dp
    val radiusSection = 16.dp // settings cards
    val spacingScreen = 16.dp
    val sectionGap = 28.dp
}
