package com.lamphaus.app.ui

import com.lamphaus.core.model.Episode
import com.lamphaus.core.model.WatchProgress
import com.lamphaus.core.model.hasAired

/** Why an episode is the one to play next. */
internal enum class NextUpKind {
    /** Partly watched: continue where the viewer stopped. */
    RESUME,

    /** The first unwatched episode after the most recently finished one. */
    NEXT,

    /** Nothing watched yet (or the series is finished): start from the beginning. */
    START,
}

internal data class NextUpEpisode(
    val episode: Episode,
    val kind: NextUpKind,
    /** Resumable progress for [NextUpKind.RESUME]; null otherwise. */
    val progress: WatchProgress? = null,
)

/**
 * The episode a series' Play action should open, and where its episode list
 * should start (SHR-PROD-02). Resumes the most recently watched unfinished
 * episode; otherwise moves to the first aired, unwatched regular episode after
 * the most recently finished one; otherwise starts at the first aired regular
 * episode. Specials (season 0) are never chosen automatically.
 */
internal fun nextUpEpisode(
    episodes: List<Episode>,
    progress: List<WatchProgress>,
    completedVideoIds: Set<String>,
    nowEpochMillis: Long = System.currentTimeMillis(),
): NextUpEpisode? {
    if (episodes.isEmpty()) return null
    val ordered = episodes.distinctBy(Episode::id).sortedWith(
        compareBy<Episode> { it.season ?: Int.MAX_VALUE }
            .thenBy { it.episode ?: Int.MAX_VALUE }
            .thenBy(Episode::id),
    )
    val byId = ordered.associateBy(Episode::id)
    val rows = progress.filter { it.videoId in byId }

    rows.filter(WatchProgress::isResumable)
        .maxByOrNull(WatchProgress::updatedAtEpochMillis)
        ?.let { row -> return NextUpEpisode(byId.getValue(row.videoId), NextUpKind.RESUME, row) }

    fun isPlayableRegular(episode: Episode) = episode.season != 0 && episode.hasAired(nowEpochMillis)
    val finished = { episode: Episode ->
        episode.id in completedVideoIds || rows.any { it.videoId == episode.id && it.completed }
    }
    val lastFinished = rows.filter { it.completed || it.videoId in completedVideoIds }
        .maxByOrNull(WatchProgress::updatedAtEpochMillis)
        ?.let { byId[it.videoId] }
        ?: ordered.lastOrNull { it.id in completedVideoIds }
    if (lastFinished != null) {
        ordered.dropWhile { it.id != lastFinished.id }
            .drop(1)
            .firstOrNull { !finished(it) && isPlayableRegular(it) }
            ?.let { return NextUpEpisode(it, NextUpKind.NEXT) }
    }
    val start = ordered.firstOrNull(::isPlayableRegular) ?: ordered.first()
    return NextUpEpisode(start, NextUpKind.START)
}
