package com.lamphaus.core.model

/**
 * Pure timing policy for the next-episode card, matching Nuvio's ending rule:
 * exact credits/ending timestamps win whenever present; otherwise the selected
 * fallback threshold decides. An unknown duration disables the fallback
 * calculation without preventing an exact-timestamp trigger. Visibility is
 * manual-only: this policy never starts playback.
 */
object NextEpisodePolicy {
    const val PERCENT_MIN = 97f
    const val PERCENT_MAX = 99.5f
    const val MINUTES_MIN = 1f
    const val MINUTES_MAX = 3.5f

    fun clampedPercent(value: Float): Float = value.coerceIn(PERCENT_MIN, PERCENT_MAX)

    fun clampedMinutesBeforeEnd(value: Float): Float = value.coerceIn(MINUTES_MIN, MINUTES_MAX)

    fun shouldShowCard(
        positionMillis: Long,
        durationMillis: Long,
        segments: List<PlaybackSegment>,
        thresholdMode: NextEpisodeThresholdMode,
        thresholdPercent: Float,
        thresholdMinutesBeforeEnd: Float,
    ): Boolean {
        val earliestEndingStart = segments
            .filter { it.type == PlaybackSegmentType.ENDING }
            .minOfOrNull { it.startMillis }
        // The card stays available once the earliest ending is reached, even
        // after that interval itself has passed.
        if (earliestEndingStart != null) return positionMillis >= earliestEndingStart
        if (durationMillis <= 0L) return false
        return when (thresholdMode) {
            NextEpisodeThresholdMode.PERCENTAGE ->
                positionMillis.toDouble() / durationMillis.toDouble() >=
                    clampedPercent(thresholdPercent) / 100.0
            NextEpisodeThresholdMode.MINUTES_BEFORE_END ->
                durationMillis - positionMillis <=
                    (clampedMinutesBeforeEnd(thresholdMinutesBeforeEnd) * 60_000f).toLong()
        }
    }
}
