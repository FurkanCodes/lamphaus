package com.lamphaus.core.provider

import java.net.URI

class ProviderUrlPolicy(
    private val allowDebugLocalhost: Boolean,
) {
    fun normalizeManifestUrl(input: String): String? {
        val trimmed = input.trim()
        val uri = runCatching { URI(trimmed) }.getOrNull()?.toTransportUri() ?: return null
        val allowedHttp = allowDebugLocalhost &&
            uri.scheme.equals("http", ignoreCase = true) &&
            uri.host in setOf("localhost", "127.0.0.1", "10.0.2.2")
        if (!uri.scheme.equals("https", ignoreCase = true) && !allowedHttp) return null
        if (uri.userInfo != null || uri.host.isNullOrBlank()) return null
        val path = uri.path.orEmpty()
        val manifestPath = if (path.endsWith("/manifest.json")) path else path.trimEnd('/') + "/manifest.json"
        return URI(uri.scheme, null, uri.host, uri.port, manifestPath, uri.query, null).toString()
    }

    fun normalizeCatalogUrl(input: String): String? {
        val trimmed = input.trim()
        val uri = runCatching { URI(trimmed) }.getOrNull()?.toTransportUri() ?: return null
        val allowedHttp = allowDebugLocalhost &&
            uri.scheme.equals("http", ignoreCase = true) &&
            uri.host in setOf("localhost", "127.0.0.1", "10.0.2.2")
        if (!uri.scheme.equals("https", ignoreCase = true) && !allowedHttp) return null
        if (uri.userInfo != null || uri.host.isNullOrBlank()) return null
        return URI(uri.scheme, null, uri.host, uri.port, uri.path, uri.query, null).toString()
    }

    /** Converts compatible custom-scheme install links while keeping all network traffic HTTPS. */
    private fun URI.toTransportUri(): URI? {
        if (!scheme.equals("http", ignoreCase = true) && !scheme.equals("https", ignoreCase = true)) {
            if (host.isNullOrBlank()) return null
            return URI("https", null, host, port, path, query, null)
        }
        return this
    }
}
