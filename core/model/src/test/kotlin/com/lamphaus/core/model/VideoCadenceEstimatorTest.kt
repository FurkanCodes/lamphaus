package com.lamphaus.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoCadenceEstimatorTest {
    @Test
    fun `QA-06 derives fractional cadence from timestamps when metadata is missing`() {
        val estimator = VideoCadenceEstimator()
        repeat(61) { estimator.onFrame((it * 1_000_000.0 * 1001 / 24000).toLong()) }
        assertEquals(23.976024f, estimator.frameRate, 0.0001f)
    }

    @Test
    fun `QA-06 handles millisecond container timestamps without confusing fractional rates`() {
        for (rate in listOf(23.976024f, 24f, 29.97003f, 30f, 59.94006f, 60f)) {
            val estimator = VideoCadenceEstimator()
            repeat(121) { estimator.onFrame((it * 1000.0 / rate).toLong() * 1000L) }
            assertEquals(rate, estimator.frameRate, 0.001f)
        }
    }

    @Test
    fun `QA-06 ignores insufficient samples and resets at source boundaries`() {
        val estimator = VideoCadenceEstimator()
        repeat(20) { estimator.onFrame(it * 40_000L) }
        assertEquals(0f, estimator.frameRate)
        repeat(41) { estimator.onFrame((it + 20) * 40_000L) }
        assertEquals(25f, estimator.frameRate)
        estimator.reset()
        assertEquals(0f, estimator.frameRate)
        repeat(61) { estimator.onFrame((it * 1_000_000.0 / 24).toLong()) }
        assertEquals(24f, estimator.frameRate)
    }

    @Test
    fun `QA-06 rejects discontinuities and inconsistent timestamps`() {
        val estimator = VideoCadenceEstimator()
        repeat(61) { estimator.onFrame(it * 40_000L) }
        estimator.onFrame(0)
        assertEquals(0f, estimator.frameRate)
        var timestamp = 0L
        repeat(61) {
            timestamp += if (it % 2 == 0) 40_000L else 50_000L
            estimator.onFrame(timestamp)
        }
        assertEquals(0f, estimator.frameRate)
    }
}
