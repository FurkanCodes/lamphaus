package com.lamphaus.app.ui

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.lamphaus.app.R
import com.lamphaus.core.model.ReleaseCountdown
import com.lamphaus.core.model.releaseCountdown

/** Episodes air; titles release. */
internal enum class ReleaseKind { EPISODE, TITLE }

/**
 * "Airs today", "Airs tomorrow", "Airs in 3 days" or "Airs 12 Oct" for an
 * episode; "Releases …" for a title. Null once it is out.
 */
@Composable
internal fun releaseCountdownText(releasedAtEpochMillis: Long?, kind: ReleaseKind): String? {
    val millis = releasedAtEpochMillis ?: return null
    val countdown = remember(millis) { releaseCountdown(millis) }
    val episode = kind == ReleaseKind.EPISODE
    return when (countdown) {
        ReleaseCountdown.Released -> null
        ReleaseCountdown.Today -> stringResource(if (episode) R.string.airs_today else R.string.releases_today)
        ReleaseCountdown.Tomorrow -> stringResource(if (episode) R.string.airs_tomorrow else R.string.releases_tomorrow)
        is ReleaseCountdown.InDays -> pluralStringResource(
            if (episode) R.plurals.airs_in_days else R.plurals.releases_in_days,
            countdown.days,
            countdown.days,
        )
        is ReleaseCountdown.OnDate ->
            stringResource(if (episode) R.string.airs_on_date else R.string.releases_on_date, releaseDate(millis))
    }
}

/** An episode's air date: its countdown before it airs, the plain date after. */
@Composable
internal fun episodeAirDateText(releasedAtEpochMillis: Long?): String? {
    val millis = releasedAtEpochMillis ?: return null
    return releaseCountdownText(millis, ReleaseKind.EPISODE) ?: releaseDate(millis)
}

@Composable
private fun releaseDate(epochMillis: Long): String {
    val context = LocalContext.current
    return remember(epochMillis, context) {
        DateUtils.formatDateTime(context, epochMillis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)
    }
}

/** "Airs in 3 days" for an upcoming episode, "New episode" or "Up next" otherwise. */
@Composable
internal fun upNextBadgeText(item: UpNextItem): String = when (item.kind) {
    UpNextKind.UPCOMING -> releaseCountdownText(item.episode.releasedAtEpochMillis, ReleaseKind.EPISODE)
        ?: stringResource(R.string.up_next)
    UpNextKind.NEW -> stringResource(R.string.new_episode)
    UpNextKind.NEXT -> stringResource(R.string.up_next)
}

/** "S1 E4 · Title", or the bare title when the episode has no numbering. */
@Composable
internal fun upNextEpisodeLabel(episode: com.lamphaus.core.model.Episode): String {
    val season = episode.season
    val number = episode.episode
    val code = if (season != null && number != null) stringResource(R.string.episode_format, season, number) else null
    return listOfNotNull(code, episode.title.takeIf(String::isNotBlank)).joinToString(" · ")
}
