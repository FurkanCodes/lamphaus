package com.lamphaus.core.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.serialization.json.Json
import com.lamphaus.core.model.PlaybackSessionState

class LamphausPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = Media3EngineFactory.createPlayer(this)
        Media3EngineFactory.sessionPlayer = player
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val sessionActivity = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        mediaSession = MediaSession.Builder(this, player)
            .apply { sessionActivity?.let(::setSessionActivity) }
            .setCallback(sessionCallback)
            .build()
        PlaybackEngineFallback.install(mediaSession!!) { state ->
            // Diagnostics record only the engine switch, never the source (SHR-PROD-06).
            android.util.Log.i("LamphausPlayback", "engine fallback: ${state.fallbackReason}")
        }
    }

    /**
     * Engine-side commands that have no Media3 Player equivalent: subtitle
     * and audio timing for the MPV engine (plan §2/§4). The Media3 engine
     * receives the same values through DelayedCuePlayer / DelayAudioProcessor
     * on the client side, so both engines honor one activity contract.
     */
    private val sessionCallback = object : MediaSession.Callback {
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: androidx.media3.session.SessionCommand,
            args: android.os.Bundle,
        ): com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.SessionResult> {
            val mpv = session.player as? com.lamphaus.core.player.mpv.MpvPlayer
            when (customCommand.customAction) {
                ACTION_SET_SUBTITLE_DELAY ->
                    mpv?.setSubtitleDelayMillis(args.getLong(EXTRA_DELAY_MILLIS, 0L))
                ACTION_SET_AUDIO_DELAY ->
                    mpv?.setAudioDelayMillis(args.getLong(EXTRA_DELAY_MILLIS, 0L))
                ACTION_APPLY_SUBTITLE_STYLE -> {
                    val payload = args.getString(EXTRA_STYLE_JSON)
                    if (payload != null && mpv != null) {
                        runCatching {
                            mpv.applySubtitleStyle(
                                Json { ignoreUnknownKeys = true }.decodeFromString(
                                    com.lamphaus.core.model.SubtitleStyle.serializer(),
                                    payload,
                                ),
                            )
                        }
                    }
                }
            }
            return com.google.common.util.concurrent.Futures.immediateFuture(
                androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS),
            )
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player ?: return
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        Media3EngineFactory.sessionPlayer = null
        super.onDestroy()
    }

    private companion object {
        const val ACTION_SET_SUBTITLE_DELAY = "lamphaus.playback.SET_SUBTITLE_DELAY"
        const val ACTION_SET_AUDIO_DELAY = "lamphaus.playback.SET_AUDIO_DELAY"
        const val ACTION_APPLY_SUBTITLE_STYLE = "lamphaus.playback.APPLY_SUBTITLE_STYLE"
        const val EXTRA_DELAY_MILLIS = "delay_millis"
        const val EXTRA_STYLE_JSON = "style_json"
    }
}
