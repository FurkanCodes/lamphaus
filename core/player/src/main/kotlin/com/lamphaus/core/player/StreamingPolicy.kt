package com.lamphaus.core.player

import androidx.media3.common.C
import androidx.media3.common.PlaybackException

/**
 * Streaming resilience, matching Nuvio's default ExoPlayer path (QA-08,
 * SHR-PROD-04): Media3's stock buffering with a short back buffer, fail-fast
 * dead links, quick load retries, a two-step recovery ladder for errors that
 * escape them, and a watchdog for loads that stop moving.
 */
object StreamingPolicy {

    /** Keeps a short skip back off the network; everything else is Media3's stock load control. */
    const val STOCK_BACK_BUFFER_MS = 1_500

    const val MIN_LOADABLE_RETRY_COUNT = 6

    /**
     * Delay before retrying a failed media load, or null when retrying cannot
     * help. Rejected, expired, or removed links fail fast so the viewer can
     * pick another source; timeouts back off; anything else takes Media3's own
     * delay ([mediaDelayMillis], null when Media3 calls the error fatal), whose
     * first retry is immediate. [errorCount] starts at 1.
     */
    fun retryDelayMillis(errorCount: Int, httpStatus: Int?, timedOut: Boolean, mediaDelayMillis: Long?): Long? {
        if (httpStatus in DEAD_LINK_STATUSES) return null
        if (timedOut) {
            return when (errorCount) {
                1 -> 750L
                2 -> 1_500L
                else -> 3_000L
            }
        }
        return mediaDelayMillis
    }

    /**
     * Errors worth an automatic retry at the same position: source and network
     * failures other than dead links, container parsing glitches, decoder
     * failures, and state or null-pointer escapes. Everything else is shown.
     */
    fun isRecoverable(errorCode: Int, httpStatus: Int?, causeIsStateOrNull: Boolean): Boolean = when (errorCode) {
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
        PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
        PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        -> true
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> httpStatus !in DEAD_LINK_STATUSES
        PlaybackException.ERROR_CODE_UNSPECIFIED -> causeIsStateOrNull
        else -> false
    }

    /** How a recovery attempt restarts playback. */
    enum class RecoveryStep {
        /** Seek one millisecond back and prepare the same source again. */
        REPREPARE,

        /** Load the media item afresh: new source, extractors, and decoders. */
        RELOAD,
    }

    /**
     * The next recovery attempt, or null once its budget of two is spent.
     * Before the first frame every attempt reloads; during playback the first
     * re-prepares and the second reloads. [attempt] counts from 0 within its
     * own budget (startup and playback each get two).
     */
    fun recoveryStep(firstFrameRendered: Boolean, attempt: Int): RecoveryStep? = when {
        attempt >= MAX_RECOVERY_ATTEMPTS -> null
        firstFrameRendered && attempt == 0 -> RecoveryStep.REPREPARE
        else -> RecoveryStep.RELOAD
    }

    const val RECOVERY_DELAY_MS = 1_500L

    /** This long of healthy playback refills both recovery budgets. */
    const val RECOVERY_RESET_MS = 5_000L

    /** What the stall watchdog does after a poll during buffering. */
    sealed interface StallDecision {
        data object Wait : StallDecision

        /** Nothing a seek can unstick; stop watching this stretch of buffering. */
        data object GiveUp : StallDecision

        data class Seek(val positionMillis: Long) : StallDecision
    }

    /**
     * While buffering, a buffered position that has not moved for 15 s means
     * the range request is stuck: seeking just past the buffered edge makes
     * Media3 drop it and open a new one, before OkHttp's read timeout fires.
     */
    fun stallDecision(
        bufferedPositionMillis: Long,
        playheadMillis: Long,
        durationMillis: Long,
        stalledForMillis: Long,
    ): StallDecision {
        if (stalledForMillis < STALL_THRESHOLD_MS) return StallDecision.Wait
        val playhead = playheadMillis.coerceAtLeast(0L)
        if (durationMillis == C.TIME_UNSET || durationMillis <= 0L) return StallDecision.GiveUp
        if (bufferedPositionMillis <= playhead) return StallDecision.GiveUp
        val target = (bufferedPositionMillis + STALL_SKIP_PAST_BUFFERED_MS).coerceAtMost(durationMillis - 1)
        if (target <= playhead || target <= 0L) return StallDecision.GiveUp
        return StallDecision.Seek(target)
    }

    const val STALL_THRESHOLD_MS = 15_000L
    const val STALL_POLL_MS = 1_000L
    private const val STALL_SKIP_PAST_BUFFERED_MS = 250L

    private const val MAX_RECOVERY_ATTEMPTS = 2
    private val DEAD_LINK_STATUSES = setOf(400, 401, 403, 404, 410)
}
