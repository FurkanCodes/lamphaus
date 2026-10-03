package com.lamphaus.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** SHR-PROD-08 / MOB-CLR-06: content accents are contrast-clamped before use. */
class ColorContrastTest {
    private val ink = 0xFF08090D.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    @Test
    fun blackOnWhiteIsTwentyOne() {
        assertEquals(21.0, contrastRatio(black, white), 0.01)
    }

    @Test
    fun readableAccentIsUnchanged() {
        val instrumentBlue = 0xFFA8C8FF.toInt()
        assertEquals(instrumentBlue, clampContrast(instrumentBlue, ink))
    }

    @Test
    fun darkAccentIsLiftedOnDarkSurface() {
        val deepRed = 0xFF5A0F12.toInt()
        val clamped = clampContrast(deepRed, ink, TEXT_CONTRAST)
        assertTrue(contrastRatio(clamped, ink) >= TEXT_CONTRAST)
        // Lifted toward white, so red stays the strongest channel.
        assertTrue((clamped shr 16 and 0xFF) > (clamped and 0xFF))
    }

    @Test
    fun nonTextThresholdNeedsLessLift() {
        val deepBlue = 0xFF0B1F4A.toInt()
        val text = clampContrast(deepBlue, ink, TEXT_CONTRAST)
        val nonText = clampContrast(deepBlue, ink, NON_TEXT_CONTRAST)
        assertTrue(contrastRatio(nonText, ink) >= NON_TEXT_CONTRAST)
        assertTrue(relativeLuminance(nonText) <= relativeLuminance(text))
    }

    @Test
    fun paleAccentIsDarkenedOnLightSurface() {
        val paleYellow = 0xFFFFF4B0.toInt()
        assertTrue(contrastRatio(clampContrast(paleYellow, white), white) >= TEXT_CONTRAST)
    }
}
