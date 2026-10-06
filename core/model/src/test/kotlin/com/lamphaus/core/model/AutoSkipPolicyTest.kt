package com.lamphaus.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSkipPolicyTest {
    private val all = PlaybackSettings(autoSkipIntro = true, autoSkipRecap = true, autoSkipOutro = true, autoSkipCredits = true)

    @Test
    fun `PLY-SKIP-01 each segment skips itself only when its switch is on`() {
        assertFalse(SkipSegmentPolicy.skipsAutomatically(PlaybackSegmentType.INTRO, isEpisode = true, PlaybackSettings()))
        assertTrue(SkipSegmentPolicy.skipsAutomatically(PlaybackSegmentType.INTRO, isEpisode = true, all))
        assertTrue(SkipSegmentPolicy.skipsAutomatically(PlaybackSegmentType.RECAP, isEpisode = true, all))
        assertFalse(SkipSegmentPolicy.skipsAutomatically(PlaybackSegmentType.POST_CREDITS, isEpisode = false, all))
    }

    @Test
    fun `PLY-SKIP-01 an ending is an outro in a series and credits in a movie`() {
        val outroOnly = PlaybackSettings(autoSkipOutro = true)
        assertTrue(SkipSegmentPolicy.skipsAutomatically(PlaybackSegmentType.ENDING, isEpisode = true, outroOnly))
        assertFalse(SkipSegmentPolicy.skipsAutomatically(PlaybackSegmentType.ENDING, isEpisode = false, outroOnly))
        val creditsOnly = PlaybackSettings(autoSkipCredits = true)
        assertTrue(SkipSegmentPolicy.skipsAutomatically(PlaybackSegmentType.ENDING, isEpisode = false, creditsOnly))
    }

    @Test
    fun `PLY-SKIP-01 automatic skipping needs its skip button`() {
        assertFalse(SkipSegmentPolicy.skipsAutomatically(PlaybackSegmentType.INTRO, true, all.copy(skipIntroEnabled = false)))
        assertFalse(SkipSegmentPolicy.skipsAutomatically(PlaybackSegmentType.ENDING, true, all.copy(skipEndingEnabled = false)))
    }
}
