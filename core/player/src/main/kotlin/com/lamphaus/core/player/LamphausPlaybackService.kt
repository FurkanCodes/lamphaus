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

    /** The Picture panel's effect, and the ExoPlayer it was installed on (PLY-PIC-01). */
    private val pictureEffect = PictureAdjustmentEffect()
    private var pictureEffectPlayer: androidx.media3.exoplayer.ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()
        playerConfig = Media3EngineFactory.deviceConfig
        val player = Media3EngineFactory.createPlayer(this, playerConfig)
        Media3EngineFactory.sessionPlayer = player
        PlaybackDiagnostics.attach(player)
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
        val current = session.player
        // Picture adjustments last one playback: the next one starts on a player
        // without the effect pipeline, and mpv back at its defaults.
        (current as? com.lamphaus.core.player.mpv.MpvPlayer)?.setPictureAdjustment(PictureAdjustment.NEUTRAL)
        SubtitleOffset.delayUs = 0L
        val carriesPictureEffect = pictureEffectPlayer != null && pictureEffectPlayer === current
        if (!Media3EngineFactory.needsRebuild(playerConfig, wanted) && !carriesPictureEffect) return
        if (current.mediaItemCount > 0 && current.playbackState != androidx.media3.common.Player.STATE_IDLE) return
        val fresh = Media3EngineFactory.createPlayer(this, wanted)
        playerConfig = wanted
        pictureEffectPlayer = null
        session.player = fresh
        Media3EngineFactory.sessionPlayer = fresh
        PlaybackDiagnostics.attach(fresh)
        current.release()
        installFallback(session)
    }

    private fun installFallback(session: MediaSession) {
        PlaybackEngineFallback.install(this, session) { replacement, state ->
            // Diagnostics record only the engine switch, never the source (SHR-PROD-06).
            PlaybackDiagnostics.note(com.lamphaus.core.model.ReportEventType.ENGINE_FALLBACK, detail = state.fallbackReason)
            adoptPlayer(session, replacement)
        }
    }

    /**
     * The session now runs [player]: ExoPlayer is published for the panels
     * and Info, while an MPV session clears it so callers read MPV's state.
     */
    private fun adoptPlayer(session: MediaSession, player: androidx.media3.common.Player) {
        Media3EngineFactory.sessionPlayer = player as? androidx.media3.exoplayer.ExoPlayer
        PlaybackDiagnostics.attach(player)
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
     * timing for both engines (mpv's sub-delay, ExoPlayer's SubtitleOffset)
     * and audio timing for MPV (plan §2/§4); ExoPlayer's audio delay is the
     * client's DelayAudioProcessor, so both engines honor one activity contract.
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
                ACTION_SET_SUBTITLE_DELAY -> {
                    val millis = args.getLong(EXTRA_DELAY_MILLIS, 0L)
                    if (mpv != null) mpv.setSubtitleDelayMillis(millis) else applySubtitleOffset(session.player, millis)
                }
                ACTION_SET_AUDIO_DELAY ->
                    mpv?.setAudioDelayMillis(args.getLong(EXTRA_DELAY_MILLIS, 0L))
                PictureAdjustment.ACTION_SET_PICTURE -> {
                    val adjustment = PictureAdjustment.fromBundle(args)
                    when (val player = session.player) {
                        is com.lamphaus.core.player.mpv.MpvPlayer -> player.setPictureAdjustment(adjustment)
                        is androidx.media3.exoplayer.ExoPlayer -> applyPicture(player, adjustment)
                    }
                }
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

    /**
     * ExoPlayer's subtitle offset ([SubtitleOffsetRenderer]). A change while
     * playing re-reads the text track at the same position, an in-buffer seek,
     * so cues already shown or passed resolve against the new offset.
     */
    private fun applySubtitleOffset(player: androidx.media3.common.Player, millis: Long) {
        val delayUs = millis.coerceIn(-MAX_SUBTITLE_OFFSET_MILLIS, MAX_SUBTITLE_OFFSET_MILLIS) * 1000
        if (delayUs == SubtitleOffset.delayUs) return
        SubtitleOffset.delayUs = delayUs
        if (player.playbackState == androidx.media3.common.Player.STATE_READY) player.seekTo(player.currentPosition)
    }

    /**
     * ExoPlayer builds its effect pipeline only when its renderers start, so
     * the first adjustment of a playback reloads it in place once; after that
     * the effect reads each change on the next frame. A paused frame is shown
     * again by seeking to where it is: Media3's redraw needs a replayable
     * frame cache the player does not keep, and fails playback without one.
     */
    private fun applyPicture(player: androidx.media3.exoplayer.ExoPlayer, adjustment: PictureAdjustment) {
        pictureEffect.adjustment = adjustment
        if (pictureEffectPlayer === player) {
            if (!player.isPlaying) player.seekTo(player.currentPosition)
            return
        }
        if (adjustment.isNeutral || player.mediaItemCount == 0) return
        if (!PictureAdjustment.isAvailable(mpv = false, playerConfig.tunneledPlayback, hasVideo = true, player.videoFormat)) return
        pictureEffectPlayer = player
        val position = player.currentPosition
        player.setVideoEffects(listOf(pictureEffect))
        // stop() resets the renderers, so the video renderer starts again with the effect.
        player.stop()
        player.seekTo(position)
        player.prepare()
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
            PictureAdjustment.ACTION_SET_PICTURE,
        )
        const val EXTRA_DELAY_MILLIS = "delay_millis"
        const val MAX_SUBTITLE_OFFSET_MILLIS = 180_000L
        const val EXTRA_STYLE_JSON = "style_json"

        /** Chrome-visibility subtitle lift as a fraction of the video frame (PLY-IMM-03). */
        const val EXTRA_LIFT_FRACTION = "lift_fraction"
    }
}
