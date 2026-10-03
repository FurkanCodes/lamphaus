package com.lamphaus.core.model

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** Grace on top of wall-clock time, for save timing jitter. */
private const val WATCH_SLACK_MILLIS = 2_000L

/**
 * Playing time between two progress saves (SHR-PROD-17): how far the
 * position advanced, but never more than the time that actually passed, so
 * seeking forward, scrubbing, or a resumed position count only what was
 * watched. Rewinds and pauses count nothing.
 */
fun countedWatchMillis(previousPositionMillis: Long, positionMillis: Long, elapsedWallMillis: Long): Long {
    val advanced = positionMillis - previousPositionMillis
    if (advanced <= 0 || elapsedWallMillis <= 0) return 0
    return minOf(advanced, elapsedWallMillis + WATCH_SLACK_MILLIS)
}

/** A month's private viewing summary for one profile (SHR-PROD-17). */
data class MonthlyRecap(
    val month: YearMonth,
    val watchedMillis: Long,
    val episodesFinished: Int,
    val moviesFinished: Int,
    /** The title watched longest, when known. */
    val topTitle: MediaPreview?,
    /** Posters of the most-watched titles, longest first. */
    val titles: List<MediaPreview>,
) {
    val hours: Int get() = (watchedMillis / 3_600_000L).toInt()
}

/** The recap shows only when the month had at least this much viewing. */
const val RECAP_MIN_WATCHED_MILLIS = 60L * 60 * 1000

/** The card shows during the first days of the month that follows. */
const val RECAP_VISIBLE_DAYS = 7

/**
 * Last month's recap while it is relevant: during the first
 * [RECAP_VISIBLE_DAYS] days of the month, when that month has at least an
 * hour of logged viewing and was not dismissed. [watchedByMedia] is the
 * month's playing time per media key; [progress] supplies what was finished
 * and the titles' artwork. Numbers only: no streaks, goals, or comparisons
 * (SHR-PROD-03).
 */
fun monthlyRecap(
    nowEpochMillis: Long,
    zone: ZoneId,
    watchedByMedia: Map<String, Long>,
    progress: List<WatchProgress>,
    dismissedMonth: String?,
): MonthlyRecap? {
    val today = Instant.ofEpochMilli(nowEpochMillis).atZone(zone).toLocalDate()
    if (today.dayOfMonth > RECAP_VISIBLE_DAYS) return null
    val month = YearMonth.from(today).minusMonths(1)
    if (month.toString() == dismissedMonth) return null
    val watched = watchedByMedia.values.sum()
    if (watched < RECAP_MIN_WATCHED_MILLIS) return null

    val finished = progress.filter { row ->
        row.completed && YearMonth.from(Instant.ofEpochMilli(row.updatedAtEpochMillis).atZone(zone)) == month
    }
    val previews = progress.mapNotNull { row -> row.preview?.let { row.mediaKey to it } }.toMap()
    val ranked = watchedByMedia.entries
        .sortedByDescending { it.value }
        .mapNotNull { previews[it.key] }
    return MonthlyRecap(
        month = month,
        watchedMillis = watched,
        episodesFinished = finished.count { it.mediaKey.startsWith("series:") },
        moviesFinished = finished.count { it.mediaKey.startsWith("movie:") },
        topTitle = ranked.firstOrNull(),
        titles = ranked.take(8),
    )
}

/** The calendar month an instant falls in, as `yyyy-MM` (the viewing log's key). */
fun viewingMonthKey(epochMillis: Long, zone: ZoneId): String =
    YearMonth.from(Instant.ofEpochMilli(epochMillis).atZone(zone)).toString()
