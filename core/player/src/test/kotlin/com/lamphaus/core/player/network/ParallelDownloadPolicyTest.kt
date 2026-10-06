package com.lamphaus.core.player.network

import androidx.media3.common.C
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParallelDownloadPolicyTest {
    private val mib = 1024L * 1024L

    @Test
    fun `PLY-NET-01 heap-bound devices cap chunks at 16 MiB`() {
        assertEquals(16 * mib, ParallelDownloadPolicy.chunkBytes(64 * 1024, maxHeapBytes = 384 * mib))
        assertEquals(64 * mib, ParallelDownloadPolicy.chunkBytes(64 * 1024, maxHeapBytes = 512 * mib))
        assertEquals(256 * 1024L, ParallelDownloadPolicy.chunkBytes(256, maxHeapBytes = 384 * mib))
    }

    @Test
    fun `PLY-NET-01 heap prefetch is one chunk per connection plus one, native follows the budget`() {
        assertEquals(3, ParallelDownloadPolicy.prefetchDepth(2, 16 * mib, nativeBudgetBytes = null, reservedBufferBytes = 0))
        // 1000 MB tier with a 500 MB sample buffer: 31 chunks fit, clamped to 4x connections.
        assertEquals(8, ParallelDownloadPolicy.prefetchDepth(2, 16 * mib, 1_000 * mib, 500 * mib))
        // A tight budget still keeps two chunks per connection.
        assertEquals(4, ParallelDownloadPolicy.prefetchDepth(2, 16 * mib, 200 * mib, 190 * mib))
        assertEquals(5, ParallelDownloadPolicy.sessionChunkCap(3, maxHeapBytes = 256 * mib))
        assertEquals(7, ParallelDownloadPolicy.sessionChunkCap(3, maxHeapBytes = 512 * mib))
        assertEquals(64 * mib, ParallelDownloadPolicy.overheadBytes(2, 16 * mib))
    }

    @Test
    fun `PLY-NET-01 rate limits honour Retry-After, otherwise back off exponentially`() {
        val noJitter = object : Random() {
            override fun nextBits(bitCount: Int): Int = 0
        }
        assertEquals(4_000L, ParallelDownloadPolicy.rateLimitWaitMillis(0, 0, 4_000L, noJitter))
        assertEquals(15_000L, ParallelDownloadPolicy.rateLimitWaitMillis(0, 0, 60_000L, noJitter))
        assertEquals(500L, ParallelDownloadPolicy.rateLimitWaitMillis(0, 0, null, noJitter))
        assertEquals(1_000L, ParallelDownloadPolicy.rateLimitWaitMillis(1, 0, null, noJitter))
        assertEquals(3_000L, ParallelDownloadPolicy.rateLimitWaitMillis(4, 0, null, noJitter))
        // Escalation raises the cap: 3 s doubled per level, never past 15 s.
        assertEquals(6_000L, ParallelDownloadPolicy.rateLimitWaitMillis(3, 1, null, noJitter))
        assertEquals(15_000L, ParallelDownloadPolicy.rateLimitWaitMillis(6, 5, null, noJitter))
        val jittered = ParallelDownloadPolicy.rateLimitWaitMillis(0, 0, null, Random(7))
        assertTrue(jittered in 500L until 750L)
        assertEquals(30_000L, ParallelDownloadPolicy.parseRetryAfterMillis(" 30 "))
        assertNull(ParallelDownloadPolicy.parseRetryAfterMillis("Wed, 21 Oct 2026 07:28:00 GMT"))
    }

    @Test
    fun `PLY-NET-01 eviction spares the reader's window and fresh chunks`() {
        val now = 100_000L
        val old = now - 10_000L
        // Cap 4, reader at chunk 5 with a window of 3 (5, 6, 7).
        val resident = mapOf(2L to old, 3L to now - 5_000L, 5L to old, 6L to old, 7L to old, 9L to old)
        assertEquals(2L, ParallelDownloadPolicy.evictionCandidate(resident, readerIndex = 5, depth = 3, cap = 4, nowMillis = now))
        val nothingBehind = mapOf(5L to old, 6L to old, 7L to old, 9L to old, 10L to old)
        assertEquals(10L, ParallelDownloadPolicy.evictionCandidate(nothingBehind, 5, 3, cap = 4, nowMillis = now))
        val touchedJustNow = mapOf(5L to old, 6L to old, 7L to old, 9L to now)
        assertNull(ParallelDownloadPolicy.evictionCandidate(touchedJustNow, 5, 3, cap = 3, nowMillis = now))
        assertNull(ParallelDownloadPolicy.evictionCandidate(resident, 5, 3, cap = 6, nowMillis = now))
    }

    @Test
    fun `PLY-NET-01 a rate-limited host halves the window, quiet intervals win it back`() {
        val depth = RateLimitDepth(configuredDepth = 4)
        assertEquals(4, depth.allowed(0))
        assertEquals(0, depth.beginEpisode(nowMillis = 1_000))
        assertEquals(2, depth.allowed(1_000))
        // At most one halving per second.
        assertEquals(1, depth.beginEpisode(nowMillis = 1_500))
        assertEquals(2, depth.allowed(1_500))
        depth.beginEpisode(nowMillis = 3_000)
        assertEquals(1, depth.allowed(3_000))
        // Halving again while capped doubled the step interval to 20 s.
        assertEquals(1, depth.allowed(15_000))
        assertEquals(2, depth.allowed(23_000))
        assertEquals(2, depth.allowed(30_000))
        assertEquals(3, depth.allowed(43_000))
        assertEquals(4, depth.allowed(63_000))
    }

    @Test
    fun `the file length comes from Content-Range`() {
        assertEquals(123_456_789L, ParallelRangeDataSource.contentRangeTotal(mapOf("content-range" to listOf("bytes 0-262143/123456789"))))
        assertEquals(C.LENGTH_UNSET.toLong(), ParallelRangeDataSource.contentRangeTotal(mapOf("Content-Range" to listOf("bytes 0-10/*"))))
        assertEquals(C.LENGTH_UNSET.toLong(), ParallelRangeDataSource.contentRangeTotal(emptyMap()))
    }
}
