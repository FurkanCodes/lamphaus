package com.lamphaus.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lamphaus.app.BuildConfig
import com.lamphaus.app.AppContainer
import com.lamphaus.core.data.cloud.AccountState
import com.lamphaus.core.data.cloud.CloudLog
import com.lamphaus.core.data.preferences.ThemePreference
import com.lamphaus.core.model.CatalogQuery
import com.lamphaus.core.model.DiagnosticsConsent
import com.lamphaus.core.model.DeviceGrant
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
import com.lamphaus.core.model.PairingSession
import java.util.UUID
import java.net.URI
import java.util.Calendar
import java.security.MessageDigest
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = mutableState.asStateFlow()
    private var refreshJob: Job? = null
    private var cloudSyncJob: Job? = null
    private var cloudSyncUserId: String? = null
    private var defaultCatalogJob: Job? = null
    private var searchJob: Job? = null
    private var detailJob: Job? = null
    private var sourceJob: Job? = null

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
                mutableState.update { current ->
                    val previousUserId = (current.account as? AccountState.SignedIn)?.userId
                    val nextUserId = (snapshot.account as? AccountState.SignedIn)?.userId
                    val accountChanged = nextUserId != null && nextUserId != previousUserId
                    current.copy(
                        account = snapshot.account,
                        profiles = snapshot.profiles,
                        providers = snapshot.providers,
                        sections = if (accountChanged || nextUserId == null) emptyList() else current.sections,
                        activeProfileId = activeId,
                        theme = snapshot.theme,
                        dynamicColor = snapshot.dynamicColor,
                        diagnostics = snapshot.diagnostics,
                        initialContentLoading = if (accountChanged || nextUserId == null) {
                            true
                        } else {
                            current.initialContentLoading
                        },
                    )
                }
                if (activeId != snapshot.activeProfileId) container.preferences.setActiveProfile(activeId)
                if (snapshot.account is AccountState.SignedIn && snapshot.profiles.isEmpty()) createInitialProfile()
                if (snapshot.account is AccountState.SignedIn) {
                    ensureDefaultCatalog(snapshot.providers)
                    startCloudSync(snapshot.account.userId, snapshot.profiles)
                } else if (cloudSyncUserId != null) {
                    // Sign-out / deletion must retire the collectors bound to
                    // the previous user's realtime channels.
                    cloudSyncJob?.cancel()
                    cloudSyncJob = null
                    cloudSyncUserId = null
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
        val explicitInstallLink = (runCatching { URI(address).scheme }
            .getOrNull()
            ?.let { !it.equals("http", ignoreCase = true) && !it.equals("https", ignoreCase = true) }) == true
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
                            CatalogQuery(catalog.type, catalog.id, search = queryText, posterShape = catalog.posterShape),
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
        val current = state.value.providers.firstOrNull { it.id == providerId } ?: return@launch
        if (current.sortOrder < 0) {
            showMessage("The Lamphaus catalog is always available.")
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
            showMessage("The Lamphaus catalog cannot be removed.")
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
        state.value.activeProfileId?.let { container.libraryRepository.removeLibrary(it, mediaKey) }
    }

    fun loadDetail(media: MediaPreview) {
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
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
            val candidates = state.value.providers
                .filter(ProviderSubscription::enabled)
                .sortedBy(ProviderSubscription::sortOrder)
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
                mutableState.update { current ->
                    if (current.selectedDetail?.preview?.stableKey != media.stableKey) current
                    else current.copy(selectedDetail = details.fold(MediaDetail(media), MediaDetail::merge), refreshing = false)
                }
            } else {
                mutableState.update { current ->
                    if (current.selectedDetail?.preview?.stableKey != media.stableKey) current
                    else current.copy(refreshing = false, message = "No metadata provider could load this title.")
                }
            }
        }
    }

    fun clearDetail() {
        detailJob?.cancel()
        mutableState.update { it.copy(selectedDetail = null, refreshing = false) }
    }

    /** Loads every compatible stream provider. This intentionally does not select or launch a source. */
    fun openSources(media: MediaPreview, episode: Episode? = null) {
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
                    sourcePicker = SourcePickerState(media = media, episode = episode),
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
                    val start = state.value.progress.firstOrNull { it.videoId == videoId }?.positionMillis ?: 0
                    mutableState.update { current ->
                        if (current.sourcePicker?.media?.stableKey != picker.media.stableKey) current
                        else current.copy(
                            playbackRequest = PlaybackRequest(
                                mediaKey = picker.media.stableKey,
                                videoId = videoId,
                                title = picker.media.name,
                                subtitle = picker.episode?.title,
                                artworkUrl = picker.media.backgroundUrl ?: picker.media.posterUrl,
                                source = PlaybackSource(
                                    uri = resolution.url,
                                    mimeType = source.mimeType ?: resolution.url.inferMimeType(),
                                    headers = source.headers,
                                    subtitles = (source.subtitles + tracks).distinctBy { "${it.language}|${it.url}|${it.id}" },
                                ),
                                startPositionMillis = start,
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
            container.pairingGateway.createPairingSession(PAIRING_DEVICE_LABEL).onSuccess { session ->
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
     * we simply poll again. Only a granted grant completes sign-in, and an
     * expired session regenerates a fresh QR automatically.
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
                DeviceGrant.Pending, null -> CloudLog.d(
                    "tv.pairing pending (session expires in ${remaining / 1000}s)",
                ) // keep polling through hiccups
            }
        }
    }

    /**
     * Consumes the single-use grant immediately: verifyEmailOtp mints the
     * TV's own GoTrue session (persisted by the SDK; refresh tokens never
     * expire by default), then register_device_session binds devices.
     * auth_session_id so revocation can later kill exactly this TV (F6).
     */
    private suspend fun completeDeviceSignIn(supabase: SupabaseClient, grant: DeviceGrant.Granted) {
        runCatching<Unit> {
            CloudLog.i("tv.pairing exchanging OTP for a TV session (email=${CloudLog.sanitize("""{"email":"${grant.email}"}""")})")
            supabase.auth.verifyEmailOtp(OtpType.Email.MAGIC_LINK, grant.email, grant.otp)
            CloudLog.i("tv.pairing OTP verified — TV session minted, binding device…")
            supabase.postgrest.rpc(
                "register_device_session",
                buildJsonObject { put("p_device_id", grant.deviceId) },
            )
            CloudLog.i("tv.pairing device=${grant.deviceId} bound to session — pairing complete 🎬")
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
            devicesLoadedOnce = false
            mutableState.update(AppUiState::clearAccountData)
            showMessage("Your account and all cloud data were deleted.")
        }.onFailure {
            CloudLog.e("settings.account delete failed", it)
            showMessage(it.message ?: "Account deletion failed. Try again.")
        }
    }

    fun setTheme(theme: ThemePreference) = viewModelScope.launch { container.preferences.setTheme(theme) }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { container.preferences.setDynamicColor(enabled) }

    fun setDiagnostics(consent: DiagnosticsConsent) = viewModelScope.launch {
        container.preferences.setDiagnostics(consent.copy(updatedAtEpochMillis = System.currentTimeMillis()))
        // Diagnostics backends are being replaced alongside the Supabase migration;
        // consent is persisted now and honored by whichever provider lands next.
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

    private fun refreshCatalogs() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val current = state.value
            if (current.account !is AccountState.SignedIn) return@launch
            if (current.providers.isEmpty()) {
                if (defaultCatalogJob?.isActive == true) {
                    mutableState.update { it.copy(refreshing = true) }
                    return@launch
                }
                val preview = if (BuildConfig.DEBUG && current.account.userId == "local-development") {
                    listOf(CatalogSection("preview", "Preview library", "Local fixture", PreviewMedia.items))
                } else emptyList()
                mutableState.update {
                    it.copy(
                        sections = preview,
                        initialContentLoading = false,
                        refreshing = false,
                    )
                }
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
                        val includeCuratedGenres = subscription.id == DEFAULT_CATALOG_PROVIDER_ID ||
                            subscription.manifestUrl == DEFAULT_CATALOG_MANIFEST
                        manifestResult.value.catalogs.flatMap { catalog ->
                            catalog.homeRequests(
                                includeCuratedGenres = includeCuratedGenres,
                                currentYear = Calendar.getInstance().get(Calendar.YEAR),
                            )
                        }.map { request ->
                            val query = request.query
                            async {
                                when (val result = container.providerClient.catalog(
                                    subscription.manifestUrl,
                                    subscription.id,
                                    query,
                                )) {
                                    is ProviderResult.Success -> CatalogSection(
                                        "${subscription.id}:${query.type}:${query.catalogId}:${query.genre.orEmpty()}",
                                        request.title,
                                        subscription.displayName,
                                        filterForProfile(result.value),
                                    )
                                    is ProviderResult.Failure -> CatalogSection(
                                        "${subscription.id}:${query.type}:${query.catalogId}:${query.genre.orEmpty()}",
                                        request.title,
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
            mutableState.update {
                it.copy(
                    sections = sections,
                    initialContentLoading = false,
                    refreshing = false,
                )
            }
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
        val existingId = state.value.providers.firstOrNull { it.manifestUrl.canonicalProviderAddress() == canonicalAddress }?.id
        val installationId = existingId ?: if (manifest.id == DEFAULT_CATALOG_PROVIDER_ID || canonicalAddress == DEFAULT_CATALOG_MANIFEST) {
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
                is ProviderResult.Failure -> {
                    mutableState.update { it.copy(initialContentLoading = false, refreshing = false) }
                    showMessage("The Lamphaus catalog is temporarily unavailable.")
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

    private fun startCloudSync(userId: String, profiles: List<Profile>) {
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
                container.cloudSyncGateway.providers(userId).onSuccess { providers ->
                    providers.forEach { container.libraryRepository.saveProvider(it) }
                }
            }
            // Legacy local installs may hold placeholder ids (e.g. "primary")
            // that cannot exist in Postgres; keep them out of cloud sync.
            val cloudBackedProfiles = profiles.filter { profile ->
                runCatching { UUID.fromString(profile.id) }.isSuccess
            }
            // Freshly linked accounts start empty in Postgres: profiles created
            // before sign-in (or whose first push landed in an auth outage)
            // would otherwise stay device-local forever, because only
            // post-sign-in mutations are pushed. Seed once while the cloud holds
            // no profiles; afterwards the inbound collectors own convergence.
            launch {
                val cloudIsEmpty = container.cloudSyncGateway.profiles(userId).first().isEmpty()
                if (cloudIsEmpty) {
                    cloudBackedProfiles.forEach { profile ->
                        container.cloudSyncGateway.saveProfile(userId, profile)
                    }
                    cloudBackedProfiles.forEach { profile ->
                        container.libraryRepository.library(profile.id).first()
                            .forEach { container.cloudSyncGateway.saveLibrary(userId, it) }
                        container.libraryRepository.progress(profile.id).first()
                            .forEach { container.cloudSyncGateway.saveProgress(userId, it) }
                    }
                }
            }
            cloudBackedProfiles.forEach { profile ->
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

private fun MediaDetail.merge(other: MediaDetail): MediaDetail = MediaDetail(
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

private fun Episode.merge(other: Episode): Episode = copy(
    title = title.ifBlank { other.title },
    season = season ?: other.season,
    episode = episode ?: other.episode,
    overview = overview ?: other.overview,
    thumbnailUrl = thumbnailUrl ?: other.thumbnailUrl,
    releasedAtEpochMillis = releasedAtEpochMillis ?: other.releasedAtEpochMillis,
    streams = (streams + other.streams).distinctBy(::sourceIdentity),
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
