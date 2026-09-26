package com.lamphaus.app.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackUriPolicyTest {
    private val clip = "file:///data/user/0/com.lamphaus.app.benchmark/files/benchmark.mp4"

    @Test
    fun `production accepts only https`() {
        assertTrue(isAllowedPlaybackUri("https://example.test/video.mp4", debug = false, benchmarkClipUri = null))
        assertFalse(isAllowedPlaybackUri("http://localhost/video.mp4", debug = false, benchmarkClipUri = null))
        assertFalse(isAllowedPlaybackUri(clip, debug = false, benchmarkClipUri = null))
    }

    @Test
    fun `benchmark builds accept only their own clip`() {
        assertTrue(isAllowedPlaybackUri(clip, debug = false, benchmarkClipUri = clip))
        assertFalse(isAllowedPlaybackUri("file:///sdcard/other.mp4", debug = false, benchmarkClipUri = clip))
    }

    @Test
    fun `debug builds accept loopback http`() {
        assertTrue(isAllowedPlaybackUri("http://10.0.2.2:8080/v.mp4", debug = true, benchmarkClipUri = null))
        assertFalse(isAllowedPlaybackUri("http://example.test/v.mp4", debug = true, benchmarkClipUri = null))
    }
}
