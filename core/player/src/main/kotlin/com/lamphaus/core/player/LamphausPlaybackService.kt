package com.lamphaus.core.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.serialization.json.Json
import com.lamphaus.core.model.PlaybackSessionState

class LamphausPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    /** The device settings the current session player was built with. */
    private var playerConfig = Media3EngineFactory.deviceConfig

    override fun onCreate() {
        super.onCreate()
        playerConfig = Media3EngineFactory.deviceConfig
        val player = Media3EngineFactory.createPlayer(this, playerConfig)
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
        installFallback(mediaSession!!)
    }

    /**
     * Audio output, decoder priority, downmix, and Dolby Vision are fixed
     * when ExoPlayer is built, and this service outlives many playbacks. A
     * new playback therefore gets a fresh player when those settings changed,
     * but only while nothing is loaded, so playback is never interrupted.
     */
    private fun refreshPlayerIfSettingsChanged(session: MediaSession) {
        val wanted = Media3EngineFactory.deviceConfig
        if (!Media3EngineFactory.needsRebuild(playerConfig, wanted)) return
        val current = session.player
        if (current.mediaItemCount > 0 && current.playbackState != androidx.media3.common.Player.STATE_IDLE) return
        val fresh = Media3EngineFactory.createPlayer(this, wanted)
        playerConfig = wanted
        session.player = fresh
        Media3EngineFactory.sessionPlayer = fresh
        current.release()
        installFallback(session)
    }

    private fun installFallback(session: MediaSession) {
        PlaybackEngineFallback.install(this, session) { replacement, state ->
            adoptPlayer(session, replacement)
            // Diagnostics record only the engine switch, never the source (SHR-PROD-06).
            android.util.Log.i("LamphausPlayback", "engine switch: ${state.fallbackReason}")
        }
    }

    /**
     * The session now runs [player]: ExoPlayer is published for the panels
     * and Info, while an MPV session clears it so callers read MPV's state.
     */
    private fun adoptPlayer(session: MediaSession, player: androidx.media3.common.Player) {
        Media3EngineFactory.sessionPlayer = player as? androidx.media3.exoplayer.ExoPlayer
        if (player is androidx.media3.exoplayer.ExoPlayer) playerConfig = Media3EngineFactory.deviceConfig
        installFallback(session)
        // The session never passes the video surface on to a new player: the
        // player screen sends it again when told.
        session.broadcastCustomCommand(
            androidx.media3.session.SessionCommand(ACTION_ENGINE_CHANGED, android.os.Bundle.EMPTY),
            android.os.Bundle.EMPTY,
        )
    }

    /**
     * Engine-side commands that have no Media3 Player equivalent: subtitle
     * and audio timing for the MPV engine (plan §2/§4). The Media3 engine
     * receives the same values through DelayedCuePlayer / DelayAudioProcessor
     * on the client side, so both engines honor one activity contract.
     */
    private val sessionCallback = object : MediaSession.Callback {
        /**
         * Grants the timing and style commands to this app's own controllers.
         * Without the grant Media3 rejects every custom command, so MPV never
         * received subtitle delay, style, or chrome lift.
         */
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            // Each playback connects its own controller: apply changed engine settings first.
            if (controller.packageName == packageName) refreshPlayerIfSettingsChanged(session)
            if (controller.packageName != packageName) {
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
            }
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .apply {
                    CUSTOM_ACTIONS.forEach { action ->
                        add(androidx.media3.session.SessionCommand(action, android.os.Bundle.EMPTY))
                    }
                }
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: androidx.media3.session.SessionCommand,
            args: android.os.Bundle,
        ): com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.SessionResult> {
            val mpv = session.player as? com.lamphaus.core.player.mpv.MpvPlayer
            when (customCommand.customAction) {
                ACTION_SELECT_ENGINE -> {
                    val engine = args.getString(EXTRA_ENGINE)
                        ?.let { name -> runCatching { com.lamphaus.core.model.PlaybackEngineKind.valueOf(name) }.getOrNull() }
                    if (engine != null) PlaybackEngineFallback.select(this@LamphausPlaybackService, session, engine) { adoptPlayer(session, it) }
                }
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
                                liftFraction = args.getFloat(EXTRA_LIFT_FRACTION, 0f),
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

        /** Puts the engine a stream starts on in place before its media item is set (PLY-ENG-01). */
        const val ACTION_SELECT_ENGINE = "lamphaus.playback.SELECT_ENGINE"
        const val EXTRA_ENGINE = "engine"

        /** Sent to the player screen when the session's engine changed, to re-send the surface. */
        const val ACTION_ENGINE_CHANGED = "lamphaus.playback.ENGINE_CHANGED"
        val CUSTOM_ACTIONS = listOf(
            ACTION_SET_SUBTITLE_DELAY,
            ACTION_SET_AUDIO_DELAY,
            ACTION_APPLY_SUBTITLE_STYLE,
            ACTION_SELECT_ENGINE,
        )
        const val EXTRA_DELAY_MILLIS = "delay_millis"
        const val EXTRA_STYLE_JSON = "style_json"

        /** Chrome-visibility subtitle lift as a fraction of the video frame (PLY-IMM-03). */
        const val EXTRA_LIFT_FRACTION = "lift_fraction"
    }
}
