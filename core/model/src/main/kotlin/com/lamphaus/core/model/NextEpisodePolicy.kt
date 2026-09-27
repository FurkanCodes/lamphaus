package com.lamphaus.core.model

/**
 * Pure timing policy for the next-episode card. With ending timestamps the
 * card appears as the credits start, which is when viewers reach for the next
 * episode; it waits only for an explicitly tagged post-credits scene. Content
 * that merely follows the credits (a next-week preview, a stinger nobody
 * tagged) never delays it: the card is a small, non-blocking offer that never
 * starts playback on its own. Without endings the selected fallback threshold
 * decides. An unknown duration disables the threshold and post-credits checks
 * but not an exact ending start.
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

        val earliestEndingStart = endings.minOf { it.startMillis }
        // Never cover a tagged post-credits scene: offer the next episode once it has played.
        val taggedScene = segments
            .filter { it.type == PlaybackSegmentType.POST_CREDITS && it.startMillis >= earliestEndingStart }
            .minByOrNull { it.startMillis }
        if (taggedScene != null) {
            val sceneEnd = (taggedScene.endMillis ?: durationMillis).coerceAtMost(durationMillis)
            return positionMillis >= sceneEnd
        }
        // Timestamps come from a reference release; whichever of the credits
        // start and the fallback threshold comes first wins, so data that runs
        // later than this file never delays the card.
        return positionMillis >= minOf(earliestEndingStart, thresholdPosition)
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
