package com.lamphaus.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletionPolicyTest {
    @Test
    fun `QA-01 natural end completes even without duration`() {
        assertTrue(
            CompletionPolicy.isComplete(
                positionMillis = 0,
                durationMillis = 0,
                naturalEnd = true,
            ),
        )
    }

    @Test
    fun `QA-01 earliest valid credits timestamp wins`() {
        val segments = listOf(
            PlaybackSegment(PlaybackSegmentType.ENDING, startMillis = 95_000),
            PlaybackSegment(PlaybackSegmentType.ENDING, startMillis = 90_000),
        )

        assertFalse(CompletionPolicy.isComplete(89_999, 100_000, false, segments))
        assertTrue(CompletionPolicy.isComplete(90_000, 100_000, false, segments))
    }

    @Test
    fun `QA-01 fallback completes at ninety percent`() {
        assertFalse(CompletionPolicy.isComplete(89_999, 100_000, false))
        assertTrue(CompletionPolicy.isComplete(90_000, 100_000, false))
    }

    @Test
    fun `QA-01 invalid credits timestamp falls back to fraction`() {
        val invalid = listOf(PlaybackSegment(PlaybackSegmentType.ENDING, startMillis = 120_000))

        assertTrue(CompletionPolicy.isComplete(90_000, 100_000, false, invalid))
    }
}
