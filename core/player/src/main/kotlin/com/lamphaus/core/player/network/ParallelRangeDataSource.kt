package com.lamphaus.core.player.network

import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** One player's parallel download settings (PLY-NET-01); see [ParallelDownloadPolicy]. */
data class ParallelDownloadSettings(
    val connections: Int,
    val chunkBytes: Long,
    /** Chunks scheduled ahead, the current one included. */
    val depth: Int,
    val sessionChunkCap: Int,
    val memory: ChunkMemory,
)

/** Process-wide counters for the Info panel. Never records a URL (SHR-PROD-06). */
object ParallelDownloadStats {
    val activeDownloads = AtomicInteger()
    val rateLimitedResponses = AtomicInteger()
}

/**
 * Downloads a progressive HTTP stream in fixed-size ranged chunks over several
 * connections at once, the way Nuvio's "Parallel Connections" does (behaviour
 * re-implemented; no Nuvio source). Debrid CDNs usually cap each connection's
 * speed, so a 4K remux that one connection cannot keep up with plays
 * smoothly over two or three.
 *
 * - The first request of a cold open reads up to the next chunk boundary and
 *   doubles as the length probe (Content-Range). A server without ranges or a
 *   known length is read over one connection, exactly as before.
 * - Chunks ahead of the reader download in parallel once playback is ready
 *   and 1 MiB was served in this open. The reader consumes a chunk as its
 *   bytes arrive.
 * - A process-wide session keeps the chunks across Media3's close and reopen
 *   on every seek; a position no chunk holds streams over one connection to
 *   the next chunk boundary.
 */
@UnstableApi
class ParallelRangeDataSource internal constructor(
    private val upstream: HttpDataSource.Factory,
    private val settings: ParallelDownloadSettings,
    private val prefetchAllowed: () -> Boolean,
) : BaseDataSource(/* isNetwork = */ true) {

    private var session: RangeSession? = null
    private var dataSpec: DataSpec? = null
    private var opened = false
    private var position = 0L
    private var bytesRemaining = 0L
    private var servedThisOpen = 0L
    private var nextPrefetchCheckAt = 0L

    /** Streams [position] up to a chunk boundary over one connection. */
    private var continuation: DataSource? = null
    private var continuationEnd = 0L

    /** The whole open goes over one connection: the server has no usable ranges. */
    private var fallback: DataSource? = null

    private var currentChunk: Chunk? = null

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        transferInitializing(dataSpec)
        val key = SessionKey(dataSpec.uri, dataSpec.httpRequestHeaders, settings)
        val active = RangeSessions.attach(key) { RangeSession(key, upstream) }
        session = active
        position = dataSpec.position
        servedThisOpen = 0L
        nextPrefetchCheckAt = position
        val total = active.totalLength
        if (total != C.LENGTH_UNSET.toLong()) {
            bytesRemaining = bounded(dataSpec, (total - position).coerceAtLeast(0L))
        } else {
            probe(dataSpec, active)
        }
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    /** Cold open: the first ranged request reads to the chunk boundary and reveals the file length. */
    private fun probe(dataSpec: DataSpec, active: RangeSession) {
        val end = boundaryAfter(position).let { boundary ->
            if (dataSpec.length == C.LENGTH_UNSET.toLong()) boundary else minOf(boundary, position + dataSpec.length)
        }
        val source = upstream.createDataSource()
        try {
            source.open(dataSpec.buildUpon().setLength(end - position).build())
        } catch (error: IOException) {
            runCatching { source.close() }
            throw error
        }
        val total = contentRangeTotal(source.responseHeaders)
        if (total == C.LENGTH_UNSET.toLong()) {
            runCatching { source.close() }
            val single = upstream.createDataSource()
            fallback = single
            bytesRemaining = single.open(dataSpec)
            return
        }
        active.publishLength(total, source.uri ?: dataSpec.uri, source.responseHeaders)
        continuation = source
        continuationEnd = end
        bytesRemaining = bounded(dataSpec, (total - position).coerceAtLeast(0L))
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        fallback?.let { single ->
            val read = single.read(buffer, offset, length)
            if (read > 0) advance(read)
            return read
        }
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val active = session ?: return C.RESULT_END_OF_INPUT
        val wanted = minOf(length.toLong(), bytesRemaining).toInt()
        if (position >= nextPrefetchCheckAt) prefetch(active)

        continuation?.let { stream ->
            if (position < continuationEnd) {
                val read = stream.read(buffer, offset, minOf(wanted.toLong(), continuationEnd - position).toInt())
                if (read == C.RESULT_END_OF_INPUT) {
                    closeContinuation()
                    // The host closed the range early; Media3 reopens at this position.
                    throw IOException("Stream ended before the chunk boundary")
                }
                advance(read)
                if (position >= continuationEnd) closeContinuation()
                return read
            }
            closeContinuation()
        }

        val index = position / settings.chunkBytes
        val chunk = chunkFor(active, index) ?: return read(buffer, offset, length)
        val offsetInChunk = (position - chunk.start).toInt()
        val available = chunk.awaitData(offsetInChunk) { active.restart(chunk) }
        if (available < 0) {
            active.drop(chunk)
            releaseCurrentChunk()
            throw HttpDataSource.HttpDataSourceException(
                chunk.failure ?: IOException("Chunk download failed"),
                dataSpec ?: DataSpec(Uri.EMPTY),
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                HttpDataSource.HttpDataSourceException.TYPE_READ,
            )
        }
        val read = minOf(wanted, available)
        chunk.buffer.read(offsetInChunk, buffer, offset, read)
        chunk.touch()
        active.readerIndex = index
        advance(read)
        if (offsetInChunk + read >= chunk.length) releaseCurrentChunk()
        return read
    }

    /**
     * The chunk holding [index], or null after opening a one-connection
     * stream from [position] to the next boundary (a seek into bytes no chunk
     * holds). A read at a chunk's very start downloads the whole chunk.
     */
    private fun chunkFor(active: RangeSession, index: Long): Chunk? {
        currentChunk?.let { if (it.index == index) return it }
        releaseCurrentChunk()
        val chunkStart = index * settings.chunkBytes
        val chunk = active.resident(index)
            ?: if (position == chunkStart) active.schedule(index) else null
        if (chunk != null && chunk.retain()) {
            currentChunk = chunk
            return chunk
        }
        openContinuation(active)
        return null
    }

    private fun openContinuation(active: RangeSession) {
        val end = minOf(boundaryAfter(position), position + bytesRemaining)
        val source = upstream.createDataSource()
        val spec = DataSpec.Builder()
            .setUri(active.resolvedUri ?: active.key.uri)
            .setHttpRequestHeaders(active.key.headers)
            .setPosition(position)
            .setLength(end - position)
            .build()
        try {
            source.open(spec)
        } catch (error: IOException) {
            runCatching { source.close() }
            throw error
        }
        continuation = source
        continuationEnd = end
    }

    private fun prefetch(active: RangeSession) {
        nextPrefetchCheckAt = position + PREFETCH_CHECK_STEP
        if (!prefetchAllowed()) return
        val chunkBytes = settings.chunkBytes
        val index = position / chunkBytes
        // Bytes a one-connection stream covers are never fetched twice: a
        // mid-chunk position without its chunk is (or will be) streamed.
        val first = when {
            continuation != null && position < continuationEnd -> (continuationEnd + chunkBytes - 1) / chunkBytes
            position == index * chunkBytes || active.resident(index) != null -> index
            else -> index + 1
        }
        val earned = servedThisOpen >= ParallelDownloadPolicy.EARNED_PREFETCH_BYTES
        val ahead = if (earned) active.depth.allowed(SystemClock.elapsedRealtime()) else 1
        // A failed allocation only costs this prefetch; the reader's own chunk still loads.
        runCatching { active.prefetch(first, ahead, readerIndex = index) }
    }

    private fun advance(read: Int) {
        position += read
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
        servedThisOpen += read
        bytesTransferred(read)
    }

    private fun boundaryAfter(at: Long): Long {
        val boundary = (at / settings.chunkBytes + 1) * settings.chunkBytes
        val total = session?.totalLength ?: C.LENGTH_UNSET.toLong()
        return if (total != C.LENGTH_UNSET.toLong()) minOf(boundary, total) else boundary
    }

    private fun closeContinuation() {
        continuation?.let { runCatching { it.close() } }
        continuation = null
        continuationEnd = 0L
    }

    private fun releaseCurrentChunk() {
        currentChunk?.release()
        currentChunk = null
    }

    override fun getUri(): Uri? = fallback?.uri ?: session?.resolvedUri ?: dataSpec?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        fallback?.responseHeaders ?: session?.responseHeaders.orEmpty()

    override fun close() {
        closeContinuation()
        fallback?.let { runCatching { it.close() } }
        fallback = null
        releaseCurrentChunk()
        session?.let(RangeSessions::detach)
        session = null
        dataSpec = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    /**
     * Creates a data source per load that uses parallel chunks only for the
     * playback's primary progressive stream ([eligible]); HLS, DASH,
     * subtitles, and other requests keep their single connection.
     */
    class Factory(
        private val upstream: HttpDataSource.Factory,
        private val settings: ParallelDownloadSettings,
        private val prefetchAllowed: () -> Boolean,
        private val eligible: (DataSpec) -> Boolean,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = RoutingDataSource()

        private inner class RoutingDataSource : DataSource {
            private val listeners = mutableListOf<TransferListener>()
            private var delegate: DataSource? = null

            override fun addTransferListener(transferListener: TransferListener) {
                listeners += transferListener
                delegate?.addTransferListener(transferListener)
            }

            override fun open(dataSpec: DataSpec): Long {
                val parallel = (dataSpec.uri.scheme == "http" || dataSpec.uri.scheme == "https") && eligible(dataSpec)
                val chosen = if (parallel) {
                    ParallelRangeDataSource(upstream, settings, prefetchAllowed)
                } else {
                    upstream.createDataSource()
                }
                listeners.forEach(chosen::addTransferListener)
                delegate = chosen
                return chosen.open(dataSpec)
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                checkNotNull(delegate).read(buffer, offset, length)

            override fun getUri(): Uri? = delegate?.uri

            override fun getResponseHeaders(): Map<String, List<String>> = delegate?.responseHeaders.orEmpty()

            override fun close() {
                delegate?.close()
                delegate = null
            }
        }
    }

    internal companion object {
        /** Prefetch decisions are re-checked after this many bytes, not on every small extractor read. */
        const val PREFETCH_CHECK_STEP = 256L * 1024L

        fun bounded(dataSpec: DataSpec, remaining: Long): Long =
            if (dataSpec.length == C.LENGTH_UNSET.toLong()) remaining else minOf(dataSpec.length, remaining)

        /** The total after the slash in "Content-Range: bytes a-b/total", or unset ("*" or absent). */
        fun contentRangeTotal(headers: Map<String, List<String>>): Long {
            val value = headers.entries.firstOrNull { it.key.equals("Content-Range", ignoreCase = true) }
                ?.value?.firstOrNull() ?: return C.LENGTH_UNSET.toLong()
            return value.substringAfterLast('/', "").trim().toLongOrNull()?.takeIf { it > 0 }
                ?: C.LENGTH_UNSET.toLong()
        }
    }
}

internal data class SessionKey(
    val uri: Uri,
    val headers: Map<String, String>,
    val settings: ParallelDownloadSettings,
)

/** The process-wide chunk sessions; a released one lingers [ParallelDownloadPolicy.SESSION_TTL_MS]. */
@UnstableApi
internal object RangeSessions {
    private val sessions = HashMap<SessionKey, RangeSession>()
    private val reaper = ScheduledThreadPoolExecutor(1) { runnable ->
        Thread(runnable, "LamphausRangeReaper").apply { isDaemon = true }
    }

    fun attach(key: SessionKey, create: () -> RangeSession): RangeSession {
        val (session, stale) = synchronized(this) {
            val existing = sessions[key]?.takeUnless { it.abandoned }
            val chosen = existing ?: create().also { sessions[key] = it }
            chosen.refs++
            // Another title's idle chunks only cost memory now.
            val idle = sessions.values.filter { it !== chosen && it.refs == 0 }
            idle.forEach { sessions.remove(it.key) }
            chosen to idle
        }
        stale.forEach(RangeSession::abandon)
        return session
    }

    fun detach(session: RangeSession) {
        synchronized(this) {
            session.refs--
            if (session.refs > 0) return
            session.releasedAt = SystemClock.elapsedRealtime()
        }
        reaper.schedule({ expire(session) }, ParallelDownloadPolicy.SESSION_TTL_MS, TimeUnit.MILLISECONDS)
    }

    private fun expire(session: RangeSession) {
        val expired = synchronized(this) {
            val idleLongEnough = session.refs == 0 &&
                SystemClock.elapsedRealtime() - session.releasedAt >= ParallelDownloadPolicy.SESSION_TTL_MS
            if (idleLongEnough && sessions[session.key] === session) sessions.remove(session.key)
            idleLongEnough
        }
        if (expired) session.abandon()
    }

    /** Ends every session, e.g. when the player is released. */
    fun clear() {
        val all = synchronized(this) { sessions.values.toList().also { sessions.clear() } }
        all.forEach(RangeSession::abandon)
    }
}

/** Chunks of one stream, shared by every open of it. */
@UnstableApi
internal class RangeSession(val key: SessionKey, private val upstream: HttpDataSource.Factory) {
    private val settings get() = key.settings
    private val lock = Any()
    private val chunks = HashMap<Long, Chunk>()
    private val usenet = key.uri.path?.contains("/usenet/") == true

    val depth = RateLimitDepth(settings.depth)
    var refs = 0
    var releasedAt = 0L

    @Volatile var abandoned = false
        private set

    @Volatile var totalLength = C.LENGTH_UNSET.toLong()
        private set

    @Volatile var resolvedUri: Uri? = null
        private set

    @Volatile var responseHeaders: Map<String, List<String>> = emptyMap()
        private set

    @Volatile var readerIndex = 0L

    fun publishLength(total: Long, resolved: Uri, headers: Map<String, List<String>>) {
        resolvedUri = resolved
        responseHeaders = headers
        totalLength = total
    }

    fun resident(index: Long): Chunk? = synchronized(lock) { chunks[index]?.takeUnless { it.failed } }

    /** The chunk at [index], downloading it if no healthy copy exists. */
    fun schedule(index: Long): Chunk? {
        val total = totalLength
        if (abandoned || total == C.LENGTH_UNSET.toLong()) return null
        val start = index * settings.chunkBytes
        if (start >= total) return null
        val (chunk, evicted) = synchronized(lock) {
            chunks[index]?.takeUnless { it.failed }?.let { return it }
            val replaced = chunks.remove(index)
            val end = minOf(start + settings.chunkBytes, total)
            val fresh = Chunk(index, start, end, settings.memory.obtain(settings.chunkBytes.toInt()), settings.memory)
            chunks[index] = fresh
            fresh to (listOfNotNull(replaced) + evictOverCap(protectIndex = index))
        }
        evicted.forEach(Chunk::evict)
        startDownload(chunk)
        return chunk
    }

    /** Ensures [ahead] chunks from [first] are resident or downloading. */
    fun prefetch(first: Long, ahead: Int, readerIndex: Long) {
        this.readerIndex = readerIndex
        val total = totalLength
        if (total == C.LENGTH_UNSET.toLong()) return
        for (offset in 0 until ahead) {
            val index = first + offset
            if (index * settings.chunkBytes >= total) break
            schedule(index)
        }
    }

    /** The reader saw no progress: drop the stuck connection and resume from the watermark. */
    fun restart(chunk: Chunk) {
        if (!abandoned) startDownload(chunk)
    }

    fun drop(chunk: Chunk) {
        val removed = synchronized(lock) { chunks.remove(chunk.index, chunk) }
        if (removed) chunk.evict()
    }

    fun abandon() {
        abandoned = true
        val all = synchronized(lock) { chunks.values.toList().also { chunks.clear() } }
        all.forEach(Chunk::evict)
        settings.memory.drain()
    }

    private fun evictOverCap(protectIndex: Long): List<Chunk> {
        val evicted = mutableListOf<Chunk>()
        while (true) {
            val now = SystemClock.elapsedRealtime()
            val resident = chunks.filterKeys { it != protectIndex }.mapValues { it.value.touchedAt }
            val victim = ParallelDownloadPolicy.evictionCandidate(
                resident = resident,
                readerIndex = readerIndex,
                depth = depth.allowed(now),
                cap = (settings.sessionChunkCap - 1).coerceAtLeast(1),
                nowMillis = now,
            ) ?: break
            chunks.remove(victim)?.let(evicted::add)
        }
        return evicted
    }

    private fun startDownload(chunk: Chunk) {
        val generation = chunk.nextGeneration()
        chunk.task = DOWNLOADS.submit { download(chunk, generation) }
    }

    private fun download(chunk: Chunk, generation: Int) {
        if (!chunk.retain()) return
        ParallelDownloadStats.activeDownloads.incrementAndGet()
        try {
            var errors = 0
            var stallRestarts = 0
            var rateLimited = 0
            var escalation = 0
            var useRequestUri = false
            while (!abandoned && chunk.isCurrent(generation)) {
                try {
                    transfer(
                        chunk = chunk,
                        generation = generation,
                        uri = if (useRequestUri) key.uri else resolvedUri ?: key.uri,
                        checkStalls = stallRestarts < ParallelDownloadPolicy.STALL_MAX_RESTARTS,
                    )
                    return
                } catch (stall: StalledChunkException) {
                    stallRestarts++
                } catch (error: IOException) {
                    if (abandoned || !chunk.isCurrent(generation) || error.isInterruption()) return
                    val response = error.responseCode()
                    if (response?.responseCode == 429 || response?.responseCode == 503) {
                        ParallelDownloadStats.rateLimitedResponses.incrementAndGet()
                        val now = SystemClock.elapsedRealtime()
                        if (rateLimited == 0) escalation = depth.beginEpisode(now) else depth.noteHit(now)
                        if (rateLimited >= ParallelDownloadPolicy.RATE_LIMIT_RETRIES) {
                            chunk.fail(generation, error)
                            return
                        }
                        val retryAfter = response.headerFields.entries
                            .firstOrNull { it.key.equals("Retry-After", ignoreCase = true) }
                            ?.value?.firstOrNull()
                        val wait = ParallelDownloadPolicy.rateLimitWaitMillis(
                            attempt = rateLimited,
                            escalation = escalation,
                            retryAfterMillis = ParallelDownloadPolicy.parseRetryAfterMillis(retryAfter),
                        )
                        rateLimited++
                        if (!sleep(wait)) return
                        continue
                    }
                    // A redirect target can expire mid-playback; the add-on's URL issues a fresh one.
                    if (response != null && response.responseCode in 400..499 && !useRequestUri &&
                        resolvedUri != null && resolvedUri != key.uri
                    ) {
                        useRequestUri = true
                        continue
                    }
                    errors++
                    if (errors >= ParallelDownloadPolicy.DOWNLOAD_ATTEMPTS) {
                        chunk.fail(generation, error)
                        return
                    }
                }
            }
        } finally {
            ParallelDownloadStats.activeDownloads.decrementAndGet()
            chunk.release()
        }
    }

    private fun transfer(chunk: Chunk, generation: Int, uri: Uri, checkStalls: Boolean) {
        val from = chunk.start + chunk.written
        if (from >= chunk.end) {
            chunk.finish(generation)
            return
        }
        val source = upstream.createDataSource()
        try {
            source.open(
                DataSpec.Builder()
                    .setUri(uri)
                    .setHttpRequestHeaders(key.headers)
                    .setPosition(from)
                    .setLength(chunk.end - from)
                    .build(),
            )
            val scratch = SCRATCH.get()!!
            val stallRate = ParallelDownloadPolicy.stallRateBytesPerSecond(usenet)
            val stallWindows = ParallelDownloadPolicy.stallWindows(usenet)
            val openedAt = SystemClock.elapsedRealtime()
            var windowStart = openedAt
            var windowBytes = 0L
            var slowWindows = 0
            while (chunk.written < chunk.length) {
                val wanted = minOf(scratch.size, chunk.length - chunk.written)
                val read = source.read(scratch, 0, wanted)
                if (read == C.RESULT_END_OF_INPUT) throw IOException("Chunk ended early")
                if (!chunk.append(generation, scratch, read)) return
                if (!checkStalls) continue
                windowBytes += read
                val now = SystemClock.elapsedRealtime()
                if (now - openedAt >= ParallelDownloadPolicy.STALL_MIN_OPEN_MS &&
                    now - windowStart >= ParallelDownloadPolicy.STALL_WINDOW_MS
                ) {
                    val rate = windowBytes * 1_000L / (now - windowStart)
                    slowWindows = if (rate < stallRate) slowWindows + 1 else 0
                    if (slowWindows >= stallWindows) throw StalledChunkException()
                    windowStart = now
                    windowBytes = 0L
                }
            }
            chunk.finish(generation)
        } finally {
            runCatching { source.close() }
        }
    }

    private fun sleep(millis: Long): Boolean {
        var left = millis
        while (left > 0) {
            if (abandoned) return false
            val slice = minOf(left, 100L)
            try {
                Thread.sleep(slice)
            } catch (_: InterruptedException) {
                return false
            }
            left -= slice
        }
        return !abandoned
    }

    private class StalledChunkException : IOException("Chunk download stalled")

    private companion object {
        val SCRATCH = ThreadLocal.withInitial { ByteArray(64 * 1024) }

        val DOWNLOADS = ThreadPoolExecutor(32, 32, 30, TimeUnit.SECONDS, LinkedBlockingQueue()) { runnable ->
            Thread(runnable, "LamphausRangeDownload").apply { isDaemon = true }
        }.apply { allowCoreThreadTimeOut(true) }

        fun Throwable.isInterruption(): Boolean =
            generateSequence(this) { it.cause }.any { it is InterruptedIOException || it is InterruptedException }

        fun Throwable.responseCode(): HttpDataSource.InvalidResponseCodeException? =
            generateSequence(this) { it.cause }.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()
    }
}

/**
 * One chunk's bytes. A download appends under the lock and readers consume
 * up to the watermark as it rises. The buffer returns to the pool only once
 * the chunk is evicted and neither a reader nor a download still holds it.
 */
internal class Chunk(
    val index: Long,
    val start: Long,
    val end: Long,
    val buffer: ChunkBuffer,
    private val memory: ChunkMemory,
) {
    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private var holders = 0
    private var evicted = false
    private var generation = 0
    private var restartedByReader = false

    val length: Int = (end - start).toInt()

    @Volatile var written = 0
        private set

    @Volatile var failure: IOException? = null
        private set

    @Volatile var touchedAt = SystemClock.elapsedRealtime()
        private set

    @Volatile var task: Future<*>? = null

    val failed: Boolean get() = failure != null

    fun touch() {
        touchedAt = SystemClock.elapsedRealtime()
    }

    fun retain(): Boolean = lock.withLock {
        if (evicted) return false
        holders++
        true
    }

    fun release() {
        val recycle = lock.withLock {
            holders--
            evicted && holders == 0
        }
        if (recycle) memory.recycle(buffer)
    }

    fun evict() {
        val recycle = lock.withLock {
            if (evicted) return
            evicted = true
            generation++
            changed.signalAll()
            holders == 0
        }
        task?.cancel(true)
        if (recycle) memory.recycle(buffer)
    }

    /** A new download takes over; any older one stops at its next append. */
    fun nextGeneration(): Int = lock.withLock {
        task?.cancel(true)
        failure = null
        ++generation
    }

    fun isCurrent(generation: Int): Boolean = lock.withLock { !evicted && this.generation == generation }

    fun append(generation: Int, source: ByteArray, count: Int): Boolean = lock.withLock {
        if (evicted || this.generation != generation) return false
        buffer.write(written, source, 0, count)
        written += count
        changed.signalAll()
        true
    }

    fun finish(generation: Int) = lock.withLock {
        if (this.generation == generation) changed.signalAll()
    }

    fun fail(generation: Int, error: IOException) = lock.withLock {
        if (this.generation != generation) return@withLock
        failure = error
        changed.signalAll()
    }

    /**
     * Bytes readable at [offset], waiting for the download when none are yet.
     * Returns -1 when the chunk failed. After 2 s without progress the reader
     * asks for a fresh connection once ([restart]); after 60 s it gives up.
     */
    fun awaitData(offset: Int, restart: () -> Unit): Int {
        written.let { if (it > offset) return it - offset }
        var restartNow = false
        val result = lock.withLock {
            var lastWritten = written
            var lastProgressAt = SystemClock.elapsedRealtime()
            while (true) {
                if (written > offset) return@withLock written - offset
                if (failure != null || evicted) return@withLock -1
                val now = SystemClock.elapsedRealtime()
                if (written != lastWritten) {
                    lastWritten = written
                    lastProgressAt = now
                }
                val stalledFor = now - lastProgressAt
                if (stalledFor >= ParallelDownloadPolicy.READER_GIVE_UP_MS) {
                    failure = IOException("No data for ${stalledFor / 1_000} s")
                    return@withLock -1
                }
                if (!restartedByReader && stalledFor >= ParallelDownloadPolicy.READER_STALL_RESTART_MS) {
                    restartedByReader = true
                    restartNow = true
                    return@withLock 0
                }
                try {
                    changed.await(WAIT_SLICE_MS, TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw InterruptedIOException("Interrupted while waiting for chunk data")
                }
            }
            @Suppress("UNREACHABLE_CODE")
            0
        }
        if (restartNow) {
            restart()
            return awaitData(offset, restart)
        }
        return result
    }

    private companion object {
        const val WAIT_SLICE_MS = 250L
    }
}
