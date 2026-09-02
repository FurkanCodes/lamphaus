package com.lamphaus.app.player

import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil3.SingletonImageLoader
import com.google.common.util.concurrent.ListenableFuture
import android.util.Log
import com.lamphaus.app.BuildConfig
import com.lamphaus.app.LamphausApplication
import com.lamphaus.app.R
import com.lamphaus.core.data.cloud.AccountState
import com.lamphaus.core.model.Episode
import com.lamphaus.core.model.PlaybackSegment
import com.lamphaus.core.model.PlaybackRequest
import com.lamphaus.core.model.PlaybackSettings
import com.lamphaus.core.model.PlaybackSource
import com.lamphaus.core.model.ProviderManifest
import com.lamphaus.core.model.ProviderResult
import com.lamphaus.core.model.ProviderSubscription
import com.lamphaus.core.model.SpoilerProtectionSettings
import com.lamphaus.core.model.StreamCandidate
import com.lamphaus.core.model.SubtitleTrack
import com.lamphaus.core.model.WatchProgress
import com.lamphaus.core.model.hasAired
import com.lamphaus.core.model.nextEpisodeAfter
import com.lamphaus.core.player.LamphausPlaybackService
import com.lamphaus.core.player.PlaybackHeaderRegistry
import com.lamphaus.core.player.toMediaItem
import com.lamphaus.app.ui.SourceResolution
import com.lamphaus.app.ui.resolveSource
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PlayerActivity : ComponentActivity() {
    private val container by lazy { (application as LamphausApplication).container }
    private val controllerState = mutableStateOf<MediaController?>(null)
    private val requestState = mutableStateOf<PlaybackRequest?>(null)
    private val playbackSettingsState = mutableStateOf(PlaybackSettings())
    private val segmentsState = mutableStateOf<List<PlaybackSegment>>(emptyList())
    private val nextEpisodeLoadingState = mutableStateOf(false)
    private val nextEpisodeMessageState = mutableStateOf<String?>(null)
    private val spoilerProtectionState = mutableStateOf(SpoilerProtectionSettings())
    private val nextEpisodeDismissedVideoId = mutableStateOf<String?>(null)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var request: PlaybackRequest? = null
    private var progressPulseJob: Job? = null
    private var segmentLookupJob: Job? = null
    private var nextEpisodeResolutionJob: Job? = null

    /** Position of the last progress write; guards against redundant periodic saves. */
    @Volatile
    private var lastSavedPositionMillis = -1L
    private val controller: MediaController? get() = controllerState.value
    private val isTelevision by lazy { packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        request = intent.getStringExtra(EXTRA_REQUEST)
            ?.let { runCatching { JSON.decodeFromString<PlaybackRequest>(it) }.getOrNull() }
        val playback = request
        if (playback == null || !playback.source.uri.isAllowedPlaybackUri()) {
            finish()
            return
        }
        requestState.value = playback

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // Browsing can leave several 4K backdrops in Coil's memory cache. Playback needs that
        // heap for demuxing high-bitrate sources, while artwork remains available on disk.
        SingletonImageLoader.get(this).memoryCache?.clear()
        connect(playback)
        lifecycleScope.launch {
            container.preferences.settings.collectLatest { settings ->
                playbackSettingsState.value = settings.playback
                spoilerProtectionState.value = settings.spoilerProtection
                loadSegments(requestState.value, settings.playback)
            }
        }
        setContent {
            requestState.value?.let { currentRequest ->
                PlaybackScreen(
                    request = currentRequest,
                    player = controllerState.value,
                    isTelevision = isTelevision,
                    settings = playbackSettingsState.value,
                    segments = segmentsState.value,
                    nextEpisodeLoading = nextEpisodeLoadingState.value,
                    nextEpisodeMessage = nextEpisodeMessageState.value,
                    spoilerProtection = spoilerProtectionState.value,
                    nextEpisodeDismissed =
                        nextEpisodeDismissedVideoId.value == currentRequest.videoId,
                    onExit = ::finish,
                    onOpenExternally = ::openExternally,
                    onNextEpisode = ::playNextEpisode,
                    onDismissNextEpisodeMessage = { nextEpisodeMessageState.value = null },
                    onDismissNextEpisodeCard = ::dismissNextEpisodeCard,
                    onPlayerViewLayout = ::updatePipSourceRect,
                )
            }
        }
    }

    private fun connect(playback: PlaybackRequest) {
        PlaybackHeaderRegistry.begin(playback.source.uri, playback.source.headers)
        playback.source.subtitles.forEach { subtitle ->
            PlaybackHeaderRegistry.put(subtitle.url, subtitle.headers)
        }
        val token = SessionToken(this, ComponentName(this, LamphausPlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching { future.get() }.onSuccess { mediaController ->
                    controllerState.value = mediaController
                    mediaController.setMediaItem(playback.toMediaItem(), playback.startPositionMillis)
                    mediaController.addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_ENDED) saveProgress(final = true)
                        }
                    })
                    mediaController.prepare()
                    mediaController.play()
                    startProgressPulse()
                }
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    private fun loadSegments(playback: PlaybackRequest?, settings: PlaybackSettings) {
        segmentLookupJob?.cancel()
        segmentsState.value = emptyList()
        val media = playback?.preview ?: return
        if (!settings.skipIntroEnabled && !settings.skipEndingEnabled && !settings.nextEpisodeEnabled) return
        segmentLookupJob = lifecycleScope.launch {
            segmentsState.value = container.skipRepository.segments(media, playback.episode)
        }
    }

    private fun playNextEpisode() {
        if (nextEpisodeLoadingState.value || request?.nextEpisode == null) return
        if (request?.nextEpisode?.hasAired() == false) return
        nextEpisodeLoadingState.value = true
        nextEpisodeMessageState.value = null
        nextEpisodeResolutionJob = lifecycleScope.launch {
            try {
                val current = request
                val next = current?.let { resolveNextPlayback(it) }
                if (current == null || next == null) {
                    nextEpisodeMessageState.value = getString(R.string.next_episode_source_unavailable)
                } else {
                    switchPlayback(current, next)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                nextEpisodeMessageState.value = getString(R.string.next_episode_source_unavailable)
            } finally {
                nextEpisodeLoadingState.value = false
            }
        }
    }

    /**
     * Dismisses the card for the current video and cancels any in-flight
     * source resolution; it returns when the episode changes.
     */
    private fun dismissNextEpisodeCard() {
        nextEpisodeDismissedVideoId.value = request?.videoId
        nextEpisodeResolutionJob?.cancel()
        nextEpisodeResolutionJob = null
        nextEpisodeLoadingState.value = false
        nextEpisodeMessageState.value = null
    }

    private suspend fun resolveNextPlayback(current: PlaybackRequest): PlaybackRequest? {
        val media = current.preview ?: return null
        val next = current.nextEpisode ?: return null
        val providers = container.libraryRepository.providers().first()
            .filter(ProviderSubscription::enabled)
            .sortedBy(ProviderSubscription::sortOrder)
        val resolvedProviders = supervisorScope {
            providers.map { subscription ->
                async {
                    val manifest = container.providerClient.manifest(subscription.manifestUrl)
                    ResolvedPlaybackProvider(
                        subscription,
                        (manifest as? ProviderResult.Success)?.value,
                    )
                }
            }.awaitAll()
        }
        val fetchedStreams = supervisorScope {
            resolvedProviders.mapNotNull { provider ->
                val manifest = provider.manifest ?: return@mapNotNull null
                if (!container.providerAggregator.supports(manifest, "stream", media.rawType, next.id)) {
                    return@mapNotNull null
                }
                async {
                    (container.providerClient.streams(
                        provider.subscription.manifestUrl,
                        provider.subscription.id,
                        media.rawType,
                        next.id,
                    ) as? ProviderResult.Success)?.value.orEmpty()
                }
            }.awaitAll().flatten()
        }
        val playable = (next.streams + fetchedStreams).distinctBy { candidate ->
            listOf(candidate.providerId, candidate.url, candidate.externalUrl, candidate.infoHash).joinToString("|")
        }.mapNotNull { source ->
            val resolution = resolveSource(source, BuildConfig.DEBUG) as? SourceResolution.Internal
            resolution?.let { source to it.url }
        }
        val selected = playable.minWithOrNull(
            compareBy<Pair<StreamCandidate, String>> {
                if (it.first.providerId == current.sourceProviderId) 0 else 1
            }.thenBy {
                if (current.sourceBingeGroup != null && it.first.bingeGroup == current.sourceBingeGroup) 0 else 1
            },
        ) ?: return null
        val source = selected.first
        val subtitles = loadSubtitlesForNext(media.rawType, next.id, source, resolvedProviders)
        return current.copy(
            videoId = next.id,
            subtitle = next.episodeLabel(),
            source = PlaybackSource(
                uri = selected.second,
                mimeType = source.mimeType ?: selected.second.inferMimeType(),
                headers = source.headers,
                subtitles = (source.subtitles + subtitles).distinctBy { "${it.language}|${it.url}|${it.id}" },
            ),
            startPositionMillis = 0,
            episode = next,
            nextEpisode = current.episodeQueue.nextEpisodeAfter(next),
            sourceProviderId = source.providerId,
            sourceBingeGroup = source.bingeGroup,
        )
    }

    private suspend fun loadSubtitlesForNext(
        rawType: String,
        videoId: String,
        source: StreamCandidate,
        providers: List<ResolvedPlaybackProvider>,
    ): List<SubtitleTrack> {
        val extras = buildMap {
            source.videoHash?.let { put("videoHash", it) }
            source.videoSize?.let { put("videoSize", it.toString()) }
            source.filename?.let { put("filename", it) }
        }
        return supervisorScope {
            providers.mapNotNull { provider ->
                val manifest = provider.manifest ?: return@mapNotNull null
                if (!container.providerAggregator.supports(manifest, "subtitles", rawType, videoId)) {
                    return@mapNotNull null
                }
                async {
                    (container.providerClient.subtitles(
                        provider.subscription.manifestUrl,
                        rawType,
                        videoId,
                        extras,
                    ) as? ProviderResult.Success)?.value.orEmpty()
                }
            }.awaitAll().flatten()
        }
    }

    private fun switchPlayback(current: PlaybackRequest, next: PlaybackRequest) {
        saveProgress(final = true)
        PlaybackHeaderRegistry.end(current.source.uri, current.source.subtitles.map { it.url })
        PlaybackHeaderRegistry.begin(next.source.uri, next.source.headers)
        next.source.subtitles.forEach { subtitle ->
            PlaybackHeaderRegistry.put(subtitle.url, subtitle.headers)
        }
        request = next
        requestState.value = next
        lastSavedPositionMillis = -1L
        nextEpisodeMessageState.value = null
        nextEpisodeDismissedVideoId.value = null
        loadSegments(next, playbackSettingsState.value)
        controller?.apply {
            setMediaItem(next.toMediaItem())
            prepare()
            play()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!isTelevision && controller?.isPlaying == true) {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build(),
            )
        }
    }

    private fun updatePipSourceRect(playerView: android.view.View) {
        if (isTelevision || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val sourceRect = Rect()
        if (!playerView.getGlobalVisibleRect(sourceRect)) return
        setPictureInPictureParams(
            PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .setAutoEnterEnabled(false)
                .setSeamlessResizeEnabled(true)
                .setSourceRectHint(sourceRect)
                .build(),
        )
    }

    private fun openExternally() {
        val uri = request?.source?.uri ?: return
        val viewIntent = Intent(Intent.ACTION_VIEW, uri.toUri()).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        runCatching {
            startActivity(Intent.createChooser(viewIntent, getString(R.string.open_with_external_player)))
        }
    }

    override fun onStop() {
        saveProgress(final = true)
        if ((isTelevision || isFinishing) && !isChangingConfigurations) controller?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        progressPulseJob?.cancel()
        progressPulseJob = null
        segmentLookupJob?.cancel()
        segmentLookupJob = null
        val playback = request
        if (playback != null) {
            PlaybackHeaderRegistry.end(playback.source.uri, playback.source.subtitles.map { it.url })
        }
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
        controllerState.value = null
        super.onDestroy()
    }

    /**
     * Periodically persists playback position while the player is alive. Reads
     * happen on the main thread (a Media3 requirement); writes are handed to
     * the application scope so they survive activity teardown. Paused playback
     * is naturally throttled by the [PROGRESS_SAVE_DELTA_MILLIS] check.
     */
    private fun startProgressPulse() {
        if (progressPulseJob?.isActive == true) return
        progressPulseJob = container.applicationScope.launch(Dispatchers.Main.immediate) {
            while (true) {
                delay(PROGRESS_SAVE_INTERVAL_MILLIS)
                saveProgress(final = false)
            }
        }
    }

    /**
     * Persists the current playback position to Room and, for final saves,
     * to the cloud sync gateway. Runs on the application scope — NOT the
     * activity lifecycleScope — because when the player is dismissed,
     * onDestroy cancels lifecycleScope before a launched write can finish,
     * silently dropping the save.
     */
    private fun saveProgress(final: Boolean) {
        val playback = request ?: run {
            Log.d(PROGRESS_LOG_TAG, "save skipped: no playback request")
            return
        }
        val current = controller ?: run {
            Log.d(PROGRESS_LOG_TAG, "save skipped: controller unavailable")
            return
        }
        val position = current.currentPosition.coerceAtLeast(0)
        val duration = current.duration.coerceAtLeast(0)
        if (duration <= 0) {
            Log.d(PROGRESS_LOG_TAG, "save skipped: duration unknown ($duration)")
            return
        }
        // Periodic saves only fire when playback actually advanced, so a paused
        // or just-started player never produces redundant writes.
        if (!final && position - lastSavedPositionMillis < PROGRESS_SAVE_DELTA_MILLIS) return
        lastSavedPositionMillis = position
        container.applicationScope.launch {
            val profileId = container.preferences.settings.first().activeProfileId ?: run {
                Log.d(PROGRESS_LOG_TAG, "save skipped: no active profile")
                return@launch
            }
            val progress = WatchProgress(
                profileId = profileId,
                mediaKey = playback.mediaKey,
                videoId = playback.videoId,
                positionMillis = position,
                durationMillis = duration,
                completed = position.toDouble() / duration >= 0.95,
                updatedAtEpochMillis = System.currentTimeMillis(),
                preview = playback.preview,
                episodeLabel = playback.subtitle?.takeIf { it.isNotBlank() },
            )
            container.libraryRepository.saveProgress(progress)
            Log.d(
                PROGRESS_LOG_TAG,
                "saved locally media=${progress.mediaKey} position=${progress.positionMillis}ms " +
                    "duration=${progress.durationMillis}ms completed=${progress.completed}",
            )
            if (!final) return@launch
            (container.accountGateway.state.value as? AccountState.SignedIn)?.let { signedIn ->
                container.cloudSyncGateway.saveProgress(signedIn.userId, progress)
                    .onSuccess { Log.d(PROGRESS_LOG_TAG, "synced to cloud user=${signedIn.userId}") }
                    .onFailure { error -> Log.w(PROGRESS_LOG_TAG, "cloud sync failed", error) }
            } ?: Log.d(PROGRESS_LOG_TAG, "cloud sync skipped: not signed in")
        }
    }

    companion object {
        private const val PROGRESS_LOG_TAG = "Lamphaus.Progress"
        private const val EXTRA_REQUEST = "playback_request"

        /** How often playback position is persisted while the player is open. */
        private const val PROGRESS_SAVE_INTERVAL_MILLIS = 10_000L

        /** Minimum playback advance between periodic progress writes. */
        private const val PROGRESS_SAVE_DELTA_MILLIS = 5_000L
        private val JSON = Json { ignoreUnknownKeys = true }

        fun intent(context: Context, request: PlaybackRequest): Intent =
            Intent(context, PlayerActivity::class.java).putExtra(EXTRA_REQUEST, JSON.encodeToString(request))
    }
}

private fun String.isAllowedPlaybackUri(): Boolean {
    if (startsWith("https://")) return true
    if (!BuildConfig.DEBUG) return false
    val uri = runCatching { URI(this) }.getOrNull() ?: return false
    return uri.scheme.equals("http", ignoreCase = true) && uri.host in setOf("localhost", "127.0.0.1", "10.0.2.2")
}

private data class ResolvedPlaybackProvider(
    val subscription: ProviderSubscription,
    val manifest: ProviderManifest?,
)

private fun Episode.episodeLabel(): String = listOfNotNull(
    season?.let { "S$it" },
    episode?.let { "E$it" },
    title.takeIf(String::isNotBlank),
).joinToString(" · ")

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
