package com.lamphaus.app.ui

import com.lamphaus.core.model.Episode
import com.lamphaus.core.model.MediaType
import com.lamphaus.core.model.MediaPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataPresentationTest {
    @Test
    fun `metadata presentation trims values deduplicates genres and formats valid ratings`() {
        val preview = MediaPreview(
            id = "title",
            type = MediaType.MOVIE,
            rawType = "movie",
            name = "Title",
            releaseYear = 2026,
            genres = listOf(" Drama ", "Drama", "", "Science fiction"),
            contentRating = " PG-13 ",
            rating = 8.35,
        )

        assertEquals(
            MediaMetadataPresentation(
                year = 2026,
                runtimeMinutes = null,
                contentRating = "PG-13",
                ratingText = "8.4",
                genres = listOf("Drama", "Science fiction"),
            ),
            preview.metadataPresentation(),
        )
    }

    @Test
    fun `metadata presentation omits invalid ratings and limits genres`() {
        val preview = MediaPreview(
            id = "title",
            type = MediaType.MOVIE,
            rawType = "movie",
            name = "Title",
            genres = listOf("Drama", "Mystery", "Adventure"),
            rating = 10.1,
        )

        val presentation = preview.metadataPresentation(maxGenres = 2)

        assertNull(presentation.ratingText)
        assertEquals(listOf("Drama", "Mystery"), presentation.genres)
    }

    @Test
    fun `episode number parts do not invent zero values`() {
        assertEquals(EpisodeNumberParts(1, 2), Episode("id", "Episode", 1, 2).numberParts())
        assertEquals(EpisodeNumberParts(1, null), Episode("id", "Episode", 1, null).numberParts())
        assertFalse(Episode("id", "Episode").numberParts().isPresent)
        assertTrue(Episode("id", "Episode", episode = 4).numberParts().isPresent)
    }
}
