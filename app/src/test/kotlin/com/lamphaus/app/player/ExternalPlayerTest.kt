package com.lamphaus.app.player

import com.lamphaus.core.model.PlaybackSegment
import com.lamphaus.core.model.PlaybackSegmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalPlayerTest {

    @Test
    fun `PLY-EXT-01 MX Player and Just Player report position, duration, and the end`() {
        val result = ExternalPlayerResults.parse(true, null, mapOf("position" to 61_000, "duration" to 120_000, "end_by" to "user"))
        assertEquals(ExternalPlaybackResult(61_000, 120_000, completed = false), result)
        // A completion that resets the position still counts as watched.
        val ended = ExternalPlayerResults.parse(true, null, mapOf("position" to 0, "duration" to 120_000, "end_by" to "playback_completion"))
        assertEquals(ExternalPlaybackResult(120_000, 120_000, completed = true), ended)
    }

    @Test
    fun `PLY-EXT-01 VLC reports long positions, vanilla mpv nothing at the end`() {
        assertEquals(
            ExternalPlaybackResult(42_000L, null, completed = false),
            ExternalPlayerResults.parse(false, null, mapOf("extra_position" to 42_000L, "extra_duration" to -1L)),
        )
        assertEquals(
            ExternalPlaybackResult(0, null, completed = true),
            ExternalPlayerResults.parse(true, "is.xyz.mpv.MPVActivity.result", emptyMap()),
        )
        // Nothing usable: nothing is saved.
        assertNull(ExternalPlayerResults.parse(false, null, emptyMap()))
    }

    @Test
    fun `PLY-EXT-01 skip timestamps travel as seconds, post-credits scenes never`() {
        val json = ExternalPlayerContract.skipSegmentsJson(
            listOf(
                PlaybackSegment(PlaybackSegmentType.INTRO, 12_000, 84_500),
                PlaybackSegment(PlaybackSegmentType.ENDING, 3_000_000, 3_100_000),
                PlaybackSegment(PlaybackSegmentType.POST_CREDITS, 3_100_000, null),
            ),
        )
        assertEquals(
            "[{\"type\":\"intro\",\"start\":12.0,\"end\":84.5}, {\"type\":\"outro\",\"start\":3000.0,\"end\":3100.0}]",
            json,
        )
    }
}
