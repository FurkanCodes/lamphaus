package com.lamphaus.core.player

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeMemoryPolicyTest {
    private val mib = 1024L * 1024L
    private val gib = 1024L * mib

    @Test
    fun `PLY-NET-01 the buffer ceiling follows physical RAM tiers`() {
        assertEquals(200, NativeMemoryPolicy.safeLimitMb(0))
        assertEquals(100, NativeMemoryPolicy.safeLimitMb(1 * gib))
        assertEquals(200, NativeMemoryPolicy.safeLimitMb(2 * gib))
        assertEquals(500, NativeMemoryPolicy.safeLimitMb(3 * gib))
        assertEquals(1_000, NativeMemoryPolicy.safeLimitMb(4 * gib))
        assertEquals(1_600, NativeMemoryPolicy.safeLimitMb(6 * gib))
        assertEquals(2_000, NativeMemoryPolicy.safeLimitMb(8 * gib))
    }

    @Test
    fun `PLY-NET-01 parallel chunks come out of the target, which never drops below 25 MiB`() {
        // 4 GB box with two connections of 16 MiB chunks: (2 + 2) x 16 = 64 MiB overhead.
        assertEquals((936 * mib).toInt(), NativeMemoryPolicy.targetBufferBytes(4 * gib, 64 * mib))
        assertEquals((200 * mib).toInt(), NativeMemoryPolicy.targetBufferBytes(2 * gib, 0))
        assertEquals((25 * mib).toInt(), NativeMemoryPolicy.targetBufferBytes(1 * gib, 512 * mib))
    }

    @Test
    fun `PLY-NET-01 time alone sets the buffer until the RAM target is reached`() {
        // One target, so loading tops up continuously instead of idling down to a lower mark.
        assertEquals(50_000, NativeMemoryPolicy.BUFFER_MS)
        // A 100 Mbit/s remux keeps all 50 s on a 4 GB box: 596 MiB of its 936 MiB target.
        val remux = 50L * 100_000_000L / 8
        assertEquals(true, remux < NativeMemoryPolicy.targetBufferBytes(4 * gib, 64 * mib))
    }

    @Test
    fun `PLY-NET-01 a rewind stays off the network only where the target can hold it`() {
        assertEquals(15_000, NativeMemoryPolicy.backBufferMs(NativeMemoryPolicy.targetBufferBytes(4 * gib, 64 * mib)))
        assertEquals(1_500, NativeMemoryPolicy.backBufferMs(NativeMemoryPolicy.targetBufferBytes(3 * gib, 64 * mib)))
        assertEquals(1_500, NativeMemoryPolicy.backBufferMs(NativeMemoryPolicy.targetBufferBytes(1 * gib, 0)))
    }
}
