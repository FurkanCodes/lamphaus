package com.lamphaus.app.ui

import com.lamphaus.core.model.ArtworkAsset
import com.lamphaus.core.model.ArtworkCandidates
import com.lamphaus.core.model.ArtworkLookupStatus
import com.lamphaus.core.model.ArtworkProvider
import com.lamphaus.core.model.ArtworkProviderResult
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ArtworkEditorStateTest {
    private val media = MediaPreview(
        id = "tt1234567",
        type = MediaType.MOVIE,
        rawType = "movie",
        name = "Example",
    )
    private val tmdb = ArtworkAsset(ArtworkProvider.TMDB, "/same.jpg")
    private val fanart = ArtworkAsset(ArtworkProvider.FANART, "https://fanart.example/same.jpg")

    @Test
    fun `filters retain provider identity and selected assets`() {
        val state = ArtworkEditorState(
            media = media,
            candidates = ArtworkCandidates(
                posters = listOf(tmdb, fanart),
                providerResults = listOf(
                    ArtworkProviderResult(ArtworkProvider.TMDB, ArtworkLookupStatus.SUCCESS),
                    ArtworkProviderResult(ArtworkProvider.FANART, ArtworkLookupStatus.SUCCESS),
                ),
            ),
            selectedPoster = tmdb,
        )

        assertEquals(listOf(ArtworkProvider.TMDB, ArtworkProvider.FANART), state.availableProviders)
        assertEquals(listOf(tmdb, fanart), state.filteredPosters)
        val fanartState = state.copy(providerFilter = ArtworkProvider.FANART)
        assertEquals(listOf(fanart), fanartState.filteredPosters)
        assertSame(tmdb, fanartState.selectedPoster)
    }
}
