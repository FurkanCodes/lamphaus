package com.lamphaus.core.data.playback

import com.lamphaus.core.model.PlaybackSegment
import com.lamphaus.core.model.PlaybackSegmentType
import org.junit.Assert.assertEquals
import org.junit.Test

class IntroDbSegmentsTest {
    @Test
    fun `series response maps intro, recap, and outro`() {
        val response = IntroDbSegmentsResponse(
            intro = IntroDbSegment(startSeconds = 314.5, endSeconds = 331.0),
            recap = IntroDbSegment(startMillis = 0, endMillis = 40_000),
            outro = IntroDbSegment(startMillis = 2_800_000, endMillis = 2_860_000),
        )
        assertEquals(
            listOf(
                PlaybackSegment(PlaybackSegmentType.INTRO, 314_500, 331_000),
                PlaybackSegment(PlaybackSegmentType.RECAP, 0, 40_000),
                PlaybackSegment(PlaybackSegmentType.ENDING, 2_800_000, 2_860_000),
            ),
            response.toSegments(movie = false),
        )
    }

    @Test
    fun `movie credits are trimmed so they never swallow the post-credits scene`() {
        val response = IntroDbSegmentsResponse(
            outro = IntroDbSegment(startMillis = 8_155_000, endMillis = 8_574_000),
            postCredits = IntroDbSegment(startMillis = 8_400_000, endMillis = 8_460_000),
        )
        assertEquals(
            listOf(
                PlaybackSegment(PlaybackSegmentType.ENDING, 8_155_000, 8_400_000),
                PlaybackSegment(PlaybackSegmentType.POST_CREDITS, 8_400_000, 8_460_000),
            ),
            response.toSegments(movie = true),
        )
    }

    @Test
    fun `invalid segments are dropped`() {
        val response = IntroDbSegmentsResponse(intro = IntroDbSegment(startMillis = 50, endMillis = 50))
        assertEquals(emptyList<PlaybackSegment>(), response.toSegments(movie = false))
    }

    @Test
    fun `merge fills each category from the first source that has it`() {
        val introDbIntro = PlaybackSegment(PlaybackSegmentType.INTRO, 60_000, 90_000)
        val fallbackIntro = PlaybackSegment(PlaybackSegmentType.INTRO, 61_000, 91_000)
        val fallbackCredits = PlaybackSegment(PlaybackSegmentType.ENDING, 2_800_000)
        assertEquals(
            listOf(introDbIntro, fallbackCredits),
            mergeSegmentsByPriority(listOf(introDbIntro), listOf(fallbackIntro, fallbackCredits)),
        )
    }
}
