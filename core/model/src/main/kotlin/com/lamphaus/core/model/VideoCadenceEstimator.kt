package com.lamphaus.core.model

/** Estimates missing frame-rate metadata from media timestamps, never wall-clock playback speed. */
class VideoCadenceEstimator {
    private var previousUs: Long? = null
    private val intervals = ArrayDeque<Long>()

    @Volatile
    var frameRate: Float = 0f
        private set

    @Synchronized
    fun reset() {
        previousUs = null
        intervals.clear()
        frameRate = 0f
    }

    @Synchronized
    fun onFrame(presentationTimeUs: Long) {
        val previous = previousUs
        previousUs = presentationTimeUs
        if (previous == null) return
        val interval = presentationTimeUs - previous
        if (interval !in 4_000L..200_000L) {
            intervals.clear()
            frameRate = 0f
            return
        }
        intervals.addLast(interval)
        if (intervals.size > 60) intervals.removeFirst()
        if (intervals.size < 60) return
        val median = intervals.sorted()[intervals.size / 2]
        // Reject variable cadence and isolated seek/drop discontinuities.
        // Matroska timestamps commonly have millisecond precision: alternating
        // 41/42 ms frames are valid 23.976/24 fps, not variable frame rate.
        val toleranceUs = maxOf(1_000.0, median * 0.001)
        val consistent = intervals.filter { kotlin.math.abs(it - median) <= toleranceUs }
        if (consistent.size < intervals.size * 0.9) {
            frameRate = 0f
            return
        }
        val measured = (1_000_000.0 / consistent.average()).toFloat()
        val standard = STANDARD_RATES.minBy { kotlin.math.abs(it - measured) }
        frameRate = if (kotlin.math.abs(standard - measured) < 0.01f) standard else measured
    }

    private companion object {
        val STANDARD_RATES = listOf(23.976024f, 24f, 25f, 29.97003f, 30f, 50f, 59.94006f, 60f, 119.88012f, 120f)
    }
}
