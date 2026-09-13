package com.lamphaus.app.ui

import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.MediaType
import com.lamphaus.core.model.WatchProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ContinueWatchingItemsTest {

    @Test
    fun `multiple episode progress rows produce one series card with the latest episode`() {
        val series = media("tt1196946", MediaType.SERIES)
        val olderEpisode = progress(series, videoId = "tt1196946:1:1", updatedAt = 100)
        val latestEpisode = progress(series, videoId = "tt1196946:1:2", updatedAt = 200)

        val items = continueWatchingItems(
            progress = listOf(olderEpisode, latestEpisode),
            catalogMedia = listOf(series),
        )

        assertEquals(listOf(series.stableKey), items.map { it.first.stableKey })
        assertSame(latestEpisode, items.single().second)
    }

    @Test
    fun `snapshot media is deduplicated when catalog has not loaded the title`() {
        val series = media("tt1196946", MediaType.SERIES)
        val first = progress(series, videoId = "tt1196946:1:1", updatedAt = 100)
        val second = progress(series, videoId = "tt1196946:1:2", updatedAt = 200)

        val items = continueWatchingItems(listOf(first, second), catalogMedia = emptyList())

        assertEquals(1, items.size)
        assertSame(second, items.single().second)
    }

    private fun media(id: String, type: MediaType) = MediaPreview(
        id = id,
        type = type,
        rawType = type.name.lowercase(),
        name = id,
    )

    private fun progress(media: MediaPreview, videoId: String, updatedAt: Long) = WatchProgress(
        profileId = "profile",
        mediaKey = media.stableKey,
        videoId = videoId,
        positionMillis = 60_000,
        durationMillis = 600_000,
        completed = false,
        updatedAtEpochMillis = updatedAt,
        preview = media,
    )
}
