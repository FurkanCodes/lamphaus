package com.lamphaus.core.player

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingPolicyTest {
    private val mib = 1024L * 1024L

    @Test
    fun `QA-08 buffer target scales with the heap and stays bounded`() {
        assertEquals((384 * mib * 0.30).toLong().toInt(), StreamingPolicy.bufferPlan(384 * mib).targetBufferBytes)
        assertEquals((48 * mib).toInt(), StreamingPolicy.bufferPlan(96 * mib).targetBufferBytes)
        assertEquals((160 * mib).toInt(), StreamingPolicy.bufferPlan(2_048 * mib).targetBufferBytes)
    }

    @Test
    fun `QA-05 transient load failures back off, dead links fail fast`() {
        assertEquals(750L, StreamingPolicy.retryDelayMillis(errorCount = 1, httpStatus = null))
        assertEquals(3_000L, StreamingPolicy.retryDelayMillis(errorCount = 9, httpStatus = 503))
        listOf(400, 401, 403, 404, 410).forEach { assertNull(StreamingPolicy.retryDelayMillis(1, it)) }
    }

    @Test
    fun `QA-05 only network errors auto retry, at most twice`() {
        val network = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
        assertTrue(StreamingPolicy.shouldAutoRetry(network, httpStatus = null, attempt = 0))
        assertTrue(StreamingPolicy.shouldAutoRetry(network, httpStatus = null, attempt = 1))
        assertFalse(StreamingPolicy.shouldAutoRetry(network, httpStatus = null, attempt = 2))
        assertFalse(StreamingPolicy.shouldAutoRetry(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 403, 0))
        assertFalse(StreamingPolicy.shouldAutoRetry(PlaybackException.ERROR_CODE_DECODING_FAILED, null, 0))
    }
}
