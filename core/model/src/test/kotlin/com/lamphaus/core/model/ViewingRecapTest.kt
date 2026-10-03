package com.lamphaus.core.model

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** SHR-PROD-17: the recap counts real watching and shows only when it is relevant. */
class ViewingRecapTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val hour = 3_600_000L

    private fun at(year: Int, month: Int, day: Int) =
        LocalDateTime.of(year, month, day, 12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun preview(key: String): MediaPreview {
        val (type, id) = key.split(':')
        return MediaPreview(id, if (type == "series") MediaType.SERIES else MediaType.MOVIE, type, id)
    }

    private fun finished(key: String, video: String, at: Long) = WatchProgress(
        profileId = "p",
        mediaKey = key,
        videoId = video,
        positionMillis = 100,
        durationMillis = 100,
        completed = true,
        updatedAtEpochMillis = at,
        preview = preview(key),
    )

    @Test
    fun watchTimeIsCappedByElapsedTime() {
        assertEquals(10_000, countedWatchMillis(0, 10_000, 10_500))
        assertEquals(12_000, countedWatchMillis(0, 600_000, 10_000))
        assertEquals(0, countedWatchMillis(50_000, 40_000, 10_000))
        assertEquals(0, countedWatchMillis(0, 10_000, 0))
    }

    @Test
    fun recapSummarisesLastMonthInTheFirstWeek() {
        val progress = listOf(
            finished("series:show", "e1", at(2026, 9, 3)),
            finished("series:show", "e2", at(2026, 9, 4)),
            finished("movie:film", "film", at(2026, 9, 20)),
            finished("movie:older", "older", at(2026, 8, 20)),
        )
        val recap = monthlyRecap(
            nowEpochMillis = at(2026, 10, 2),
            zone = zone,
            watchedByMedia = mapOf("series:show" to 3 * hour, "movie:film" to 2 * hour),
            progress = progress,
            dismissedMonth = null,
        )!!
        assertEquals("2026-09", recap.month.toString())
        assertEquals(5, recap.hours)
        assertEquals(2, recap.episodesFinished)
        assertEquals(1, recap.moviesFinished)
        assertEquals("show", recap.topTitle?.id)
        assertEquals(listOf("show", "film"), recap.titles.map { it.id })
    }

    @Test
    fun recapHidesAfterTheFirstWeekWhenDismissedOrUnderAnHour() {
        val watched = mapOf("movie:film" to 2 * hour)
        assertNull(monthlyRecap(at(2026, 10, 8), zone, watched, emptyList(), null))
        assertNull(monthlyRecap(at(2026, 10, 2), zone, watched, emptyList(), "2026-09"))
        assertNull(monthlyRecap(at(2026, 10, 2), zone, mapOf("movie:film" to hour - 1), emptyList(), null))
    }

    @Test
    fun januaryLooksBackToDecember() {
        val recap = monthlyRecap(at(2027, 1, 1), zone, mapOf("movie:film" to 2 * hour), emptyList(), null)
        assertEquals("2026-12", recap?.month.toString())
    }

    @Test
    fun monthKeyFollowsTheZone() {
        assertEquals("2026-09", viewingMonthKey(at(2026, 9, 30), zone))
    }
}
