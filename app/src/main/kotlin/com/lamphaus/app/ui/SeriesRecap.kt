package com.lamphaus.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lamphaus.app.R
import com.lamphaus.core.model.Episode
import com.lamphaus.core.model.WatchProgress

/** A series earns a recap once the viewer has been away from it this long. */
internal const val RECAP_AFTER_MILLIS = 21L * 24 * 60 * 60 * 1000

/**
 * The episode to recap on a series' details page ("Previously", SHR-PROD-13):
 * the one the viewer most recently finished, when they last watched anything
 * of the series at least [RECAP_AFTER_MILLIS] ago and there is still an
 * episode to continue with. Its synopsis is of an episode already watched, so
 * it never spoils. Null when there is no gap, nothing to continue, or no
 * synopsis to show.
 */
internal fun seriesRecap(
    episodes: List<Episode>,
    progress: List<WatchProgress>,
    completedVideoIds: Set<String>,
    nowEpochMillis: Long = System.currentTimeMillis(),
): Episode? {
    val byId = episodes.associateBy(Episode::id)
    val rows = progress.filter { it.videoId in byId }
    val lastActivity = rows.maxOfOrNull(WatchProgress::updatedAtEpochMillis) ?: return null
    if (nowEpochMillis - lastActivity < RECAP_AFTER_MILLIS) return null
    val nextUp = nextUpEpisode(episodes, progress, completedVideoIds, nowEpochMillis) ?: return null
    if (nextUp.kind == NextUpKind.START) return null
    return rows.filter(WatchProgress::completed)
        .maxByOrNull(WatchProgress::updatedAtEpochMillis)
        ?.let { byId[it.videoId] }
        ?.takeIf { it.id != nextUp.episode.id && !it.overview.isNullOrBlank() }
}

/** "S2 · E4  The Long Night", or the title alone when the episode has no number. */
@Composable
internal fun recapEpisodeTitle(episode: Episode): String {
    val season = episode.season
    val number = episode.episode
    if (season == null || number == null) return episode.title
    return stringResource(
        R.string.recap_episode_format,
        stringResource(R.string.episode_format, season, number),
        episode.title,
    )
}
