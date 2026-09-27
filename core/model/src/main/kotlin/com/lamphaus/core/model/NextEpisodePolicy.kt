package com.lamphaus.core.model

/**
 * Pure timing policy for the next-episode card, ported from Nuvio's
 * PlayerNextEpisodeRules. With ending timestamps: the card waits for a
 * post-credits scene to finish, appears at the earliest ending when the
 * credits run to the end of the file, and otherwise follows the threshold.
 * Without them the selected fallback threshold decides. An unknown duration
 * disables the threshold and post-credits checks but not an exact ending start.
 */
object NextEpisodePolicy {
    const val PERCENT_MIN = 97f
    const val PERCENT_MAX = 99.5f
    const val MINUTES_MIN = 1f
    const val MINUTES_MAX = 3.5f

    /** A position this far past the duration is not a valid end-of-video signal. */
    const val END_OF_VIDEO_EPSILON_MILLIS = 1_000L

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
        val endings = segments.filter { it.type == PlaybackSegmentType.ENDING }
        // Without a duration only an exact ending start can trigger the card.
        if (durationMillis <= 0L) return endings.isNotEmpty() && positionMillis >= endings.minOf { it.startMillis }
        if (positionMillis > durationMillis + END_OF_VIDEO_EPSILON_MILLIS) return false
        val thresholdPosition = thresholdPositionMillis(
            durationMillis, thresholdMode, thresholdPercent, thresholdMinutesBeforeEnd,
        )
        if (endings.isEmpty()) return positionMillis >= thresholdPosition

        val latestEnding = endings.maxBy { it.endMillis ?: durationMillis }
        // Never cover a post-credits scene: wait until it has played.
        SkipSegmentPolicy.postCreditsSceneAfter(latestEnding, segments, durationMillis)?.let { scene ->
            val sceneEnd = (scene.endMillis ?: durationMillis).coerceAtMost(durationMillis)
            return positionMillis >= maxOf(sceneEnd, thresholdPosition)
        }
        val latestEndingEnd = endings.maxOf { it.endMillis ?: durationMillis }
        return if (durationMillis - latestEndingEnd > durationMillis - thresholdPosition) {
            positionMillis >= thresholdPosition
        } else {
            // The credits run to (nearly) the end of the file: offer the next episode as they start.
            positionMillis >= endings.minOf { it.startMillis }
        }
    }

    private fun thresholdPositionMillis(
        durationMillis: Long,
        mode: NextEpisodeThresholdMode,
        percent: Float,
        minutesBeforeEnd: Float,
    ): Long = when (mode) {
        NextEpisodeThresholdMode.PERCENTAGE ->
            kotlin.math.ceil(durationMillis * (clampedPercent(percent) / 100.0)).toLong()
        NextEpisodeThresholdMode.MINUTES_BEFORE_END ->
            durationMillis - (clampedMinutesBeforeEnd(minutesBeforeEnd) * 60_000f).toLong()
    }
}
