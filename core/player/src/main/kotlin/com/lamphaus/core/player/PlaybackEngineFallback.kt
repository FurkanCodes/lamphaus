package com.lamphaus.core.player

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import com.lamphaus.core.model.PlaybackEngineKind
import com.lamphaus.core.model.PlaybackSessionState
import com.lamphaus.core.player.mpv.MpvLibrary
import com.lamphaus.core.player.mpv.MpvPlayer

/**
 * Engine handoff supervisor for the session player (plan §1): listens for
 * fatal Media3 failures and swaps the session to the MPV engine when the
 * failure is engine-shaped and libmpv is packaged. One handoff per media
 * item; the session object stays constant so controllers never reconnect.
 */
@UnstableApi
object PlaybackEngineFallback {

    /**
     * Installs the fallback listener on the session's player. Returns the
     * listener (kept for symmetry; the service never needs to remove it).
     */
    fun install(session: MediaSession, onFallback: (PlaybackSessionState) -> Unit) {
        var fallbackUsedForItem: Any? = null

        session.player.addListener(
            object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    val player = session.player
                    val currentUid = player.currentMediaItem?.localConfiguration?.uri ?: return
                    if (fallbackUsedForItem == currentUid) return // no bouncing (plan §1)

                    val failureKind = EngineHandoff.failureKindFrom(error.errorCode)
                    if (!PlaybackEnginePolicy.shouldFallbackToMpv(failureKind, PlaybackEngineKind.MEDIA3)) return
                    if (!MpvLibrary.isAvailable()) return

                    val handoff = EngineHandoff.snapshot(player)
                    val mediaItem: MediaItem = player.currentMediaItem ?: return
                    val headers = PlaybackHeaderRegistry.get(currentUid.toString())

                    val mpvPlayer = MpvPlayer(player.applicationLooper)
                    mpvPlayer.load(mediaItem, handoff.positionMillis, headers)
                    mpvPlayer.prepare()
                    mpvPlayer.restore(handoff)
                    mpvPlayer.playWhenReady = handoff.playWhenReady
                    mpvPlayer.setPlaybackSpeed(handoff.speed)

                    fallbackUsedForItem = currentUid
                    session.player = mpvPlayer
                    onFallback(
                        PlaybackSessionState(
                            requestedEngine = PlaybackEngineKind.AUTO,
                            activeEngine = PlaybackEngineKind.MPV,
                            fallbackReason = PlaybackEnginePolicy.fallbackReason(failureKind),
                        ),
                    )
                }
            },
        )
    }
}
