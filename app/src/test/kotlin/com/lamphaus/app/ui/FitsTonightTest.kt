package com.lamphaus.app.ui

import com.lamphaus.core.model.Episode
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.MediaType
import com.lamphaus.core.model.WatchProgress
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FitsTonightTest {
    private fun at(hour: Int, minute: Int) =
        LocalDateTime.of(2026, 9, 28, hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun movie(id: String, runtime: Int?, type: MediaType = MediaType.MOVIE) =
        MediaPreview(id = id, type = type, rawType = type.name.lowercase(), name = id, runtimeMinutes = runtime, releaseYear = 2000)

    private fun section(id: String, vararg items: MediaPreview) =
        CatalogSection(id = id, providerId = "p", title = id, providerName = "P", items = items.toList())

    @Test
    fun `the row belongs to the evening before bedtime`() {
        assertEquals(90, minutesUntilBedtime(at(22, 0), 23 * 60 + 30, ZoneOffset.UTC))
        assertEquals(150, minutesUntilBedtime(at(22, 0), 30, ZoneOffset.UTC))
        assertNull(minutesUntilBedtime(at(12, 0), 23 * 60 + 30, ZoneOffset.UTC))
        assertNull(minutesUntilBedtime(at(23, 45), 23 * 60 + 30, ZoneOffset.UTC))
    }

    @Test
    fun `only unfinished released movies that end in time`() {
        val sections = listOf(
            section("a", movie("short", 95), movie("long", 170), movie("unknown", null), movie("show", 45, MediaType.SERIES)),
            section("b", movie("short", 95), movie("watched", 80), movie("future", 90).copy(releaseYear = 2999)),
        )
        val picked = fitsTonight(sections, completedVideoIds = setOf("watched"), minutesLeft = 120, nowEpochMillis = at(21, 0))
        assertEquals(listOf("short"), picked.map(MediaPreview::id))
    }

    @Test
    fun `the generated row never feeds itself`() {
        val sections = listOf(section(FITS_TONIGHT_SECTION_ID, movie("short", 95)))
        assertEquals(emptyList<MediaPreview>(), fitsTonight(sections, emptySet(), minutesLeft = 120))
    }

    private val episodes = (1..5).map { Episode(id = "s:2:$it", title = "E$it", season = 2, episode = it, releasedAtEpochMillis = 0) }

    private fun row(number: Int, completed: Boolean, positionMinutes: Long = 0) = WatchProgress(
        profileId = "p",
        mediaKey = "series:s",
        videoId = "s:2:$number",
        positionMillis = positionMinutes * 60_000,
        durationMillis = 50 * 60_000,
        completed = completed,
        updatedAtEpochMillis = 0,
    )

    @Test
    fun `season time left counts unfinished episodes and the rest of a started one`() {
        val progress = listOf(row(1, completed = true), row(2, completed = false, positionMinutes = 20))
        assertEquals(
            SeasonTimeLeft(episodes = 4, minutes = 30 + 3 * 50),
            seasonTimeLeft(episodes, 2, progress, emptySet(), runtimeMinutes = 50),
        )
    }

    @Test
    fun `season time left needs a begun season and a running time`() {
        assertNull(seasonTimeLeft(episodes, 2, emptyList(), emptySet(), runtimeMinutes = 50))
        assertNull(seasonTimeLeft(episodes, 2, listOf(row(1, completed = true)), emptySet(), runtimeMinutes = null))
        val all = episodes.map { it.id }.toSet()
        assertNull(seasonTimeLeft(episodes, 2, emptyList(), all, runtimeMinutes = 50))
    }
}
