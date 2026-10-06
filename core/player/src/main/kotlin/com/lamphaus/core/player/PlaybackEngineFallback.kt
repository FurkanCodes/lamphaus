package com.lamphaus.core.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import com.lamphaus.core.model.PlaybackEngineKind
import com.lamphaus.core.model.PlaybackSessionState
import com.lamphaus.core.player.mpv.MpvCertificates
import com.lamphaus.core.player.mpv.MpvLibrary
import com.lamphaus.core.player.mpv.MpvPlayer

/**
 * Engine switching for the session player (PLY-ENG-01). The session object
 * stays constant, so controllers never reconnect.
 *
 * - [select] puts the engine a stream should start on in place while nothing
 *   is loaded: ExoPlayer, or libmpv when the setting (or Auto for anime) asks.
 * - [install] is Nuvio's "Auto-switch engine on startup error": with the
 *   setting on, a stream that fails before its first frame moves to the other
 *   engine once, at the same position. Network and authorisation failures are
 *   the source's problem and never switch.
 */
@UnstableApi
object PlaybackEngineFallback {

    fun select(context: Context, session: MediaSession, engine: PlaybackEngineKind, onSwitch: (Player) -> Unit) {
        val current = session.player
        val idle = current.mediaItemCount == 0 || current.playbackState == Player.STATE_IDLE ||
            current.playbackState == Player.STATE_ENDED
        if (!idle) return
        val replacement = when {
            engine == PlaybackEngineKind.MPV && current !is MpvPlayer && MpvLibrary.isAvailable() ->
                runCatching { MpvPlayer(current.applicationLooper, MpvCertificates.bundle(context)) }.getOrNull()
            engine == PlaybackEngineKind.MEDIA3 && current is MpvPlayer -> Media3EngineFactory.createPlayer(context)
            else -> null
        } ?: return
        session.player = replacement
        current.release()
        onSwitch(replacement)
    }

    fun install(context: Context, session: MediaSession, onFallback: (Player, PlaybackSessionState) -> Unit) {
        val player = session.player
        var firstFrameRendered = false
        var switchedForItem: Any? = null
        player.addListener(
            object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    firstFrameRendered = true
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    firstFrameRendered = false
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (session.player !== player || firstFrameRendered) return
                    if (!Media3EngineFactory.deviceConfig.autoSwitchEngineOnStartupError) return
                    val uri = player.currentMediaItem?.localConfiguration?.uri ?: return
                    if (switchedForItem == uri) return
                    val failureKind = EngineHandoff.failureKindFrom(error.errorCode)
                    if (failureKind == EngineFailureKind.NETWORK || failureKind == EngineFailureKind.AUTHORIZATION) return
                    // A converted Dolby Vision stream first retries as its HDR10 base layer in Media3.
                    if (DolbyVisionSession.converting && !DolbyVisionSession.forceBaseLayer) return
                    val toMpv = player !is MpvPlayer
                    if (toMpv && !MpvLibrary.isAvailable()) return
                    val item: MediaItem = player.currentMediaItem ?: return
                    val handoff = EngineHandoff.snapshot(player)
                    val replacement: Player = if (toMpv) {
                        MpvPlayer(player.applicationLooper, MpvCertificates.bundle(context)).apply {
                            load(item, handoff.positionMillis, PlaybackHeaderRegistry.get(uri.toString()))
                            prepare()
                            restore(handoff)
                        }
                    } else {
                        Media3EngineFactory.createPlayer(context).apply {
                            setMediaItem(item, handoff.positionMillis)
                            prepare()
                        }
                    }
                    replacement.playWhenReady = handoff.playWhenReady
                    replacement.setPlaybackSpeed(handoff.speed)
                    switchedForItem = uri
                    session.player = replacement
                    onFallback(
                        replacement,
                        PlaybackSessionState(
                            requestedEngine = if (toMpv) PlaybackEngineKind.MEDIA3 else PlaybackEngineKind.MPV,
                            activeEngine = if (toMpv) PlaybackEngineKind.MPV else PlaybackEngineKind.MEDIA3,
                            fallbackReason = PlaybackEnginePolicy.fallbackReason(failureKind) ?: "startup failure",
                        ),
                    )
                }
            },
        )
    }
}
