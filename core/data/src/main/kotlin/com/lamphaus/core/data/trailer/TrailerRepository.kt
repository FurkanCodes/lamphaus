package com.lamphaus.core.data.trailer

import com.lamphaus.core.model.TrailerSource
import java.util.concurrent.ConcurrentHashMap
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Resolves a title's trailer ids to a playable source, trying the ids in
 * order. Resolved streams are reused until their signed URLs expire and a
 * failed id is not retried for a while, so moving focus back to a card
 * restarts its trailer without another extraction.
 */
class TrailerRepository(
    private val extractor: TrailerExtractor = YouTubeTrailerExtractor(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val resolved = ConcurrentHashMap<String, Resolved>()
    private val missUntil = ConcurrentHashMap<String, Long>()

    /** [refresh] ignores both caches: a viewer's retry after a failed stream. */
    suspend fun source(ytIds: List<String>, maxHeight: Int, refresh: Boolean = false): TrailerSource? {
        for (id in ytIds.distinct().take(MAX_CANDIDATES)) {
            val key = "$id@$maxHeight"
            resolved[key]?.let { cached ->
                if (!refresh && cached.expiresAtMillis > now()) return cached.source
                resolved.remove(key, cached)
            }
            if (!refresh && (missUntil[key] ?: 0L) > now()) continue
            val source = extractor.extract(id, maxHeight)
            if (source != null) {
                resolved[key] = Resolved(source, expiresAtMillis(source.videoUrl, now()))
                return source
            }
            missUntil[key] = now() + MISS_TTL_MILLIS
        }
        return null
    }

    /** Drops [source] from the cache, so the next request extracts its id again. */
    fun forget(source: TrailerSource) {
        resolved.entries.removeIf { it.value.source == source }
    }

    private data class Resolved(val source: TrailerSource, val expiresAtMillis: Long)

    internal companion object {
        const val MAX_CANDIDATES = 3
        const val MISS_TTL_MILLIS = 30L * 60 * 1000
        const val DEFAULT_TTL_MILLIS = 3L * 60 * 60 * 1000
        const val EXPIRY_MARGIN_MILLIS = 5L * 60 * 1000

        /** Signed googlevideo URLs carry `expire` (epoch seconds); keep a margin before it. */
        fun expiresAtMillis(url: String, nowMillis: Long): Long {
            val expire = url.toHttpUrlOrNull()?.queryParameter("expire")?.toLongOrNull()
                ?: Regex("/expire/(\\d+)/").find(url)?.groupValues?.get(1)?.toLongOrNull()
            return expire?.let { it * 1000 - EXPIRY_MARGIN_MILLIS } ?: (nowMillis + DEFAULT_TTL_MILLIS)
        }
    }
}
