package com.lamphaus.core.model

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * How far away a release is, in the viewer's calendar days (Nuvio's air-date
 * badge): today, tomorrow, within a week, or a later date.
 */
sealed interface ReleaseCountdown {
    data object Released : ReleaseCountdown
    data object Today : ReleaseCountdown
    data object Tomorrow : ReleaseCountdown
    data class InDays(val days: Int) : ReleaseCountdown
    data class OnDate(val epochMillis: Long) : ReleaseCountdown

    companion object {
        /** Past this many days the badge names the date instead of counting. */
        const val MAX_COUNTDOWN_DAYS = 7
    }
}

fun releaseCountdown(
    releasedAtEpochMillis: Long,
    nowEpochMillis: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): ReleaseCountdown {
    if (releasedAtEpochMillis <= nowEpochMillis) return ReleaseCountdown.Released
    val today = Instant.ofEpochMilli(nowEpochMillis).atZone(zone).toLocalDate()
    val releaseDay = Instant.ofEpochMilli(releasedAtEpochMillis).atZone(zone).toLocalDate()
    return when (val days = ChronoUnit.DAYS.between(today, releaseDay)) {
        0L -> ReleaseCountdown.Today
        1L -> ReleaseCountdown.Tomorrow
        in 2L..ReleaseCountdown.MAX_COUNTDOWN_DAYS.toLong() -> ReleaseCountdown.InDays(days.toInt())
        else -> ReleaseCountdown.OnDate(releasedAtEpochMillis)
    }
}

/**
 * Not out yet: its release date is in the future or, without one, its year
 * is later than this one. A title with neither counts as released.
 */
fun MediaPreview.isUnreleased(
    nowEpochMillis: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): Boolean {
    releasedAtEpochMillis?.let { return it > nowEpochMillis }
    val year = releaseYear ?: return false
    return year > Instant.ofEpochMilli(nowEpochMillis).atZone(zone).year
}
