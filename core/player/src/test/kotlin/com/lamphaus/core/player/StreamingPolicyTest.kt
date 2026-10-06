package com.lamphaus.core.player

import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import com.lamphaus.core.player.StreamingPolicy.RecoveryStep
import com.lamphaus.core.player.StreamingPolicy.StallDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingPolicyTest {

    @Test
    fun `QA-05 dead links fail fast, timeouts back off, other errors take Media3's delay`() {
        listOf(400, 401, 403, 404, 410).forEach {
            assertNull(StreamingPolicy.retryDelayMillis(1, it, timedOut = false, mediaDelayMillis = 0L))
        }
        assertEquals(750L, StreamingPolicy.retryDelayMillis(1, null, timedOut = true, mediaDelayMillis = 0L))
        assertEquals(1_500L, StreamingPolicy.retryDelayMillis(2, null, timedOut = true, mediaDelayMillis = 1_000L))
        assertEquals(3_000L, StreamingPolicy.retryDelayMillis(5, null, timedOut = true, mediaDelayMillis = 4_000L))
        // The first non-timeout retry is immediate, as Media3 decides.
        assertEquals(0L, StreamingPolicy.retryDelayMillis(1, 503, timedOut = false, mediaDelayMillis = 0L))
        assertNull(StreamingPolicy.retryDelayMillis(1, null, timedOut = false, mediaDelayMillis = null))
    }

    @Test
    fun `SHR-PROD-04 source, parsing, decoder, and state errors recover, dead links do not`() {
        assertTrue(StreamingPolicy.isRecoverable(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, null, false))
        assertTrue(StreamingPolicy.isRecoverable(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 503, false))
        assertFalse(StreamingPolicy.isRecoverable(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 403, false))
        assertTrue(StreamingPolicy.isRecoverable(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, null, false))
        assertTrue(StreamingPolicy.isRecoverable(PlaybackException.ERROR_CODE_DECODING_FAILED, null, false))
        assertTrue(StreamingPolicy.isRecoverable(PlaybackException.ERROR_CODE_UNSPECIFIED, null, true))
        assertFalse(StreamingPolicy.isRecoverable(PlaybackException.ERROR_CODE_UNSPECIFIED, null, false))
        assertFalse(StreamingPolicy.isRecoverable(PlaybackException.ERROR_CODE_DRM_UNSPECIFIED, null, false))
    }

    @Test
    fun `SHR-PROD-04 startup reloads twice, playback re-prepares then reloads`() {
        assertEquals(RecoveryStep.RELOAD, StreamingPolicy.recoveryStep(firstFrameRendered = false, attempt = 0))
        assertEquals(RecoveryStep.RELOAD, StreamingPolicy.recoveryStep(firstFrameRendered = false, attempt = 1))
        assertNull(StreamingPolicy.recoveryStep(firstFrameRendered = false, attempt = 2))
        assertEquals(RecoveryStep.REPREPARE, StreamingPolicy.recoveryStep(firstFrameRendered = true, attempt = 0))
        assertEquals(RecoveryStep.RELOAD, StreamingPolicy.recoveryStep(firstFrameRendered = true, attempt = 1))
        assertNull(StreamingPolicy.recoveryStep(firstFrameRendered = true, attempt = 2))
    }

    @Test
    fun `QA-05 a load stuck for 15 s skips just past the buffered edge`() {
        assertEquals(StallDecision.Wait, StreamingPolicy.stallDecision(60_000, 50_000, 3_600_000, 14_999))
        assertEquals(StallDecision.Seek(60_250), StreamingPolicy.stallDecision(60_000, 50_000, 3_600_000, 15_000))
        // Nothing ahead of the playhead, or no known duration: a seek cannot help.
        assertEquals(StallDecision.GiveUp, StreamingPolicy.stallDecision(50_000, 50_000, 3_600_000, 20_000))
        assertEquals(StallDecision.GiveUp, StreamingPolicy.stallDecision(60_000, 50_000, C.TIME_UNSET, 20_000))
        // The target never passes the end.
        assertEquals(StallDecision.Seek(99_999), StreamingPolicy.stallDecision(99_900, 90_000, 100_000, 15_000))
    }
}
