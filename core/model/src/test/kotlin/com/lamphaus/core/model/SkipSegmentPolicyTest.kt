package com.lamphaus.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Nuvio's SkipIntroVisibilityRules and skip targets (QA-06). */
class SkipSegmentPolicyTest {
    private val intro = PlaybackSegment(PlaybackSegmentType.INTRO, 60_000, 90_000)
    private val recap = PlaybackSegment(PlaybackSegmentType.RECAP, 0, 45_000)
    private val ending = PlaybackSegment(PlaybackSegmentType.ENDING, 1_700_000, 1_800_000)
    private val scene = PlaybackSegment(PlaybackSegmentType.POST_CREDITS, 1_800_000, 1_860_000)

    @Test
    fun `active segment contains the position with an exclusive end`() {
        val segments = listOf(recap, intro, ending)
        assertEquals(recap, SkipSegmentPolicy.activeSegment(segments, 10_000, 1_900_000))
        assertEquals(intro, SkipSegmentPolicy.activeSegment(segments, 60_000, 1_900_000))
        assertNull(SkipSegmentPolicy.activeSegment(segments, 90_000, 1_900_000))
    }

    @Test
    fun `post-credits scenes are never active`() {
        assertNull(SkipSegmentPolicy.activeSegment(listOf(scene), 1_810_000, 1_900_000))
    }

    @Test
    fun `an open ending is active until the duration`() {
        val open = PlaybackSegment(PlaybackSegmentType.ENDING, 1_700_000)
        assertEquals(open, SkipSegmentPolicy.activeSegment(listOf(open), 1_899_999, 1_900_000))
    }

    @Test
    fun `skipping credits lands on an explicit post-credits scene`() {
        assertEquals(1_800_000L, SkipSegmentPolicy.skipTarget(ending, listOf(ending, scene), 1_900_000))
        assertTrue(SkipSegmentPolicy.postCreditsSceneAfter(ending, listOf(ending, scene), 1_900_000) != null)
    }

    @Test
    fun `a long tail after the credits counts as a post-credits scene`() {
        val found = SkipSegmentPolicy.postCreditsSceneAfter(ending, listOf(ending), 1_900_000)
        assertEquals(1_800_000L, found?.startMillis)
    }

    @Test
    fun `credits that end at the file end have no post-credits scene`() {
        val closing = PlaybackSegment(PlaybackSegmentType.ENDING, 1_700_000, 1_897_000)
        assertNull(SkipSegmentPolicy.postCreditsSceneAfter(closing, listOf(closing), 1_900_000))
        assertEquals(1_897_000L, SkipSegmentPolicy.skipTarget(closing, listOf(closing), 1_900_000))
    }

    @Test
    fun `an open ending skips to the end of the file`() {
        val open = PlaybackSegment(PlaybackSegmentType.ENDING, 1_700_000)
        assertEquals(1_900_000L, SkipSegmentPolicy.skipTarget(open, listOf(open), 1_900_000))
    }

    @Test
    fun `intro skips to its end`() {
        assertEquals(90_000L, SkipSegmentPolicy.skipTarget(intro, listOf(intro), 1_900_000))
    }

    @Test
    fun `button visibility follows dismissal, auto-hide, and the chrome`() {
        assertTrue(SkipSegmentPolicy.isButtonVisible(true, dismissed = false, controlsVisible = false, autoHidden = false))
        assertFalse(SkipSegmentPolicy.isButtonVisible(true, dismissed = true, controlsVisible = false, autoHidden = false))
        assertFalse(SkipSegmentPolicy.isButtonVisible(true, dismissed = false, controlsVisible = false, autoHidden = true))
        assertTrue(SkipSegmentPolicy.isButtonVisible(true, dismissed = true, controlsVisible = true, autoHidden = true))
        assertFalse(SkipSegmentPolicy.isButtonVisible(false, dismissed = false, controlsVisible = true, autoHidden = false))
    }
}
