package com.lamphaus.app.ui

import com.lamphaus.core.model.ArtworkAsset
import com.lamphaus.core.model.ArtworkOverride
import com.lamphaus.core.model.ArtworkProviderId
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
                    poster = ArtworkAsset(ArtworkProviderId.TMDB, "/poster.jpg"),
                    backdrop = ArtworkAsset(ArtworkProviderId.FANART, "https://fanart.example/backdrop.jpg"),
                    logo = ArtworkAsset(ArtworkProviderId("fixture_art"), "https://fixture.example/logo.png"),
                ),
            ),
        ).resolve(media)
        assertEquals("https://image.tmdb.org/t/p/w500/poster.jpg", resolved.media.posterUrl)
        assertEquals("https://fanart.example/backdrop.jpg", resolved.media.backgroundUrl)
        assertEquals("https://fixture.example/logo.png", resolved.media.logoUrl)
        assertTrue(resolved.hasOverride)
    }

    @Test
    fun `partial and invalid overrides preserve catalog artwork`() {
        val partial = ArtworkResolver(
            mapOf(
                media.stableKey to ArtworkOverride(
                    profileId = "profile",
                    mediaKey = media.stableKey,
                    backdrop = ArtworkAsset(ArtworkProviderId.TMDB, " /custom.jpg "),
                ),
            ),
        ).resolve(media)
        val invalid = ArtworkResolver(
            mapOf(
                media.stableKey to ArtworkOverride(
                    profileId = "profile",
                    mediaKey = media.stableKey,
                    poster = ArtworkAsset(ArtworkProviderId("fixture_art"), "http://fixture.example/poster.jpg"),
                    backdrop = ArtworkAsset(ArtworkProviderId("fixture_art"), "not-a-url"),
                ),
            ),
        ).resolve(media)
        assertEquals("https://catalog.example/poster.jpg", partial.media.posterUrl)
        assertEquals("https://image.tmdb.org/t/p/original/custom.jpg", partial.media.backgroundUrl)
        assertEquals(media.posterUrl, invalid.media.posterUrl)
        assertEquals(media.backgroundUrl, invalid.media.backgroundUrl)
        assertFalse(invalid.hasOverride)
    }
}
