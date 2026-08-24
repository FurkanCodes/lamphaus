package com.lamphaus.app.ui

import com.lamphaus.core.model.StreamCandidate
import java.net.URI
import java.net.URLEncoder

internal sealed interface SourceResolution {
    data class Internal(val url: String) : SourceResolution
    data class External(val uri: String) : SourceResolution
    data class Unsupported(val message: String) : SourceResolution
}

internal fun resolveSource(source: StreamCandidate, allowDebugLocalhost: Boolean): SourceResolution {
    source.url?.let { url ->
        if (url.startsWith("https://", ignoreCase = true) || (allowDebugLocalhost && url.isDebugLocalStream())) {
            return SourceResolution.Internal(url)
        }
    }
    source.externalUrl?.let { return it.toExternalResolution() }
    source.ytId?.let { return SourceResolution.External("https://youtu.be/${it.urlEncode()}") }
    source.infoHash?.let { hash ->
        val parameters = buildList {
            add("xt=urn:btih:${hash.urlEncode()}")
            source.fileIndex?.let { add("so=$it") }
            source.sourceUrls.forEach { tracker ->
                val trackerUrl = when {
                    tracker.startsWith("tracker:", ignoreCase = true) -> tracker.substringAfter(':')
                    tracker.startsWith("http://", ignoreCase = true) ||
                        tracker.startsWith("https://", ignoreCase = true) ||
                        tracker.startsWith("udp://", ignoreCase = true) -> tracker
                    else -> null
                }
                trackerUrl?.takeIf(::isSafeExternalUri)?.let { add("tr=${it.urlEncode()}") }
            }
        }
        return SourceResolution.External("magnet:?${parameters.joinToString("&")}")
    }
    source.url?.let { return it.toExternalResolution() }
    source.nzbUrl?.let { return it.toExternalResolution() }
    source.archiveFiles.firstOrNull()?.url?.let { return it.toExternalResolution() }
    return SourceResolution.Unsupported("This source needs a compatible external player.")
}

internal fun sourceItemKey(source: StreamCandidate, index: Int): String = listOf(
    index,
    source.providerId,
    source.url,
    source.externalUrl,
    source.infoHash,
    source.fileIndex,
    source.ytId,
    source.nzbUrl,
    source.archiveFiles.firstOrNull()?.url,
).joinToString(":")

internal fun isSafeExternalUri(value: String): Boolean {
    val scheme = runCatching { URI(value).scheme?.lowercase() }.getOrNull() ?: return false
    return scheme !in setOf("file", "content", "javascript", "data", "intent", "android-app", "package")
}

private fun String.toExternalResolution(): SourceResolution = if (isSafeExternalUri(this)) {
    SourceResolution.External(this)
} else {
    SourceResolution.Unsupported("This source uses an unsafe or unsupported address.")
}

private fun String.isDebugLocalStream(): Boolean {
    val uri = runCatching { URI(this) }.getOrNull() ?: return false
    return uri.scheme.equals("http", ignoreCase = true) && uri.host in setOf("localhost", "127.0.0.1", "10.0.2.2")
}

private fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8").replace("+", "%20")
