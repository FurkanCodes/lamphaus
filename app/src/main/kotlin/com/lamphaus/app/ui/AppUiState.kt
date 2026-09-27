package com.lamphaus.app.ui

import com.lamphaus.core.data.cloud.AccountState
import com.lamphaus.core.data.preferences.ThemePreference
import com.lamphaus.core.model.MediaType
import com.lamphaus.core.model.ArtworkAsset
import com.lamphaus.core.model.ArtworkCandidates
import com.lamphaus.core.model.ArtworkOverride
import com.lamphaus.core.model.ArtworkProviderId
import com.lamphaus.core.model.ArtworkProviderStatus
import com.lamphaus.core.model.DiagnosticsConsent
import com.lamphaus.core.model.SpoilerProtectionSettings
import com.lamphaus.core.model.LibraryEntry
import com.lamphaus.core.model.MediaDetail
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.PairedDevice
import com.lamphaus.core.model.PairingSession
import com.lamphaus.core.model.PlaybackRequest
import com.lamphaus.core.model.PlaybackSettings
import com.lamphaus.core.model.DevicePlaybackConfig
import com.lamphaus.core.model.ProfilePlaybackPreferences
import com.lamphaus.core.model.Profile
import com.lamphaus.core.model.ProviderSubscription
import com.lamphaus.core.model.StreamCandidate
import com.lamphaus.core.model.WatchProgress
import com.lamphaus.core.model.Episode
import com.lamphaus.core.model.CatalogQuery
import com.lamphaus.core.model.DetailEnrichment
import com.lamphaus.core.model.IntegrationStatus

data class ArtworkEditorState(
    val media: MediaPreview,
    val candidates: ArtworkCandidates? = null,
    val selectedPoster: ArtworkAsset? = null,
    val selectedBackdrop: ArtworkAsset? = null,
    val selectedLogo: ArtworkAsset? = null,
    val providerFilter: ArtworkProviderId? = null,
    val loading: Boolean = true,
    val error: String? = null,
) {
    val availableProviders: List<ArtworkProviderId>
        get() {
            val resultProviders = candidates?.providerResults?.map { it.provider }.orEmpty()
            val assetProviders = candidates?.let {
                (it.posters + it.backdrops + it.logos).map { asset -> asset.provider }
            }.orEmpty()
            return (resultProviders + assetProviders).distinct()
        }
    val filteredPosters: List<ArtworkAsset>
        get() = candidates?.posters.orEmpty().filterByProvider(providerFilter)

    val filteredBackdrops: List<ArtworkAsset>
        get() = candidates?.backdrops.orEmpty().filterByProvider(providerFilter)

    val filteredLogos: List<ArtworkAsset>
        get() = candidates?.logos.orEmpty().filterByProvider(providerFilter)
}

private fun List<ArtworkAsset>.filterByProvider(provider: ArtworkProviderId?): List<ArtworkAsset> =
    provider?.let { selected -> filter { it.provider == selected } } ?: this

data class CatalogSection(
    val id: String,
    val providerId: String,
    val title: String,
    val providerName: String,
    val items: List<MediaPreview>,
    val initialLoading: Boolean = false,
    val errorMessage: String? = null,
    val baseQuery: CatalogQuery = CatalogQuery("", ""),
    val supportsSkip: Boolean = false,
    val skipStep: Int = 100,
    val nextSkip: Int = 0,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    val loadMoreError: String? = null,
)

data class CatalogBrowseState(
    val targets: List<CatalogBrowseTarget> = emptyList(),
    val selectedType: String? = null,
    val selectedCatalogId: String? = null,
    val selectedGenre: String? = null,
    val result: CatalogSection? = null,
    val loading: Boolean = false,
    val selectorError: String? = null,
)

data class SourcePickerState(
    val media: MediaPreview,
    val episode: Episode? = null,
    val startFromBeginning: Boolean = false,
    val sources: List<StreamCandidate> = emptyList(),
    val providerLabels: Map<String, String> = emptyMap(),
    val failures: Map<String, String> = emptyMap(),
    val selectedProviderId: String? = null,
    val loading: Boolean = true,
    /** Add-ons that have not answered yet; their sources are appended in add-on order. */
    val pendingProviderCount: Int = 0,
) {
    // Computed once per state (QA-08): with hundreds of sources the getters
    // re-filtered on every read during composition.
    val providerIds: List<String> = sources.map(StreamCandidate::providerId).distinct()

    val visibleSources: List<StreamCandidate> =
        selectedProviderId?.let { id -> sources.filter { it.providerId == id } } ?: sources

    fun selectProvider(providerId: String?) = copy(selectedProviderId = providerId)
}

/** A poster, Continue Watching row, or episode card opened as a context menu. */
data class ContentMenuTarget(
    val media: MediaPreview,
    /** Progress row backing this menu (Continue Watching rows; poster/card lookups). */
    val progress: WatchProgress? = null,
    /** Known episode metadata (detail episode cards); null for posters. */
    val episode: Episode? = null,
    /** Distinguishes row-specific actions without coupling the shared model to either renderer. */
    val origin: ContentMenuOrigin = ContentMenuOrigin.POSTER,
)

enum class ContentMenuOrigin {
    POSTER,
    CONTINUE_WATCHING,
    EPISODE,
    DETAIL,
}

sealed interface ContentMenuAction {
    data object ViewDetails : ContentMenuAction
    data object ToggleLibrary : ContentMenuAction
    data object MarkWatched : ContentMenuAction
    data object MarkUnwatched : ContentMenuAction
    data object RemoveFromContinueWatching : ContentMenuAction
    data object StartFromBeginning : ContentMenuAction
}

data class ContentMenuState(
    val target: ContentMenuTarget? = null,
    /** Episode metadata resolution is running for a Start-from-beginning action. */
    val resolving: Boolean = false,
    /** Resolution failed; the menu offers a local Retry instead of playing. */
    val resolutionError: Boolean = false,
)

/**
 * Action matrix per menu target (SHR-ARC-14: one model, platform renderers).
 * Movie posters get watched controls; series posters deliberately do not,
 * and only rows with progress offer Start from beginning.
 */
fun ContentMenuTarget.menuActions(): List<ContentMenuAction> = buildList {
    add(ContentMenuAction.ViewDetails)
    add(ContentMenuAction.ToggleLibrary)
    when {
        origin == ContentMenuOrigin.CONTINUE_WATCHING -> {
            add(if (progress?.completed == true) ContentMenuAction.MarkUnwatched else ContentMenuAction.MarkWatched)
            add(ContentMenuAction.StartFromBeginning)
            add(ContentMenuAction.RemoveFromContinueWatching)
        }
        episode != null -> {
            add(if (progress?.completed == true) ContentMenuAction.MarkUnwatched else ContentMenuAction.MarkWatched)
            if (progress != null) add(ContentMenuAction.StartFromBeginning)
        }
        media.type == MediaType.MOVIE -> {
            add(if (progress?.completed == true) ContentMenuAction.MarkUnwatched else ContentMenuAction.MarkWatched)
            if (progress != null) add(ContentMenuAction.StartFromBeginning)
        }
    }
}

data class HomeCatalogBatchState(
    val consumedTargetCount: Int = 0,
    val hasMore: Boolean = false,
    /** A window is in flight or prepared rows are still waiting to be revealed. */
    val loadingMore: Boolean = false,
    val loadMoreFailed: Boolean = false,
    /** Prepared rows not yet revealed (see [revealSettledHomeRows]). */
    val pendingRowCount: Int = 0,
)

/** TV Home layouts (TV-CNT-01, TV-CNT-03). */
enum class TvHomeLayout {
    /** A featured hero followed by poster rows. */
    CLASSIC,

    /** No hero; the focused title widens in its row and its details appear below it. */
    SPOTLIGHT,
    ;

    companion object {
        fun fromName(name: String?): TvHomeLayout = entries.firstOrNull { it.name == name } ?: CLASSIC
    }
}

/**
 * The viewer's explicit choice wins; otherwise background artwork is on,
 * except on devices Android classifies as low-memory, where it starts off.
 */
internal fun effectiveBackgroundArtwork(choice: Boolean?, lowRamDevice: Boolean): Boolean =
    choice ?: !lowRamDevice

/** Minimum watch time before an entry qualifies for Continue Watching. */
internal const val CONTINUE_WATCHING_MIN_POSITION_MILLIS = 30_000L

internal fun WatchProgress.isResumable(): Boolean =
    !completed &&
        positionMillis >= CONTINUE_WATCHING_MIN_POSITION_MILLIS &&
        fraction <= 0.98f

/**
 * Produces at most one Continue Watching card per title. A series can have one
 * progress row per episode, and duplicate title keys crash Compose lazy lists.
 * Sorting first preserves the most recently watched episode for each title.
 */
internal fun continueWatchingItems(
    progress: List<WatchProgress>,
    catalogMedia: List<MediaPreview>,
): List<Pair<MediaPreview, WatchProgress>> =
    continueWatchingItems(progress, catalogMedia.associateBy(MediaPreview::stableKey))

/**
 * Same result as [continueWatchingItems] over every catalog title, without
 * flattening the catalog. QA-08: with 100+ addon rows that flattening ran on
 * the main thread for every row that loaded. Only titles with resumable
 * progress are looked up, and the last duplicate wins as in `associateBy`.
 */
internal fun continueWatchingItemsFromSections(
    progress: List<WatchProgress>,
    sections: List<CatalogSection>,
): List<Pair<MediaPreview, WatchProgress>> {
    val wantedKeys = progress.asSequence().filter(WatchProgress::isResumable).mapTo(HashSet()) { it.mediaKey }
    val mediaByKey = HashMap<String, MediaPreview>()
    if (wantedKeys.isNotEmpty()) {
        // Compare ids first; stableKey allocates, so build it only for candidates.
        val wantedIds = wantedKeys.mapTo(HashSet()) { it.substringAfter(':') }
        for (section in sections) {
            for (media in section.items) {
                if (media.id in wantedIds) {
                    val key = media.stableKey
                    if (key in wantedKeys) mediaByKey[key] = media
                }
            }
        }
    }
    return continueWatchingItems(progress, mediaByKey)
}

/** TV-CNT-01: Movies and Series show only the Home catalogs of their type; null keeps every catalog. */
internal fun homeSectionsOfType(sections: List<CatalogSection>, catalogType: String?): List<CatalogSection> =
    if (catalogType == null) sections else sections.filter { it.baseQuery.type.equals(catalogType, ignoreCase = true) }

/** The first [limit] distinct titles in catalog order, stopping as soon as they are found. */
internal fun firstDistinctMedia(sections: List<CatalogSection>, limit: Int): List<MediaPreview> {
    val seen = HashSet<String>()
    val result = ArrayList<MediaPreview>(limit)
    for (section in sections) {
        for (media in section.items) {
            if (seen.add(media.stableKey)) {
                result += media
                if (result.size == limit) return result
            }
        }
    }
    return result
}

private fun continueWatchingItems(
    progress: List<WatchProgress>,
    mediaByKey: Map<String, MediaPreview>,
): List<Pair<MediaPreview, WatchProgress>> {
    return progress
        .asSequence()
        .filter(WatchProgress::isResumable)
        .sortedByDescending(WatchProgress::updatedAtEpochMillis)
        .mapNotNull { row ->
            val media = mediaByKey[row.mediaKey] ?: row.preview ?: return@mapNotNull null
            media to row
        }
        .distinctBy { (media) -> media.stableKey }
        .toList()
}

/** Unfinished rows removed when a title leaves Continue Watching. */
internal fun continueWatchingRemovalRows(
    progress: List<WatchProgress>,
    mediaKey: String,
): List<WatchProgress> = progress.filter { row ->
    row.mediaKey == mediaKey && row.isResumable()
}

data class AppUiState(
    val account: AccountState = AccountState.Loading,
    val profiles: List<Profile> = emptyList(),
    val activeProfileId: String? = null,
    val providers: List<ProviderSubscription> = emptyList(),
    val sections: List<CatalogSection> = emptyList(),
    val homeCatalogBatch: HomeCatalogBatchState = HomeCatalogBatchState(),
    val searchSections: List<CatalogSection> = emptyList(),
    val browse: CatalogBrowseState = CatalogBrowseState(),
    val library: List<LibraryEntry> = emptyList(),
    val progress: List<WatchProgress> = emptyList(),
    val selectedDetail: MediaDetail? = null,
    val artworkOverrides: List<ArtworkOverride> = emptyList(),
    val artworkEditor: ArtworkEditorState? = null,
    val artworkProviders: List<ArtworkProviderStatus> = emptyList(),
    val artworkKeyStatusLoading: Boolean = false,
    val artworkStorageModeChanging: Boolean = false,
    val lastArtworkLookupFailures: Map<ArtworkProviderId, Long> = emptyMap(),
    val artworkProviderCatalogError: String? = null,
    val sourcePicker: SourcePickerState? = null,
    val contentMenu: ContentMenuState = ContentMenuState(),
    val pairingSession: PairingSession? = null,
    val playbackRequest: PlaybackRequest? = null,
    val externalPlaybackUrl: String? = null,
    val configurationUrl: String? = null,
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val dynamicColor: Boolean = true,
    val kenBurnsEnabled: Boolean = true,
    val localOnlyArtworkKeys: Boolean = false,
    /** Effective TV background artwork choice (see [effectiveBackgroundArtwork]). */
    val backgroundArtworkEnabled: Boolean = true,
    val tvHomeLayout: TvHomeLayout = TvHomeLayout.CLASSIC,
    /** Imported Nuvio-compatible stream badges; null when none are imported. */
    val streamBadges: com.lamphaus.core.data.repository.StreamBadgeImport? = null,
    val streamBadgesImporting: Boolean = false,
    val diagnostics: DiagnosticsConsent = DiagnosticsConsent(),
    val spoilerProtection: SpoilerProtectionSettings = SpoilerProtectionSettings(),
    val playbackSettings: PlaybackSettings = PlaybackSettings(),
    val profilePlaybackPreferences: ProfilePlaybackPreferences = ProfilePlaybackPreferences(),
    val devicePlaybackConfig: DevicePlaybackConfig = DevicePlaybackConfig(),
    val pairedDevices: List<PairedDevice> = emptyList(),
    val initialContentLoading: Boolean = true,
    val refreshing: Boolean = false,
    val searching: Boolean = false,
    val message: String? = null,
    /** Provider-neutral enrichment for the open detail; independent of base metadata (SHR-ARC-05). */
    val detailEnrichment: DetailEnrichment? = null,
    val detailEnrichmentLoading: Boolean = false,
    /** Refresh failed with a retryable error; render a local retry in the affected section. */
    val detailEnrichmentFailed: Boolean = false,
    val integrations: List<IntegrationStatus> = emptyList(),
    val integrationsLoading: Boolean = false,
    /** Integration list could not be loaded (not "no integrations configured"). */
    val integrationsFailed: Boolean = false,
) {
    val activeProfile: Profile? get() = profiles.firstOrNull { it.id == activeProfileId }
    val allMedia: List<MediaPreview>
        get() = sections.flatMap(CatalogSection::items).distinctBy(MediaPreview::stableKey)

    /** Everything account-scoped resets; device-local preferences survive.
     *  [account] must be carried over: rebuilding with the Loading default
     *  would strand the UI on the loading screen, since no further auth
     *  status events arrive once the post-delete sign-out has settled. */
    fun clearAccountData() = AppUiState(
        account = account,
        theme = theme,
        dynamicColor = dynamicColor,
        kenBurnsEnabled = kenBurnsEnabled,
        localOnlyArtworkKeys = localOnlyArtworkKeys,
        backgroundArtworkEnabled = backgroundArtworkEnabled,
        tvHomeLayout = tvHomeLayout,
        diagnostics = diagnostics,
        spoilerProtection = spoilerProtection,
        playbackSettings = playbackSettings,
        devicePlaybackConfig = devicePlaybackConfig,
        initialContentLoading = false,
    )
}
