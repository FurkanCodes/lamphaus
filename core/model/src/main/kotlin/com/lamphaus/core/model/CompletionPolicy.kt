package com.lamphaus.core.model

/**
 * Credits-aware playback completion (SHR-ARC-05): a video completes at the
 * earliest valid IntroDB credits start; without credits data it completes at
 * 90% of a known duration; natural playback end always completes. Skip and
 * next-episode settings never influence watch history. Stickiness is owned by
 * the persistence layer: an existing completed row stays completed until an
 * explicit Mark unwatched deletes it.
 */
object CompletionPolicy {
    /** Nuvio's default completion fraction for titles without credits data. */
    const val FALLBACK_FRACTION = 0.90

    fun isComplete(
        positionMillis: Long,
        durationMillis: Long,
        naturalEnd: Boolean,
        endingSegments: List<PlaybackSegment> = emptyList(),
    ): Boolean {
        if (naturalEnd) return true
        val creditsStart = endingSegments
            .filter { it.type == PlaybackSegmentType.ENDING }
            .map { it.startMillis }
            .filter { start -> start > 0 && (durationMillis <= 0 || start <= durationMillis) }
            .minOrNull()
        if (creditsStart != null) return positionMillis >= creditsStart
        if (durationMillis <= 0) return false
        return positionMillis.toDouble() / durationMillis >= FALLBACK_FRACTION
    }
}
