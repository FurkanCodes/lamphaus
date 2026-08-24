package com.lamphaus.app.ui

import com.lamphaus.core.data.cloud.AccountState
import com.lamphaus.core.data.preferences.ThemePreference
import com.lamphaus.core.model.DiagnosticsConsent
import com.lamphaus.core.model.LibraryEntry
import com.lamphaus.core.model.MediaDetail
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.PairingSession
import com.lamphaus.core.model.PlaybackRequest
import com.lamphaus.core.model.Profile
import com.lamphaus.core.model.ProviderSubscription
import com.lamphaus.core.model.WatchProgress

data class CatalogSection(
    val id: String,
    val title: String,
    val providerName: String,
    val items: List<MediaPreview>,
    val errorMessage: String? = null,
)

data class AppUiState(
    val account: AccountState = AccountState.Loading,
    val profiles: List<Profile> = emptyList(),
    val activeProfileId: String? = null,
    val providers: List<ProviderSubscription> = emptyList(),
    val sections: List<CatalogSection> = emptyList(),
    val searchResults: List<MediaPreview> = emptyList(),
    val library: List<LibraryEntry> = emptyList(),
    val progress: List<WatchProgress> = emptyList(),
    val selectedDetail: MediaDetail? = null,
    val pairingSession: PairingSession? = null,
    val playbackRequest: PlaybackRequest? = null,
    val externalPlaybackUrl: String? = null,
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val dynamicColor: Boolean = true,
    val diagnostics: DiagnosticsConsent = DiagnosticsConsent(),
    val refreshing: Boolean = false,
    val searching: Boolean = false,
    val message: String? = null,
) {
    val activeProfile: Profile? get() = profiles.firstOrNull { it.id == activeProfileId }
    val allMedia: List<MediaPreview>
        get() = sections.flatMap(CatalogSection::items).distinctBy(MediaPreview::stableKey)
}
