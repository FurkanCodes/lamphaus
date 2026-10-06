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
        // The back buffer gives way first: at most half the minimum buffer.
        assertEquals(7_500, NativeMemoryPolicy.backBufferMs)
    }
}
