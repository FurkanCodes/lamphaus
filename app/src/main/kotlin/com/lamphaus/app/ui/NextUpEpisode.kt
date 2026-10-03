package com.lamphaus.app.ui

import com.lamphaus.core.model.Episode
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.WatchProgress
import com.lamphaus.core.model.hasAired
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

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

/** How a Continue Watching "up next" card presents the following episode. */
enum class UpNextKind {
    /** Aired before the viewer finished the previous one: simply the next episode. */
    NEXT,

    /** Aired after the viewer finished the previous one (Nuvio's "New episode"). */
    NEW,

    /** Not out yet but soon: the card counts down to it. */
    UPCOMING,
}

/** A series the viewer finished an episode of, with the episode that follows. */
data class UpNextItem(
    val media: MediaPreview,
    val episode: Episode,
    val kind: UpNextKind,
    /** The finished episode's progress row; orders the row and keys dismissal. */
    val lastWatched: WatchProgress,
) {
    val dismissalKey: String get() = upNextDismissalKey(lastWatched)
}

internal fun upNextDismissalKey(lastWatched: WatchProgress): String = "${lastWatched.mediaKey}|${lastWatched.videoId}"

/** Only recent viewing earns an up-next card, and only this many series are looked up. */
internal const val UP_NEXT_RECENT_MILLIS = 60L * 24 * 60 * 60 * 1000
internal const val UP_NEXT_MAX_SERIES = 20

/** An unaired episode shows up only when it airs within this window. */
internal const val UP_NEXT_UPCOMING_WINDOW_MILLIS = 30L * 24 * 60 * 60 * 1000

/**
 * The latest finished episode of each recently watched series that has no
 * resumable episode (Continue Watching already shows that one) and was not
 * dismissed, most recent first.
 */
internal fun upNextCandidates(
    progress: List<WatchProgress>,
    dismissed: Set<String>,
    nowEpochMillis: Long,
): List<WatchProgress> {
    val resumableKeys = progress.filter(WatchProgress::isResumable).mapTo(HashSet()) { it.mediaKey }
    return progress.asSequence()
        .filter { it.completed && it.mediaKey.startsWith("series:") && it.preview != null }
        .filter { nowEpochMillis - it.updatedAtEpochMillis <= UP_NEXT_RECENT_MILLIS }
        .groupBy(WatchProgress::mediaKey)
        .values
        .map { rows -> rows.maxBy(WatchProgress::updatedAtEpochMillis) }
        .filter { it.mediaKey !in resumableKeys && upNextDismissalKey(it) !in dismissed }
        .sortedByDescending(WatchProgress::updatedAtEpochMillis)
        .take(UP_NEXT_MAX_SERIES)
}

/**
 * Continue Watching's up-next set (SHR-PROD-10): for each candidate series,
 * the episode after the one the viewer last finished. Home, the new-episode
 * check, and the widget all compute it here so they always agree. A series
 * whose episodes cannot be loaded is simply left out.
 */
internal suspend fun computeUpNext(
    progress: List<WatchProgress>,
    dismissed: Set<String>,
    nowEpochMillis: Long,
    episodesOf: suspend (MediaPreview) -> List<Episode>,
): List<UpNextItem> = coroutineScope {
    val completed = progress.filter(WatchProgress::completed).mapTo(HashSet()) { it.videoId }
    upNextCandidates(progress, dismissed, nowEpochMillis).map { row ->
        async {
            val media = row.preview ?: return@async null
            runCatching { episodesOf(media) }
                .onFailure { if (it is CancellationException) throw it }
                .getOrNull()
                ?.let { episodes -> upNextAfter(media, row, episodes, completed, nowEpochMillis) }
        }
    }.awaitAll().filterNotNull()
}

/**
 * The regular episode after [lastWatched] that the viewer has not finished.
 * Aired, it is next up ("New episode" when it aired after the viewer finished
 * the previous one); unaired, it counts down when it airs within the window.
 */
internal fun upNextAfter(
    media: MediaPreview,
    lastWatched: WatchProgress,
    episodes: List<Episode>,
    completedVideoIds: Set<String>,
    nowEpochMillis: Long,
): UpNextItem? {
    val ordered = episodes.distinctBy(Episode::id)
        .filter { it.season != 0 }
        .sortedWith(compareBy<Episode> { it.season ?: Int.MAX_VALUE }.thenBy { it.episode ?: Int.MAX_VALUE }.thenBy(Episode::id))
    val index = ordered.indexOfFirst { it.id == lastWatched.videoId }
    if (index < 0) return null
    val next = ordered.drop(index + 1).firstOrNull { it.id !in completedVideoIds } ?: return null
    val released = next.releasedAtEpochMillis
    val kind = when {
        next.hasAired(nowEpochMillis) ->
            if (released != null && released > lastWatched.updatedAtEpochMillis) UpNextKind.NEW else UpNextKind.NEXT
        released != null && released - nowEpochMillis <= UP_NEXT_UPCOMING_WINDOW_MILLIS -> UpNextKind.UPCOMING
        else -> return null
    }
    return UpNextItem(media, next, kind, lastWatched)
}
