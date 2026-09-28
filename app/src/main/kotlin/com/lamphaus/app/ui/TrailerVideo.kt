package com.lamphaus.app.ui

import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.lamphaus.app.R

/**
 * Draws a trailer [player]. Without controls it is a bare TextureView
 * surface that clips and fades with its container (TV card previews); with
 * controls it is Media3's standard view with play/pause and scrubbing, so
 * the trailer stays operable without gestures (MOB-A11Y-03).
 */
@OptIn(UnstableApi::class)
@Composable
internal fun TrailerVideo(
    player: Player,
    modifier: Modifier = Modifier,
    showControls: Boolean = false,
    crop: Boolean = false,
    onFirstFrame: () -> Unit = {},
    onEnded: () -> Unit = {},
    onError: () -> Unit = onEnded,
) {
    val currentOnFirstFrame by rememberUpdatedState(onFirstFrame)
    val currentOnEnded by rememberUpdatedState(onEnded)
    val currentOnError by rememberUpdatedState(onError)
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() = currentOnFirstFrame()

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) currentOnEnded()
            }

            override fun onPlayerError(error: PlaybackException) = currentOnError()
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    AndroidView(
        factory = { context ->
            val view = if (showControls) {
                PlayerView(context).apply {
                    useController = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
            } else {
                LayoutInflater.from(context)
                    .inflate(R.layout.trailer_player_view, FrameLayout(context), false) as PlayerView
            }
            view.apply {
                this.player = player
                keepScreenOn = true
                resizeMode = if (crop) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        update = { view ->
            if (view.player !== player) view.player = player
        },
        onRelease = { view ->
            view.player = null
            view.keepScreenOn = false
        },
        modifier = modifier,
    )
}
