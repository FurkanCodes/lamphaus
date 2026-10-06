package com.lamphaus.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Auto engine choice starts anime on libmpv (PLY-ENG-01). */
class AnimeDetectionTest {

    private fun preview(id: String = "tt0111161", rawType: String = "movie", genres: List<String> = emptyList()) =
        MediaPreview(id = id, type = MediaType.MOVIE, rawType = rawType, name = "Title", genres = genres)

    @Test
    fun `anime type, genre, or catalog id counts as anime`() {
        assertTrue(preview(rawType = "anime").looksLikeAnime())
        assertTrue(preview(genres = listOf("Action", "Anime")).looksLikeAnime())
        assertTrue(preview(id = "kitsu:1376").looksLikeAnime())
        assertTrue(preview(id = "mal:5114").looksLikeAnime())
        assertTrue(preview(id = "anilist:21").looksLikeAnime())
        assertTrue(preview(id = "anidb:4563").looksLikeAnime())
    }

    @Test
    fun `other titles are not anime`() {
        assertFalse(preview().looksLikeAnime())
        assertFalse(preview(id = "tmdb:603", genres = listOf("Animation")).looksLikeAnime())
    }
}
