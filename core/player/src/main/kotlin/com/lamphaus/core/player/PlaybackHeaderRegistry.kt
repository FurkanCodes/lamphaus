package com.lamphaus.core.player

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

object PlaybackHeaderRegistry {
    private val values = ConcurrentHashMap<String, Map<String, String>>()
    private val active = AtomicReference<ActiveHeaders?>()

    fun begin(uri: String, headers: Map<String, String>) {
        active.set(ActiveHeaders(uri, headers))
    }

    fun put(uri: String, headers: Map<String, String>) {
        if (headers.isNotEmpty()) values[uri] = headers
    }

    fun get(uri: String): Map<String, String> = active.get()?.headers.orEmpty() + values[uri].orEmpty()

    fun end(uri: String, auxiliaryUris: Collection<String> = emptyList()) {
        active.compareAndSet(active.get()?.takeIf { it.uri == uri }, null)
        auxiliaryUris.forEach(values::remove)
    }

    private data class ActiveHeaders(val uri: String, val headers: Map<String, String>)
}
