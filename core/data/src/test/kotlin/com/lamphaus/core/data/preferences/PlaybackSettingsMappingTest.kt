package com.lamphaus.core.data.preferences

import com.lamphaus.core.model.NextEpisodeThresholdMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The DataStore read mapping: older payloads (missing keys) fall back to the
 * shipped defaults, unknown mode strings degrade to PERCENTAGE, and stored
 * threshold floats are defensively clamped.
 */
class PlaybackSettingsMappingTest {

    @Test
    fun `missing keys fall back to shipped defaults`() {
        val settings = playbackSettingsFromKeys(
            skipIntro = null,
            skipEnding = null,
            nextEpisode = null,
            thresholdMode = null,
            thresholdPercent = null,
            thresholdMinutes = null,
        )

        assertTrue(settings.skipIntroEnabled)
        assertTrue(settings.skipEndingEnabled)
        assertTrue(settings.nextEpisodeEnabled)
        assertEquals(NextEpisodeThresholdMode.PERCENTAGE, settings.nextEpisodeThresholdMode)
        assertEquals(98f, settings.nextEpisodeThresholdPercent)
        assertEquals(2f, settings.nextEpisodeThresholdMinutesBeforeEnd)
    }

    @Test
    fun `unknown mode strings degrade to percentage`() {
        val unknown = playbackSettingsFromKeys(true, true, true, "SOMETING", 98f, 2f)
        assertEquals(NextEpisodeThresholdMode.PERCENTAGE, unknown.nextEpisodeThresholdMode)

        val known = playbackSettingsFromKeys(
            true, true, true, "MINUTES_BEFORE_END", 98f, 2f,
        )
        assertEquals(NextEpisodeThresholdMode.MINUTES_BEFORE_END, known.nextEpisodeThresholdMode)
    }

    @Test
    fun `stored values are preserved`() {
        val settings = playbackSettingsFromKeys(false, false, false, "MINUTES_BEFORE_END", 99f, 1.5f)

        assertFalse(settings.skipIntroEnabled)
        assertFalse(settings.skipEndingEnabled)
        assertFalse(settings.nextEpisodeEnabled)
        assertEquals(NextEpisodeThresholdMode.MINUTES_BEFORE_END, settings.nextEpisodeThresholdMode)
        assertEquals(99f, settings.nextEpisodeThresholdPercent)
        assertEquals(1.5f, settings.nextEpisodeThresholdMinutesBeforeEnd)
    }

    @Test
    fun `out-of-range stored values are defensively clamped`() {
        val clamped = playbackSettingsFromKeys(true, true, true, "PERCENTAGE", 120f, 0f)

        assertEquals(99.5f, clamped.nextEpisodeThresholdPercent)
        assertEquals(1f, clamped.nextEpisodeThresholdMinutesBeforeEnd)
    }
}
