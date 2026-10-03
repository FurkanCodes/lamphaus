package com.lamphaus.core.model

/** A series the viewer follows (in Library or watched recently) with its current episode list. */
data class FollowedSeries(
    val media: MediaPreview,
    val episodes: List<Episode>,
)

/** One notification: the first new episode of a series, and how many arrived with it. */
data class NewEpisodeAlert(
    val media: MediaPreview,
    val episode: Episode,
    val newCount: Int = 1,
)

data class NewEpisodeCheckResult(
    val alerts: List<NewEpisodeAlert>,
    /** Every episode this check considered announced, alerted or not. */
    val announcedVideoIds: Set<String>,
)

/** Episodes older than this never alert, so a long-idle phone does not announce a backlog. */
const val NEW_EPISODE_LOOKBACK_MILLIS = 7L * 24 * 60 * 60 * 1000

/** At most this many series alert from one check; the rest are still marked announced. */
const val NEW_EPISODE_MAX_ALERTS = 5

/**
 * Which followed series just gained an episode (SHR-PROD-16). An episode is
 * new when it is a regular episode (not a special) with a known date that
 * aired after the previous check and within the lookback, and the viewer has
 * neither watched it nor been told about it. A first check
 * ([lastCheckedAtEpochMillis] null) only records the moment: it never
 * announces what was already out. Unaired episodes never alert
 * (SHR-PROD-09). One alert per series names its earliest new episode.
 */
fun selectNewEpisodes(
    followed: List<FollowedSeries>,
    watchedVideoIds: Set<String>,
    announcedVideoIds: Set<String>,
    lastCheckedAtEpochMillis: Long?,
    nowEpochMillis: Long,
): NewEpisodeCheckResult {
    if (lastCheckedAtEpochMillis == null) return NewEpisodeCheckResult(emptyList(), announcedVideoIds)
    val since = maxOf(lastCheckedAtEpochMillis, nowEpochMillis - NEW_EPISODE_LOOKBACK_MILLIS)
    val announced = announcedVideoIds.toMutableSet()
    val alerts = followed
        .distinctBy { it.media.stableKey }
        .mapNotNull { series ->
            val fresh = series.episodes
                .distinctBy(Episode::id)
                .filter { episode ->
                    val released = episode.releasedAtEpochMillis
                    episode.season != 0 &&
                        released != null &&
                        released > since &&
                        released <= nowEpochMillis &&
                        episode.id !in watchedVideoIds &&
                        episode.id !in announcedVideoIds
                }
                .sortedWith(compareBy<Episode>({ it.season ?: Int.MAX_VALUE }, { it.episode ?: Int.MAX_VALUE }))
            if (fresh.isEmpty()) return@mapNotNull null
            fresh.mapTo(announced, Episode::id)
            NewEpisodeAlert(series.media, fresh.first(), fresh.size)
        }
        .sortedByDescending { it.episode.releasedAtEpochMillis ?: 0L }
        .take(NEW_EPISODE_MAX_ALERTS)
    return NewEpisodeCheckResult(alerts, announced)
}
