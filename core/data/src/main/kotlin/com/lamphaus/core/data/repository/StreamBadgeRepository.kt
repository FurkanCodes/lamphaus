package com.lamphaus.core.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * One badge rule from a Nuvio-compatible `badges.json` ("Fusion" badges).
 * [pattern] is a Java regex run over the stream's display text; a match adds
 * the badge. Colors are `#AARRGGBB` or `#RRGGBB`.
 */
@Serializable
data class StreamBadgeFilter(
    val id: String = "",
    val groupId: String = "",
    val name: String = "",
    val pattern: String = "",
    val imageURL: String = "",
    val isEnabled: Boolean = true,
    val tagColor: String = "",
    val tagStyle: String = "",
    val textColor: String = "",
    val borderColor: String = "",
)

@Serializable
data class StreamBadgeImport(
    val sourceUrl: String,
    val filters: List<StreamBadgeFilter>,
) {
    val enabledFilterCount: Int get() = filters.count { it.isEnabled }
}

/**
 * Owns the imported stream-badge rules (SHR-ARC-04, SHR-ARC-05). The rules are
 * downloaded once on import and kept on the device, so the source list never
 * waits on the badge host.
 */
class StreamBadgeRepository(private val context: Context) {
    val rules: Flow<StreamBadgeImport?> = context.badgeStore.data.map { values ->
        values[RULES]?.let { runCatching { json.decodeFromString<StreamBadgeImport>(it) }.getOrNull() }
    }

    /** Downloads, validates, and stores the badge file at [url], replacing any previous import. */
    suspend fun import(url: String): Result<StreamBadgeImport> = withContext(Dispatchers.IO) {
        runCatching {
            val normalized = url.trim()
            require(normalized.startsWith("https://", ignoreCase = true)) { "Use an HTTPS badge address." }
            val parsed = StreamBadgeRulesParser.parse(normalized, download(normalized))
            context.badgeStore.edit { it[RULES] = json.encodeToString(StreamBadgeImport.serializer(), parsed) }
            parsed
        }
    }

    suspend fun remove() {
        context.badgeStore.edit { it.remove(RULES) }
    }

    private fun download(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.instanceFollowRedirects = true
            val code = connection.responseCode
            require(code in 200..299) { "The badge address answered $code." }
            val bytes = connection.inputStream.use { input ->
                val buffer = input.readNBytesCompat(MAX_BYTES + 1)
                require(buffer.size <= MAX_BYTES) { "The badge file is too large." }
                buffer
            }
            return bytes.decodeToString()
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        val RULES = stringPreferencesKey("stream_badge_rules")
        const val TIMEOUT_MILLIS = 15_000
        const val MAX_BYTES = 1_000_000
        val json = Json { ignoreUnknownKeys = true }
    }
}

private val Context.badgeStore by preferencesDataStore("lamphaus_stream_badges")

private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val chunk = ByteArray(8 * 1024)
    while (out.size() < limit) {
        val read = read(chunk, 0, minOf(chunk.size, limit - out.size()))
        if (read < 0) break
        out.write(chunk, 0, read)
    }
    return out.toByteArray()
}

/** Parses the Nuvio badge format: `{ "groups": [...], "filters": [...] }`. */
object StreamBadgeRulesParser {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }

    @Serializable
    private data class Payload(val filters: List<FilterPayload> = emptyList())

    @Serializable
    private data class FilterPayload(
        val id: String? = null,
        val groupId: String? = null,
        val name: String? = null,
        val pattern: String? = null,
        val imageURL: String? = null,
        val isEnabled: Boolean? = null,
        val tagColor: String? = null,
        val tagStyle: String? = null,
        val textColor: String? = null,
        val borderColor: String? = null,
    )

    fun parse(sourceUrl: String, payload: String): StreamBadgeImport {
        val decoded = try {
            json.decodeFromString<Payload>(payload)
        } catch (error: SerializationException) {
            throw IllegalArgumentException("That address is not a badge file.", error)
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("That address is not a badge file.", error)
        }
        val filters = decoded.filters.asSequence()
            .mapNotNull { filter ->
                val name = filter.name.orEmpty().trim()
                val pattern = filter.pattern.orEmpty().trim()
                // Unusable or oversized rules are dropped rather than failing the import.
                if (name.isBlank() || pattern.isBlank() || pattern.length > MAX_PATTERN_LENGTH) return@mapNotNull null
                if (runCatching { Regex(pattern) }.isFailure) return@mapNotNull null
                StreamBadgeFilter(
                    id = filter.id.orEmpty(),
                    groupId = filter.groupId.orEmpty(),
                    name = name,
                    pattern = pattern,
                    imageURL = filter.imageURL.orEmpty().trim().takeIf { it.startsWith("https://", ignoreCase = true) }.orEmpty(),
                    isEnabled = filter.isEnabled ?: true,
                    tagColor = filter.tagColor.orEmpty(),
                    tagStyle = filter.tagStyle.orEmpty(),
                    textColor = filter.textColor.orEmpty(),
                    borderColor = filter.borderColor.orEmpty(),
                )
            }
            .take(MAX_FILTERS)
            .toList()
        require(filters.isNotEmpty()) { "The badge file has no usable badges." }
        return StreamBadgeImport(sourceUrl = sourceUrl, filters = filters)
    }

    private const val MAX_FILTERS = 400
    private const val MAX_PATTERN_LENGTH = 500
}
