package com.lamphaus.core.player

import com.lamphaus.core.model.SubtitleCue
import com.lamphaus.core.model.SubtitleCharset
import com.lamphaus.core.model.SubtitleCueParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches a text sidecar subtitle with its per-source headers (plan §4),
 * retries once safely, detects charset, and parses cues. Errors surface as
 * null so the timing panel can offer Retry without crashing playback; URLs
 * and headers are never logged (SHR-PROD-06).
 */
class SidecarSubtitleLoader(
    private val headerSource: (url: String) -> Map<String, String> = { PlaybackHeaderRegistry.get(it) },
) {

    suspend fun load(url: String): List<SubtitleCue>? = withContext(Dispatchers.IO) {
        fetch(url) ?: fetch(url) // one safe retry
    }

    private fun fetch(url: String): List<SubtitleCue>? = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        headerSource(url).forEach { (name, value) -> connection.setRequestProperty(name, value) }
        if (connection.responseCode !in 200..299) {
            connection.disconnect()
            return@runCatching null
        }
        val bytes = connection.inputStream.use { it.readBytes() }
        connection.disconnect()
        SubtitleCueParser.parse(SubtitleCharset.decode(bytes)).takeIf(List<SubtitleCue>::isNotEmpty)
    }.getOrNull()
}
