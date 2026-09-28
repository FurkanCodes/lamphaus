package com.lamphaus.core.model

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseDatesTest {
    private val zone = ZoneId.of("Europe/Istanbul")
    private val now = ZonedDateTime.of(2026, 9, 28, 22, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun at(day: Int, hour: Int = 3, month: Int = 9) =
        ZonedDateTime.of(2026, month, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun countdown(millis: Long) = releaseCountdown(millis, now, zone)

    @Test
    fun `countdown follows the viewer's calendar days`() {
        assertEquals(ReleaseCountdown.Released, countdown(at(28, hour = 21)))
        assertEquals(ReleaseCountdown.Today, countdown(at(28, hour = 23)))
        assertEquals(ReleaseCountdown.Tomorrow, countdown(at(29, hour = 1)))
        assertEquals(ReleaseCountdown.InDays(2), countdown(at(30)))
        assertEquals(ReleaseCountdown.InDays(7), countdown(at(5, month = 10)))
        assertEquals(ReleaseCountdown.OnDate(at(6, month = 10)), countdown(at(6, month = 10)))
    }

    @Test
    fun `unreleased uses the full date first, then the year`() {
        val title = MediaPreview(id = "tt1", type = MediaType.MOVIE, rawType = "movie", name = "Title")

        assertTrue(title.copy(releasedAtEpochMillis = at(1, month = 10)).isUnreleased(now, zone))
        assertFalse(title.copy(releasedAtEpochMillis = at(1), releaseYear = 2027).isUnreleased(now, zone))
        assertTrue(title.copy(releaseYear = 2027).isUnreleased(now, zone))
        assertFalse(title.copy(releaseYear = 2026).isUnreleased(now, zone))
        assertFalse(title.isUnreleased(now, zone))
    }
}
