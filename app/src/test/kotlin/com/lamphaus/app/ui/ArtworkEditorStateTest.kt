package com.lamphaus.app.ui

import com.lamphaus.core.model.ArtworkAsset
import com.lamphaus.core.model.ArtworkCandidates
import com.lamphaus.core.model.ArtworkLookupStatus
import com.lamphaus.core.model.ArtworkProviderId
import com.lamphaus.core.model.ArtworkProviderResult
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ArtworkEditorStateTest {
    private val media = MediaPreview(id = "tt1234567", type = MediaType.MOVIE, rawType = "movie", name = "Example")
    private val tmdb = ArtworkAsset(ArtworkProviderId.TMDB, "/same.jpg")
    private val fanart = ArtworkAsset(ArtworkProviderId.FANART, "https://fanart.example/same.jpg")
    private val fixture = ArtworkAsset(ArtworkProviderId("fixture_art"), "https://fixture.example/same.jpg")

    @Test
    fun `filters retain server provider order and selected assets`() {
        val state = ArtworkEditorState(
            media = media,
            candidates = ArtworkCandidates(
                posters = listOf(tmdb, fanart, fixture),
                providerResults = listOf(
                    ArtworkProviderResult(ArtworkProviderId.TMDB, ArtworkLookupStatus.SUCCESS, "TMDB"),
                    ArtworkProviderResult(ArtworkProviderId("fixture_art"), ArtworkLookupStatus.SUCCESS, "Fixture Art"),
                    ArtworkProviderResult(ArtworkProviderId.FANART, ArtworkLookupStatus.SUCCESS, "Fanart.tv"),
                ),
            ),
            selectedPoster = tmdb,
        )
        assertEquals(listOf(ArtworkProviderId.TMDB, ArtworkProviderId("fixture_art"), ArtworkProviderId.FANART), state.availableProviders)
        assertEquals(listOf(tmdb, fanart, fixture), state.filteredPosters)
        val fixtureState = state.copy(providerFilter = ArtworkProviderId("fixture_art"))
        assertEquals(listOf(fixture), fixtureState.filteredPosters)
        assertSame(tmdb, fixtureState.selectedPoster)
    }
}
