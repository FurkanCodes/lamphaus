package com.lamphaus.core.player

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownmixTest {
    @Test
    fun `PLY-AUD-01 every supported input folds to every target layout`() {
        Downmix.targetChannelCounts.forEach { target ->
            Downmix.supportedInputChannelCounts.forEach { channels ->
                assertEquals(channels * target, Downmix.coefficients(channels, target).size)
            }
        }
    }

    @Test
    fun `stereo passes through untouched`() {
        assertArrayEquals(floatArrayOf(1f, 0f, 0f, 1f), Downmix.coefficients(2, 2), 0f)
    }

    @Test
    fun `5_1 to stereo keeps dialogue centred and surrounds on their side, as before`() {
        val gains = Downmix.coefficients(6, 2)
        // Centre (input 2) feeds both sides equally at -3 dB.
        assertEquals(0.7071f, gains[4], 0f)
        assertEquals(gains[4], gains[5], 0f)
        // LFE (input 3) at -6 dB; left surround (input 4) only on the left.
        assertEquals(0.5f, gains[6], 0f)
        assertEquals(0.7071f, gains[8], 0f)
        assertEquals(0f, gains[9], 0f)
    }

    @Test
    fun `7_1 to 5_1 folds the side speakers into the rear`() {
        val gains = Downmix.coefficients(8, 6)
        // Input 6 (side left) goes to output 4 (rear left); input 4 (rear left) stays there.
        assertEquals(0.7071f, gains[6 * 6 + 4], 0f)
        assertEquals(1f, gains[4 * 6 + 4], 0f)
        // Centre and LFE keep their own speakers.
        assertEquals(1f, gains[2 * 6 + 2], 0f)
        assertEquals(1f, gains[3 * 6 + 3], 0f)
    }

    @Test
    fun `without keeping the volume no output channel can clip`() {
        val gains = Downmix.coefficients(8, 2, keepVolume = false)
        (0 until 2).forEach { output ->
            val sum = (0 until 8).sumOf { input -> gains[input * 2 + output].toDouble() }
            assertTrue("output $output sums to $sum", sum <= 1.0 + 1e-6)
        }
        assertTrue(Downmix.coefficients(8, 2, keepVolume = true)[0] > gains[0])
    }
}
