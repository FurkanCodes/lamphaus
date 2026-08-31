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
import com.lamphaus.core.model.PlaybackRequest
import com.lamphaus.core.model.WatchProgress
import com.lamphaus.core.player.LamphausPlaybackService
import com.lamphaus.core.player.PlaybackHeaderRegistry
import com.lamphaus.core.player.toMediaItem
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PlayerActivity : ComponentActivity() {
    private val container by lazy { (application as LamphausApplication).container }
    private val controllerState = mutableStateOf<MediaController?>(null)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var request: PlaybackRequest? = null
    private var progressPulseJob: Job? = null

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
        setContent {
            PlaybackScreen(
                request = playback,
                player = controllerState.value,
                isTelevision = isTelevision,
                onExit = ::finish,
                onOpenExternally = ::openExternally,
                onPlayerViewLayout = ::updatePipSourceRect,
            )
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
