package com.lamphaus.app.ui

import com.lamphaus.core.model.ArtworkOverride
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkResolverTest {
    private val media = MediaPreview(
        id = "tt1234567",
        type = MediaType.MOVIE,
        rawType = "movie",
        name = "Example",
        posterUrl = "https://catalog.example/poster.jpg",
        backgroundUrl = "https://catalog.example/backdrop.jpg",
        logoUrl = "https://catalog.example/logo.png",
    )

    @Test
    fun `override paths resolve to TMDB image URLs`() {
        val resolved = ArtworkResolver(
            mapOf(
                media.stableKey to ArtworkOverride(
                    profileId = "profile",
                    mediaKey = media.stableKey,
                    posterPath = "/poster.jpg",
                    backdropPath = "backdrop.jpg",
                    logoPath = "/logo.png",
                ),
            ),
        ).resolve(media)

        assertEquals("https://image.tmdb.org/t/p/w500/poster.jpg", resolved.media.posterUrl)
        assertEquals("https://image.tmdb.org/t/p/original/backdrop.jpg", resolved.media.backgroundUrl)
        assertEquals("https://image.tmdb.org/t/p/w500/logo.png", resolved.media.logoUrl)
        assertTrue(resolved.hasOverride)
    }

    @Test
    fun `partial and blank overrides preserve catalog artwork`() {
        val partial = ArtworkResolver(
            mapOf(
                media.stableKey to ArtworkOverride(
                    profileId = "profile",
                    mediaKey = media.stableKey,
                    backdropPath = " /custom.jpg ",
                ),
            ),
        ).resolve(media)
        val blank = ArtworkResolver(
            mapOf(
                media.stableKey to ArtworkOverride(
                    profileId = "profile",
                    mediaKey = media.stableKey,
                    posterPath = " ",
                    backdropPath = null,
                ),
            ),
        ).resolve(media)

        assertEquals("https://catalog.example/poster.jpg", partial.media.posterUrl)
        assertEquals("https://image.tmdb.org/t/p/original/custom.jpg", partial.media.backgroundUrl)
        assertEquals(media.posterUrl, blank.media.posterUrl)
        assertEquals(media.backgroundUrl, blank.media.backgroundUrl)
        assertFalse(blank.hasOverride)
    }
}
