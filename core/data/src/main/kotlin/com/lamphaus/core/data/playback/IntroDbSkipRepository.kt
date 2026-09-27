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
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

/**
 * Public, read-only timestamp lookup, following Nuvio: IntroDB (introdb.app)
 * supplies intro, recap, outro, and movie post-credits segments; TheIntroDB
 * fills any category IntroDB lacks. Missing data and network failures are a
 * normal empty result: playback must remain fully functional without them.
 */
class IntroDbSkipRepository(
    private val client: HttpClient = HttpClient(OkHttp) {
        install(HttpTimeout)
    },
    private val baseUrl: String = "https://api.theintrodb.org/v3/media",
    private val introDbUrl: String = "https://api.introdb.app/segments",
) {
    private val cache = ConcurrentHashMap<String, List<PlaybackSegment>>()

    suspend fun segments(media: MediaPreview, episode: Episode?): List<PlaybackSegment> {
        val identifier = IntroDbIdentifier.from(media.id) ?: return emptyList()
        val movie = media.type != MediaType.SERIES
        if (!movie && (episode?.season == null || episode.episode == null)) {
            return emptyList()
        }
        val cacheKey = "${identifier::class.simpleName}:${identifier.value}:${episode?.season}:${episode?.episode}"
        cache[cacheKey]?.let { return it }
        val (primary, fallback) = coroutineScope {
            val introDb = async {
                if (identifier is IntroDbIdentifier.Imdb) fetchIntroDb(identifier.value, episode, movie) else null
            }
            val theIntroDb = async { fetchTheIntroDb(identifier, episode, movie) }
            introDb.await() to theIntroDb.await()
        }
        // Do not cache transient failures; a later playback can retry.
        if (primary == null && fallback == null) return emptyList()
        return mergeSegmentsByPriority(primary.orEmpty(), fallback.orEmpty())
            .also { cache[cacheKey] = it }
    }

    private suspend fun fetchIntroDb(imdbId: String, episode: Episode?, movie: Boolean): List<PlaybackSegment>? =
        runCatching {
            val response = client.get(introDbUrl) {
                requestTimeout()
                parameter("imdb_id", imdbId)
                if (movie) {
                    parameter("is_movie", true)
                } else {
                    parameter("season", checkNotNull(episode?.season))
                    parameter("episode", checkNotNull(episode.episode))
                }
            }
            if (response.status == HttpStatusCode.NotFound) return@runCatching emptyList()
            if (response.status != HttpStatusCode.OK) return@runCatching null
            JSON.decodeFromString<IntroDbSegmentsResponse>(response.bodyAsText()).toSegments(movie)
        }.getOrNull()

    private suspend fun fetchTheIntroDb(
        identifier: IntroDbIdentifier,
        episode: Episode?,
        movie: Boolean,
    ): List<PlaybackSegment>? = runCatching {
        val response = client.get(baseUrl) {
            requestTimeout()
            when (identifier) {
                is IntroDbIdentifier.Imdb -> parameter("imdb_id", identifier.value)
                is IntroDbIdentifier.Tmdb -> parameter("tmdb_id", identifier.value)
                is IntroDbIdentifier.Tvdb -> parameter("tvdb_id", identifier.value)
            }
            if (!movie) {
                parameter("season", checkNotNull(episode?.season))
                parameter("episode", checkNotNull(episode.episode))
            }
        }
        if (response.status == HttpStatusCode.NotFound) return@runCatching emptyList()
        if (response.status != HttpStatusCode.OK) return@runCatching null
        val payload = JSON.decodeFromString<TheIntroDbMediaResponse>(response.bodyAsText())
        buildList {
            payload.intro.orEmpty().mapNotNullTo(this) { it.toSegment(PlaybackSegmentType.INTRO) }
            payload.credits.orEmpty().mapNotNullTo(this) { it.toSegment(PlaybackSegmentType.ENDING) }
        }.distinct().sortedBy(PlaybackSegment::startMillis)
    }.getOrNull()

    private fun HttpRequestBuilder.requestTimeout() {
        timeout {
            connectTimeoutMillis = REQUEST_TIMEOUT_MILLIS
            requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
            socketTimeoutMillis = REQUEST_TIMEOUT_MILLIS
        }
    }

    private fun TheIntroDbTimestamp.toSegment(type: PlaybackSegmentType): PlaybackSegment? {
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

/**
 * Nuvio's best-of merge: each category (intro, recap, ending) comes from the
 * highest-priority source that has it, so a partial result never shadows a
 * complete segment elsewhere. Post-credits scenes only come from [primary].
 */
internal fun mergeSegmentsByPriority(
    primary: List<PlaybackSegment>,
    fallback: List<PlaybackSegment>,
): List<PlaybackSegment> {
    val categories = listOf(PlaybackSegmentType.INTRO, PlaybackSegmentType.RECAP, PlaybackSegmentType.ENDING)
    return buildList {
        categories.forEach { type ->
            addAll(primary.filter { it.type == type }.ifEmpty { fallback.filter { it.type == type } })
        }
        addAll(primary.filter { it.type == PlaybackSegmentType.POST_CREDITS })
    }.distinct().sortedBy(PlaybackSegment::startMillis)
}

/** Movie credits map to ENDING, trimmed so skipping them never skips the post-credits scene. */
internal fun IntroDbSegmentsResponse.toSegments(movie: Boolean): List<PlaybackSegment> {
    if (!movie) {
        return listOfNotNull(
            intro.toSegmentOrNull(PlaybackSegmentType.INTRO),
            recap.toSegmentOrNull(PlaybackSegmentType.RECAP),
            outro.toSegmentOrNull(PlaybackSegmentType.ENDING),
        )
    }
    val credits = outro.toSegmentOrNull(PlaybackSegmentType.ENDING)
    val scene = postCredits.toSegmentOrNull(PlaybackSegmentType.POST_CREDITS)
    val creditsEnd = credits?.endMillis
    val safeCredits = if (credits != null && scene != null && creditsEnd != null &&
        scene.startMillis < creditsEnd && (scene.endMillis ?: Long.MAX_VALUE) > credits.startMillis
    ) {
        credits.copy(endMillis = scene.startMillis).takeIf { scene.startMillis > it.startMillis }
    } else {
        credits
    }
    return listOfNotNull(safeCredits, scene)
}

private fun IntroDbSegment?.toSegmentOrNull(type: PlaybackSegmentType): PlaybackSegment? {
    if (this == null) return null
    val start = startMillis ?: startSeconds?.let { (it * 1_000).toLong() } ?: return null
    val end = endMillis ?: endSeconds?.let { (it * 1_000).toLong() } ?: return null
    if (start < 0 || end <= start) return null
    return PlaybackSegment(type = type, startMillis = start, endMillis = end)
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
internal data class IntroDbSegmentsResponse(
    val intro: IntroDbSegment? = null,
    val recap: IntroDbSegment? = null,
    val outro: IntroDbSegment? = null,
    @SerialName("post_credits") val postCredits: IntroDbSegment? = null,
)

@Serializable
internal data class IntroDbSegment(
    @SerialName("start_sec") val startSeconds: Double? = null,
    @SerialName("end_sec") val endSeconds: Double? = null,
    @SerialName("start_ms") val startMillis: Long? = null,
    @SerialName("end_ms") val endMillis: Long? = null,
)

@Serializable
private data class TheIntroDbMediaResponse(
    val intro: List<TheIntroDbTimestamp>? = null,
    val credits: List<TheIntroDbTimestamp>? = null,
)

@Serializable
private data class TheIntroDbTimestamp(
    @SerialName("start_ms") val startMillis: Long? = null,
    @SerialName("end_ms") val endMillis: Long? = null,
)
