package com.lamphaus.app.ui

import com.lamphaus.core.model.Episode
import com.lamphaus.core.model.MediaDetail
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataMergeTest {
    @Test
    fun `catalog preview artwork and rating remain authoritative while metadata fills gaps`() {
        val catalogPreview = MediaPreview(
            id = "tt1234567",
            type = MediaType.MOVIE,
            rawType = "movie",
            name = "Example",
            posterUrl = "https://catalog.example/native-poster.jpg",
            backgroundUrl = "https://catalog.example/native-backdrop.jpg",
            rating = 8.8,
            providerIds = setOf("catalog-provider"),
        )
        val fallbackDetail = MediaDetail(
            preview = MediaPreview(
                id = catalogPreview.id,
                type = catalogPreview.type,
                rawType = catalogPreview.rawType,
                name = catalogPreview.name,
                posterUrl = "https://meta.example/fallback-poster.jpg",
                backgroundUrl = "https://meta.example/fallback-backdrop.jpg",
                description = "Filled description",
                rating = 6.1,
                providerIds = setOf("meta-provider"),
            ),
            runtimeMinutes = 96,
            episodes = listOf(Episode(id = "episode-1", title = "Pilot")),
        )

        val merged = MediaDetail(catalogPreview).merge(fallbackDetail)

        assertEquals("https://catalog.example/native-poster.jpg", merged.preview.posterUrl)
        assertEquals("https://catalog.example/native-backdrop.jpg", merged.preview.backgroundUrl)
        assertEquals(8.8, merged.preview.rating)
        assertEquals("Filled description", merged.preview.description)
        assertEquals(96, merged.runtimeMinutes)
        assertEquals(listOf("episode-1"), merged.episodes.map(Episode::id))
        assertEquals(setOf("catalog-provider", "meta-provider"), merged.preview.providerIds)
    }

    @Test
    fun `episode merge preserves existing values and fills missing fields`() {
        val primary = Episode(id = "episode-1", title = "Pilot", season = 1)
        val fallback = Episode(id = "episode-1", title = "Fallback", episode = 1, overview = "Overview")

        val merged = primary.merge(fallback)

        assertEquals("Pilot", merged.title)
        assertEquals(1, merged.season)
        assertEquals(1, merged.episode)
        assertEquals("Overview", merged.overview)
    }
}
