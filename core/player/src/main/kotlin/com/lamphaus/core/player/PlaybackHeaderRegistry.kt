package com.lamphaus.core.player

import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

object PlaybackHeaderRegistry {
    private val values = ConcurrentHashMap<String, Map<String, String>>()
    private val active = AtomicReference<ActiveHeaders?>()

    /** [mimeType] tells HLS, DASH, and Smooth Streaming apart from progressive files. */
    @androidx.annotation.OptIn(UnstableApi::class)
    fun begin(uri: String, headers: Map<String, String>, mimeType: String? = null) {
        val progressive = runCatching {
            Util.inferContentTypeForUriAndMimeType(uri.toUri(), mimeType) == C.CONTENT_TYPE_OTHER
        }.getOrDefault(false)
        active.set(ActiveHeaders(uri, headers, progressive))
    }

    fun put(uri: String, headers: Map<String, String>) {
        if (headers.isNotEmpty()) values[uri] = headers
    }

    fun get(uri: String): Map<String, String> = active.get()?.headers.orEmpty() + values[uri].orEmpty()

    /** True for the active playback's own progressive stream, the only one parallel downloads apply to. */
    fun isProgressiveStream(uri: String): Boolean = active.get()?.let { it.progressive && it.uri == uri } == true

    fun end(uri: String, auxiliaryUris: Collection<String> = emptyList()) {
        active.compareAndSet(active.get()?.takeIf { it.uri == uri }, null)
        auxiliaryUris.forEach(values::remove)
    }

    private data class ActiveHeaders(val uri: String, val headers: Map<String, String>, val progressive: Boolean)
}
