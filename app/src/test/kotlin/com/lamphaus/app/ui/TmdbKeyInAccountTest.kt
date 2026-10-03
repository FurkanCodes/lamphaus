package com.lamphaus.app.ui

import com.lamphaus.core.model.ArtworkProviderId
import com.lamphaus.core.model.ArtworkProviderStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cast pages read the viewer's own TMDB key from the account (MOB-SRCH-01). */
class TmdbKeyInAccountTest {
    private val tmdbSaved = ArtworkProviderStatus(ArtworkProviderId.TMDB, configured = true)
    private val fanartSaved = ArtworkProviderStatus(ArtworkProviderId.FANART, configured = true)

    @Test
    fun `a TMDB key saved to the account is usable`() {
        assertTrue(AppUiState(artworkProviders = listOf(tmdbSaved)).tmdbKeyInAccount)
    }

    @Test
    fun `no TMDB key means no cloud lookups`() {
        assertFalse(AppUiState().tmdbKeyInAccount)
        assertFalse(AppUiState(artworkProviders = listOf(fanartSaved)).tmdbKeyInAccount)
        assertFalse(AppUiState(artworkProviders = listOf(tmdbSaved.copy(configured = false))).tmdbKeyInAccount)
    }

    @Test
    fun `local-only keys never reach the cloud`() {
        assertFalse(AppUiState(artworkProviders = listOf(tmdbSaved), localOnlyArtworkKeys = true).tmdbKeyInAccount)
    }
}
