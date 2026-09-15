package com.lamphaus.core.model

/** Estimates missing frame-rate metadata from media timestamps, never wall-clock playback speed. */
class VideoCadenceEstimator {
    private var previousUs: Long? = null
    private val intervals = LongArray(WINDOW)
    private val scratch = LongArray(WINDOW)
    private var count = 0
    private var cursor = 0
    private var intervalsSinceComputation = 0

    @Volatile
    var frameRate: Float = 0f
        private set

    @Synchronized
    fun reset() {
        previousUs = null
        count = 0
        cursor = 0
        intervalsSinceComputation = 0
        frameRate = 0f
    }

    @Synchronized
    fun onFrame(presentationTimeUs: Long) {
        val previous = previousUs
        previousUs = presentationTimeUs
        if (previous == null) return
        val interval = presentationTimeUs - previous
        if (interval !in 4_000L..200_000L) {
            count = 0
            cursor = 0
            intervalsSinceComputation = 0
            frameRate = 0f
            return
        }
        // Bounded primitive ring (PERF-09): no boxed interval list and no
        // per-frame sort on the render thread.
        intervals[cursor] = interval
        cursor = (cursor + 1) % WINDOW
        if (count < WINDOW) count++
        if (count < WINDOW) return
        intervalsSinceComputation++
        // Compute on the first full window, then at a cadence that still
        // catches discontinuities and VFR without scanning every frame.
        if (frameRate != 0f && intervalsSinceComputation < COMPUTE_EVERY_INTERVALS) return
        intervalsSinceComputation = 0
        computeFrameRate()
    }

    private fun computeFrameRate() {
        System.arraycopy(intervals, 0, scratch, 0, count)
        java.util.Arrays.sort(scratch, 0, count)
        val median = scratch[count / 2]
        // Reject variable cadence and isolated seek/drop discontinuities.
        // Matroska timestamps commonly have millisecond precision: alternating
        // 41/42 ms frames are valid 23.976/24 fps, not variable frame rate.
        val toleranceUs = maxOf(1_000.0, median * 0.001)
        val lower = firstAtLeast(median - toleranceUs)
        val upper = lastAtMost(median + toleranceUs)
        val consistent = upper - lower + 1
        if (consistent < count * 0.9) {
            frameRate = 0f
            return
        }
        var sum = 0L
        for (index in lower..upper) sum += scratch[index]
        val measured = (1_000_000.0 / (sum.toDouble() / consistent)).toFloat()
        val standard = STANDARD_RATES.minBy { kotlin.math.abs(it - measured) }
        frameRate = if (kotlin.math.abs(standard - measured) < 0.01f) standard else measured
    }

    /** First index whose sorted value is >= [threshold], or [count] when none. */
    private fun firstAtLeast(threshold: Double): Int {
        var low = 0
        var high = count
        while (low < high) {
            val mid = (low + high) / 2
            if (scratch[mid] >= threshold) high = mid else low = mid + 1
        }
        return low
    }

    /** Last index whose sorted value is <= [threshold], or -1 when none. */
    private fun lastAtMost(threshold: Double): Int {
        var low = 0
        var high = count
        while (low < high) {
            val mid = (low + high) / 2
            if (scratch[mid] <= threshold) low = mid + 1 else high = mid
        }
        return low - 1
    }

    private companion object {
        const val WINDOW = 60
        const val COMPUTE_EVERY_INTERVALS = 8

        val STANDARD_RATES = listOf(23.976024f, 24f, 25f, 29.97003f, 30f, 50f, 59.94006f, 60f, 119.88012f, 120f)
    }
}
