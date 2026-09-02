package com.lamphaus.core.data.playback

import com.lamphaus.core.model.Episode
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.MediaType
import com.lamphaus.core.model.PlaybackSegment
import com.lamphaus.core.model.PlaybackSegmentType
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/**
 * Public, read-only timestamp lookup. Missing data and network failures are a normal empty result:
 * playback must remain fully functional when a title is not represented in TheIntroDB.
 */
class IntroDbSkipRepository(
    private val client: HttpClient = HttpClient(OkHttp) {
        install(HttpTimeout)
    },
    private val baseUrl: String = "https://api.theintrodb.org/v3/media",
) {
    private val cache = ConcurrentHashMap<String, List<PlaybackSegment>>()

    suspend fun segments(media: MediaPreview, episode: Episode?): List<PlaybackSegment> {
        val identifier = IntroDbIdentifier.from(media.id) ?: return emptyList()
        if (media.type == MediaType.SERIES && (episode?.season == null || episode.episode == null)) {
            return emptyList()
        }
        val cacheKey = "${identifier::class.simpleName}:${identifier.value}:${episode?.season}:${episode?.episode}"
        cache[cacheKey]?.let { return it }
        return runCatching {
            val response = client.get(baseUrl) {
                timeout {
                    connectTimeoutMillis = REQUEST_TIMEOUT_MILLIS
                    requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
                    socketTimeoutMillis = REQUEST_TIMEOUT_MILLIS
                }
                when (identifier) {
                    is IntroDbIdentifier.Imdb -> parameter("imdb_id", identifier.value)
                    is IntroDbIdentifier.Tmdb -> parameter("tmdb_id", identifier.value)
                    is IntroDbIdentifier.Tvdb -> parameter("tvdb_id", identifier.value)
                }
                if (media.type == MediaType.SERIES) {
                    parameter("season", checkNotNull(episode?.season))
                    parameter("episode", checkNotNull(episode.episode))
                }
            }
            if (response.status != HttpStatusCode.OK) return@runCatching emptyList()
            val payload = JSON.decodeFromString<IntroDbMediaResponse>(response.bodyAsText())
            buildList {
                payload.intro.orEmpty().mapNotNullTo(this) { it.toSegment(PlaybackSegmentType.INTRO) }
                payload.credits.orEmpty().mapNotNullTo(this) { it.toSegment(PlaybackSegmentType.ENDING) }
            }.distinct().sortedBy(PlaybackSegment::startMillis).also { cache[cacheKey] = it }
        }.getOrElse {
            // Do not cache transient failures; a later playback can retry.
            emptyList()
        }
    }

    private fun IntroDbTimestamp.toSegment(type: PlaybackSegmentType): PlaybackSegment? {
        val start = (startMillis ?: 0L).coerceAtLeast(0L)
        val end = endMillis?.coerceAtLeast(0L)
        if (end != null && end <= start) return null
        if (type == PlaybackSegmentType.INTRO && end == null) return null
        if (type == PlaybackSegmentType.ENDING && start == 0L) return null
        return PlaybackSegment(type = type, startMillis = start, endMillis = end)
    }

    private companion object {
        const val REQUEST_TIMEOUT_MILLIS = 3_500L
        val JSON = Json { ignoreUnknownKeys = true }
    }
}

internal sealed interface IntroDbIdentifier {
    val value: String

    data class Imdb(override val value: String) : IntroDbIdentifier
    data class Tmdb(override val value: String) : IntroDbIdentifier
    data class Tvdb(override val value: String) : IntroDbIdentifier

    companion object {
        private val imdbPattern = Regex("tt[0-9]{7,8}", RegexOption.IGNORE_CASE)
        private val tmdbPattern = Regex("(?:^|[:/_-])tmdb[:/_-](?:tv[:/_-]|movie[:/_-])?([0-9]{1,8})(?:$|[:/_-])", RegexOption.IGNORE_CASE)
        private val tvdbPattern = Regex("(?:^|[:/_-])tvdb[:/_-]([0-9]{1,8})(?:$|[:/_-])", RegexOption.IGNORE_CASE)

        fun from(rawId: String): IntroDbIdentifier? {
            imdbPattern.find(rawId)?.value?.lowercase()?.let { return Imdb(it) }
            tmdbPattern.find(rawId)?.groupValues?.getOrNull(1)?.let { return Tmdb(it) }
            tvdbPattern.find(rawId)?.groupValues?.getOrNull(1)?.let { return Tvdb(it) }
            return null
        }
    }
}

@Serializable
private data class IntroDbMediaResponse(
    val intro: List<IntroDbTimestamp>? = null,
    val credits: List<IntroDbTimestamp>? = null,
)

@Serializable
private data class IntroDbTimestamp(
    @SerialName("start_ms") val startMillis: Long? = null,
    @SerialName("end_ms") val endMillis: Long? = null,
)
