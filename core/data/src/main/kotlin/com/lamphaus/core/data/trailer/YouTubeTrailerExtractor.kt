package com.lamphaus.core.data.trailer

import com.lamphaus.core.model.TrailerSource
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Turns a YouTube video id into a playable [TrailerSource]; null when it cannot. */
fun interface TrailerExtractor {
    suspend fun extract(videoId: String, maxHeight: Int): TrailerSource?
}

/**
 * In-app YouTube stream extraction, ported from NuvioTV's
 * `InAppYouTubeExtractor`: ask the innertube player endpoint as the visionOS,
 * Android and iOS clients, then prefer separate adaptive video and audio,
 * falling back to the HLS manifest and finally a muxed progressive stream.
 *
 * Nothing here is logged: stream URLs are account-free but still
 * provider-sensitive (SHR-PROD-06).
 */
class YouTubeTrailerExtractor(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val now: () -> Long = System::currentTimeMillis,
) : TrailerExtractor {
    private val probeClient by lazy {
        client.newBuilder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .build()
    }
    private val configLock = Mutex()

    @Volatile
    private var config: WatchConfig? = null

    override suspend fun extract(videoId: String, maxHeight: Int): TrailerSource? {
        if (!YOUTUBE_ID.matches(videoId)) return null
        return withContext(Dispatchers.IO) {
            // A stale visitor id is the usual failure; retry once with a fresh one.
            attempt(videoId, maxHeight, refreshConfig = false)
                ?: attempt(videoId, maxHeight, refreshConfig = true)
        }
    }

    private suspend fun attempt(videoId: String, maxHeight: Int, refreshConfig: Boolean): TrailerSource? = try {
        withTimeout(EXTRACT_TIMEOUT_MILLIS) {
            val config = watchConfig(refreshConfig)
            val players = CLIENTS.mapNotNull { youTubeClient ->
                ensureActive()
                runCatching { playerResponse(config, videoId, youTubeClient) }
                    .onFailure { if (it is CancellationException) throw it }
                    .getOrNull()
            }
            if (players.isNotEmpty() && players.all { it.status == "LOGIN_REQUIRED" }) {
                this@YouTubeTrailerExtractor.config = null
                return@withTimeout null
            }
            choose(players.filter { it.status == null || it.status == "OK" }, maxHeight)
        }
    } catch (_: TimeoutCancellationException) {
        null
    } catch (error: CancellationException) {
        // Must precede IllegalStateException, which it extends: a card that
        // lost focus cancels its extraction, and that is not a failed extraction.
        throw error
    } catch (_: IOException) {
        null
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: IllegalStateException) {
        null
    }

    private fun choose(players: List<YouTubePlayer>, maxHeight: Int): TrailerSource? {
        val preferred = players.filter { it.client == PREFERRED_CLIENT }.ifEmpty { players }
        val video = rankVideo(preferred.flatMap(YouTubePlayer::adaptive), maxHeight).firstOrNull()
            ?: rankVideo(players.flatMap(YouTubePlayer::adaptive), maxHeight).firstOrNull()
        val audio = rankAudio(preferred.flatMap(YouTubePlayer::adaptive)).firstOrNull()
            ?: rankAudio(players.flatMap(YouTubePlayer::adaptive)).firstOrNull()
        if (video != null && audio != null) {
            reachableUrl(video.url)?.let { videoUrl ->
                return TrailerSource(videoUrl = videoUrl, audioUrl = reachableUrl(audio.url) ?: audio.url)
            }
        }
        // Adaptive refused (403): the HLS manifest works for age-gated and kids titles.
        players.firstNotNullOfOrNull(YouTubePlayer::hlsManifestUrl)?.let { return TrailerSource(videoUrl = it) }
        val progressive = rankVideo(players.flatMap(YouTubePlayer::progressive), maxHeight).firstOrNull()
            ?: return null
        return reachableUrl(progressive.url)?.let { TrailerSource(videoUrl = it) }
    }

    private suspend fun watchConfig(refresh: Boolean): WatchConfig {
        config?.takeIf { !refresh && now() - it.fetchedAtMillis < CONFIG_TTL_MILLIS }?.let { return it }
        return configLock.withLock {
            config?.takeIf { !refresh && now() - it.fetchedAtMillis < CONFIG_TTL_MILLIS }?.let { return@withLock it }
            val html = runCatching { get(WATCH_URL, DEFAULT_HEADERS) }.getOrNull()
            if (html == null) {
                // A stale config beats failing outright.
                return@withLock config ?: WatchConfig(FALLBACK_API_KEY, null, now())
            }
            WatchConfig(
                apiKey = API_KEY.find(html)?.groupValues?.get(1) ?: FALLBACK_API_KEY,
                visitorData = VISITOR_DATA.find(html)?.groupValues?.get(1),
                fetchedAtMillis = now(),
            ).also { config = it }
        }
    }

    private fun playerResponse(config: WatchConfig, videoId: String, youTubeClient: YouTubeClient): YouTubePlayer {
        val body = buildJsonObject {
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            putJsonObject("context") { put("client", youTubeClient.context) }
            putJsonObject("playbackContext") {
                putJsonObject("contentPlaybackContext") { put("html5Preference", "HTML5_PREF_WANTS") }
            }
        }.toString()
        val headers = DEFAULT_HEADERS + buildMap {
            put("content-type", "application/json")
            put("origin", "https://www.youtube.com")
            put("x-youtube-client-name", youTubeClient.id)
            put("x-youtube-client-version", youTubeClient.version)
            put("user-agent", youTubeClient.userAgent)
            config.visitorData?.let { put("x-goog-visitor-id", it) }
        }
        val request = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/player?key=${config.apiKey}")
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val text = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("player ${youTubeClient.key} ${response.code}")
            response.body?.string().orEmpty()
        }
        return parsePlayerResponse(text, youTubeClient.key)
    }

    private fun get(url: String, headers: Map<String, String>): String {
        val request = Request.Builder()
            .url(url)
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("GET ${response.code}")
            response.body?.string().orEmpty()
        }
    }

    /**
     * Probes the stream, then the alternate CDN nodes named in its `mn`
     * parameter, and returns the first that serves bytes.
     */
    private fun reachableUrl(url: String): String? {
        if (isReachable(url)) return url
        return alternateCdnUrls(url).firstOrNull(::isReachable)
    }

    private fun isReachable(url: String): Boolean = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-0")
            .header("user-agent", DEFAULT_USER_AGENT)
            .build()
        probeClient.newCall(request).execute().use { it.code == 200 || it.code == 206 }
    }.getOrDefault(false)

    private data class WatchConfig(
        val apiKey: String,
        val visitorData: String?,
        val fetchedAtMillis: Long,
    )

    internal data class YouTubeClient(
        val key: String,
        val id: String,
        val version: String,
        val userAgent: String,
        val context: JsonObject,
    )

    companion object {
        private val YOUTUBE_ID = Regex("^[A-Za-z0-9_-]{11}$")
        private val API_KEY = Regex("\"INNERTUBE_API_KEY\":\"([^\"]+)\"")
        private val VISITOR_DATA = Regex("\"VISITOR_DATA\":\"([^\"]+)\"")
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private const val WATCH_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&hl=en"
        private const val FALLBACK_API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
        private const val PREFERRED_CLIENT = "visionos"
        private const val EXTRACT_TIMEOUT_MILLIS = 30_000L
        private const val CONFIG_TTL_MILLIS = 3L * 60 * 60 * 1000
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 12; Android TV) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
        private val DEFAULT_HEADERS = mapOf(
            "accept-language" to "en-US,en;q=0.9",
            "user-agent" to DEFAULT_USER_AGENT,
        )

        private fun clientContext(vararg values: Pair<String, Any>): JsonObject = buildJsonObject {
            values.forEach { (key, value) ->
                when (value) {
                    is Int -> put(key, value)
                    else -> put(key, value.toString())
                }
            }
            put("hl", "en")
            put("gl", "US")
        }

        internal val CLIENTS = listOf(
            YouTubeClient(
                key = "visionos",
                id = "101",
                version = "1.02",
                userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 " +
                    "(KHTML, like Gecko) Version/26.0 Safari/605.1.15",
                context = clientContext(
                    "clientName" to "VISIONOS",
                    "clientVersion" to "1.02",
                    "deviceMake" to "Apple",
                    "deviceModel" to "RealityDevice17,1",
                    "osName" to "visionOS",
                    "osVersion" to "26.5.23O471",
                ),
            ),
            YouTubeClient(
                key = "android",
                id = "3",
                version = "20.10.35",
                userAgent = "com.google.android.youtube/20.10.35 (Linux; U; Android 14; en_US) gzip",
                context = clientContext(
                    "clientName" to "ANDROID",
                    "clientVersion" to "20.10.35",
                    "osName" to "Android",
                    "osVersion" to "14",
                    "platform" to "MOBILE",
                    "androidSdkVersion" to 34,
                ),
            ),
            YouTubeClient(
                key = "ios",
                id = "5",
                version = "20.10.1",
                userAgent = "com.google.ios.youtube/20.10.1 (iPhone16,2; U; CPU iOS 17_4 like Mac OS X)",
                context = clientContext(
                    "clientName" to "IOS",
                    "clientVersion" to "20.10.1",
                    "deviceModel" to "iPhone16,2",
                    "osName" to "iPhone",
                    "osVersion" to "17.4.0.21E219",
                    "platform" to "MOBILE",
                ),
            ),
        )
    }
}

internal data class YouTubeFormat(
    val url: String,
    val mimeType: String,
    val height: Int = 0,
    val fps: Int = 0,
    val bitrate: Long = 0,
    /** False only for an alternate-language dub; the original track wins. */
    val defaultAudio: Boolean = true,
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val isAudio: Boolean get() = mimeType.startsWith("audio/")
    val isMp4: Boolean get() = "mp4" in mimeType
}

internal data class YouTubePlayer(
    val client: String,
    val status: String?,
    val hlsManifestUrl: String?,
    val progressive: List<YouTubeFormat>,
    val adaptive: List<YouTubeFormat>,
)

private val JSON = Json { ignoreUnknownKeys = true }
private val QUALITY_LABEL = Regex("(\\d{2,4})p")

internal fun parsePlayerResponse(body: String, client: String): YouTubePlayer {
    val root = JSON.parseToJsonElement(body).jsonObject
    val streaming = root.obj("streamingData")
    return YouTubePlayer(
        client = client,
        status = root.obj("playabilityStatus")?.string("status"),
        hlsManifestUrl = streaming?.string("hlsManifestUrl")?.takeIf(String::isNotBlank),
        progressive = streaming?.formats("formats").orEmpty(),
        adaptive = streaming?.formats("adaptiveFormats").orEmpty(),
    )
}

/**
 * Best video first: formats within [maxHeight] (the surface never needs
 * more), tallest first, H.264/MP4 before VP9/WebM for hardware decoding.
 * AV1 is dropped: many TVs and phones cannot decode it.
 */
internal fun rankVideo(formats: List<YouTubeFormat>, maxHeight: Int): List<YouTubeFormat> =
    formats
        .filter { it.isVideo && "av01" !in it.mimeType }
        .sortedWith(
            compareBy<YouTubeFormat> { if (it.height <= maxHeight) 0 else 1 }
                .thenBy { if (it.height <= maxHeight) -it.height else it.height }
                .thenBy { if (it.isMp4) 0 else 1 }
                .thenByDescending { it.fps }
                .thenByDescending { it.bitrate },
        )

/** Best audio first: the original track, AAC/MP4 before Opus/WebM, highest bitrate. */
internal fun rankAudio(formats: List<YouTubeFormat>): List<YouTubeFormat> =
    formats
        .filter(YouTubeFormat::isAudio)
        .sortedWith(
            compareBy<YouTubeFormat> { if (it.defaultAudio) 0 else 1 }
                .thenBy { if (it.isMp4) 0 else 1 }
                .thenByDescending { it.bitrate },
        )

/**
 * The same URL on the other CDN nodes listed in its `mn` parameter
 * (`rr1---sn-a` → `rr2---sn-b`), as NuvioTV probes them.
 */
internal fun alternateCdnUrls(url: String): List<String> {
    val parsed = url.toHttpUrlOrNull() ?: return emptyList()
    if (!parsed.host.endsWith("googlevideo.com")) return emptyList()
    val servers = parsed.queryParameter("mn")?.split(',')?.map(String::trim)?.filter(String::isNotBlank).orEmpty()
    if (servers.size < 2) return emptyList()
    return servers.mapIndexedNotNull { index, server ->
        val host = parsed.host
            .replaceFirst(Regex("^rr\\d+---"), "rr${index + 1}---")
            .replaceFirst(Regex("sn-[a-z0-9]+-[a-z0-9]+"), server)
        if (host == parsed.host) null else parsed.newBuilder().host(host).build().toString()
    }
}

private fun JsonObject.formats(key: String): List<YouTubeFormat> =
    (this[key] as? JsonArray).orEmpty().mapNotNull { element ->
        val format = element as? JsonObject ?: return@mapNotNull null
        val url = format.string("url") ?: return@mapNotNull null
        YouTubeFormat(
            url = url,
            mimeType = format.string("mimeType").orEmpty(),
            height = format.number("height")?.toInt()
                ?: format.string("qualityLabel")?.let { QUALITY_LABEL.find(it)?.groupValues?.get(1)?.toIntOrNull() }
                ?: 0,
            fps = format.number("fps")?.toInt() ?: 0,
            bitrate = (format.number("bitrate") ?: format.number("averageBitrate"))?.toLong() ?: 0,
            defaultAudio = format.obj("audioTrack")?.let { (it["audioIsDefault"] as? JsonPrimitive)?.booleanOrNull } ?: true,
        )
    }

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
private fun JsonObject.number(key: String): Double? = string(key)?.toDoubleOrNull()
