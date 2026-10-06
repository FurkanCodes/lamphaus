package com.lamphaus.core.player

import android.os.Handler
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import com.lamphaus.core.player.mpv.MpvLibrary

/** Process-wide recovery state the activity reads while it waits for playback to start. */
object PlaybackRecovery {
    /**
     * True from a scheduled retry until playback is ready again or recovery
     * gives up, so startup keeps its loading surface instead of failing.
     */
    @Volatile
    var isRecovering: Boolean = false
        internal set
}

/**
 * Nuvio's recovery for errors that escape the load retries, and its stall
 * watchdog (SHR-PROD-04, see [StreamingPolicy]). Startup and playback each get
 * two attempts, 1.5 s apart; healthy playback refills both after 5 s. A reload
 * runs on the same player, so the session and its controllers stay connected.
 */
@UnstableApi
internal class PlaybackRecoveryListener(private val player: ExoPlayer) : Player.Listener {
    private val handler = Handler(player.applicationLooper)
    private var firstFrameRendered = false
    private var startupAttempts = 0
    private var playbackAttempts = 0
    private var reloading = false
    private var pendingRetry: Runnable? = null

    private val refillBudgets = Runnable {
        if (player.playbackState == Player.STATE_READY && player.isPlaying) {
            startupAttempts = 0
            playbackAttempts = 0
        }
    }

    private var stallBufferedMillis = 0L
    private var stallSinceMillis = 0L
    private val stallPoll = object : Runnable {
        override fun run() {
            if (player.playbackState != Player.STATE_BUFFERING) return
            val now = SystemClock.elapsedRealtime()
            val buffered = player.bufferedPosition
            if (buffered > stallBufferedMillis) {
                stallBufferedMillis = buffered
                stallSinceMillis = now
                handler.postDelayed(this, StreamingPolicy.STALL_POLL_MS)
                return
            }
            when (
                val decision = StreamingPolicy.stallDecision(
                    bufferedPositionMillis = buffered,
                    playheadMillis = player.currentPosition,
                    durationMillis = player.duration,
                    stalledForMillis = now - stallSinceMillis,
                )
            ) {
                StreamingPolicy.StallDecision.Wait -> handler.postDelayed(this, StreamingPolicy.STALL_POLL_MS)
                StreamingPolicy.StallDecision.GiveUp -> Unit
                is StreamingPolicy.StallDecision.Seek -> player.seekTo(decision.positionMillis)
            }
        }
    }

    override fun onRenderedFirstFrame() {
        firstFrameRendered = true
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        firstFrameRendered = false
        if (reloading) return
        // A new playback starts with both budgets full.
        cancelRetry()
        startupAttempts = 0
        playbackAttempts = 0
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        handler.removeCallbacks(stallPoll)
        when (playbackState) {
            Player.STATE_READY -> {
                reloading = false
                PlaybackRecovery.isRecovering = false
                scheduleBudgetRefill()
            }
            Player.STATE_BUFFERING -> {
                stallBufferedMillis = player.bufferedPosition
                stallSinceMillis = SystemClock.elapsedRealtime()
                handler.postDelayed(stallPoll, StreamingPolicy.STALL_POLL_MS)
            }
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) scheduleBudgetRefill() else handler.removeCallbacks(refillBudgets)
    }

    override fun onPlayerError(error: PlaybackException) {
        handler.removeCallbacks(stallPoll)
        handler.removeCallbacks(refillBudgets)
        val causes = generateSequence<Throwable>(error) { it.cause }.toList()
        val status = causes.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()?.responseCode
        val stateOrNull = error.cause is IllegalStateException || error.cause is NullPointerException
        val decoderShaped = EngineHandoff.failureKindFrom(error.errorCode) in DECODER_FAILURES
        // When libmpv is packaged, PlaybackEngineFallback owns decoder failures.
        val recoverable = !(decoderShaped && MpvLibrary.isAvailable()) &&
            (
                error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW ||
                    StreamingPolicy.isRecoverable(error.errorCode, status, stateOrNull)
                )
        val attempt = if (firstFrameRendered) playbackAttempts else startupAttempts
        val step = StreamingPolicy.recoveryStep(firstFrameRendered, attempt)?.takeIf { recoverable }
        if (step == null) {
            PlaybackRecovery.isRecovering = false
            return
        }
        if (firstFrameRendered) playbackAttempts++ else startupAttempts++
        val position = player.currentPosition.coerceAtLeast(0L)
        PlaybackRecovery.isRecovering = true
        cancelRetry()
        val retry = Runnable {
            pendingRetry = null
            if (Media3EngineFactory.sessionPlayer !== player || player.playerError == null) {
                PlaybackRecovery.isRecovering = false
                return@Runnable
            }
            val item = player.currentMediaItem
            when {
                error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                    player.seekToDefaultPosition()
                    player.prepare()
                }
                step == StreamingPolicy.RecoveryStep.REPREPARE || item == null -> {
                    if (position > 0) player.seekTo(position - 1)
                    player.prepare()
                }
                else -> {
                    reloading = true
                    player.stop()
                    player.setMediaItem(item, position)
                    player.prepare()
                }
            }
        }
        pendingRetry = retry
        handler.postDelayed(retry, StreamingPolicy.RECOVERY_DELAY_MS)
    }

    private fun scheduleBudgetRefill() {
        handler.removeCallbacks(refillBudgets)
        handler.postDelayed(refillBudgets, StreamingPolicy.RECOVERY_RESET_MS)
    }

    private fun cancelRetry() {
        pendingRetry?.let(handler::removeCallbacks)
        pendingRetry = null
    }

    private companion object {
        val DECODER_FAILURES = setOf(EngineFailureKind.DECODER_INIT_FAILED, EngineFailureKind.DECODER_FAILED)
    }
}
