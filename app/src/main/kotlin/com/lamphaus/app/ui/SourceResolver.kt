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

/**
 * Stable, unique list keys for [sources]. Keys do not depend on list position,
 * so when a slower add-on's sources arrive above the viewport the lazy list
 * keeps the focused and first visible rows in place (TV-FOC-01). True
 * duplicates within one add-on get an occurrence suffix; that order is fixed
 * by the add-on, so the suffix is stable too.
 */
internal fun sourceItemKeys(sources: List<StreamCandidate>): List<String> {
    val occurrences = HashMap<String, Int>()
    return sources.map { source ->
        val identity = listOf(
            source.providerId,
            source.url,
            source.externalUrl,
            source.infoHash,
            source.fileIndex,
            source.ytId,
            source.nzbUrl,
            source.archiveFiles.firstOrNull()?.url,
            source.name,
            source.title,
        ).joinToString(":")
        val occurrence = occurrences.merge(identity, 1, Int::plus)!!
        "$identity#$occurrence"
    }
}

/** How one add-on answered a source request. */
internal sealed interface SourceProviderOutcome {
    data class Streams(val streams: List<StreamCandidate>) : SourceProviderOutcome
    data class Failed(val message: String, val supportsStreams: Boolean) : SourceProviderOutcome
    data object Unsupported : SourceProviderOutcome
}

/**
 * Sources from the add-ons that have answered so far, in add-on order, so
 * the final list equals the order shown when every add-on had answered.
 */
internal fun mergeSourcesInProviderOrder(
    providerOrder: List<String>,
    outcomes: Map<String, SourceProviderOutcome>,
): List<StreamCandidate> = providerOrder.flatMap { id ->
    (outcomes[id] as? SourceProviderOutcome.Streams)?.streams.orEmpty()
}

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
