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
import java.util.UUID
import java.net.URI
import java.util.Calendar
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
                showMessage("${directResult.value.name} installed. Loading catalogs…")
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
        val providerId = media.providerIds.firstOrNull()
        val provider = state.value.providers.firstOrNull { it.id == providerId }
        if (provider == null) {
            mutableState.update { it.copy(refreshing = false) }
            return@launch
        }
        when (val result = container.providerClient.meta(provider.manifestUrl, provider.id, media.rawType, media.id)) {
            is ProviderResult.Success -> mutableState.update { it.copy(selectedDetail = result.value, refreshing = false) }
            is ProviderResult.Failure -> mutableState.update { it.copy(refreshing = false, message = result.safeMessage) }
        }
    }

    fun clearDetail() = mutableState.update { it.copy(selectedDetail = null) }

    fun preparePlayback(media: MediaPreview, episode: Episode? = null) = viewModelScope.launch {
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
        mutableState.update { it.copy(refreshing = true) }
        val resolvedProviders = coroutineScope {
            enabledProviders.map { subscription ->
                async {
                    val manifest = container.providerClient.manifest(subscription.manifestUrl)
                    subscription to (manifest as? ProviderResult.Success)?.value
                }
            }.awaitAll()
        }
        val streamProviders = resolvedProviders.filter { (_, manifest) ->
            manifest?.supports("stream", media.rawType, videoId) == true
        }
        if (streamProviders.isEmpty()) {
            showMessage("Install a compatible stream add-on to play this title.")
            mutableState.update { it.copy(refreshing = false) }
            return@launch
        }
        val streamResults = coroutineScope {
            streamProviders.map { (subscription, _) ->
                async { container.providerClient.streams(subscription.manifestUrl, subscription.id, media.rawType, videoId) }
            }.awaitAll()
        }
        val streams = streamResults.flatMap { (it as? ProviderResult.Success)?.value.orEmpty() }
        val subtitleProviders = resolvedProviders.filter { (_, manifest) ->
            manifest?.supports("subtitles", media.rawType, videoId) == true
        }
        val tracks = coroutineScope {
            subtitleProviders.map { (subscription, _) ->
                async { container.providerClient.subtitles(subscription.manifestUrl, media.rawType, videoId) }
            }.awaitAll()
        }.flatMap { (it as? ProviderResult.Success)?.value.orEmpty() }.distinctBy { it.id }
        val internal = streams.firstOrNull {
            it.isPlayableInternally || (BuildConfig.DEBUG && it.url.isDebugLocalStream())
        }
        val external = streams.firstOrNull { !it.externalUrl.isNullOrBlank() }
        val internalUrl = internal?.url
        val externalUrl = external?.externalUrl
        when {
            internalUrl != null -> {
                val start = state.value.progress.firstOrNull { it.videoId == videoId }?.positionMillis ?: 0
                mutableState.update {
                    it.copy(
                        playbackRequest = PlaybackRequest(
                            mediaKey = media.stableKey,
                            videoId = videoId,
                            title = media.name,
                            subtitle = episode?.title,
                            artworkUrl = media.backgroundUrl ?: media.posterUrl,
                            source = PlaybackSource(internalUrl, internalUrl.inferMimeType(), internal.headers, tracks),
                            startPositionMillis = start,
                        ),
                    )
                }
            }
            externalUrl != null -> mutableState.update { it.copy(externalPlaybackUrl = externalUrl) }
            else -> showMessage("No compatible source is currently available.")
        }
        mutableState.update { it.copy(refreshing = false) }
    }

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
        val provider = ProviderSubscription(
            id = manifest.id,
            manifestUrl = address,
            displayName = displayName,
            sortOrder = sortOrder,
            updatedAtEpochMillis = System.currentTimeMillis(),
        )
        container.libraryRepository.saveProvider(provider)
        (state.value.account as? AccountState.SignedIn)?.userId?.let {
            container.cloudSyncGateway.saveProvider(it, provider)
        }
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

private fun ProviderManifest.supports(resourceName: String, type: String, id: String): Boolean = resources.any { resource ->
    resource.name.equals(resourceName, ignoreCase = true) &&
        (resource.types.isEmpty() || resource.types.any { it.equals(type, ignoreCase = true) }) &&
        (resource.idPrefixes.isEmpty() || resource.idPrefixes.any(id::startsWith))
}

private fun StateFlow<AppUiState>.mapActiveProfileId() = map { it.activeProfileId }
