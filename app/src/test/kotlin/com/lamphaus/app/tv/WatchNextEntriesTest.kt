package com.lamphaus.app.tv

import com.lamphaus.app.ui.UpNextItem
import com.lamphaus.app.ui.UpNextKind
import com.lamphaus.core.model.Episode
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.MediaType
import com.lamphaus.core.model.WatchProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchNextEntriesTest {
    private val movie = MediaPreview(id = "tt1", type = MediaType.MOVIE, rawType = "movie", name = "Movie", backgroundUrl = "https://img/b.jpg")
    private val show = MediaPreview(id = "tt2", type = MediaType.SERIES, rawType = "series", name = "Show", posterUrl = "https://img/p.jpg")

    private fun row(media: MediaPreview, videoId: String, position: Long, updatedAt: Long, completed: Boolean = false) = WatchProgress(
        profileId = "p",
        mediaKey = media.stableKey,
        videoId = videoId,
        positionMillis = position,
        durationMillis = 6_000_000,
        completed = completed,
        updatedAtEpochMillis = updatedAt,
        preview = media,
        episodeLabel = if (media.type == MediaType.SERIES) "S1 · E2" else null,
    )

    @Test
    fun `one continue card per title, newest first, with art and progress`() {
        val entries = watchNextEntries(
            progress = listOf(
                row(movie, "tt1", position = 600_000, updatedAt = 10),
                row(show, "tt2:1:1", position = 300_000, updatedAt = 20),
                row(show, "tt2:1:2", position = 900_000, updatedAt = 30),
            ),
            upNext = emptyList(),
        )
        assertEquals(listOf(show.stableKey, movie.stableKey), entries.map { it.mediaKey })
        val episode = entries.first()
        assertEquals(WatchNextEntry.Kind.CONTINUE, episode.kind)
        assertEquals(900_000L, episode.positionMillis)
        assertEquals("S1 · E2", episode.episodeTitle)
        assertEquals(true, episode.artIsPoster)
        assertEquals(false, entries.last().artIsPoster)
    }

    @Test
    fun `finished and barely started titles are left off`() {
        val entries = watchNextEntries(
            progress = listOf(
                row(movie, "tt1", position = 6_000_000, updatedAt = 10, completed = true),
                row(show, "tt2:1:1", position = 5_000, updatedAt = 20),
            ),
            upNext = emptyList(),
        )
        assertEquals(emptyList<WatchNextEntry>(), entries)
    }

    @Test
    fun `aired up next episodes become next or new cards, upcoming ones do not`() {
        val finished = row(show, "tt2:1:1", position = 6_000_000, updatedAt = 50, completed = true)
        val next = Episode(id = "tt2:1:2", title = "Two", season = 1, episode = 2, releasedAtEpochMillis = 0)
        val upcoming = next.copy(id = "tt2:1:3", releasedAtEpochMillis = Long.MAX_VALUE)
        val entries = watchNextEntries(
            progress = listOf(finished),
            upNext = listOf(
                UpNextItem(show, next, UpNextKind.NEW, finished),
                UpNextItem(show.copy(id = "tt3"), upcoming, UpNextKind.UPCOMING, finished),
            ),
            nowEpochMillis = 1_000,
        )
        assertEquals(1, entries.size)
        assertEquals(WatchNextEntry.Kind.NEW, entries.single().kind)
        assertEquals(2, entries.single().episode)
    }
}
