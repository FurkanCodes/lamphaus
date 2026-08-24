package com.lamphaus.core.player

import java.util.concurrent.ConcurrentHashMap

object PlaybackHeaderRegistry {
    private val values = ConcurrentHashMap<String, Map<String, String>>()

    fun put(uri: String, headers: Map<String, String>) {
        if (headers.isNotEmpty()) values[uri] = headers
    }

    fun get(uri: String): Map<String, String> = values[uri].orEmpty()

    fun remove(uri: String) {
        values.remove(uri)
    }
}

