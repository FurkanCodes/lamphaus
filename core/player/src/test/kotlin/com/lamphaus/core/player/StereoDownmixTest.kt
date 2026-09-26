package com.lamphaus.core.player

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class StereoDownmixTest {
    @Test
    fun `every supported layout folds to two outputs`() {
        StereoDownmix.supportedChannelCounts.forEach { channels ->
            assertEquals(channels * 2, StereoDownmix.coefficients(channels).size)
        }
    }

    @Test
    fun `stereo passes through untouched`() {
        assertArrayEquals(floatArrayOf(1f, 0f, 0f, 1f), StereoDownmix.coefficients(2), 0f)
    }

    @Test
    fun `5_1 keeps dialogue centred and surrounds on their side`() {
        val gains = StereoDownmix.coefficients(6)
        // Centre (input 2) feeds both sides equally.
        assertEquals(gains[4], gains[5], 0f)
        // Left surround (input 4) feeds only the left output.
        assertEquals(0f, gains[9], 0f)
    }
}
