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
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture
import com.lamphaus.core.model.PlaybackRequest
import com.lamphaus.core.player.LamphausPlaybackService
import com.lamphaus.core.player.PlaybackHeaderRegistry
import com.lamphaus.core.player.toMediaItem
import com.lamphaus.app.LamphausApplication
import com.lamphaus.app.BuildConfig
import com.lamphaus.core.data.cloud.AccountState
import com.lamphaus.core.model.WatchProgress
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI

class PlayerActivity : ComponentActivity() {
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var request: PlaybackRequest? = null
    private val isTelevision by lazy { packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        request = intent.getStringExtra(EXTRA_REQUEST)?.let { runCatching { JSON.decodeFromString<PlaybackRequest>(it) }.getOrNull() }
        if (request == null || !request!!.source.uri.isAllowedPlaybackUri()) {
            finish()
            return
        }
        setContent { PlayerSurface(request!!) { controller = it } }
    }

    @Composable
    private fun PlayerSurface(request: PlaybackRequest, onController: (MediaController?) -> Unit) {
        var player by androidx.compose.runtime.remember { mutableStateOf<MediaController?>(null) }
        DisposableEffect(request) {
            PlaybackHeaderRegistry.put(request.source.uri, request.source.headers)
            val token = SessionToken(this@PlayerActivity, ComponentName(this@PlayerActivity, LamphausPlaybackService::class.java))
            val future = MediaController.Builder(this@PlayerActivity, token).buildAsync()
            controllerFuture = future
            future.addListener(
                {
                    runCatching { future.get() }.onSuccess { mediaController ->
                        player = mediaController
                        onController(mediaController)
                        mediaController.setMediaItem(request.toMediaItem(), request.startPositionMillis)
                        mediaController.prepare()
                        mediaController.play()
                    }
                },
                ContextCompat.getMainExecutor(this@PlayerActivity),
            )
            onDispose {
                PlaybackHeaderRegistry.remove(request.source.uri)
                player?.release()
                player = null
                onController(null)
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        useController = true
                        controllerAutoShow = true
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        setShowSubtitleButton(true)
                        addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ -> updatePipSourceRect(view) }
                    }
                },
                update = {
                    it.player = player
                    updatePipSourceRect(it)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!isTelevision && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && controller?.isPlaying == true) {
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

    override fun onStop() {
        saveProgress()
        if (isTelevision && !isChangingConfigurations) controller?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
        super.onDestroy()
    }

    private fun saveProgress() {
        val playback = request ?: return
        val current = controller ?: return
        val position = current.currentPosition.coerceAtLeast(0)
        val duration = current.duration.coerceAtLeast(0)
        if (duration <= 0) return
        lifecycleScope.launch {
            val container = (application as LamphausApplication).container
            val profileId = container.preferences.settings.first().activeProfileId ?: return@launch
            val progress = WatchProgress(
                profileId = profileId,
                mediaKey = playback.mediaKey,
                videoId = playback.videoId,
                positionMillis = position,
                durationMillis = duration,
                completed = position.toDouble() / duration >= 0.95,
                updatedAtEpochMillis = System.currentTimeMillis(),
            )
            container.libraryRepository.saveProgress(progress)
            (container.accountGateway.state.value as? AccountState.SignedIn)?.userId?.let {
                container.cloudSyncGateway.saveProgress(it, progress)
            }
        }
    }

    companion object {
        private const val EXTRA_REQUEST = "playback_request"
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
