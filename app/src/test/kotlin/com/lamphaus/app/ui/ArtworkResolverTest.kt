package com.lamphaus.app.ui

import com.lamphaus.core.model.ArtworkAsset
import com.lamphaus.core.model.ArtworkOverride
import com.lamphaus.core.model.ArtworkProvider
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
    fun `override assets resolve to provider-specific image URLs`() {
        val resolved = ArtworkResolver(
            mapOf(
                media.stableKey to ArtworkOverride(
                    profileId = "profile",
                    mediaKey = media.stableKey,
                    poster = ArtworkAsset(ArtworkProvider.TMDB, "/poster.jpg"),
                    backdrop = ArtworkAsset(ArtworkProvider.FANART, "https://fanart.example/backdrop.jpg"),
                    logo = ArtworkAsset(ArtworkProvider.FANART, "https://fanart.example/logo.png"),
                ),
            ),
        ).resolve(media)

        assertEquals("https://image.tmdb.org/t/p/w500/poster.jpg", resolved.media.posterUrl)
        assertEquals("https://fanart.example/backdrop.jpg", resolved.media.backgroundUrl)
        assertEquals("https://fanart.example/logo.png", resolved.media.logoUrl)
        assertTrue(resolved.hasOverride)
    }

    @Test
    fun `partial and invalid overrides preserve catalog artwork`() {
        val partial = ArtworkResolver(
            mapOf(
                media.stableKey to ArtworkOverride(
                    profileId = "profile",
                    mediaKey = media.stableKey,
                    backdrop = ArtworkAsset(ArtworkProvider.TMDB, " /custom.jpg "),
                ),
            ),
        ).resolve(media)
        val invalidFanart = ArtworkResolver(
            mapOf(
                media.stableKey to ArtworkOverride(
                    profileId = "profile",
                    mediaKey = media.stableKey,
                    poster = ArtworkAsset(ArtworkProvider.FANART, "http://fanart.example/poster.jpg"),
                    backdrop = ArtworkAsset(ArtworkProvider.FANART, " "),
                ),
            ),
        ).resolve(media)

        assertEquals("https://catalog.example/poster.jpg", partial.media.posterUrl)
        assertEquals("https://image.tmdb.org/t/p/original/custom.jpg", partial.media.backgroundUrl)
        assertEquals(media.posterUrl, invalidFanart.media.posterUrl)
        assertEquals(media.backgroundUrl, invalidFanart.media.backgroundUrl)
        assertFalse(invalidFanart.hasOverride)
    }
}
