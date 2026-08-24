package com.lamphaus.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance
import com.lamphaus.app.AppContainer
import com.lamphaus.app.BuildConfig
import com.lamphaus.core.data.cloud.AccountState
import com.lamphaus.core.data.preferences.ThemePreference
import com.lamphaus.core.model.CatalogQuery
import com.lamphaus.core.model.DiagnosticsConsent
import com.lamphaus.core.model.LibraryEntry
import com.lamphaus.core.model.MediaDetail
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.Profile
import com.lamphaus.core.model.ProfileKind
import com.lamphaus.core.model.ProviderResult
import com.lamphaus.core.model.ProviderCatalog
import com.lamphaus.core.model.ProviderManifest
import com.lamphaus.core.model.ProviderSubscription
import com.lamphaus.core.model.PlaybackRequest
import com.lamphaus.core.model.PlaybackSource
import com.lamphaus.core.model.Episode
import com.lamphaus.core.model.StreamCandidate
import com.lamphaus.core.model.SubtitleTrack
import com.lamphaus.core.model.MediaType
import java.util.UUID
import java.net.URI
import java.util.Calendar
import java.security.MessageDigest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = mutableState.asStateFlow()
    private var refreshJob: Job? = null
    private var cloudSyncJob: Job? = null
    private var defaultCatalogJob: Job? = null
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            combine(
                container.accountGateway.state,
                container.libraryRepository.profiles(),
                container.libraryRepository.providers(),
                container.preferences.settings,
            ) { account, profiles, providers, settings ->
                Snapshot(account, profiles, providers, settings.activeProfileId, settings.theme, settings.dynamicColor, settings.diagnostics)
            }.collectLatest { snapshot ->
                val activeId = snapshot.activeProfileId?.takeIf { id -> snapshot.profiles.any { it.id == id } }
                    ?: snapshot.profiles.firstOrNull()?.id
                mutableState.update {
                    it.copy(
                        account = snapshot.account,
                        profiles = snapshot.profiles,
                        providers = snapshot.providers,
                        activeProfileId = activeId,
                        theme = snapshot.theme,
                        dynamicColor = snapshot.dynamicColor,
                        diagnostics = snapshot.diagnostics,
                    )
                }
                if (activeId != snapshot.activeProfileId) container.preferences.setActiveProfile(activeId)
                if (snapshot.account is AccountState.SignedIn && snapshot.profiles.isEmpty()) createInitialProfile()
                if (snapshot.account is AccountState.SignedIn) {
                    ensureDefaultCatalog(snapshot.providers)
                    startCloudSync(snapshot.account.userId, snapshot.profiles)
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
    }

    fun openDevelopmentSession() {
        runCatching(container::openDevelopmentSession)
            .onFailure { showMessage(it.message ?: "Development session is unavailable.") }
    }

    fun signInWithGoogleToken(token: String) = viewModelScope.launch {
        container.accountGateway.signInWithGoogleIdToken(token).onFailure { showMessage(it.safeAuthMessage()) }
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
        mutableState.update { it.copy(refreshing = true, message = null) }
        val directResult = container.providerClient.manifest(address)
        when (directResult) {
            is ProviderResult.Success -> {
                saveProvider(address, directResult.value, state.value.providers.size)
                val suffix = if (directResult.value.behaviorHints.configurationRequired) {
                    " Finish its setup in the provider's browser page."
                } else ""
                showMessage("${directResult.value.name} installed.$suffix Loading catalogs…")
            }
            is ProviderResult.Failure -> {
                when (val discovery = container.providerClient.discoverProviderUrls(address)) {
                    is ProviderResult.Success -> {
                        var installed = 0
                        discovery.value.distinct().take(MAX_DISCOVERED_PROVIDERS).forEach { manifestUrl ->
                            when (val manifest = container.providerClient.manifest(manifestUrl)) {
                                is ProviderResult.Success -> {
                                    saveProvider(manifestUrl, manifest.value, state.value.providers.size + installed)
                                    installed += 1
                                }
                                is ProviderResult.Failure -> Unit
                            }
                        }
                        if (installed > 0) {
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
        refreshCatalogs()
    }

    fun searchContent(input: String) {
        searchJob?.cancel()
        val queryText = input.trim()
        if (queryText.isBlank()) {
            mutableState.update { it.copy(searchResults = emptyList(), searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            mutableState.update { it.copy(searching = true) }
            val enabledProviders = state.value.providers
                .filter(ProviderSubscription::enabled)
                .sortedBy(ProviderSubscription::sortOrder)
            val searchableCatalogs = coroutineScope {
                enabledProviders.map { subscription ->
                    async {
                        val manifest = (container.providerClient.manifest(subscription.manifestUrl) as? ProviderResult.Success)?.value
                        manifest?.catalogs
                            ?.filter { "search" in it.extras && it.requiredExtras.all { extra -> extra == "search" } }
                            ?.groupBy(ProviderCatalog::type)
                            ?.values
                            ?.mapNotNull { catalogs -> catalogs.firstOrNull { it.id == "top" } ?: catalogs.firstOrNull() }
                            .orEmpty()
                            .map { subscription to it }
                    }
                }.awaitAll().flatten()
            }
            val results = coroutineScope {
                searchableCatalogs.map { (subscription, catalog) ->
                    async {
                        container.providerClient.catalog(
                            subscription.manifestUrl,
                            subscription.id,
                            CatalogQuery(catalog.type, catalog.id, search = queryText),
                        )
                    }
                }.awaitAll()
            }.flatMap { (it as? ProviderResult.Success)?.value.orEmpty() }
            mutableState.update {
                it.copy(
                    searchResults = filterForProfile(results).distinctBy(MediaPreview::stableKey),
                    searching = false,
                )
            }
        }
    }

    fun toggleProvider(providerId: String, enabled: Boolean) = viewModelScope.launch {
        if (state.value.providers.firstOrNull { it.id == providerId }?.sortOrder?.let { it < 0 } == true) {
            showMessage("The Lamphaus catalog is always available.")
            return@launch
        }
        container.libraryRepository.setProviderEnabled(providerId, enabled)
    }

    fun removeProvider(providerId: String) = viewModelScope.launch {
        if (state.value.providers.firstOrNull { it.id == providerId }?.sortOrder?.let { it < 0 } == true) {
            showMessage("The Lamphaus catalog cannot be removed.")
            return@launch
        }
        container.libraryRepository.removeProvider(providerId)
        (state.value.account as? AccountState.SignedIn)?.userId?.let {
            container.cloudSyncGateway.deleteProvider(it, providerId)
        }
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
        state.value.activeProfileId?.let { container.libraryRepository.removeLibrary(it, mediaKey) }
    }

    fun loadDetail(media: MediaPreview) = viewModelScope.launch {
        mutableState.update { it.copy(selectedDetail = MediaDetail(media), refreshing = true) }
        if (media.id.startsWith("fixture:")) {
            mutableState.update {
                it.copy(
                    selectedDetail = MediaDetail(media, runtimeMinutes = 52, episodes = if (media.type.name == "SERIES") PreviewMedia.episodes else emptyList()),
                    refreshing = false,
                )
            }
            return@launch
        }
        val enabledProviders = state.value.providers
            .filter(ProviderSubscription::enabled)
            .sortedBy(ProviderSubscription::sortOrder)
        val candidates = enabledProviders
            .filter { media.providerIds.isEmpty() || it.id in media.providerIds }
            .ifEmpty { enabledProviders }
        if (candidates.isEmpty()) {
            mutableState.update { it.copy(refreshing = false) }
            return@launch
        }
        val details = supervisorScope {
            candidates.map { provider ->
                async {
                    val manifest = container.providerClient.manifest(provider.manifestUrl)
                    if (manifest !is ProviderResult.Success || !container.providerAggregator.supports(manifest.value, "meta", media.rawType, media.id)) {
                        null
                    } else {
                        container.providerClient.meta(provider.manifestUrl, provider.id, media.rawType, media.id)
                    }
                }
            }.awaitAll().filterIsInstance<ProviderResult.Success<MediaDetail>>().map(ProviderResult.Success<MediaDetail>::value)
        }
        if (details.isNotEmpty()) {
            mutableState.update { it.copy(selectedDetail = details.fold(MediaDetail(media), MediaDetail::merge), refreshing = false) }
        } else {
            mutableState.update { it.copy(refreshing = false, message = "No metadata provider could load this title.") }
        }
    }

    fun clearDetail() = mutableState.update { it.copy(selectedDetail = null) }

    /** Loads every compatible stream provider. This intentionally does not select or launch a source. */
    fun openSources(media: MediaPreview, episode: Episode? = null) = viewModelScope.launch {
        if (media.id.startsWith("fixture:")) {
            showMessage("Fixture artwork has no media source. Install a stream add-on to play content.")
            return@launch
        }
        val enabledProviders = state.value.providers
            .filter(ProviderSubscription::enabled)
            .sortedBy(ProviderSubscription::sortOrder)
        if (enabledProviders.isEmpty()) {
            showMessage("Install a stream add-on to play this title.")
            return@launch
        }
        val videoId = episode?.id ?: media.id
        mutableState.update {
            it.copy(
                refreshing = true,
                sourcePicker = SourcePickerState(media = media, episode = episode),
            )
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
        val subtitleProviders = resolvedProviders.filter { (_, manifest) ->
            manifest?.let { container.providerAggregator.supports(it, "subtitles", media.rawType, videoId) } == true
        }
        val tracks = supervisorScope {
            subtitleProviders.map { (subscription, _) ->
                async {
                    container.providerClient.subtitles(
                        subscription.manifestUrl,
                        media.rawType,
                        videoId,
                        extras = buildMap {
                            episode?.let { put("videoId", it.id) }
                        },
                    )
                }
            }.awaitAll()
        }.flatMap { (it as? ProviderResult.Success)?.value.orEmpty() }
            .distinctBy { it.id }
        val labels = streamProviders.associate { (provider, _) ->
            provider.id to provider.displayName
        }
        val failures = manifestFailures + streamResults.mapNotNull { (provider, result) ->
            (result as? ProviderResult.Failure)?.let { provider.id to it.safeMessage }
        }.toMap()
        mutableState.update {
            it.copy(
                refreshing = false,
                sourcePicker = SourcePickerState(
                    media = media,
                    episode = episode,
                    sources = streams,
                    subtitles = tracks,
                    providerLabels = labels,
                    failures = failures,
                    loading = false,
                ),
            )
        }
    }

    fun selectSourceProvider(providerId: String?) = mutableState.update {
        it.copy(sourcePicker = it.sourcePicker?.selectProvider(providerId))
    }

    /** Resolves one explicit source. Direct HTTPS is internal; everything else is handed off explicitly. */
    fun playSource(source: StreamCandidate) = viewModelScope.launch {
        val picker = state.value.sourcePicker ?: return@launch
        val direct = source.url?.takeIf { it.startsWith("https://", ignoreCase = true) || (BuildConfig.DEBUG && it.isDebugLocalStream()) }
            ?: source.sourceUrls.firstOrNull { it.startsWith("https://", ignoreCase = true) || (BuildConfig.DEBUG && it.isDebugLocalStream()) }
        when {
            direct != null -> {
                val videoId = picker.episode?.id ?: picker.media.id
                val start = state.value.progress.firstOrNull { it.videoId == videoId }?.positionMillis ?: 0
                mutableState.update {
                    it.copy(
                        playbackRequest = PlaybackRequest(
                            mediaKey = picker.media.stableKey,
                            videoId = videoId,
                            title = picker.media.name,
                            subtitle = picker.episode?.title,
                            artworkUrl = picker.media.backgroundUrl ?: picker.media.posterUrl,
                            source = PlaybackSource(
                                uri = direct,
                                mimeType = source.mimeType ?: direct.inferMimeType(),
                                headers = source.headers,
                                subtitles = (source.subtitles + picker.subtitles).distinctBy(SubtitleTrack::id),
                            ),
                            startPositionMillis = start,
                        ),
                        sourcePicker = null,
                    )
                }
            }
            source.externalUrl != null -> mutableState.update { it.copy(externalPlaybackUrl = source.externalUrl) }
            source.ytId != null -> mutableState.update { it.copy(externalPlaybackUrl = "https://youtu.be/${source.ytId}") }
            source.infoHash != null -> mutableState.update {
                it.copy(externalPlaybackUrl = "magnet:?xt=urn:btih:${source.infoHash}")
            }
            source.url != null -> mutableState.update { it.copy(externalPlaybackUrl = source.url) }
            else -> showMessage("This source needs an external player that is not installed.")
        }
    }

    fun closeSourcePicker() = mutableState.update { it.copy(sourcePicker = null) }

    fun playbackLaunchHandled() = mutableState.update { it.copy(playbackRequest = null, externalPlaybackUrl = null) }

    fun createPairingSession() = viewModelScope.launch {
        container.pairingGateway.createPairingSession("Living room TV").onSuccess { session ->
            mutableState.update { it.copy(pairingSession = session) }
        }.onFailure { showMessage(it.message ?: "Pairing is temporarily unavailable.") }
    }

    fun setTheme(theme: ThemePreference) = viewModelScope.launch { container.preferences.setTheme(theme) }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { container.preferences.setDynamicColor(enabled) }

    fun setDiagnostics(consent: DiagnosticsConsent) = viewModelScope.launch {
        container.preferences.setDiagnostics(consent.copy(updatedAtEpochMillis = System.currentTimeMillis()))
        if (BuildConfig.CLOUD_CONFIGURED) {
            FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = consent.crashReports
            FirebasePerformance.getInstance().isPerformanceCollectionEnabled = consent.performanceMetrics
        }
    }

    fun dismissMessage() = mutableState.update { it.copy(message = null) }

    private fun refreshCatalogs() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val current = state.value
            if (current.account !is AccountState.SignedIn) return@launch
            if (current.providers.isEmpty()) {
                val preview = if (BuildConfig.DEBUG && current.account.userId == "local-development") {
                    listOf(CatalogSection("preview", "Preview library", "Local fixture", PreviewMedia.items))
                } else emptyList()
                mutableState.update { it.copy(sections = preview, refreshing = false) }
                return@launch
            }
            mutableState.update { it.copy(refreshing = true) }
            val sections = mutableListOf<CatalogSection>()
            current.providers.filter(ProviderSubscription::enabled).sortedBy(ProviderSubscription::sortOrder).forEach { subscription ->
                when (val manifestResult = container.providerClient.manifest(subscription.manifestUrl)) {
                    is ProviderResult.Failure -> sections += CatalogSection(
                        "${subscription.id}:error",
                        subscription.displayName,
                        subscription.displayName,
                        emptyList(),
                        manifestResult.safeMessage,
                    )
                    is ProviderResult.Success -> sections += coroutineScope {
                        manifestResult.value.catalogs.mapNotNull { catalog ->
                            val query = catalog.defaultQuery() ?: return@mapNotNull null
                            async {
                                when (val result = container.providerClient.catalog(
                                    subscription.manifestUrl,
                                    subscription.id,
                                    query,
                                )) {
                                    is ProviderResult.Success -> CatalogSection(
                                        "${subscription.id}:${catalog.type}:${catalog.id}:${query.genre.orEmpty()}",
                                        catalog.displayTitle(),
                                        subscription.displayName,
                                        filterForProfile(result.value),
                                    )
                                    is ProviderResult.Failure -> CatalogSection(
                                        "${subscription.id}:${catalog.type}:${catalog.id}:${query.genre.orEmpty()}",
                                        catalog.displayTitle(),
                                        subscription.displayName,
                                        emptyList(),
                                        result.safeMessage,
                                    )
                                }
                            }
                        }.awaitAll()
                    }
                }
            }
            mutableState.update { it.copy(sections = sections, refreshing = false) }
        }
    }

    private fun filterForProfile(items: List<MediaPreview>): List<MediaPreview> {
        val profile = state.value.activeProfile ?: return items
        return if (profile.kind == ProfileKind.CHILD && profile.hideUnrated) {
            items.filter { !it.contentRating.isNullOrBlank() }
        } else items
    }

    private suspend fun saveProvider(
        address: String,
        manifest: ProviderManifest,
        sortOrder: Int,
        displayName: String = manifest.name,
    ) {
        val canonicalAddress = address.canonicalProviderAddress()
        val installationId = if (manifest.id == DEFAULT_CATALOG_PROVIDER_ID || canonicalAddress == DEFAULT_CATALOG_MANIFEST) {
            DEFAULT_CATALOG_PROVIDER_ID
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
            container.cloudSyncGateway.saveProvider(it, provider)
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
            it.id == DEFAULT_CATALOG_PROVIDER_ID || it.manifestUrl == DEFAULT_CATALOG_MANIFEST
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
                    manifestUrl = DEFAULT_CATALOG_MANIFEST,
                    displayName = DEFAULT_CATALOG_DISPLAY_NAME,
                    enabled = true,
                    sortOrder = DEFAULT_CATALOG_SORT_ORDER,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                )
                container.libraryRepository.saveProvider(catalog)
                (state.value.account as? AccountState.SignedIn)?.userId?.let {
                    container.cloudSyncGateway.saveProvider(it, catalog)
                }
                return@launch
            }
            when (val result = container.providerClient.manifest(DEFAULT_CATALOG_MANIFEST)) {
                is ProviderResult.Success -> saveProvider(
                    address = DEFAULT_CATALOG_MANIFEST,
                    manifest = result.value,
                    sortOrder = DEFAULT_CATALOG_SORT_ORDER,
                    displayName = DEFAULT_CATALOG_DISPLAY_NAME,
                )
                is ProviderResult.Failure -> showMessage("The Lamphaus catalog is temporarily unavailable.")
            }
        }
    }

    private suspend fun createInitialProfile() {
        val now = System.currentTimeMillis()
        val profile = Profile("primary", "Home", "moon", ProfileKind.ADULT, updatedAtEpochMillis = now)
        container.libraryRepository.saveProfile(profile, null)
        (state.value.account as? AccountState.SignedIn)?.userId?.let { container.cloudSyncGateway.saveProfile(it, profile) }
        container.preferences.setActiveProfile(profile.id)
    }

    private fun showMessage(message: String) = mutableState.update { it.copy(message = message) }

    private fun startCloudSync(userId: String, profiles: List<Profile>) {
        if (!BuildConfig.CLOUD_CONFIGURED || cloudSyncJob?.isActive == true) return
        cloudSyncJob = viewModelScope.launch {
            launch {
                container.cloudSyncGateway.profiles(userId).collect { cloudProfiles ->
                    cloudProfiles.forEach { container.libraryRepository.saveProfile(it, null) }
                }
            }
            launch {
                container.cloudSyncGateway.providers(userId).onSuccess { providers ->
                    providers.forEach { container.libraryRepository.saveProvider(it) }
                }
            }
            profiles.forEach { profile ->
                launch {
                    container.cloudSyncGateway.library(userId, profile.id).collect { entries ->
                        entries.forEach { container.libraryRepository.saveLibrary(it) }
                    }
                }
                launch {
                    container.cloudSyncGateway.progress(userId, profile.id).collect { entries ->
                        entries.forEach { container.libraryRepository.saveProgress(it) }
                    }
                }
            }
        }
    }
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
        val diagnostics: DiagnosticsConsent,
    )

    companion object {
        private const val MAX_DISCOVERED_PROVIDERS = 50
        private const val DEFAULT_CATALOG_SORT_ORDER = -100
        private const val DEFAULT_CATALOG_DISPLAY_NAME = "Lamphaus Catalog"
        private const val DEFAULT_CATALOG_PROVIDER_ID = "com.linvo.cinemeta"
        private const val DEFAULT_CATALOG_MANIFEST = "https://v3-cinemeta.strem.io/manifest.json"
        private const val DEVELOPMENT_SOURCE_ID = "lamphaus.dev.source"

        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(container) as T
        }
    }
}

private fun String.canonicalProviderAddress(): String {
    val uri = runCatching { URI(trim()) }.getOrNull() ?: return trim()
    val transport = if (uri.scheme.equals("lamphaus", ignoreCase = true) || uri.scheme.equals("addon", ignoreCase = true)) {
        URI("https", null, uri.host, uri.port, uri.path, uri.query, null)
    } else uri
    val path = transport.path.orEmpty()
    val manifestPath = if (path.endsWith("/manifest.json")) path else path.trimEnd('/') + "/manifest.json"
    return URI(transport.scheme, null, transport.host, transport.port, manifestPath, transport.query, null).toString()
}

private fun String.inferMimeType(): String? = when {
    contains(".m3u8", ignoreCase = true) -> "application/x-mpegURL"
    contains(".mpd", ignoreCase = true) -> "application/dash+xml"
    contains(".mp4", ignoreCase = true) -> "video/mp4"
    else -> null
}

private fun String?.isDebugLocalStream(): Boolean {
    val uri = this?.let { runCatching { URI(it) }.getOrNull() } ?: return false
    return uri.scheme.equals("http", ignoreCase = true) && uri.host in setOf("localhost", "127.0.0.1", "10.0.2.2")
}

private fun ProviderCatalog.defaultQuery(): CatalogQuery? = when {
    requiredExtras.isEmpty() -> CatalogQuery(type, id)
    id == "year" && requiredExtras == setOf("genre") -> CatalogQuery(
        type = type,
        catalogId = id,
        genre = Calendar.getInstance().get(Calendar.YEAR).toString(),
    )
    else -> null
}

private fun ProviderCatalog.displayTitle(): String = when (type.lowercase()) {
    "movie" -> "$name Movies"
    "series" -> "$name Series"
    else -> name
}

private fun MediaDetail.merge(other: MediaDetail): MediaDetail = MediaDetail(
    preview = preview.merge(other.preview),
    runtimeMinutes = runtimeMinutes ?: other.runtimeMinutes,
    cast = (cast + other.cast).distinct(),
    directors = (directors + other.directors).distinct(),
    episodes = (episodes + other.episodes).distinctBy(Episode::id),
)

private fun MediaPreview.merge(other: MediaPreview): MediaPreview = copy(
    posterUrl = posterUrl ?: other.posterUrl,
    backgroundUrl = backgroundUrl ?: other.backgroundUrl,
    logoUrl = logoUrl ?: other.logoUrl,
    description = description ?: other.description,
    releaseYear = releaseYear ?: other.releaseYear,
    genres = (genres + other.genres).distinct(),
    contentRating = contentRating ?: other.contentRating,
    rating = rating ?: other.rating,
    providerIds = providerIds + other.providerIds,
)

private fun StateFlow<AppUiState>.mapActiveProfileId() = map { it.activeProfileId }
