package com.lamphaus.core.player.network

import kotlin.random.Random

/**
 * The numbers behind parallel range downloads (PLY-NET-01), matching Nuvio's
 * "Parallel Connections": 16 MiB chunks over two connections by default,
 * restarts for stalled chunks, and a back-off that shrinks the prefetch window
 * when the host rate-limits. Pure, so every rule is unit-tested.
 */
object ParallelDownloadPolicy {

    const val DEFAULT_CONNECTIONS = 2
    val CONNECTION_RANGE = 2..4

    /** First request of a cold open; its Content-Range tells the file length. */
    const val PROBE_BYTES = 256L * 1024L

    /** Prefetch beyond the current chunk starts once this much was served in one open. */
    const val EARNED_PREFETCH_BYTES = 1L * 1024L * 1024L

    /** A released session survives Media3's close-and-reopen on every seek this long. */
    const val SESSION_TTL_MS = 45_000L

    /** Chunks read within this long are never evicted. */
    const val EVICTION_TOUCH_GUARD_MS = 2_000L

    /** A reader that sees no progress this long restarts the chunk's download. */
    const val READER_STALL_RESTART_MS = 2_000L

    /** A reader gives up on a chunk (and lets Media3 retry) after this long without data. */
    const val READER_GIVE_UP_MS = 60_000L

    const val STALL_MIN_OPEN_MS = 1_500L
    const val STALL_WINDOW_MS = 2_000L
    const val STALL_MAX_RESTARTS = 3

    /** Attempts per chunk for errors other than rate limits and stalls. */
    const val DOWNLOAD_ATTEMPTS = 2

    const val RATE_LIMIT_RETRIES = 3

    /** Chunk size ceiling on heap-bound devices: their retained set must stay small. */
    const val LOW_RAM_MAX_CHUNK_BYTES = 16L * 1024L * 1024L
    private const val LOW_RAM_HEAP_BYTES = 512L * 1024L * 1024L

    /** Below this rate over a window a chunk counts as stalled. */
    fun stallRateBytesPerSecond(usenet: Boolean): Long = if (usenet) 128L * 1024L else 256L * 1024L

    /** How many consecutive slow windows make a stall. */
    fun stallWindows(usenet: Boolean): Int = if (usenet) 2 else 1

    fun isLowRamHeap(maxHeapBytes: Long): Boolean = maxHeapBytes < LOW_RAM_HEAP_BYTES

    fun chunkBytes(configuredKb: Int, maxHeapBytes: Long): Long {
        val configured = configuredKb.toLong().coerceAtLeast(1L) * 1024L
        return if (isLowRamHeap(maxHeapBytes)) configured.coerceAtMost(LOW_RAM_MAX_CHUNK_BYTES) else configured
    }

    /**
     * Chunks scheduled ahead, the current one included. On the heap it is one
     * per connection plus one; with native memory the RAM-tier budget left
     * after the sample buffer decides, between two and four per connection.
     */
    fun prefetchDepth(connections: Int, chunkBytes: Long, nativeBudgetBytes: Long?, reservedBufferBytes: Long): Int {
        if (nativeBudgetBytes == null) return connections + 1
        val chunk = chunkBytes.coerceAtLeast(1L)
        val budget = (nativeBudgetBytes - reservedBufferBytes.coerceAtLeast(0L)).coerceAtLeast(chunk * 2)
        return (budget / chunk).toInt().coerceIn(connections * 2, connections * 4)
    }

    /** Chunks a session may hold before evicting: the window plus a margin behind the reader. */
    fun sessionChunkCap(depth: Int, maxHeapBytes: Long): Int = depth + if (isLowRamHeap(maxHeapBytes)) 2 else 4

    /** Memory the downloads hold beyond the sample buffer: (connections + 2) chunks. */
    fun overheadBytes(connections: Int, chunkBytes: Long): Long = (connections + 2L) * chunkBytes

    /**
     * Wait before retrying a rate-limited (429/503) chunk: the host's
     * Retry-After up to 15 s, otherwise an exponential back-off that grows
     * with this episode's [escalation], plus up to 250 ms of jitter.
     */
    fun rateLimitWaitMillis(attempt: Int, escalation: Int, retryAfterMillis: Long?, random: Random = Random): Long {
        val jitter = random.nextLong(0, RATE_LIMIT_JITTER_MS)
        if (retryAfterMillis != null) return retryAfterMillis.coerceIn(0L, RATE_LIMIT_HARD_CAP_MS) + jitter
        val cycleCap = (RATE_LIMIT_CYCLE_CAP_MS shl escalation.coerceIn(0, RATE_LIMIT_ESCALATION_MAX))
            .coerceAtMost(RATE_LIMIT_HARD_CAP_MS)
        val base = RATE_LIMIT_BASE_MS shl (attempt + escalation).coerceIn(0, 6)
        return base.coerceIn(RATE_LIMIT_BASE_MS, cycleCap) + jitter
    }

    /** Retry-After as delay seconds; HTTP-date values are ignored (the back-off applies). */
    fun parseRetryAfterMillis(header: String?): Long? =
        header?.trim()?.toLongOrNull()?.takeIf { it >= 0 }?.let { it * 1_000L }

    /**
     * Chunk to evict when a session holds more than [cap]: never the reader's
     * window or anything touched in the last 2 s; the stalest chunk behind the
     * reader first, then the one farthest ahead.
     */
    fun evictionCandidate(
        resident: Map<Long, Long>,
        readerIndex: Long,
        depth: Int,
        cap: Int,
        nowMillis: Long,
    ): Long? {
        if (resident.size <= cap) return null
        val protectedRange = readerIndex until readerIndex + depth
        val candidates = resident.filter { (index, touched) ->
            index !in protectedRange && nowMillis - touched >= EVICTION_TOUCH_GUARD_MS
        }
        val behind = candidates.filterKeys { it < readerIndex }
        if (behind.isNotEmpty()) return behind.minBy { it.value }.key
        return candidates.keys.maxOrNull()
    }

    private const val RATE_LIMIT_BASE_MS = 500L
    private const val RATE_LIMIT_CYCLE_CAP_MS = 3_000L
    private const val RATE_LIMIT_HARD_CAP_MS = 15_000L
    private const val RATE_LIMIT_JITTER_MS = 250L
    internal const val RATE_LIMIT_ESCALATION_MAX = 5
    internal const val DEPTH_STEP_BASE_MS = 10_000L
    internal const val DEPTH_STEP_MAX_MS = 60_000L
}

/**
 * How deep a session may prefetch while its host rate-limits: each episode
 * halves the window (at most once a second), and every quiet interval wins one
 * chunk back, starting at 10 s and doubling, up to 60 s, while limits recur.
 */
class RateLimitDepth(private val configuredDepth: Int) {
    private var cap = Int.MAX_VALUE
    private var escalation = 0
    private var lastHitAt = Long.MIN_VALUE / 2
    private var lastHalveAt = Long.MIN_VALUE / 2
    private var lastStepAt = Long.MIN_VALUE / 2
    private var stepInterval = ParallelDownloadPolicy.DEPTH_STEP_BASE_MS

    /** A chunk was rate-limited: returns the escalation for its back-off. */
    @Synchronized
    fun beginEpisode(nowMillis: Long): Int {
        lastHitAt = nowMillis
        val current = escalation
        escalation = (escalation + 1).coerceAtMost(ParallelDownloadPolicy.RATE_LIMIT_ESCALATION_MAX)
        if (nowMillis - lastHalveAt >= 1_000L) {
            val alreadyCapped = cap < configuredDepth
            val effective = cap.coerceAtMost(configuredDepth)
            val halved = (effective / 2).coerceAtLeast(1)
            if (halved < effective) {
                cap = halved
                lastHalveAt = nowMillis
                if (alreadyCapped) {
                    stepInterval = (stepInterval * 2).coerceAtMost(ParallelDownloadPolicy.DEPTH_STEP_MAX_MS)
                }
            }
        }
        return current
    }

    /** A retry of an already limited chunk was limited again. */
    @Synchronized
    fun noteHit(nowMillis: Long) {
        lastHitAt = nowMillis
    }

    @Synchronized
    fun allowed(nowMillis: Long): Int {
        if (cap >= configuredDepth) return configuredDepth
        if (nowMillis - lastHitAt >= stepInterval && nowMillis - lastStepAt >= stepInterval) {
            cap += 1
            lastStepAt = nowMillis
            escalation = (escalation - 1).coerceAtLeast(0)
            if (cap >= configuredDepth) {
                cap = Int.MAX_VALUE
                stepInterval = ParallelDownloadPolicy.DEPTH_STEP_BASE_MS
            }
        }
        return cap.coerceAtMost(configuredDepth)
    }
}
