package com.lamphaus.core.model

/**
 * Nuvio's skip rules: the active segment is the one containing the position
 * (post-credits scenes are destinations, never skipped), and skipping an
 * ending lands on a following post-credits scene when there is one.
 */
object SkipSegmentPolicy {
    /** The skip button hides itself after this long unless the chrome is shown. */
    const val AUTO_HIDE_MILLIS = 10_000

    /** Content longer than this after the credits is treated as a post-credits scene. */
    const val POST_ENDING_SCENE_GAP_MILLIS = 5_000L

    fun activeSegment(segments: List<PlaybackSegment>, positionMillis: Long, durationMillis: Long): PlaybackSegment? =
        segments.firstOrNull { segment ->
            segment.type != PlaybackSegmentType.POST_CREDITS &&
                positionMillis >= segment.startMillis &&
                positionMillis < segment.endOrDuration(durationMillis)
        }

    /**
     * The scene after an ending: an explicit POST_CREDITS segment, or — when a
     * known duration leaves more than [POST_ENDING_SCENE_GAP_MILLIS] after the
     * credits — the remainder of the file.
     */
    fun postCreditsSceneAfter(
        segment: PlaybackSegment,
        segments: List<PlaybackSegment>,
        durationMillis: Long,
    ): PlaybackSegment? {
        if (segment.type != PlaybackSegmentType.ENDING) return null
        val endingEnd = segment.endMillis ?: return null
        segments.filter {
            it.type == PlaybackSegmentType.POST_CREDITS &&
                it.startMillis >= endingEnd &&
                (it.endMillis == null || it.endMillis > it.startMillis) &&
                // A different release can end before the submitted scene does; its start is still playable.
                (durationMillis <= 0L || it.startMillis < durationMillis)
        }.minByOrNull { it.startMillis }?.let { return it }
        if (durationMillis > 0L && durationMillis - endingEnd > POST_ENDING_SCENE_GAP_MILLIS) {
            return PlaybackSegment(PlaybackSegmentType.POST_CREDITS, startMillis = endingEnd, endMillis = durationMillis)
        }
        return null
    }

    /** Where Skip lands: the post-credits scene, else the segment end, else the end of the file. */
    fun skipTarget(segment: PlaybackSegment, segments: List<PlaybackSegment>, durationMillis: Long): Long? {
        if (segment.type == PlaybackSegmentType.POST_CREDITS) return null
        val target = postCreditsSceneAfter(segment, segments, durationMillis)?.startMillis
            ?: segment.endMillis
            ?: durationMillis.takeIf { it > 0 }
            ?: return null
        return if (durationMillis > 0) target.coerceAtMost(durationMillis) else target
    }

    /** Nuvio's visibility: shown while active, until dismissed or auto-hidden; the chrome brings it back. */
    fun isButtonVisible(hasActiveSegment: Boolean, dismissed: Boolean, controlsVisible: Boolean, autoHidden: Boolean): Boolean {
        val shouldShow = hasActiveSegment && (!dismissed || controlsVisible)
        return shouldShow && (!autoHidden || controlsVisible)
    }

    internal fun PlaybackSegment.endOrDuration(durationMillis: Long): Long =
        endMillis ?: durationMillis.takeIf { it > 0 } ?: Long.MAX_VALUE
}
