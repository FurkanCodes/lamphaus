package com.lamphaus.app.widget

import com.lamphaus.app.ui.UpNextItem
import com.lamphaus.app.ui.UpNextKind
import com.lamphaus.app.ui.continueWatchingItems
import com.lamphaus.app.ui.withUpNext
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.WatchProgress

/** What the Continue watching widget shows (MOB-WGT-01). */
internal sealed interface WidgetContent {
    /** No account or profile on this device yet. */
    data object SignedOut : WidgetContent

    /** Signed in, nothing to continue. */
    data object Empty : WidgetContent

    data class Cards(val cards: List<WidgetCard>) : WidgetContent
}

/**
 * One title to continue. [episode] is the episode code and title for series
 * ("S2 · E5 · Title"), null for movies; [kind] says whether the viewer
 * resumes, starts the next episode, or a new one aired. Posters and titles
 * only: nothing account-specific is drawn (MOB-NOT-08).
 */
internal data class WidgetCard(
    val mediaKey: String,
    val title: String,
    val episodeSeason: Int?,
    val episodeNumber: Int?,
    val episodeTitle: String?,
    val progress: Float?,
    val minutesLeft: Int?,
    val kind: Kind,
    val artworkUrl: String?,
) {
    enum class Kind { RESUME, UP_NEXT, NEW_EPISODE }
}

/** At most this many titles are drawn at the largest size. */
internal const val WIDGET_MAX_CARDS = 6

/**
 * Home's Continue Watching order with aired up-next episodes, minus
 * upcoming countdown cards: the widget offers only what can be watched now
 * (SHR-PROD-09, SHR-PROD-10).
 */
internal fun widgetCards(progress: List<WatchProgress>, upNext: List<UpNextItem>): List<WidgetCard> {
    val watchable = upNext.filter { it.kind != UpNextKind.UPCOMING }
    val upNextByKey = watchable.associateBy { it.media.stableKey }
    return withUpNext(continueWatchingItems(progress, emptyList<MediaPreview>()), watchable)
        .take(WIDGET_MAX_CARDS)
        .map { (media, row) ->
            val next = upNextByKey[media.stableKey]?.takeIf { it.lastWatched.videoId == row.videoId }
            if (next != null) {
                WidgetCard(
                    mediaKey = media.stableKey,
                    title = media.name,
                    episodeSeason = next.episode.season,
                    episodeNumber = next.episode.episode,
                    episodeTitle = next.episode.title.takeIf(String::isNotBlank),
                    progress = null,
                    minutesLeft = null,
                    kind = if (next.kind == UpNextKind.NEW) WidgetCard.Kind.NEW_EPISODE else WidgetCard.Kind.UP_NEXT,
                    // The title's own art, never the next episode's still,
                    // which spoiler protection would hide.
                    artworkUrl = media.backgroundUrl ?: media.posterUrl,
                )
            } else {
                WidgetCard(
                    mediaKey = media.stableKey,
                    title = media.name,
                    episodeSeason = null,
                    episodeNumber = null,
                    episodeTitle = row.episodeLabel,
                    progress = row.fraction.coerceIn(0f, 1f),
                    minutesLeft = ((row.durationMillis - row.positionMillis).coerceAtLeast(0) / 60_000).toInt()
                        .coerceAtLeast(1),
                    kind = WidgetCard.Kind.RESUME,
                    artworkUrl = media.backgroundUrl ?: media.posterUrl,
                )
            }
        }
}
