package com.lamphaus.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Nuvio-accurate ending rule: exact ENDING timestamps win over the fallback,
 * the fallback uses only the selected threshold mode, and unknown duration
 * disables the fallback without blocking exact triggers.
 */
class NextEpisodePolicyTest {
    private fun ending(start: Long, end: Long? = null) =
        PlaybackSegment(type = PlaybackSegmentType.ENDING, startMillis = start, endMillis = end)

    private fun shows(
        positionMillis: Long,
        durationMillis: Long,
        segments: List<PlaybackSegment> = emptyList(),
        mode: NextEpisodeThresholdMode = NextEpisodeThresholdMode.PERCENTAGE,
        percent: Float = 98f,
        minutes: Float = 2f,
    ): Boolean = NextEpisodePolicy.shouldShowCard(
        positionMillis = positionMillis,
        durationMillis = durationMillis,
        segments = segments,
        thresholdMode = mode,
        thresholdPercent = percent,
        thresholdMinutesBeforeEnd = minutes,
    )

    @Test
    fun `exact ending start triggers the card`() {
        val segments = listOf(ending(start = 1_700_000, end = 1_800_000))

        assertFalse(shows(1_699_999, 1_900_000, segments))
        assertTrue(shows(1_700_000, 1_900_000, segments))
    }

    @Test
    fun `card stays available after the ending interval ends`() {
        val segments = listOf(ending(start = 1_700_000, end = 1_800_000))

        assertTrue(shows(1_850_000, 1_900_000, segments))
    }

    @Test
    fun `earliest ending interval of several controls the trigger`() {
        val segments = listOf(
            ending(start = 1_700_000, end = 1_750_000),
            PlaybackSegment(
                type = PlaybackSegmentType.INTRO,
                startMillis = 0,
                endMillis = 30_000,
            ),
            ending(start = 1_650_000, end = 1_690_000),
        )

        assertFalse(shows(1_649_999, 1_900_000, segments))
        assertTrue(shows(1_650_000, 1_900_000, segments))
    }

    @Test
    fun `ending timestamps trigger without a known duration`() {
        assertTrue(shows(1_700_000, 0, listOf(ending(start = 1_700_000))))
    }

    @Test
    fun `percentage fallback uses the selected threshold`() {
        assertTrue(shows(980_000, 1_000_000))
        assertFalse(shows(979_999, 1_000_000))
    }

    @Test
    fun `percentage fallback clamps out-of-range preferences`() {
        // 50% clamps to 97% → threshold 970_000.
        assertTrue(shows(970_000, 1_000_000, percent = 50f))
        assertFalse(shows(969_999, 1_000_000, percent = 50f))
        // 120% clamps to 99.5% → threshold 995_000.
        assertTrue(shows(995_000, 1_000_000, percent = 120f))
        assertFalse(shows(994_999, 1_000_000, percent = 120f))
    }

    @Test
    fun `minutes fallback uses remaining playback time`() {
        val mode = NextEpisodeThresholdMode.MINUTES_BEFORE_END
        assertTrue(shows(1_780_000, 1_900_000, mode = mode))
        assertFalse(shows(1_779_999, 1_900_000, mode = mode))
    }

    @Test
    fun `minutes fallback clamps out-of-range preferences`() {
        val mode = NextEpisodeThresholdMode.MINUTES_BEFORE_END
        // 10 minutes clamps to 3.5 → a 210_000 ms window.
        assertTrue(shows(1_690_000, 1_900_000, mode = mode, minutes = 10f))
        assertFalse(shows(1_689_999, 1_900_000, mode = mode, minutes = 10f))
    }

    @Test
    fun `unknown duration disables the fallback`() {
        assertFalse(shows(999_999, 0))
        assertFalse(shows(500_000, -1))
    }

    @Test
    fun `clamp helpers respect the documented bounds`() {
        assertEquals(97f, NextEpisodePolicy.clampedPercent(0f))
        assertEquals(99.5f, NextEpisodePolicy.clampedPercent(200f))
        assertEquals(98.5f, NextEpisodePolicy.clampedPercent(98.5f))
        assertEquals(1f, NextEpisodePolicy.clampedMinutesBeforeEnd(0f))
        assertEquals(3.5f, NextEpisodePolicy.clampedMinutesBeforeEnd(9f))
    }
}
