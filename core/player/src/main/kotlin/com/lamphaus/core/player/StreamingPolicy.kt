package com.lamphaus.core.player

import androidx.media3.common.PlaybackException

/** Buffer sizes for the Media3 engine (QA-08). */
data class PlaybackBufferPlan(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int,
    val backBufferMs: Int,
    val targetBufferBytes: Int,
)

/**
 * Streaming resilience, ported from Nuvio's ExoPlayer setup: time-first
 * buffering with a heap-scaled byte budget, fail-fast on dead links, backoff
 * on transient failures, and a bounded re-prepare when an error still escapes.
 *
 * The previous fixed 32 MB byte cap stopped loading after about four seconds
 * of a 60 Mbps remux, so any CDN pause drained the buffer and playback stalled.
 */
object StreamingPolicy {

    fun bufferPlan(maxHeapBytes: Long): PlaybackBufferPlan = PlaybackBufferPlan(
        minBufferMs = 15_000,
        maxBufferMs = 45_000,
        bufferForPlaybackMs = 3_000,
        bufferForPlaybackAfterRebufferMs = 3_000,
        // Keeps a short skip back off the network.
        backBufferMs = 1_500,
        targetBufferBytes = (maxHeapBytes * TARGET_HEAP_SHARE).toLong()
            .coerceIn(MIN_TARGET_BYTES, MAX_TARGET_BYTES)
            .toInt(),
    )

    /**
     * Delay before retrying a failed media load, or null when retrying cannot
     * help: rejected, expired, or removed links fail fast so the viewer can
     * pick another source. [errorCount] starts at 1.
     */
    fun retryDelayMillis(errorCount: Int, httpStatus: Int?): Long? {
        if (httpStatus in DEAD_LINK_STATUSES) return null
        return if (errorCount <= 1) 750L else minOf((errorCount - 1) * 1_000L, 3_000L)
    }

    /**
     * Whether an error that escaped the load retries earns an automatic
     * re-prepare at the same position. Only source/network errors qualify:
     * decoder failures belong to the MPV fallback (see [PlaybackEngineFallback]).
     */
    fun shouldAutoRetry(errorCode: Int, httpStatus: Int?, attempt: Int): Boolean =
        attempt < MAX_AUTO_RETRIES &&
            httpStatus !in DEAD_LINK_STATUSES &&
            (errorCode in 2000..2999 || errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW)

    const val MIN_LOADABLE_RETRY_COUNT = 6
    const val AUTO_RETRY_DELAY_MS = 1_500L

    /** A retry this long ago means playback recovered; the budget starts over. */
    const val AUTO_RETRY_RESET_MS = 30_000L

    private const val MAX_AUTO_RETRIES = 2
    private const val TARGET_HEAP_SHARE = 0.30
    private const val MIN_TARGET_BYTES = 48L * 1024 * 1024
    private const val MAX_TARGET_BYTES = 160L * 1024 * 1024
    private val DEAD_LINK_STATUSES = setOf(400, 401, 403, 404, 410)
}
