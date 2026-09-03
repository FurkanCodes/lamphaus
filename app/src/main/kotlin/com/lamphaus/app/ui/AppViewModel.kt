package com.lamphaus.app.ui
import android.util.Log


import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lamphaus.app.BuildConfig
import com.lamphaus.app.AppContainer
import com.lamphaus.core.data.repository.reconcileLibrary
import com.lamphaus.core.data.repository.reconcileProgress
import com.lamphaus.core.data.cloud.AccountState
import com.lamphaus.core.data.cloud.CloudLog
import com.lamphaus.core.data.cloud.CloudNotConfiguredException
import com.lamphaus.core.data.cloud.ArtworkKeyInvalidException
import com.lamphaus.core.data.cloud.ArtworkKeysNotConfiguredException
import com.lamphaus.core.data.cloud.IntegrationInvalidCredentialException
import com.lamphaus.core.model.IntegrationStatus
import com.lamphaus.core.data.preferences.SyncedSettings
import com.lamphaus.core.data.preferences.ThemePreference
import com.lamphaus.core.model.SpoilerProtectionSettings
import com.lamphaus.core.model.ArtworkAsset
import com.lamphaus.core.model.ArtworkLookupStatus
import com.lamphaus.core.model.ArtworkOverride
import com.lamphaus.core.model.ArtworkProviderId
import com.lamphaus.core.model.CatalogQuery
import com.lamphaus.core.model.DiagnosticsConsent
import com.lamphaus.core.model.DeviceGrant
import com.lamphaus.core.model.LibraryEntry
import com.lamphaus.core.model.ProfileKind
import com.lamphaus.core.model.ProviderResult
import com.lamphaus.core.model.ProviderManifest
import com.lamphaus.core.model.ProviderSubscription
import com.lamphaus.core.model.PlaybackRequest
import com.lamphaus.core.model.PlaybackSettings
import com.lamphaus.core.model.PlaybackSource
import com.lamphaus.core.model.Episode
import com.lamphaus.core.model.StreamCandidate
import com.lamphaus.core.model.SubtitleTrack
import com.lamphaus.core.model.MediaType
import com.lamphaus.core.model.MediaDetail
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.nextEpisodeAfter
import com.lamphaus.core.model.enrichmentMediaKey
import com.lamphaus.core.model.playbackQueueFrom
import com.lamphaus.core.model.Profile
import com.lamphaus.core.model.PairingSession
import java.util.UUID
import java.net.URI
import com.lamphaus.core.model.WatchProgress
import java.util.Calendar
import java.security.MessageDigest

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.CancellationException

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

private const val CONTENT_RESOLVE_TIMEOUT_MILLIS = 15_000L
private const val CLOUD_SYNC_LOG_TAG = "Lamphaus.Sync"
private val DEVICE_BINDING_BACKOFF_MILLIS = longArrayOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L)
private const val ARTWORK_KEYS_NOT_CONFIGURED_MESSAGE =
    "Artwork keys aren't configured. Add a provider key in Settings > Artwork."
private const val ARTWORK_KEYS_NOT_CONFIGURED_EDITOR_ERROR =
    "Artwork keys aren't configured. Add a provider key in Settings > Artwork to load artwork."
private const val HOME_CATALOG_LOG_TAG = "Lamphaus.Home"
private fun homeLog(message: String) = Log.d(HOME_CATALOG_LOG_TAG, message)



internal fun deviceBindingBackoffMillis(attempt: Int): Long =
    DEVICE_BINDING_BACKOFF_MILLIS[attempt.coerceIn(0, DEVICE_BINDING_BACKOFF_MILLIS.lastIndex)]

internal fun shouldRetryDeviceBinding(account: AccountState, result: Result<Unit>): Boolean =
    account is AccountState.SignedIn && result.isFailure

internal const val DEVICE_UNBINDABLE_ERROR = "DEVICE_NOT_FOUND_OR_FORBIDDEN"

/**
 * The register RPC answers a device row that is gone, revoked, or owned by
 * someone else with this marker. Retrying cannot succeed — the stored device
 * binding is stale and must be dropped (re-pairing mints a fresh one).
 */
internal fun isTerminalDeviceBindingError(error: Throwable?): Boolean {
    var current: Throwable? = error
    while (current != null) {
        if (current.message?.contains(DEVICE_UNBINDABLE_ERROR) == true) return true
        current = current.cause
    }
    return false
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val mutableState = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = mutableState.asStateFlow()
    private val catalogRefreshGate = CatalogRefreshGate()
    private var refreshJob: Job? = null
    private var homeCatalogBatchJob: Job? = null
    private var homeCatalogGeneration = 0L
    private var homeCatalogLoader: HomeCatalogLoader? = null

    private var cloudSyncJob: Job? = null
    private var cloudSyncUserId: String? = null
    private var defaultCatalogJob: Job? = null
    private var searchJob: Job? = null
    private var browseJob: Job? = null
    private val pageJobs = mutableMapOf<String, Job>()
    private var detailJob: Job? = null
    private var enrichmentJob: Job? = null
    private var sourceJob: Job? = null

    /**
     * Recently opened details so re-entering a title renders complete
     * metadata instantly; the provider refresh below still lands behind it
     * (SHR-ARC-13). Main-confined: all readers/writers run on viewModelScope.
     */
    private val recentlyLoadedDetails = object : LinkedHashMap<String, MediaDetail>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MediaDetail>): Boolean =
            size > 24
    }

    init {
        viewModelScope.launch {
            combine(
                container.accountGateway.state,
                container.libraryRepository.profiles(),
                container.libraryRepository.providers(),
                container.preferences.settings,
            ) { account, profiles, providers, settings ->
                Snapshot(
                    account = account,
                    profiles = profiles,
                    providers = providers,
                    activeProfileId = settings.activeProfileId,
                    theme = settings.theme,
                    dynamicColor = settings.dynamicColor,
                    kenBurnsEnabled = settings.kenBurnsEnabled,
                    localOnlyArtworkKeys = settings.localOnlyArtworkKeys,
                    diagnostics = settings.diagnostics,
                    spoilerProtection = settings.spoilerProtection,
                    playbackSettings = settings.playback,
                )
            }.collectLatest { snapshot ->
                val activeId = snapshot.activeProfileId?.takeIf { id -> snapshot.profiles.any { it.id == id } }
                    ?: snapshot.profiles.firstOrNull()?.id
                var leftAnAccount = false
                mutableState.update { current ->
                    val previousUserId = (current.account as? AccountState.SignedIn)?.userId
                    val nextUserId = (snapshot.account as? AccountState.SignedIn)?.userId
                    val accountChanged = nextUserId != null && nextUserId != previousUserId
                    // Loading is the auth flow re-resolving (every task relaunch
                    // replays Initializing → SignedIn while a live collector
                    // still holds SignedIn); only an explicit SignedOut means
                    // the user actually left. Treating Loading as a departure
                    // wiped freshly-saved local progress on every relaunch.
                    leftAnAccount = snapshot.account is AccountState.SignedOut && previousUserId != null
                    current.copy(
                        account = snapshot.account,
                        profiles = snapshot.profiles,
                        providers = snapshot.providers,
                        // Without this the UI state's activeProfileId stayed
                        // null forever, so the Room progress/library observers
                        // and per-profile cloud channels never subscribed:
                        // Continue Watching, Library, resume and re-hydration
                        // were all dead while the data sat in Room.
                        activeProfileId = activeId,
                        sections = if (accountChanged || nextUserId == null) emptyList() else current.sections,
                        homeCatalogBatch = if (accountChanged || nextUserId == null) {
                            HomeCatalogBatchState()
                        } else {
                            current.homeCatalogBatch
                        },
                        artworkOverrides = if (accountChanged || nextUserId == null) emptyList() else current.artworkOverrides,
                        artworkEditor = if (accountChanged || nextUserId == null) null else current.artworkEditor,
                        artworkProviders = if (accountChanged || nextUserId == null) {
                            emptyList()
                        } else {
                            current.artworkProviders
                        },
                        artworkKeyStatusLoading = if (accountChanged || nextUserId == null) false else current.artworkKeyStatusLoading,
                        lastArtworkLookupFailures = if (accountChanged || nextUserId == null) {
                            emptyMap()
                        } else {
                            current.lastArtworkLookupFailures
                        },
                        artworkProviderCatalogError = if (accountChanged || nextUserId == null) null else current.artworkProviderCatalogError,
                        theme = snapshot.theme,
                        dynamicColor = snapshot.dynamicColor,
                        kenBurnsEnabled = snapshot.kenBurnsEnabled,
                        localOnlyArtworkKeys = snapshot.localOnlyArtworkKeys,
                        diagnostics = snapshot.diagnostics,
                        spoilerProtection = snapshot.spoilerProtection,
                        playbackSettings = snapshot.playbackSettings,
                        initialContentLoading = if (accountChanged || nextUserId == null) {
                            true
                        } else {
                            current.initialContentLoading
                        },
                    )
                }
                if (activeId != snapshot.activeProfileId) container.preferences.setActiveProfile(activeId)
                if (snapshot.account is AccountState.SignedIn) {
                    // Cloud mode defers first-profile creation to the sync job's
                    // cloud probe (adopt-before-mint); local mode has no cloud
                    // truth to consult, so create eagerly.
                    if (snapshot.profiles.isEmpty() && !BuildConfig.CLOUD_CONFIGURED) createInitialProfile()
                    ensureDefaultCatalog(snapshot.providers)
                    startCloudSync(snapshot.account.userId)
                } else {
                    refreshJob?.cancel()
                    refreshJob = null
                    catalogRefreshGate.reset()
                    if (cloudSyncUserId != null) {
                        // Sign-out / deletion must retire the collectors bound to
                        // the previous user's realtime channels — before any
                        // local wipe, or surviving collectors would resurrect rows.
                        cloudSyncJob?.cancel()
                        cloudSyncJob = null
                        cloudSyncUserId = null
                    }
                    if (leftAnAccount) {
                        // Leaving an account must leave nothing behind. Room rows
                        // used to survive sign-out/unpair, so the next sign-in
                        // showed stale local profiles UNION the account's real
                        // cloud ones ("why do I see more than 2 profiles?") — and
                        // synced settings would leak into a successor account too.
                        // Everything re-arrives from the cloud on next sign-in.
                        container.libraryRepository.clearLocalAccountData()
                        container.preferences.clearSyncedSettings()
                        // The device binding belongs to the previous account.
                        // Keeping it would re-bind this TV's row to the next
                        // account's session and fail permanently (P1-6).
                        container.preferences.setPairingDeviceId(null)
                    }
                }
                refreshCatalogs()
            }
        }
        viewModelScope.launch {
            state.mapActiveProfileId().filterNotNull().flatMapLatest(container.libraryRepository::library).collectLatest { entries ->
                mutableState.update { it.copy(library = entries) }
            }
        }
        viewModelScope.launch {
            state.mapActiveProfileId().filterNotNull().flatMapLatest(container.libraryRepository::progress).collectLatest { progress ->
                mutableState.update { it.copy(progress = progress) }
            }
        }
        viewModelScope.launch {
            combine(
                container.accountGateway.state,
                state.mapActiveProfileId(),
            ) { account, profileId ->
                (account as? AccountState.SignedIn)?.userId?.let { userId ->
                    profileId?.let { userId to it }
                }
            }
                .distinctUntilChanged()
                .flatMapLatest { binding ->
                    binding?.let { (userId, profileId) ->
                        container.cloudSyncGateway.artworkOverrides(userId, profileId)
                    } ?: flowOf(emptyList())
                }
                .collectLatest { overrides ->
                    mutableState.update { it.copy(artworkOverrides = overrides) }
                }
        }
        // Re-bind the persisted device whenever a signed-in account/session
        // becomes available. The outer loop handles offline/server recovery;
        // the gateway handles one access-token refresh per failed request.
        viewModelScope.launch {
            combine(
                container.accountGateway.state,
                container.preferences.pairingDeviceId,
            ) { account, deviceId ->
                (account as? AccountState.SignedIn)?.userId?.let { userId ->
                    deviceId?.let { userId to it }
                }
            }
                .distinctUntilChanged()
                .collectLatest { binding ->
                    if (binding == null) return@collectLatest
                    var attempt = 0
                    while (currentCoroutineContext().isActive) {
                        val result = container.pairingGateway.registerDeviceSession(binding.second)
                        if (result.isSuccess) {
                            CloudLog.i("tv.pairing device=${binding.second} rebound to current session")
                            return@collectLatest
                        }
                        val error = result.exceptionOrNull()
                        if (isTerminalDeviceBindingError(error)) {
                            // Stale or foreign device row (e.g. the account
                            // switched on this TV): retrying would loop forever.
                            // Drop the stored ID; re-pairing mints a fresh one.
                            CloudLog.w("tv.pairing device=${binding.second} unbindable — clearing stale binding", error)
                            container.preferences.setPairingDeviceId(null)
                            return@collectLatest
                        }
                        if (!shouldRetryDeviceBinding(container.accountGateway.state.value, result)) {
                            CloudLog.w("tv.pairing device=${binding.second} bind stopped", result.exceptionOrNull())
                            return@collectLatest
                        }
                        val backoff = deviceBindingBackoffMillis(attempt++)
                        CloudLog.w("tv.pairing device=${binding.second} bind deferred; retrying in ${backoff}ms")
                        delay(backoff)
                    }
                }
        }
    }

    fun openDevelopmentSession() {
        runCatching(container::openDevelopmentSession)
            .onFailure { showMessage(it.message ?: "Development session is unavailable.") }
    }

    fun signInWithGoogleToken(token: String, nonce: String?) = viewModelScope.launch {
        container.accountGateway.signInWithGoogleIdToken(token, nonce).onFailure { showMessage(it.safeAuthMessage()) }
    }

    fun sendEmailLink(email: String) = viewModelScope.launch {
        container.accountGateway.sendEmailLink(email.trim()).onSuccess {
            showMessage("Check your email to finish signing in.")
        }.onFailure { showMessage(it.safeAuthMessage()) }
    }

    fun completeEmailLink(email: String, link: String) = viewModelScope.launch {
        container.accountGateway.completeEmailLink(email, link)
            .onFailure { showMessage(it.safeAuthMessage()) }
    }

    fun reportMessage(message: String) = showMessage(message)

    fun selectProfile(profileId: String) = viewModelScope.launch {
        container.preferences.setActiveProfile(profileId)
    }

    fun addProfile(name: String, child: Boolean, pin: CharArray? = null) = viewModelScope.launch {
        val profile = Profile(
            id = UUID.randomUUID().toString(),
            name = name.trim().take(30).ifBlank { if (child) "Kids" else "Viewer" },
            avatarKey = if (child) "sprout" else "moon",
            kind = if (child) ProfileKind.CHILD else ProfileKind.ADULT,
            hasPin = pin != null,
            updatedAtEpochMillis = System.currentTimeMillis(),
        )
        container.libraryRepository.saveProfile(profile, pin)
        (state.value.account as? AccountState.SignedIn)?.userId?.let { container.cloudSyncGateway.saveProfile(it, profile) }
        container.preferences.setActiveProfile(profile.id)
    }

    fun addProvider(input: String) = viewModelScope.launch {
        val address = input.trim()
        if (address.isBlank()) {
            showMessage("Paste an HTTPS add-on address first.")
            return@launch
        }
        // A custom-scheme install link (addon://, …) is an explicit
        // addon handoff; a plain http(s) address may still need configuration.
        val explicitInstallLink = address.contains("://") &&
            !address.startsWith("http://", ignoreCase = true) &&
            !address.startsWith("https://", ignoreCase = true)
        mutableState.update { it.copy(refreshing = true, message = null) }
        val directResult = container.providerClient.manifest(address)
        when (directResult) {
            is ProviderResult.Success -> {
                if (directResult.value.behaviorHints.configurationRequired && !explicitInstallLink) {
                    requestProviderConfiguration(address, directResult.value.name)
                } else {
                    saveProvider(address, directResult.value, state.value.providers.size)
                    showMessage("${directResult.value.name} installed. Loading catalogs…")
                }
            }
            is ProviderResult.Failure -> {
                when (val discovery = container.providerClient.discoverProviderUrls(address)) {
                    is ProviderResult.Success -> {
                        var installed = 0
                        var configuration: Pair<String, String>? = null
                        discovery.value.distinct().take(MAX_DISCOVERED_PROVIDERS).forEach { manifestUrl ->
                            when (val manifest = container.providerClient.manifest(manifestUrl)) {
                                is ProviderResult.Success -> {
                                    if (manifest.value.behaviorHints.configurationRequired) {
                                        if (configuration == null) configuration = manifestUrl to manifest.value.name
                                    } else {
                                        saveProvider(manifestUrl, manifest.value, state.value.providers.size + installed)
                                        installed += 1
                                    }
                                }
                                is ProviderResult.Failure -> Unit
                            }
                        }
                        if (configuration != null) {
                            requestProviderConfiguration(configuration.first, configuration.second)
                        } else if (installed > 0) {
                            showMessage("Installed $installed add-on${if (installed == 1) "" else "s"}. Loading catalogs…")
                        } else {
                            showMessage(directResult.safeMessage)
                        }
                    }
                    is ProviderResult.Failure -> showMessage(directResult.safeMessage)
                }
            }
        }
        mutableState.update { it.copy(refreshing = false) }
    }

    fun refreshContent() {
        ensureDefaultCatalog(state.value.providers)
        refreshCatalogs(force = true)
    }

    fun searchContent(input: String) {
        searchJob?.cancel()
        val queryText = input.trim()
        if (queryText.isBlank()) {
            mutableState.update { it.copy(searchSections = emptyList(), searching = false) }
            ensureBrowseTargets()
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            mutableState.update { it.copy(searching = true) }
            val enabledProviders = state.value.providers
                .filter(ProviderSubscription::enabled)
                .sortedWith(compareBy<ProviderSubscription> { it.sortOrder }.thenBy { it.id })
            val searchableCatalogs = coroutineScope {
                enabledProviders.map { subscription ->
                    async {
                        val manifest = (container.providerClient.manifest(subscription.manifestUrl) as? ProviderResult.Success)?.value
                        manifest?.catalogs
                            ?.filter { it.supportsExtra("search") }
                            .orEmpty()
                            .map { subscription to it }
                    }
                }.awaitAll().flatten()
            }
            val sections = coroutineScope {
                searchableCatalogs.map { (subscription, catalog) ->
                    async {
                        val query = catalog.request(search = queryText)
                        val section = CatalogSection(
                            id = "search:${canonicalCatalogRequestIdentity(subscription.id, query)}",
                            providerId = subscription.id,
                            title = catalog.name,
                            providerName = subscription.displayName,
                            items = emptyList(),
                            baseQuery = query,
                            supportsSkip = catalog.supportsSkip(),
                            skipStep = catalog.initialSkipStep(),
                            hasMore = catalog.supportsSkip(),
                        )
                        val missingRequired = catalog.requiredExtras.filterNot { required ->
                            required.canonicalExtraName() == "search" ||
                                catalog.extraDefaults.keys.any { key ->
                                    key.canonicalExtraName() == required.canonicalExtraName()
                                }
                        }
                        if (missingRequired.isNotEmpty()) {
                            section.copy(
                                errorMessage = "Unavailable: ${missingRequired.sorted().joinToString(", ")} is required.",
                                hasMore = false,
                            )
                        } else {
                            when (val result = container.providerClient.catalog(
                                subscription.manifestUrl,
                                subscription.id,
                                query,
                            )) {
                                is ProviderResult.Success -> firstCatalogPage(
                                    section,
                                    result.value,
                                    filterForProfile(result.value),
                                )
                                is ProviderResult.Failure -> section.copy(errorMessage = result.safeMessage, hasMore = false)
                            }
                        }
                    }
                }.awaitAll()
            }
            mutableState.update {
                it.copy(
                    searchSections = sections,
                    searching = false,
                )
            }
        }
    }

    fun selectBrowseType(type: String) {
        val target = state.value.browse.targets.firstOrNull { it.catalog.type == type } ?: return
        selectBrowseTarget(target, genre = null)
    }

    fun selectBrowseCatalog(catalogId: String) {
        val target = state.value.browse.targets.firstOrNull {
            it.id == catalogId || it.catalog.id == catalogId
        } ?: return
        selectBrowseTarget(target, genre = null)
    }

    fun selectBrowseGenre(genre: String?) {
        val browse = state.value.browse
        val target = browse.targets.firstOrNull { it.id == browse.selectedCatalogId } ?: return
        selectBrowseTarget(target, genre)
    }

    fun loadMoreBrowse() {
        state.value.browse.result?.let { loadMoreCatalog(it.id) }
    }

    fun retryBrowse() {
        state.value.browse.result?.let { retryCatalogPage(it.id) }
    }

    fun loadMoreCatalog(sectionId: String) {
        val current = findCatalogSection(state.value, sectionId) ?: return
        if (!current.supportsSkip || !current.hasMore || current.loadingMore) return
        val provider = state.value.providers.firstOrNull { it.id == current.providerId && it.enabled } ?: return
        val request = current.baseQuery.copy(skip = current.nextSkip)
        setCatalogSection(sectionId) { section ->
            if (section.baseQuery != current.baseQuery) section else section.copy(loadingMore = true, loadMoreError = null)
        }
        val job = viewModelScope.launch {
            val result = container.providerClient.catalog(provider.manifestUrl, provider.id, request)
            val latest = findCatalogSection(state.value, sectionId) ?: return@launch
            if (latest.baseQuery != current.baseQuery || latest.nextSkip != current.nextSkip) return@launch
            val updated = when (result) {
                is ProviderResult.Success -> mergeCatalogPage(
                    latest,
                    result.value,
                    filterForProfile(result.value),
                )
                is ProviderResult.Failure -> mergeCatalogPage(latest, null, errorMessage = result.safeMessage)
            }
            setCatalogSection(sectionId) { section ->
                if (section.baseQuery == current.baseQuery && section.loadingMore) updated else section
            }
        }
        pageJobs[sectionId]?.cancel()
        pageJobs[sectionId] = job
    }

    fun retryCatalogPage(sectionId: String) = loadMoreCatalog(sectionId)

    private fun ensureBrowseTargets() {
        if (state.value.browse.targets.isNotEmpty() || browseJob?.isActive == true) return
        browseJob = viewModelScope.launch {
            val providers = state.value.providers
                .filter(ProviderSubscription::enabled)
                .sortedWith(compareBy<ProviderSubscription> { it.sortOrder }.thenBy { it.id })
            val targets = coroutineScope {
                providers.map { subscription ->
                    async {
                        val manifest = (container.providerClient.manifest(subscription.manifestUrl) as? ProviderResult.Success)?.value
                        manifest?.catalogs
                            ?.filterNot { catalog ->
                                catalog.requiredExtras.isNotEmpty() &&
                                    catalog.requiredExtras.all { it.canonicalExtraName() == "search" }
                            }
                            .orEmpty()
                            .map { catalog ->
                                val missing = catalog.requiredExtras.filterNot { required ->
                                    catalog.extraDefaults.keys.any { key -> key.canonicalExtraName() == required.canonicalExtraName() }
                                }
                                CatalogBrowseTarget(
                                    providerId = subscription.id,
                                    providerName = subscription.displayName,
                                    manifestUrl = subscription.manifestUrl,
                                    catalog = catalog,
                                    unavailableReason = missing.takeIf(List<String>::isNotEmpty)?.let {
                                        "Unavailable: ${it.sorted().joinToString(", ")} is required."
                                    },
                                )
                            }
                    }
                }.awaitAll().flatten()
            }
            val selected = targets.firstOrNull { it.unavailableReason == null } ?: targets.firstOrNull()
            mutableState.update { current ->
                val selectedType = current.browse.selectedType?.takeIf { type -> targets.any { it.catalog.type == type } }
                    ?: selected?.catalog?.type
                val selectedTarget = current.browse.selectedCatalogId?.let { id -> targets.firstOrNull { it.id == id } }
                    ?: targets.firstOrNull { it.catalog.type == selectedType }
                current.copy(
                    browse = current.browse.copy(
                        targets = targets,
                        selectedType = selectedType,
                        selectedCatalogId = selectedTarget?.id,
                        selectorError = null,
                    ),
                )
            }
            val selectedToLoad = state.value.browse.selectedCatalogId?.let { id ->
                targets.firstOrNull { it.id == id }
            } ?: selected
            selectedToLoad?.let { loadBrowseTarget(it, state.value.browse.selectedGenre) }
        }
    }

    /** Discover loads the addon-declared category options on first entry. */
    fun prepareDiscover() {
        ensureBrowseTargets()
    }

    /** Back from category results: drop the genre filter without reloading. */
    fun clearBrowseGenre() {
        mutableState.update { it.copy(browse = it.browse.copy(selectedGenre = null)) }
    }

    private fun selectBrowseTarget(target: CatalogBrowseTarget, genre: String?) {
        browseJob?.cancel()
        browseJob = viewModelScope.launch {
            mutableState.update {
                it.copy(
                    browse = it.browse.copy(
                        selectedType = target.catalog.type,
                        selectedCatalogId = target.id,
                        selectedGenre = genre,
                        result = null,
                        loading = true,
                        selectorError = null,
                    ),
                )
            }
            loadBrowseTarget(target, genre)
        }
    }

    private suspend fun loadBrowseTarget(target: CatalogBrowseTarget, genre: String?) {
        if (target.unavailableReason != null) {
            mutableState.update {
                it.copy(
                    browse = it.browse.copy(
                        result = CatalogSection(
                            id = "browse:${target.id}",
                            providerId = target.providerId,
                            title = target.catalog.name,
                            providerName = target.providerName,
                            items = emptyList(),
                            errorMessage = target.unavailableReason,
                        ),
                        loading = false,
                        selectorError = target.unavailableReason,
                    ),
                )
            }
            return
        }
        val query = target.catalog.request(genre = genre)
        val section = CatalogSection(
            id = "browse:${canonicalCatalogRequestIdentity(target.providerId, query)}",
            providerId = target.providerId,
            title = target.catalog.name,
            providerName = target.providerName,
            items = emptyList(),
            baseQuery = query,
            supportsSkip = target.catalog.supportsSkip(),
            skipStep = target.catalog.initialSkipStep(),
            hasMore = target.catalog.supportsSkip(),
        )
        mutableState.update { it.copy(browse = it.browse.copy(result = section, loading = true, selectorError = null)) }
        when (val result = container.providerClient.catalog(target.manifestUrl, target.providerId, query)) {
            is ProviderResult.Success -> mutableState.update {
                it.copy(
                    browse = it.browse.copy(
                        result = firstCatalogPage(section, result.value, filterForProfile(result.value)),
                        loading = false,
                    ),
                )
            }
            is ProviderResult.Failure -> mutableState.update {
                it.copy(
                    browse = it.browse.copy(
                        result = section.copy(errorMessage = result.safeMessage, hasMore = false),
                        loading = false,
                        selectorError = result.safeMessage,
                    ),
                )
            }
        }
    }
    private fun findCatalogSection(current: AppUiState, sectionId: String): CatalogSection? =
        current.sections.firstOrNull { it.id == sectionId }
            ?: current.searchSections.firstOrNull { it.id == sectionId }
            ?: current.browse.result?.takeIf { it.id == sectionId }

    private fun setCatalogSection(sectionId: String, transform: (CatalogSection) -> CatalogSection) {
        mutableState.update { current ->
            val home = current.sections.map { section -> if (section.id == sectionId) transform(section) else section }
            val search = current.searchSections.map { section -> if (section.id == sectionId) transform(section) else section }
            val browse = current.browse.result?.let { section ->
                if (section.id == sectionId) transform(section) else section
            }
            current.copy(
                sections = home,
                searchSections = search,
                browse = current.browse.copy(result = browse),
            )
        }
    }

    fun toggleProvider(providerId: String, enabled: Boolean) = viewModelScope.launch {
        val current = state.value.providers.firstOrNull { it.id == providerId } ?: return@launch
        if (current.sortOrder < 0) {
            showMessage("The Cinemeta catalog is always available.")

            return@launch
        }
        container.libraryRepository.setProviderEnabled(providerId, enabled)
        val updated = current.copy(enabled = enabled, updatedAtEpochMillis = System.currentTimeMillis())
        (state.value.account as? AccountState.SignedIn)?.userId?.let { userId ->
            container.cloudSyncGateway.saveProvider(userId, updated).onFailure {
                showMessage("The add-on changed on this device, but could not sync.")
            }
        }
    }

    fun removeProvider(providerId: String) = viewModelScope.launch {
        val current = state.value.providers.firstOrNull { it.id == providerId } ?: return@launch
        if (current.sortOrder < 0) {
            showMessage("The Cinemeta catalog cannot be removed.")
            return@launch
        }
        val userId = (state.value.account as? AccountState.SignedIn)?.userId
        if (userId != null) {
            val deleted = container.cloudSyncGateway.deleteProvider(userId, providerId)
            if (deleted.isFailure) {
                showMessage("Could not remove the add-on. Check your connection and try again.")
                return@launch
            }
        }
        container.libraryRepository.removeProvider(providerId)
        showMessage("Add-on removed.")
    }

    fun addToLibrary(media: MediaPreview) = viewModelScope.launch {
        val profileId = state.value.activeProfileId ?: return@launch
        val now = System.currentTimeMillis()
        container.libraryRepository.saveLibrary(LibraryEntry(profileId, media.stableKey, media, now, now))
        (state.value.account as? AccountState.SignedIn)?.userId?.let {
            container.cloudSyncGateway.saveLibrary(it, LibraryEntry(profileId, media.stableKey, media, now, now))
        }
        showMessage("Added to Library.")
    }

    fun removeFromLibrary(mediaKey: String) = viewModelScope.launch {
        val profileId = state.value.activeProfileId ?: return@launch
        container.libraryRepository.removeLibrary(profileId, mediaKey)
        (state.value.account as? AccountState.SignedIn)?.userId
            ?.let { container.cloudSyncGateway.deleteLibraryEntry(it, profileId, mediaKey) }
            ?.onFailure { showMessage("Could not sync the Library removal.") }
    }

    fun openContentMenu(target: ContentMenuTarget) = mutableState.update {
        it.copy(contentMenu = ContentMenuState(target = target.withCurrentProgress(it.progress)))
    }

    fun dismissContentMenu() = mutableState.update { it.copy(contentMenu = ContentMenuState()) }

    /**
     * One entry point for every context-menu action on both platforms
     * (SHR-ARC-06). Hold actions close the menu after a successful local
     * mutation and surface the platform message surface.
     */
    fun onContentMenuAction(action: ContentMenuAction) {
        val target = state.value.contentMenu.target ?: return
        performContentMenuAction(target, action)
    }

    /** Executes accessibility actions without depending on a sheet/dialog being open. */
    fun onContentMenuAction(target: ContentMenuTarget, action: ContentMenuAction) {
        performContentMenuAction(target.withCurrentProgress(state.value.progress), action)
    }

    private fun performContentMenuAction(target: ContentMenuTarget, action: ContentMenuAction) = viewModelScope.launch {
        val profileId = state.value.activeProfileId ?: return@launch
        val userId = (state.value.account as? AccountState.SignedIn)?.userId
        when (action) {
            ContentMenuAction.ViewDetails -> {
                dismissContentMenu()
                loadDetail(target.media)
            }
            is ContentMenuAction.ToggleLibrary -> {
                val inLibrary = state.value.library.any { it.mediaKey == target.media.stableKey }
                if (inLibrary) {
                    container.libraryRepository.removeLibrary(profileId, target.media.stableKey)
                    dismissContentMenu()
                    showMessage("Removed from Library.")
                    userId?.let {
                        container.cloudSyncGateway.deleteLibraryEntry(it, profileId, target.media.stableKey)
                            .onFailure { showMessage("Could not sync the Library removal.") }
                    }
                } else {
                    val now = System.currentTimeMillis()
                    val entry = LibraryEntry(profileId, target.media.stableKey, target.media, now, now)
                    container.libraryRepository.saveLibrary(entry)
                    dismissContentMenu()
                    showMessage("Added to Library.")
                    userId?.let {
                        container.cloudSyncGateway.saveLibrary(it, entry)
                            .onFailure { showMessage("Could not sync the Library addition.") }
                    }
                }
            }
            ContentMenuAction.MarkWatched -> {
                val stored = container.libraryRepository.saveProgress(watchedProgress(target, profileId))
                dismissContentMenu()
                showMessage("Marked as watched.")
                userId?.let {
                    container.cloudSyncGateway.saveProgress(it, stored)
                        .onFailure { showMessage("Could not sync watched status.") }
                }
            }
            ContentMenuAction.MarkUnwatched -> {
                val videoId = deleteProgressRow(target, profileId)
                dismissContentMenu()
                showMessage("Marked as unwatched.")
                userId?.let {
                    container.cloudSyncGateway.deleteProgress(it, profileId, videoId)
                        .onFailure { showMessage("Could not sync the removal.") }
                }
            }
            ContentMenuAction.RemoveFromContinueWatching -> {
                val videoId = deleteProgressRow(target, profileId)
                dismissContentMenu()
                showMessage("Removed from Continue Watching.")
                userId?.let {
                    container.cloudSyncGateway.deleteProgress(it, profileId, videoId)
                        .onFailure { showMessage("Could not sync the removal.") }
                }
            }
            ContentMenuAction.StartFromBeginning -> startFromBeginning(target)
        }
    }

    private fun ContentMenuTarget.withCurrentProgress(progressRows: List<WatchProgress>): ContentMenuTarget {
        if (progress != null) return this
        val videoId = episode?.id ?: media.id.takeIf { media.type == MediaType.MOVIE }
        return copy(progress = videoId?.let { id -> progressRows.firstOrNull { it.videoId == id } })
    }

    private fun watchedProgress(target: ContentMenuTarget, profileId: String): WatchProgress {
        val videoId = target.progress?.videoId ?: target.episode?.id ?: target.media.id
        return WatchProgress(
            profileId = profileId,
            mediaKey = target.media.stableKey,
            videoId = videoId,
            positionMillis = 0,
            durationMillis = 0,
            completed = true,
            updatedAtEpochMillis = System.currentTimeMillis(),
            preview = target.media,
            episodeLabel = target.progress?.episodeLabel
                ?: target.episode?.let { episode ->
                    listOfNotNull(
                        episode.season?.let { season -> "S$season" },
                        episode.episode?.let { number -> "E$number" },
                        episode.title.takeIf(String::isNotBlank),
                    ).joinToString(" · ")
                },
        )
    }

    private suspend fun deleteProgressRow(
        target: ContentMenuTarget,
        profileId: String,
    ): String {
        val videoId = target.progress?.videoId ?: target.episode?.id ?: target.media.id
        container.libraryRepository.removeProgress(profileId, videoId)
        return videoId
    }

    /**
     * Start-over plays the saved video from zero without touching the
     * completed badge. Episodes resolve the saved videoId back to episode
     * metadata first; a failed resolution keeps the menu open in a retry
     * state instead of playing the wrong episode (MOB-CMP-09).
     */
    private suspend fun startFromBeginning(target: ContentMenuTarget) {
        val videoId = target.progress?.videoId
        if (target.media.type == MediaType.MOVIE && target.episode == null) {
            dismissContentMenu()
            openSources(target.media, startFromBeginning = true)
            return
        }
        target.episode?.let { episode ->
            dismissContentMenu()
            openSources(target.media, episode, startFromBeginning = true)
            return
        }
        if (videoId == null) return
        mutableState.update {
            it.copy(contentMenu = it.contentMenu.copy(target = target, resolving = true, resolutionError = false))
        }
        val episode = resolveEpisodeForVideo(target.media, videoId)
        if (episode == null) {
            mutableState.update {
                it.copy(contentMenu = it.contentMenu.copy(resolving = false, resolutionError = true))
            }
            return
        }
        dismissContentMenu()
        openSources(target.media, episode, startFromBeginning = true)
    }

    private suspend fun resolveEpisodeForVideo(media: MediaPreview, videoId: String): Episode? {
        val loaded = state.value.selectedDetail
            ?.takeIf { it.preview.stableKey == media.stableKey }
            ?.episodes
            ?.firstOrNull { it.id == videoId }
        if (loaded != null) return loaded
        loadDetail(media)
        val deadline = System.currentTimeMillis() + CONTENT_RESOLVE_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            delay(150)
            if (state.value.selectedDetail?.preview?.stableKey != media.stableKey) return null
            if (!state.value.refreshing) {
                return state.value.selectedDetail?.episodes?.firstOrNull { it.id == videoId }
            }
        }
        return null
    }

    fun loadDetail(media: MediaPreview) {
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            // A recently opened title renders complete metadata instantly; the
            // provider refresh below still lands behind it (SHR-ARC-13).
            val cached = recentlyLoadedDetails[media.stableKey]
            mutableState.update {
                it.copy(selectedDetail = cached ?: MediaDetail(media), refreshing = cached == null)
            }
            // Enrichment streams in independently of base metadata: sections
            // appear when ready without displacing the hero (TV-CNT-02).
            startDetailEnrichment(media)
            if (media.id.startsWith("fixture:")) {
                mutableState.update {
                    it.copy(
                        selectedDetail = MediaDetail(media, runtimeMinutes = 52, episodes = if (media.type.name == "SERIES") PreviewMedia.episodes else emptyList()),
                        refreshing = false,
                    )
                }
                return@launch
            }
            val candidates = state.value.providers
                .filter(ProviderSubscription::enabled)
                .sortedWith(
                    compareBy<ProviderSubscription> { if (it.id in media.providerIds) 0 else 1 }
                        .thenBy(ProviderSubscription::sortOrder)
                        .thenBy(ProviderSubscription::id),
                )
            if (candidates.isEmpty()) {
                mutableState.update { it.copy(refreshing = false) }
                return@launch
            }
            // Progressive merge: each add-on's meta lands the moment it
            // resolves instead of gating the screen on the slowest provider
            // (SHR-PROD-04). A fresh detail request cancels this job, and the
            // stable-key checks keep late responses from touching it.
            var mergedAny = false
            supervisorScope {
                candidates.forEach { provider ->
                    launch {
                        runCatching {
                            val manifest = container.providerClient.manifest(provider.manifestUrl)
                            if (manifest !is ProviderResult.Success ||
                                !container.providerAggregator.supports(manifest.value, "meta", media.rawType, media.id)
                            ) {
                                return@launch
                            }
                            val result = container.providerClient.meta(provider.manifestUrl, provider.id, media.rawType, media.id)
                            if (result !is ProviderResult.Success<MediaDetail>) return@launch
                            mergedAny = true
                            mutableState.update { current ->
                                if (current.selectedDetail?.preview?.stableKey != media.stableKey) {
                                    current
                                } else {
                                    current.copy(selectedDetail = current.selectedDetail?.merge(result.value))
                                }
                            }
                        }
                    }
                }
            }
            mutableState.update { current ->
                if (current.selectedDetail?.preview?.stableKey != media.stableKey) {
                    current
                } else if (!mergedAny) {
                    current.copy(refreshing = false, message = "No metadata provider could load this title.")
                } else {
                    current.copy(refreshing = false)
                }
            }
            state.value.selectedDetail
                ?.takeIf { it.preview.stableKey == media.stableKey }
                ?.let { recentlyLoadedDetails[media.stableKey] = it }
        }
    }

    /**
     * Cache-first enrichment flow (SHR-ARC-06/13): Room content renders
     * immediately, the TTL-gated refresh lands behind it, and a retryable
     * failure surfaces only inside the affected sections (SHR-PROD-04).
     * Cloud-less builds stay silent — enrichment is additive, never required.
     */
    private fun startDetailEnrichment(media: MediaPreview, force: Boolean = false) {
        val repository = container.detailEnrichmentRepository
        if (repository == null || media.id.startsWith("fixture:")) return
        val mediaKey = media.enrichmentMediaKey()
        val activeKey = media.stableKey
        fun isCurrent(state: AppUiState) = state.selectedDetail?.preview?.stableKey == activeKey
        enrichmentJob?.cancel()
        enrichmentJob = viewModelScope.launch {
            mutableState.update {
                if (isCurrent(it)) {
                    it.copy(detailEnrichmentLoading = true, detailEnrichmentFailed = false)
                } else {
                    it
                }
            }
            // Cache-first: the observe collector renders Room content as soon
            // as it exists, while the TTL-gated refresh lands behind it. The
            // collector keeps running until the next detail opens or the
            // screen closes, so the refresh's upsert re-emits the fresh row.
            val observe = launch {
                repository.observe(mediaKey).collect { cached ->
                    mutableState.update { current ->
                        if (isCurrent(current)) current.copy(detailEnrichment = cached) else current
                    }
                }
            }
            val refresh = launch {
                val result = repository.refresh(media, force = force)
                mutableState.update { current ->
                    if (!isCurrent(current)) return@update current
                    result.fold(
                        onSuccess = { current.copy(detailEnrichmentLoading = false, detailEnrichmentFailed = false) },
                        onFailure = { error ->
                            when (error) {
                                // No cloud in this build: enrichment simply stays absent.
                                is CloudNotConfiguredException ->
                                    current.copy(detailEnrichmentLoading = false)
                                else ->
                                    current.copy(detailEnrichmentLoading = false, detailEnrichmentFailed = true)
                            }
                        },
                    )
                }
            }
            refresh.join()
            observe.join()
        }
    }

    /** Force-reloads enrichment for the open detail after a failed attempt. */
    fun retryDetailEnrichment() {
        val media = state.value.selectedDetail?.preview ?: return
        startDetailEnrichment(media, force = true)
    }

    /**
     * Credential changes (artwork TMDB key, integration credentials, enabled
     * rating sources) change what the edge would return; the 12h cache would
     * otherwise keep serving stale empty sections (SHR-ARC-05). Drops the
     * cache and force-refreshes the open detail so sections recover at once
     * (SHR-PROD-04). Absent when the build has no cloud.
     */
    private suspend fun invalidateDetailEnrichment() {
        container.detailEnrichmentRepository?.let { repository ->
            runCatching { repository.invalidate() }
        }
        state.value.selectedDetail?.preview?.let { startDetailEnrichment(it, force = true) }
    }

    fun refreshArtworkKeyStatus() = viewModelScope.launch {
        val userId = (state.value.account as? AccountState.SignedIn)?.userId ?: return@launch
        refreshArtworkKeyStatus(userId)
    }

    private suspend fun refreshArtworkKeyStatus(userId: String) {
        mutableState.update { it.copy(artworkKeyStatusLoading = true) }
        val result = container.cloudSyncGateway.artworkProviderStatuses(userId)
        mutableState.update { current ->
            result.fold(
                onSuccess = { providers ->
                    current.copy(
                        artworkProviders = providers.sortedWith(compareBy({ it.sortOrder }, { it.provider.value })),
                        artworkKeyStatusLoading = false,
                        artworkProviderCatalogError = null,
                    )
                },
                onFailure = {
                    current.copy(
                        artworkKeyStatusLoading = false,
                        message = "Artwork providers could not be loaded. Try again.",
                        artworkProviderCatalogError = "Artwork providers could not be loaded. Try again.",
                    )
                },
            )
        }
    }

    fun saveArtworkKey(provider: ArtworkProviderId, apiKey: String) = viewModelScope.launch {
        val userId = (state.value.account as? AccountState.SignedIn)?.userId ?: return@launch
        val displayName = state.value.artworkProviders.firstOrNull { it.provider == provider }?.displayName ?: provider.value
        if (apiKey.trim().isBlank()) {
            showMessage("Paste a $displayName API key first.")
            return@launch
        }
        val result = container.cloudSyncGateway.saveArtworkKey(userId, provider, apiKey.trim())
        if (result.isSuccess) invalidateDetailEnrichment()
        result
            .onSuccess {
                mutableState.update { current ->
                    current.copy(
                        artworkProviders = current.artworkProviders.map {
                            if (it.provider == provider) it.copy(configured = true) else it
                        },
                        lastArtworkLookupFailures = current.lastArtworkLookupFailures - provider,
                    )
                }
                showMessage("$displayName artwork key saved.")
            }
            .onFailure { error ->
                showMessage(
                    when (error) {
                        is ArtworkKeyInvalidException ->
                            "The key was rejected by the provider. Check it and try again."
                        else -> "Could not save the $displayName artwork key. Try again."
                    },
                )
            }
    }

    fun deleteArtworkKey(provider: ArtworkProviderId) = viewModelScope.launch {
        val userId = (state.value.account as? AccountState.SignedIn)?.userId ?: return@launch
        val displayName = state.value.artworkProviders.firstOrNull { it.provider == provider }?.displayName ?: provider.value
        val result = container.cloudSyncGateway.deleteArtworkKey(userId, provider)
        if (result.isSuccess) invalidateDetailEnrichment()
        result
            .onSuccess {
                mutableState.update { current ->
                    current.copy(
                        artworkProviders = current.artworkProviders.map {
                            if (it.provider == provider) it.copy(configured = false) else it
                        },
                        lastArtworkLookupFailures = current.lastArtworkLookupFailures - provider,
                    )
                }
                showMessage("$displayName artwork key removed.")
            }
            .onFailure { showMessage("Could not remove the $displayName artwork key. Try again.") }
    }

    // ── Integrations (Settings) ──────────────────────────────────────────
    // Credentials travel to the encrypted server-side store and are never
    // returned; the client only ever sees connection state (SHR-PROD-06).

    fun refreshIntegrations() = viewModelScope.launch {
        mutableState.update { it.copy(integrationsLoading = true) }
        val userId = (state.value.account as? AccountState.SignedIn)?.userId
        if (userId == null) {
            mutableState.update { it.copy(integrations = emptyList(), integrationsLoading = false) }
            return@launch
        }
        container.integrationsGateway.statuses(userId)
            .onSuccess { statuses ->
                mutableState.update {
                    it.copy(integrations = statuses, integrationsLoading = false, integrationsFailed = false)
                }
            }
            .onFailure { error ->
                mutableState.update {
                    it.copy(
                        integrationsLoading = false,
                        integrationsFailed = error !is CloudNotConfiguredException,
                    )
                }
            }
    }

    fun saveIntegrationCredential(integration: String, credential: String) = viewModelScope.launch {
        val userId = (state.value.account as? AccountState.SignedIn)?.userId
        if (userId == null) {
            showMessage("Sign in to connect integrations.")
            return@launch
        }
        if (credential.isBlank()) {
            showMessage("Paste an API key first.")
            return@launch
        }
        val result = container.integrationsGateway.saveCredential(userId, integration, credential.trim())
        if (result.isSuccess) invalidateDetailEnrichment()
        result
            .onSuccess {
                showMessage("Integration connected.")
                refreshIntegrations()
            }
            .onFailure { error ->
                showMessage(
                    when (error) {
                        is IntegrationInvalidCredentialException ->
                            "The key was rejected by the provider. Check it and try again."
                        is CloudNotConfiguredException ->
                            "Cloud services aren't configured for this build."
                        else -> "Could not save the key. Try again."
                    },
                )
            }
    }

    fun setIntegrationSources(integration: String, sources: List<String>) = viewModelScope.launch {
        val userId = (state.value.account as? AccountState.SignedIn)?.userId ?: return@launch
        val result = container.integrationsGateway.setEnabledSources(userId, integration, sources)
        if (result.isSuccess) invalidateDetailEnrichment()
        result
            .onSuccess { refreshIntegrations() }
            .onFailure { error ->
                if (error !is CloudNotConfiguredException) {
                    showMessage("Could not update the rating sources. Try again.")
                }
            }
    }

    fun removeIntegration(integration: String) = viewModelScope.launch {
        val userId = (state.value.account as? AccountState.SignedIn)?.userId ?: return@launch
        val result = container.integrationsGateway.removeCredential(userId, integration)
        if (result.isSuccess) invalidateDetailEnrichment()
        result
            .onSuccess {
                showMessage("Integration removed.")
                refreshIntegrations()
            }
            .onFailure { error ->
                if (error !is CloudNotConfiguredException) {
                    showMessage("Could not remove the integration. Try again.")
                }
            }
    }

    fun openArtworkProviderKeyPage(providerId: ArtworkProviderId) {
        val entry = state.value.artworkProviders.firstOrNull { it.provider == providerId }
        val uri = entry?.keyPageUrl?.let { runCatching { URI(it) }.getOrNull() }
        val safe = uri?.takeIf {
            it.scheme.equals("https", ignoreCase = true) && !it.host.isNullOrBlank()
        }?.toString()
        mutableState.update {
            it.copy(
                configurationUrl = safe,
                message = if (safe == null) "The provider key page is unavailable." else it.message,
            )
        }
    }

    fun openArtworkEditor(media: MediaPreview) {
        val current = state.value
        val userId = (current.account as? AccountState.SignedIn)?.userId
        if (userId == null) {
            showMessage("Sign in to edit artwork.")
            return
        }
        val existing = current.artworkOverrides.firstOrNull { it.mediaKey == media.stableKey }
        val resolvedMedia = ArtworkResolver(
            current.artworkOverrides.associateBy { it.mediaKey },
        ).resolve(media).media
        viewModelScope.launch {
            val providerStatuses = if (current.artworkProviders.isNotEmpty()) {
                current.artworkProviders
            } else {
                val result = container.cloudSyncGateway.artworkProviderStatuses(userId)
                when {
                    result.isSuccess -> result.getOrNull()
                    result.exceptionOrNull() is CloudNotConfiguredException -> {
                        showMessage(ARTWORK_KEYS_NOT_CONFIGURED_MESSAGE)
                        return@launch
                    }
                    else -> null
                }
            }
            if (providerStatuses != null && providerStatuses.none { it.configured }) {
                showMessage(ARTWORK_KEYS_NOT_CONFIGURED_MESSAGE)
                return@launch
            }
            mutableState.update {
                it.copy(
                    artworkEditor = ArtworkEditorState(
                        media = resolvedMedia,
                        selectedPoster = existing?.poster,
                        selectedBackdrop = existing?.backdrop,
                        selectedLogo = existing?.logo,
                    ),
                )
            }
            val result = container.cloudSyncGateway.artworkCandidates(
                userId = userId,
                mediaKey = media.stableKey,
                name = media.name,
                releaseYear = media.releaseYear,
                mediaType = media.type,
            )
            mutableState.update { state ->
                val editor = state.artworkEditor?.takeIf { it.media.stableKey == media.stableKey }
                    ?: return@update state
                result.fold(
                    onSuccess = { candidates ->
                        val now = System.currentTimeMillis()
                        val failures = state.lastArtworkLookupFailures.toMutableMap()
                        candidates.providerResults.forEach { providerResult ->
                            if (providerResult.status == ArtworkLookupStatus.SUCCESS) {
                                failures.remove(providerResult.provider)
                            } else {
                                failures[providerResult.provider] = now
                            }
                        }
                        state.copy(
                            artworkEditor = editor.copy(
                                candidates = candidates,
                                loading = false,
                                error = null,
                            ),
                            lastArtworkLookupFailures = failures,
                        )
                    },
                    onFailure = { error ->
                        val keysMissing = error is ArtworkKeysNotConfiguredException ||
                            error is CloudNotConfiguredException
                        state.copy(
                            message = if (keysMissing) {
                                ARTWORK_KEYS_NOT_CONFIGURED_MESSAGE
                            } else {
                                state.message
                            },
                            artworkEditor = editor.copy(
                                loading = false,
                                error = if (keysMissing) {
                                    ARTWORK_KEYS_NOT_CONFIGURED_EDITOR_ERROR
                                } else {
                                    "Artwork lookup failed. Try again."
                                },
                            ),
                        )
                    },
                )
            }
        }
    }

    fun closeArtworkEditor() {
        mutableState.update { it.copy(artworkEditor = null) }
    }

    fun selectArtworkProvider(provider: ArtworkProviderId?) {
        mutableState.update { it.copy(artworkEditor = it.artworkEditor?.copy(providerFilter = provider)) }
    }

    fun selectArtworkPoster(asset: ArtworkAsset?) {
        mutableState.update { it.copy(artworkEditor = it.artworkEditor?.copy(selectedPoster = asset)) }
    }

    fun selectArtworkBackdrop(asset: ArtworkAsset?) {
        mutableState.update { it.copy(artworkEditor = it.artworkEditor?.copy(selectedBackdrop = asset)) }
    }

    fun selectArtworkLogo(asset: ArtworkAsset?) {
        mutableState.update { it.copy(artworkEditor = it.artworkEditor?.copy(selectedLogo = asset)) }
    }

    fun saveArtworkSelection() = viewModelScope.launch {
        val editor = state.value.artworkEditor ?: return@launch
        val profileId = state.value.activeProfileId ?: return@launch
        if (
            editor.selectedPoster == null &&
            editor.selectedBackdrop == null &&
            editor.selectedLogo == null
        ) {
            showMessage("Choose a poster, backdrop, or logo first.")
            return@launch
        }
        val override = ArtworkOverride(
            profileId = profileId,
            mediaKey = editor.media.stableKey,
            poster = editor.selectedPoster,
            backdrop = editor.selectedBackdrop,
            logo = editor.selectedLogo,
            updatedAtEpochMillis = System.currentTimeMillis(),
        )
        val userId = (state.value.account as? AccountState.SignedIn)?.userId ?: return@launch
        container.cloudSyncGateway.saveArtworkOverride(userId, override)
            .onSuccess {
                mutableState.update { current ->
                    current.copy(
                        artworkOverrides = (current.artworkOverrides.filterNot { it.mediaKey == override.mediaKey } + override),
                        artworkEditor = null,
                    )
                }
                showMessage("Artwork updated.")
            }
            .onFailure { showMessage("Could not save artwork. Try again.") }
    }

    fun clearDetail() {
        detailJob?.cancel()
        enrichmentJob?.cancel()
        mutableState.update {
            it.copy(
                selectedDetail = null,
                refreshing = false,
                detailEnrichment = null,
                detailEnrichmentLoading = false,
                detailEnrichmentFailed = false,
            )
        }
    }

    /** Loads every compatible stream provider. This intentionally does not select or launch a source. */
    fun openSources(media: MediaPreview, episode: Episode? = null, startFromBeginning: Boolean = false) {
        sourceJob?.cancel()
        sourceJob = viewModelScope.launch {
            if (media.id.startsWith("fixture:")) {
                showMessage("Fixture artwork has no media source. Install a stream add-on to play content.")
                return@launch
            }
            val videoId = episode?.id ?: media.id
            mutableState.update {
                it.copy(
                    refreshing = true,
                    sourcePicker = SourcePickerState(
                        media = media,
                        episode = episode,
                        startFromBeginning = startFromBeginning,
                    ),
                )
            }
            val embedded = episode?.streams.orEmpty().ifEmpty {
                if (episode == null) state.value.selectedDetail?.embeddedStreams.orEmpty() else emptyList()
            }
            if (embedded.isNotEmpty()) {
                val providerNames = state.value.providers.associate { it.id to it.displayName }
                mutableState.update {
                    it.copy(
                        refreshing = false,
                        sourcePicker = SourcePickerState(
                            media = media,
                            episode = episode,
                            startFromBeginning = startFromBeginning,
                            sources = embedded,
                            providerLabels = embedded.associate { source ->
                                source.providerId to (providerNames[source.providerId] ?: source.providerId)
                            },
                            loading = false,
                        ),
                    )
                }
                return@launch
            }
            val enabledProviders = state.value.providers
                .filter(ProviderSubscription::enabled)
                .sortedBy(ProviderSubscription::sortOrder)
            if (enabledProviders.isEmpty()) {
                mutableState.update { it.copy(refreshing = false, sourcePicker = null) }
                showMessage("Install a stream add-on to play this title.")
                return@launch
            }
            val resolvedProviders = supervisorScope {
                enabledProviders.map { subscription ->
                    async {
                        val manifest = container.providerClient.manifest(subscription.manifestUrl)
                        subscription to (manifest as? ProviderResult.Success)?.value
                    }
                }.awaitAll()
            }
            val manifestFailures = resolvedProviders
                .filter { (_, manifest) -> manifest == null }
                .associate { (provider, _) -> provider.id to "${provider.displayName} is unavailable." }
            val streamProviders = resolvedProviders.filter { (_, manifest) ->
                manifest?.let { container.providerAggregator.supports(it, "stream", media.rawType, videoId) } == true
            }
            if (streamProviders.isEmpty()) {
                mutableState.update {
                    it.copy(
                        refreshing = false,
                        sourcePicker = it.sourcePicker?.copy(loading = false, failures = manifestFailures),
                    )
                }
                showMessage("No installed add-on supports sources for this title.")
                return@launch
            }
            val streamResults = supervisorScope {
                streamProviders.map { (subscription, _) ->
                    async {
                        subscription to container.providerClient.streams(
                            subscription.manifestUrl,
                            subscription.id,
                            media.rawType,
                            videoId,
                        )
                    }
                }.awaitAll()
            }
            val streams = streamResults.flatMap { (_, result) -> (result as? ProviderResult.Success)?.value.orEmpty() }
            val labels = streamProviders.associate { (provider, _) -> provider.id to provider.displayName }
            val failures = manifestFailures + streamResults.mapNotNull { (provider, result) ->
                (result as? ProviderResult.Failure)?.let { provider.id to it.safeMessage }
            }.toMap()
            mutableState.update {
                it.copy(
                    refreshing = false,
                    sourcePicker = SourcePickerState(
                        media = media,
                        episode = episode,
                        startFromBeginning = startFromBeginning,
                        sources = streams,
                        providerLabels = labels,
                        failures = failures,
                        loading = false,
                    ),
                )
            }
        }
    }

    fun selectSourceProvider(providerId: String?) = mutableState.update {
        it.copy(sourcePicker = it.sourcePicker?.selectProvider(providerId))
    }

    /** Resolves one explicit source. Direct HTTPS is internal; everything else is handed off explicitly. */
    fun playSource(source: StreamCandidate) {
        val picker = state.value.sourcePicker ?: return
        sourceJob?.cancel()
        sourceJob = viewModelScope.launch {
            when (val resolution = resolveSource(source, BuildConfig.DEBUG)) {
                is SourceResolution.Internal -> {
                    mutableState.update { it.copy(sourcePicker = it.sourcePicker?.copy(loading = true)) }
                    val videoId = picker.episode?.id ?: picker.media.id
                    val tracks = loadSubtitles(picker.media, videoId, source)
                    val existingProgress = state.value.progress.firstOrNull { it.videoId == videoId }
                    val start = when {
                        picker.startFromBeginning -> 0
                        // Completed content restarts from the beginning; the
                        // completed badge itself survives until Mark unwatched.
                        existingProgress?.completed == true -> 0
                        else -> existingProgress?.positionMillis ?: 0
                    }
                    val episode = picker.episode
                    val episodeQueue = state.value.selectedDetail
                        ?.takeIf { it.preview.stableKey == picker.media.stableKey }
                        ?.episodes
                        .orEmpty()
                        .playbackQueueFrom(episode)
                        .map { queued ->
                            queued.copy(
                                overview = null,
                                thumbnailUrl = null,
                                streams = queued.streams
                                    .filter(StreamCandidate::isPlayableInternally)
                                    .take(8)
                                    .map(StreamCandidate::compactForPlaybackQueue),
                            )
                        }
                    val episodeLabel = episode?.let { ep ->
                        listOfNotNull(
                            ep.season?.let { season -> "S$season" },
                            ep.episode?.let { number -> "E$number" },
                            ep.title.takeIf { it.isNotBlank() },
                        ).joinToString(" · ")
                    }
                    mutableState.update { current ->
                        if (current.sourcePicker?.media?.stableKey != picker.media.stableKey) current
                        else current.copy(
                            playbackRequest = PlaybackRequest(
                                mediaKey = picker.media.stableKey,
                                videoId = videoId,
                                title = picker.media.name,
                                subtitle = episodeLabel,
                                artworkUrl = picker.media.backgroundUrl ?: picker.media.posterUrl,
                                preview = picker.media,
                                source = PlaybackSource(
                                    uri = resolution.url,
                                    mimeType = source.mimeType ?: resolution.url.inferMimeType(),
                                    headers = source.headers,
                                    subtitles = (source.subtitles + tracks).distinctBy { "${it.language}|${it.url}|${it.id}" },
                                ),
                                startPositionMillis = start,
                                episode = episode,
                                nextEpisode = episodeQueue.nextEpisodeAfter(episode),
                                episodeQueue = episodeQueue,
                                sourceProviderId = source.providerId,
                                sourceBingeGroup = source.bingeGroup,
                            ),
                            sourcePicker = null,
                        )
                    }
                }
                is SourceResolution.External -> mutableState.update { it.copy(externalPlaybackUrl = resolution.uri) }
                is SourceResolution.Unsupported -> showMessage(resolution.message)
            }
        }
    }

    fun closeSourcePicker() {
        sourceJob?.cancel()
        mutableState.update { it.copy(sourcePicker = null, refreshing = false) }
    }

    fun playbackLaunchHandled() = mutableState.update { it.copy(playbackRequest = null, externalPlaybackUrl = null) }

    fun configurationLaunchHandled() = mutableState.update { it.copy(configurationUrl = null) }

    private var pairingPollJob: Job? = null

    fun createPairingSession() {
        pairingPollJob?.cancel()
        pairingPollJob = viewModelScope.launch {
            CloudLog.i("tv.pairing creating session…")
            container.pairingGateway.createPairingSession(PAIRING_DEVICE_LABEL, container.pairingDeviceKey).onSuccess { session ->
                mutableState.update { it.copy(pairingSession = session) }
                pollForDeviceGrant(session)
            }.onFailure {
                CloudLog.e("tv.pairing createPairingSession failed — QR unavailable", it)
                showMessage(it.message ?: "Pairing is temporarily unavailable.")
            }
        }
    }

    /**
     * F3 "pairing sticks" guardrails: network errors never break the loop —
     * we simply poll again. Only a granted grant completes sign-in; a
     * terminal Expired/Consumed answer regenerates a fresh QR immediately
     * instead of polling a dead session until the local timer expires.
     */
    private suspend fun pollForDeviceGrant(session: PairingSession) {
        val supabase = container.supabase ?: return
        while (currentCoroutineContext().isActive) {
            delay(PAIRING_POLL_MILLIS)
            if (state.value.account is AccountState.SignedIn) return
            val remaining = session.expiresAtEpochMillis - System.currentTimeMillis()
            if (remaining <= 0) {
                CloudLog.i("tv.pairing session=${session.id} expired — regenerating QR")
                createPairingSession()
                return
            }
            when (val grant = container.pairingGateway.exchangeDeviceGrant(session.id).getOrNull()) {
                is DeviceGrant.Granted -> {
                    CloudLog.i("tv.pairing GRANT received for device=${grant.deviceId} — signing in")
                    completeDeviceSignIn(supabase, grant)
                    return
                }
                DeviceGrant.Expired -> {
                    CloudLog.i("tv.pairing session=${session.id} expired server-side — regenerating QR")
                    createPairingSession()
                    return
                }
                DeviceGrant.Consumed -> {
                    CloudLog.i("tv.pairing session=${session.id} consumed — regenerating QR")
                    showMessage("That pairing code was already used. Scan the fresh code to try again.")
                    createPairingSession()
                    return
                }
                DeviceGrant.Pending, null -> CloudLog.d(
                    "tv.pairing pending (session expires in ${remaining / 1000}s)",
                ) // keep polling through hiccups
            }
        }
    }

    /**
     * Consumes the single-use grant immediately: verifyEmailOtp mints the
     * TV's own GoTrue session (persisted by the SDK; refresh tokens never
     * expire by default). Persisting the device ID is enough — the lifecycle
     * collector is the SINGLE writer for register_device_session (it retries
     * with backoff), binding devices.auth_session_id so revocation can later
     * kill exactly this TV (F6).
     */
    private suspend fun completeDeviceSignIn(supabase: SupabaseClient, grant: DeviceGrant.Granted) {
        runCatching<Unit> {
            CloudLog.i("tv.pairing exchanging OTP for a TV session (email=${CloudLog.sanitize("""{"email":"${grant.email}"}""")})")
            supabase.auth.verifyEmailOtp(OtpType.Email.MAGIC_LINK, grant.email, grant.otp)
            CloudLog.i("tv.pairing OTP verified — TV session minted, binding device…")
            container.preferences.setPairingDeviceId(grant.deviceId)
            CloudLog.i("tv.pairing device=${grant.deviceId} pairing session persisted")
        }.onFailure {
            CloudLog.e("tv.pairing sign-in after grant failed (device=${grant.deviceId})", it)
            showMessage("The TV was claimed, but signing in failed. Refresh the QR and try again.")
        }
    }

    fun claimPairingSession(code: String) = viewModelScope.launch {
        container.pairingGateway.claimPairingSession(code.trim()).onSuccess {
            CloudLog.i("mobile.pairing claim succeeded (code=$code) — TV will sign in on its next poll")
            showMessage("TV paired successfully.")
        }.onFailure {
            CloudLog.e("mobile.pairing claim failed (code=$code)", it)
            showMessage(it.message ?: "The pairing code is invalid or expired.")
        }
    }

    private var devicesLoadedOnce = false

    fun loadDevices() {
        if (devicesLoadedOnce && state.value.pairedDevices.isNotEmpty()) return
        viewModelScope.launch {
            container.pairingGateway.listDevices().onSuccess { devices ->
                devicesLoadedOnce = true
                mutableState.update { it.copy(pairedDevices = devices) }
            }.onFailure {
                CloudLog.w("settings.devices load failed — leaving section empty", it)
            }
        }
    }

    fun revokeDevice(deviceId: String, label: String) = viewModelScope.launch {
        container.pairingGateway.revokeDevice(deviceId).onSuccess {
            CloudLog.i("settings.devices revoke succeeded (device=$deviceId)")
            mutableState.update { current ->
                current.copy(pairedDevices = current.pairedDevices.filterNot { it.id == deviceId })
            }
            showMessage("$label disconnected. It will return to its pairing screen.")
        }.onFailure {
            CloudLog.e("settings.devices revoke failed (device=$deviceId)", it)
            showMessage(it.message ?: "Disconnecting failed. Try again.")
        }
    }

    fun deleteAccount() = viewModelScope.launch {
        container.accountGateway.deleteAccount().onSuccess {
            CloudLog.i("settings.account deleted — wiping local cache, session cleared")
            // Cloud rows are gone (cascade); without this the stale Room data
            // would suppress createInitialProfile for the next registration
            // and leak the old account's content into it via seeding.
            container.libraryRepository.clearLocalAccountData()
            container.preferences.setActiveProfile(null)
            container.preferences.setPairingDeviceId(null)
            devicesLoadedOnce = false
            mutableState.update(AppUiState::clearAccountData)
            showMessage("Your account and all cloud data were deleted.")
        }.onFailure {
            CloudLog.e("settings.account delete failed", it)
            showMessage(it.message ?: "Account deletion failed. Try again.")
        }
    }

    fun setTheme(theme: ThemePreference) = viewModelScope.launch {
        container.preferences.setTheme(theme)
        pushSyncedSettings()
    }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch {
        container.preferences.setDynamicColor(enabled)
        pushSyncedSettings()
    }

    fun setKenBurnsEnabled(enabled: Boolean) = viewModelScope.launch {
        container.preferences.setKenBurnsEnabled(enabled)
        pushSyncedSettings()
    }

    fun changeArtworkKeyStorageMode(enabled: Boolean) = viewModelScope.launch {
        val userId = (state.value.account as? AccountState.SignedIn)?.userId
        if (userId == null) {
            showMessage("Sign in before changing artwork key storage.")
            return@launch
        }
        if (state.value.artworkStorageModeChanging) return@launch

        mutableState.update { it.copy(artworkStorageModeChanging = true) }
        val result = container.artworkStorageModeGateway.changeLocalOnlyMode(userId, enabled)
        result.fold(
            onSuccess = {
                mutableState.update { current ->
                    current.copy(
                        artworkStorageModeChanging = false,
                        artworkProviders = current.artworkProviders.map { it.copy(configured = false) },
                        lastArtworkLookupFailures = emptyMap(),
                    )
                }
                refreshArtworkKeyStatus(userId)
                showMessage(
                    if (enabled) {
                        "Local-only artwork keys enabled. Add your keys on this device."
                    } else {
                        "Local-only artwork keys disabled. Add keys for cloud artwork lookups."
                    },
                )
            },
            onFailure = {
                mutableState.update {
                    it.copy(
                        artworkStorageModeChanging = false,
                        message = "Could not switch artwork key storage. Some keys may already have been deleted; check your provider settings and try again.",
                    )
                }
            },
        )
    }

    fun setDiagnostics(consent: DiagnosticsConsent) = viewModelScope.launch {
        container.preferences.setDiagnostics(consent.copy(updatedAtEpochMillis = System.currentTimeMillis()))
        // Diagnostics backends are being replaced alongside the Supabase migration;
        // consent is persisted now and honored by whichever provider lands next.
        pushSyncedSettings()
    }

    fun setSpoilerProtection(settings: SpoilerProtectionSettings) = viewModelScope.launch {
        container.preferences.setSpoilerProtection(settings)
        pushSyncedSettings()
    }

    fun setPlaybackSettings(settings: PlaybackSettings) = viewModelScope.launch {
        container.preferences.setPlaybackSettings(settings)
    }



    fun dismissMessage() = mutableState.update { it.copy(message = null) }

    private suspend fun loadSubtitles(
        media: MediaPreview,
        videoId: String,
        source: StreamCandidate,
    ): List<SubtitleTrack> {
        val extras = buildMap {
            source.videoHash?.let { put("videoHash", it) }
            source.videoSize?.let { put("videoSize", it.toString()) }
            source.filename?.let { put("filename", it) }
        }
        return supervisorScope {
            state.value.providers
                .filter(ProviderSubscription::enabled)
                .sortedBy(ProviderSubscription::sortOrder)
                .map { subscription ->
                    async {
                        val manifest = container.providerClient.manifest(subscription.manifestUrl)
                        if (manifest !is ProviderResult.Success ||
                            !container.providerAggregator.supports(manifest.value, "subtitles", media.rawType, videoId)
                        ) {
                            emptyList()
                        } else {
                            (container.providerClient.subtitles(
                                subscription.manifestUrl,
                                media.rawType,
                                videoId,
                                extras,
                            ) as? ProviderResult.Success)?.value.orEmpty()
                        }
                    }
                }.awaitAll().flatten()
        }.distinctBy { "${it.language}|${it.url}|${it.id}" }
    }

    private fun requestProviderConfiguration(address: String, providerName: String) {
        val configurationUrl = address.providerConfigurationUrl()
        if (configurationUrl == null) {
            showMessage("$providerName needs browser setup, but its configuration page is invalid.")
            return
        }
        mutableState.update {
            it.copy(
                configurationUrl = configurationUrl,
                message = "Finish $providerName setup in the browser, then choose Install.",
            )
        }
    }

    private fun refreshCatalogs(force: Boolean = false) {
        val current = state.value
        val account = current.account as? AccountState.SignedIn
        if (account == null) {
            homeLog("refresh skipped: account signed out")
            refreshJob?.cancel()
            refreshJob = null
            homeCatalogBatchJob?.cancel()
            homeCatalogBatchJob = null
            homeCatalogGeneration++
            homeCatalogLoader = null
            catalogRefreshGate.reset()
            mutableState.update { it.copy(homeCatalogBatch = HomeCatalogBatchState()) }
            homeLog("refresh cleared generation=$homeCatalogGeneration")
            return
        }
        val fingerprint = CatalogRefreshFingerprint(
            userId = account.userId,
            childFilterEnabled = current.activeProfile?.let { profile ->
                profile.kind == ProfileKind.CHILD && profile.hideUnrated
            } == true,
            providers = current.providers
                .map { provider ->
                    CatalogProviderFingerprint(
                        id = provider.id,
                        manifestUrl = provider.manifestUrl,
                        displayName = provider.displayName,
                        enabled = provider.enabled,
                        sortOrder = provider.sortOrder,
                    )
                }
                .sortedWith(compareBy<CatalogProviderFingerprint> { it.sortOrder }.thenBy { it.id }),
        )
        if (!catalogRefreshGate.shouldStart(fingerprint, force)) {
            homeLog("refresh skipped: fingerprint unchanged")
            return
        }

        refreshJob?.cancel()
        homeCatalogBatchJob?.cancel()
        homeCatalogGeneration++
        val generation = homeCatalogGeneration
        homeCatalogLoader = null
        homeLog(
            "refresh started force=$force generation=$generation providers=${current.providers.size} " +
                "previousSections=${current.sections.size}",
        )
        refreshJob = viewModelScope.launch {
            if (current.providers.isEmpty()) {
                homeLog("refresh providerless branch")
                if (defaultCatalogJob?.isActive == true) {
                    currentCoroutineContext().ensureActive()
                    mutableState.update { it.copy(refreshing = true) }
                    return@launch
                }
                val preview = if (BuildConfig.DEBUG && account.userId == "local-development") {
                    listOf(
                        CatalogSection(
                            id = "preview",
                            providerId = "local-fixture",
                            title = "Preview library",
                            providerName = "Local fixture",
                            items = PreviewMedia.items,
                        ),
                    )
                } else {
                    emptyList()
                }
                currentCoroutineContext().ensureActive()
                mutableState.update {
                    it.copy(
                        sections = mergeCatalogRefresh(current.sections, preview),
                        homeCatalogBatch = HomeCatalogBatchState(),
                        initialContentLoading = false,
                        refreshing = false,
                    )
                }
                homeLog("refresh providerless published sections=${preview.size}")
                return@launch
            }

            val loader = HomeCatalogLoader(
                providerClient = container.providerClient,
                providers = current.providers,
                currentYear = Calendar.getInstance().get(Calendar.YEAR),
                logger = ::homeLog,
            )
            homeCatalogLoader = loader
            mutableState.update {
                it.copy(
                    refreshing = true,
                    homeCatalogBatch = HomeCatalogBatchState(loadingMore = true),
                )
            }
            homeLog("initial Home window requested generation=$generation")
            try {
                val window = requestHomeCatalogWindow(
                    loader = loader,
                    generation = generation,
                    childFilterEnabled = fingerprint.childFilterEnabled,
                    append = false,
                )
                finishHomeCatalogWindow(generation, window)
                homeLog(
                    "initial Home window complete sections=${window.sections.size} " +
                        "consumed=${window.consumedTargetCount} hasMore=${window.hasMore}",
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                markHomeCatalogFailure("initial Home window failed", generation, error)
            }
        }
    }

    fun loadMoreHomeCatalogSections() {
        val current = state.value
        val batchState = current.homeCatalogBatch
        val loader = homeCatalogLoader
        if (
            !batchState.hasMore ||
            batchState.loadingMore ||
            batchState.loadMoreFailed ||
            loader == null
        ) {
            homeLog(
                "window load ignored hasMore=${batchState.hasMore} loading=${batchState.loadingMore} " +
                    "failed=${batchState.loadMoreFailed} loader=${loader != null}",
            )
            return
        }
        val generation = homeCatalogGeneration
        val childFilterEnabled = current.activeProfile?.let { profile ->
            profile.kind == ProfileKind.CHILD && profile.hideUnrated
        } == true
        mutableState.update {
            if (it.homeCatalogBatch != batchState) {
                it
            } else {
                it.copy(homeCatalogBatch = batchState.copy(loadingMore = true))
            }
        }
        homeLog(
            "Home window load started generation=$generation consumed=${batchState.consumedTargetCount}",
        )
        homeCatalogBatchJob = viewModelScope.launch {
            try {
                val window = requestHomeCatalogWindow(
                    loader = loader,
                    generation = generation,
                    childFilterEnabled = childFilterEnabled,
                    append = true,
                )
                finishHomeCatalogWindow(generation, window)
                homeLog(
                    "Home window load complete sections=${window.sections.size} " +
                        "consumed=${window.consumedTargetCount} hasMore=${window.hasMore}",
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                markHomeCatalogFailure("Home window load failed", generation, error)
            }
        }
    }

    fun retryHomeCatalogSections() {
        val current = state.value
        val batchState = current.homeCatalogBatch
        val loader = homeCatalogLoader
        if (batchState.loadingMore || !batchState.loadMoreFailed || loader == null) return

        val generation = homeCatalogGeneration
        val childFilterEnabled = current.activeProfile?.let { profile ->
            profile.kind == ProfileKind.CHILD && profile.hideUnrated
        } == true
        val append = batchState.consumedTargetCount > 0
        mutableState.update {
            it.copy(
                homeCatalogBatch = it.homeCatalogBatch.copy(
                    loadingMore = true,
                    loadMoreFailed = false,
                ),
            )
        }
        homeLog(
            "Home window retry started generation=$generation consumed=${batchState.consumedTargetCount}",
        )
        homeCatalogBatchJob = viewModelScope.launch {
            try {
                val window = requestHomeCatalogWindow(
                    loader = loader,
                    generation = generation,
                    childFilterEnabled = childFilterEnabled,
                    append = append,
                )
                finishHomeCatalogWindow(generation, window)
                homeLog(
                    "Home window retry complete consumed=${window.consumedTargetCount} hasMore=${window.hasMore}",
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                markHomeCatalogFailure("Home window retry failed", generation, error)
            }
        }
    }

    private suspend fun requestHomeCatalogWindow(
        loader: HomeCatalogLoader,
        generation: Long,
        childFilterEnabled: Boolean,
        append: Boolean,
    ): HomeCatalogWindow = loader.loadNextWindow(
        childFilterEnabled = childFilterEnabled,
        onPrepared = prepared@{ window ->
            if (generation != homeCatalogGeneration) return@prepared
            mutableState.update { current ->
                val sections = if (append) {
                    appendHomeCatalogBatch(current.sections, window.sections)
                } else {
                    mergeCatalogRefresh(current.sections, window.sections)
                }
                current.copy(
                    sections = sections,
                    homeCatalogBatch = current.homeCatalogBatch.copy(
                        consumedTargetCount = window.consumedTargetCount,
                        hasMore = window.hasMore,
                        loadingMore = window.sections.any(CatalogSection::initialLoading),
                        loadMoreFailed = false,
                    ),
                    initialContentLoading = false,
                )
            }
            homeLog(
                "Home window prepared append=$append sections=${window.sections.size} " +
                    "consumed=${window.consumedTargetCount} hasMore=${window.hasMore}",
            )
        },
        onResolved = resolved@{ section ->
            if (generation != homeCatalogGeneration) return@resolved
            mutableState.update { current ->
                val index = current.sections.indexOfFirst { it.id == section.id }
                if (index < 0) return@update current
                val currentSection = current.sections[index]
                val merged = mergeCatalogRefresh(listOf(currentSection), listOf(section)).single()
                val sections = current.sections.toMutableList()
                sections[index] = merged
                current.copy(sections = sections)
            }
            homeLog("Home section resolved id=${section.id}")
        },
    )

    private fun finishHomeCatalogWindow(
        generation: Long,
        window: HomeCatalogWindow,
    ) {
        if (generation != homeCatalogGeneration) return
        mutableState.update {
            it.copy(
                homeCatalogBatch = it.homeCatalogBatch.copy(
                    consumedTargetCount = window.consumedTargetCount,
                    hasMore = window.hasMore,
                    loadingMore = false,
                    loadMoreFailed = false,
                ),
                initialContentLoading = false,
                refreshing = false,
            )
        }
    }

    private fun markHomeCatalogFailure(
        message: String,
        generation: Long,
        error: Throwable,
    ) {
        Log.e(HOME_CATALOG_LOG_TAG, "$message generation=$generation", error)
        if (generation != homeCatalogGeneration) return
        mutableState.update {
            it.copy(
                homeCatalogBatch = it.homeCatalogBatch.copy(
                    loadingMore = false,
                    loadMoreFailed = true,
                ),
                initialContentLoading = false,
                refreshing = false,
            )
        }
    }

    private fun filterForProfile(
        items: List<MediaPreview>,
        childFilterEnabled: Boolean = state.value.activeProfile?.let { profile ->
            profile.kind == ProfileKind.CHILD && profile.hideUnrated
        } == true,
    ): List<MediaPreview> = if (childFilterEnabled) items.filter { !it.contentRating.isNullOrBlank() } else items


    private suspend fun saveProvider(
        address: String,
        manifest: ProviderManifest,
        sortOrder: Int,
        displayName: String = manifest.name,
    ) {
        val canonicalAddress = address.canonicalProviderAddress()
        val existingId = state.value.providers.firstOrNull { it.manifestUrl.canonicalProviderAddress() == canonicalAddress }?.id
        val installationId = existingId ?: if (manifest.id == CINEMETA_PROVIDER_ID || canonicalAddress == CINEMETA_MANIFEST_URL) {
            CINEMETA_PROVIDER_ID
        } else {
            stableInstallationId(manifest.id, canonicalAddress)
        }
        val provider = ProviderSubscription(
            id = installationId,
            manifestUrl = canonicalAddress,
            displayName = displayName,
            sortOrder = sortOrder,
            updatedAtEpochMillis = System.currentTimeMillis(),
        )
        container.libraryRepository.saveProvider(provider)
        (state.value.account as? AccountState.SignedIn)?.userId?.let {
            container.cloudSyncGateway.saveProvider(it, provider).onFailure { error ->
                CloudLog.w("provider.install not synced (${provider.id}) — converges next session", error)
            }
        }
    }

    private fun stableInstallationId(manifestId: String, address: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(address.trim().toByteArray())
            .joinToString("") { "%02x".format(it) }
        val safeManifestId = manifestId
            .filter { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }
            .take(80)
            .ifBlank { "provider" }
        return "$safeManifestId@${digest.take(12)}"
    }

    private fun ensureDefaultCatalog(providers: List<ProviderSubscription>) {
        val developmentSources = providers.filter { BuildConfig.DEBUG && it.id == DEVELOPMENT_SOURCE_ID }
        val existing = providers.firstOrNull {
            it.id == CINEMETA_PROVIDER_ID || it.manifestUrl == CINEMETA_MANIFEST_URL
        }
        val normalized = existing?.let {
            it.displayName == DEFAULT_CATALOG_DISPLAY_NAME && it.sortOrder == DEFAULT_CATALOG_SORT_ORDER && it.enabled
        } == true
        if (developmentSources.isEmpty() && normalized) return
        if (defaultCatalogJob?.isActive == true) return
        defaultCatalogJob = viewModelScope.launch {
            developmentSources.forEach { container.libraryRepository.removeProvider(it.id) }
            if (existing != null) {
                val catalog = existing.copy(
                    manifestUrl = CINEMETA_MANIFEST_URL,
                    displayName = DEFAULT_CATALOG_DISPLAY_NAME,
                    enabled = true,
                    sortOrder = DEFAULT_CATALOG_SORT_ORDER,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                )
                container.libraryRepository.saveProvider(catalog)
                (state.value.account as? AccountState.SignedIn)?.userId?.let {
                    container.cloudSyncGateway.saveProvider(it, catalog).onFailure { error ->
                        CloudLog.w("provider.catalog not synced (${catalog.id})", error)
                    }
                }
                return@launch
            }
            when (val result = container.providerClient.manifest(CINEMETA_MANIFEST_URL)) {
                is ProviderResult.Success -> saveProvider(
                    address = CINEMETA_MANIFEST_URL,
                    manifest = result.value,
                    sortOrder = DEFAULT_CATALOG_SORT_ORDER,
                    displayName = DEFAULT_CATALOG_DISPLAY_NAME,
                )
                is ProviderResult.Failure -> {
                    defaultCatalogJob = null
                    refreshCatalogs(force = true)
                    showMessage("The Cinemeta catalog is temporarily unavailable.")
                }
            }
        }
    }

    private suspend fun createInitialProfile() {
        val now = System.currentTimeMillis()
        val profile = Profile(UUID.randomUUID().toString(), "Home", "moon", ProfileKind.ADULT, updatedAtEpochMillis = now)
        container.libraryRepository.saveProfile(profile, null)
        (state.value.account as? AccountState.SignedIn)?.userId?.let { container.cloudSyncGateway.saveProfile(it, profile) }
        container.preferences.setActiveProfile(profile.id)
    }

    private fun showMessage(message: String) = mutableState.update { it.copy(message = message) }

    /**
     * Add-ons ride Edge Functions (deny-all table → no realtime): one pull
     * per session. An empty cloud gets seeded from local installs — mirroring
     * the profiles seeding rule — while a non-empty cloud is authoritative:
     * locally-unknown ids mean the add-on was removed on another device.
     */
    private suspend fun syncProviders(userId: String) {
        val cloudProviders = container.cloudSyncGateway.providers(userId).getOrElse { error ->
            CloudLog.w("providers.pull failed — keeping local add-ons", error)
            return
        }
        if (cloudProviders.isEmpty()) {
            localSyncableProviders().forEach { provider ->
                container.cloudSyncGateway.saveProvider(userId, provider).onFailure {
                    CloudLog.w("provider.seed failed (${provider.id})", it)
                }
            }
            return
        }
        cloudProviders.forEach { container.libraryRepository.saveProvider(it) }
    }

    /** Real installed add-ons only: the built-in catalog and debug sources never sync. */
    private suspend fun localSyncableProviders(): List<ProviderSubscription> =
        container.libraryRepository.providers().first().filter { provider ->
            provider.sortOrder >= 0 && provider.id != DEVELOPMENT_SOURCE_ID
        }

    /**
     * Settings follow the account. Inbound rows win only when newer than the
     * last local mutation (LWW); an absent row gets seeded from local values.
     * Applies never re-push, so realtime echoes cannot loop.
     */
    private suspend fun syncSettings(userId: String) {
        container.cloudSyncGateway.settings(userId).collect { remote ->
            val local = container.preferences.current()
            when {
                remote == null -> container.cloudSyncGateway.saveSettings(
                    userId = userId,
                    settings = SyncedSettings(
                        theme = local.theme,
                        dynamicColor = local.dynamicColor,
                        kenBurnsEnabled = local.kenBurnsEnabled,
                        diagnostics = local.diagnostics,
                        spoilerProtection = local.spoilerProtection,
                        updatedAtEpochMillis = local.updatedAtEpochMillis,
                    ),
                ).onFailure { error -> CloudLog.w("settings.seed failed — staying local", error) }

                remote.updatedAtEpochMillis > local.updatedAtEpochMillis ->
                    container.preferences.applyRemoteSettings(remote)
            }
        }
    }

    /** Mirrors a local settings change into the account row. */
    private fun pushSyncedSettings() = viewModelScope.launch {
        val userId = (state.value.account as? AccountState.SignedIn)?.userId ?: return@launch
        val local = container.preferences.current()
        container.cloudSyncGateway.saveSettings(
            userId = userId,
            settings = SyncedSettings(
                theme = local.theme,
                dynamicColor = local.dynamicColor,
                kenBurnsEnabled = local.kenBurnsEnabled,
                diagnostics = local.diagnostics,
                spoilerProtection = local.spoilerProtection,
                updatedAtEpochMillis = local.updatedAtEpochMillis,
            ),
        ).onFailure { error -> CloudLog.w("settings.push failed — converges next session", error) }
    }

    private fun startCloudSync(userId: String) {
        if (!BuildConfig.CLOUD_CONFIGURED) return
        // A live job belongs to exactly one user. After sign-out → sign-in
        // (or a deleted account's successor) a surviving job would still be
        // bound to the old uid's channels and silently swallow the new
        // user's sync — including empty-cloud seeding — leaving profiles
        // unwritten. Restart whenever the identity changes.
        if (cloudSyncJob?.isActive == true && cloudSyncUserId == userId) return
        cloudSyncJob?.cancel()
        cloudSyncUserId = userId
        cloudSyncJob = viewModelScope.launch {
            launch {
                container.cloudSyncGateway.profiles(userId).collect { cloudProfiles ->
                    cloudProfiles.forEach { container.libraryRepository.saveProfile(it, null) }
                }
            }
            launch {
                syncProviders(userId)
            }
            launch {
                syncSettings(userId)
            }
            // One cloud probe decides how the account boots on this device:
            // non-empty → the collector above adopts those rows and nothing is
            // minted locally; empty → seed from local data, or — when this
            // device has nothing either — create the account's first profile.
            // (Creation used to run before this probe on every clean sign-in,
            // so a freshly paired TV minted a duplicate "Home" alongside the
            // account's real profiles.)
            launch {
                val cloudProfiles = container.cloudSyncGateway.profiles(userId).first()
                if (cloudProfiles.isNotEmpty()) return@launch
                // Legacy local installs may hold placeholder ids (e.g. "primary")
                // that cannot exist in Postgres; keep them out of cloud sync.
                val localProfiles = container.libraryRepository.profiles().first()
                    .filter { isCloudBackedId(it.id) }
                if (localProfiles.isEmpty()) {
                    createInitialProfile()
                    return@launch
                }
                localProfiles.forEach { profile ->
                    container.cloudSyncGateway.saveProfile(userId, profile)
                }
                localProfiles.forEach { profile ->
                    container.libraryRepository.library(profile.id).first()
                        .forEach { container.cloudSyncGateway.saveLibrary(userId, it) }
                    container.libraryRepository.progress(profile.id).first()
                        .forEach { container.cloudSyncGateway.saveProgress(userId, it) }
                }
            }
            launch {
                watchProfileChannels(userId)
            }
        }
    }


    /**
     * Live library+progress channels for every cloud-backed profile. Follows
     * profile creation/removal mid-session — capturing the list once at
     * sign-in left late-created profiles deaf to other devices until relaunch.
     */
    private fun CoroutineScope.watchProfileChannels(userId: String): Job = launch {
        val channels = mutableMapOf<String, Job>()
        container.libraryRepository.profiles()
            .map { profiles -> profiles.map(Profile::id).filter(::isCloudBackedId).sorted() }
            .distinctUntilChanged()
            .collect { profileIds ->
                profileIds.forEach { id -> channels.getOrPut(id) { launchProfileChannels(userId, id) } }
                channels.keys.toList().forEach { id ->
                    if (id !in profileIds) channels.remove(id)?.cancel()
                }
            }
    }

    private fun CoroutineScope.launchProfileChannels(userId: String, profileId: String): Job = launch {
        launch {
            container.cloudSyncGateway.library(userId, profileId)
                .retryWhen { throwable, attempt ->
                    if (throwable is CancellationException) throw throwable
                    Log.w(CLOUD_SYNC_LOG_TAG, "Library sync round failed; keeping local rows", throwable)
                    delay(((attempt + 1).coerceAtMost(30)) * 1_000L)
                    true
                }
                .collect { entries -> container.libraryRepository.reconcileLibrary(profileId, entries) }
        }
        launch {
            container.cloudSyncGateway.progress(userId, profileId)
                .retryWhen { throwable, attempt ->
                    if (throwable is CancellationException) throw throwable
                    Log.w(CLOUD_SYNC_LOG_TAG, "Progress sync round failed; keeping local rows", throwable)
                    delay(((attempt + 1).coerceAtMost(30)) * 1_000L)
                    true
                }
                .collect { progress -> container.libraryRepository.reconcileProgress(profileId, progress) }
        }
    }

    /** Placeholder ids from legacy local installs cannot exist in Postgres. */
    private fun isCloudBackedId(id: String) = runCatching { UUID.fromString(id) }.isSuccess
    private fun Throwable.safeAuthMessage(): String = when {
        message?.contains("network", ignoreCase = true) == true -> "Check your connection and try again."
        else -> "Sign-in could not be completed. Try again."
    }
    private data class Snapshot(
        val account: AccountState,
        val profiles: List<Profile>,
        val providers: List<ProviderSubscription>,
        val activeProfileId: String?,
        val theme: ThemePreference,
        val dynamicColor: Boolean,
        val kenBurnsEnabled: Boolean,
        val localOnlyArtworkKeys: Boolean,
        val diagnostics: DiagnosticsConsent,
        val spoilerProtection: SpoilerProtectionSettings,
        val playbackSettings: PlaybackSettings,
    )

    companion object {
        private const val MAX_DISCOVERED_PROVIDERS = 50
        private const val DEFAULT_CATALOG_SORT_ORDER = -100
        private const val DEFAULT_CATALOG_DISPLAY_NAME = "Cinemeta"

        private const val PAIRING_POLL_MILLIS = 3_000L
        private const val PAIRING_DEVICE_LABEL = "Living room TV"
        private const val DEVELOPMENT_SOURCE_ID = "lamphaus.dev.source"

        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(container) as T
        }
    }
}


private fun String.canonicalProviderAddress(): String {
    val uri = runCatching { URI(trim()) }.getOrNull() ?: return trim()
    val transport = if (!uri.scheme.equals("http", ignoreCase = true) && !uri.scheme.equals("https", ignoreCase = true) && uri.host != null) {
        URI("https", null, uri.host, uri.port, uri.path, uri.query, null)
    } else uri
    if (transport.host == null) return trim()
    val path = transport.path.orEmpty()
    val manifestPath = if (path.endsWith("/manifest.json")) path else path.trimEnd('/') + "/manifest.json"
    return URI(transport.scheme, null, transport.host, transport.port, manifestPath, transport.query, null).toString()
}

private fun String.providerConfigurationUrl(): String? {
    val uri = runCatching { URI(canonicalProviderAddress()) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true) || uri.host == null) return null
    val manifestPath = uri.path.orEmpty()
    val configurationPath = if (manifestPath.endsWith("/manifest.json")) {
        manifestPath.removeSuffix("/manifest.json") + "/configure"
    } else {
        manifestPath.trimEnd('/') + "/configure"
    }
    return URI("https", null, uri.host, uri.port, configurationPath, null, null).toString()
}

private fun StreamCandidate.compactForPlaybackQueue() = StreamCandidate(
    providerId = providerId,
    name = name,
    title = title,
    url = url,
    mimeType = mimeType,
    bingeGroup = bingeGroup,
    headers = headers,
    subtitles = subtitles,
)

private fun String.inferMimeType(): String? = when {
    contains(".m3u8", ignoreCase = true) -> "application/x-mpegURL"
    contains(".mpd", ignoreCase = true) -> "application/dash+xml"
    contains(".ism", ignoreCase = true) -> "application/vnd.ms-sstr+xml"
    contains(".mkv", ignoreCase = true) -> "video/x-matroska"
    contains(".webm", ignoreCase = true) -> "video/webm"
    contains(".ts", ignoreCase = true) -> "video/mp2t"
    contains(".mp4", ignoreCase = true) -> "video/mp4"
    else -> null
}

internal fun MediaDetail.merge(other: MediaDetail): MediaDetail = MediaDetail(
    preview = preview.merge(other.preview),
    runtimeMinutes = runtimeMinutes ?: other.runtimeMinutes,
    cast = (cast + other.cast).distinct(),
    directors = (directors + other.directors).distinct(),
    episodes = (episodes + other.episodes)
        .groupBy(Episode::id)
        .values
        .map { versions -> versions.reduce(Episode::merge) },
    embeddedStreams = (embeddedStreams + other.embeddedStreams).distinctBy(::sourceIdentity),
)

internal fun Episode.merge(other: Episode): Episode = copy(
    title = title.ifBlank { other.title },
    season = season ?: other.season,
    episode = episode ?: other.episode,
    overview = overview ?: other.overview,
    thumbnailUrl = thumbnailUrl ?: other.thumbnailUrl,
    releasedAtEpochMillis = releasedAtEpochMillis ?: other.releasedAtEpochMillis,
    streams = (streams + other.streams).distinctBy(::sourceIdentity),
)

internal fun MediaPreview.merge(other: MediaPreview): MediaPreview = copy(
    posterUrl = posterUrl ?: other.posterUrl,
    backgroundUrl = backgroundUrl ?: other.backgroundUrl,
    logoUrl = logoUrl ?: other.logoUrl,
    description = description ?: other.description,
    releaseYear = releaseYear ?: other.releaseYear,
    genres = (genres + other.genres).distinct(),
    contentRating = contentRating ?: other.contentRating,
    rating = rating ?: other.rating,
    ratingSource = ratingSource ?: other.ratingSource,
    providerIds = providerIds + other.providerIds,
    posterShape = posterShape ?: other.posterShape,
)

private fun sourceIdentity(source: StreamCandidate): String = listOfNotNull(
    source.providerId,
    source.url,
    source.externalUrl,
    source.infoHash,
    source.fileIndex?.toString(),
    source.ytId,
    source.nzbUrl,
    source.archiveFiles.firstOrNull()?.url,
).joinToString("|")

private fun StateFlow<AppUiState>.mapActiveProfileId() = map { it.activeProfileId }
