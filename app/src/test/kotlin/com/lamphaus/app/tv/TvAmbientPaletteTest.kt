package com.lamphaus.app.tv

import android.annotation.SuppressLint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.color.utilities.Hct
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@SuppressLint("RestrictedApi")
class TvAmbientPaletteTest {
    private val background = Color(0xFF1A1C1E)
    private val surface = Color(0xFF121316)

    // Pure red, neon green, deep blue, hot magenta, warm orange.
    private val chromaticSeeds = listOf(0xFFFF0000, 0xFF39FF14, 0xFF0A1A8C, 0xFFFF00CC, 0xFFFF8A00)
        .map(Long::toInt)

    @Test
    fun `SHR-PROD-08 the artwork accent keeps focus-ring contrast on dark surfaces`() {
        chromaticSeeds.forEach { seed ->
            val accent = checkNotNull(tvAmbientPalette(seed)) { seed.hex }.accent
            assertTrue("seed ${seed.hex}", contrast(accent, background) >= 3.0)
            assertTrue("seed ${seed.hex}", contrast(accent, surface) >= 3.0)
        }
    }

    @Test
    fun `SHR-PROD-03 saturated artwork is toned and chroma-capped`() {
        chromaticSeeds.forEach { seed ->
            val palette = checkNotNull(tvAmbientPalette(seed)) { seed.hex }
            val accent = Hct.fromInt(palette.accent.toArgb())
            val container = Hct.fromInt(palette.accentContainer.toArgb())
            assertEquals("seed ${seed.hex}", TvAmbientTokens.accentTone, accent.tone, 1.0)
            assertEquals("seed ${seed.hex}", TvAmbientTokens.accentContainerTone, container.tone, 1.0)
            assertTrue("seed ${seed.hex}", accent.chroma <= TvAmbientTokens.accentMaxChroma + 1.0)
            assertTrue("seed ${seed.hex}", container.chroma <= TvAmbientTokens.accentContainerMaxChroma + 1.0)
        }
    }

    @Test
    fun `near-grey artwork keeps the instrument-blue defaults`() {
        assertNull(tvAmbientPalette(0xFF808080.toInt()))
        assertNull(tvAmbientPalette(0xFF2A2B2D.toInt()))
    }

    private val Int.hex get() = "%08X".format(this)

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun luminance(color: Color): Double {
        fun channel(c: Float): Double = if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }
}
