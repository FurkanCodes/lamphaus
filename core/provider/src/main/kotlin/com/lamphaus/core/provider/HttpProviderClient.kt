package com.lamphaus.core.provider

import com.lamphaus.core.model.CatalogQuery
import com.lamphaus.core.model.Episode
import com.lamphaus.core.model.MediaDetail
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.MediaType
import com.lamphaus.core.model.ProviderCatalog
import com.lamphaus.core.model.ProviderBehaviorHints
import com.lamphaus.core.model.ProviderFailureKind
import com.lamphaus.core.model.ProviderManifest
import com.lamphaus.core.model.ProviderResource
import com.lamphaus.core.model.ProviderResult
import com.lamphaus.core.model.StreamCandidate
import com.lamphaus.core.model.StreamFile
import com.lamphaus.core.model.SubtitleTrack
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.doubleOrNull
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
class HttpProviderClient(
    private val urlPolicy: ProviderUrlPolicy,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false)
        .retryOnConnectionFailure(true)
        .build(),
) : ProviderClient {
    private data class CacheEntry(
        val body: String,
        val etag: String?,
        val lastModified: String?,
        val fetchedAtMillis: Long,
    )

    private data class Payload(val element: JsonElement, val stale: Boolean)

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    override suspend fun manifest(manifestUrl: String): ProviderResult<ProviderManifest> = guardedProviderCall {
        val normalized = urlPolicy.normalizeManifestUrl(manifestUrl)
            ?: return@guardedProviderCall ProviderResult.Failure(ProviderFailureKind.INVALID_URL, "Use a valid HTTPS provider address.")
        val payload = fetch(normalized)
        ProviderResult.Success(parseManifest(payload.element.jsonObject), payload.stale)
    }

    override suspend fun discoverProviderUrls(catalogUrl: String): ProviderResult<List<String>> = guardedProviderCall {
        val normalized = urlPolicy.normalizeCatalogUrl(catalogUrl)
            ?: return@guardedProviderCall ProviderResult.Failure(ProviderFailureKind.INVALID_URL, "Use a valid HTTPS provider catalog address.")
        val payload = fetch(normalized)
        val root = payload.element
        val candidates = when (root) {
            is JsonArray -> root
            is JsonObject -> root.array("providers") ?: root.array("addons") ?: root.array("items") ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }
        val urls = candidates.mapNotNull { item ->
            when (item) {
                is JsonPrimitive -> item.contentOrNull
                is JsonObject -> item.string("manifestUrl") ?: item.string("transportUrl") ?: item.string("url")
                else -> null
            }
        }.mapNotNull(urlPolicy::normalizeManifestUrl).distinct()
        ProviderResult.Success(urls, payload.stale)
    }

    override suspend fun catalog(
        manifestUrl: String,
        providerId: String,
        query: CatalogQuery,
    ): ProviderResult<List<MediaPreview>> = guardedProviderCall {
        val url = resourceUrl(manifestUrl, "catalog", query.type, query.catalogId, query.extras())
            ?: return@guardedProviderCall ProviderResult.Failure(ProviderFailureKind.INVALID_URL, "Provider address is invalid.")
        val payload = fetch(url.toString())
        val items = payload.element.jsonObject.array("metas") ?: JsonArray(emptyList())
        ProviderResult.Success(
            items.mapNotNull { (it as? JsonObject)?.toPreview(providerId, query.type, query.posterShape) },
            payload.stale,
        )
    }

    override suspend fun meta(
        manifestUrl: String,
        providerId: String,
        type: String,
        id: String,
    ): ProviderResult<MediaDetail> = guardedProviderCall {
        val url = resourceUrl(manifestUrl, "meta", type, id)
            ?: return@guardedProviderCall ProviderResult.Failure(ProviderFailureKind.INVALID_URL, "Provider address is invalid.")
        val payload = fetch(url.toString())
        val meta = payload.element.jsonObject.obj("meta") ?: payload.element.jsonObject
        ProviderResult.Success(meta.toDetail(providerId, type), payload.stale)
    }

    override suspend fun streams(
        manifestUrl: String,
        providerId: String,
        type: String,
        id: String,
    ): ProviderResult<List<StreamCandidate>> = guardedProviderCall {
        val url = resourceUrl(manifestUrl, "stream", type, id)
            ?: return@guardedProviderCall ProviderResult.Failure(ProviderFailureKind.INVALID_URL, "Provider address is invalid.")
        val payload = fetch(url.toString())
        val streams = payload.element.jsonObject.array("streams") ?: JsonArray(emptyList())
        ProviderResult.Success(streams.mapNotNull { (it as? JsonObject)?.toStream(providerId) }, payload.stale)
    }

    override suspend fun subtitles(
        manifestUrl: String,
        type: String,
        id: String,
        extras: Map<String, String>,
    ): ProviderResult<List<SubtitleTrack>> = guardedProviderCall {
        val url = resourceUrl(manifestUrl, "subtitles", type, id, extras)
            ?: return@guardedProviderCall ProviderResult.Failure(ProviderFailureKind.INVALID_URL, "Provider address is invalid.")
        val payload = fetch(url.toString())
        val subtitles = payload.element.jsonObject.array("subtitles") ?: JsonArray(emptyList())
        ProviderResult.Success(subtitles.mapNotNull { (it as? JsonObject)?.toSubtitle() }, payload.stale)
    }

    private suspend fun fetch(url: String): Payload = withContext(Dispatchers.IO) {
        val cached = cache[url]
        try {
            var currentUrl = url
            repeat(MAX_REDIRECTS + 1) { redirectCount ->
                val request = Request.Builder().url(currentUrl).header("Accept", "application/json").apply {
                    cached?.etag?.let { header("If-None-Match", it) }
                    cached?.lastModified?.let { header("If-Modified-Since", it) }
                }.build()
                client.newCall(request).awaitResponse().use { response ->
                    if (response.isRedirect) {
                        if (redirectCount == MAX_REDIRECTS) throw ProviderProtocolException("Too many redirects")
                        val resolved = response.header("Location")?.let(request.url::resolve)
                            ?: throw ProviderProtocolException("Invalid redirect")
                        currentUrl = urlPolicy.normalizeCatalogUrl(resolved.toString())
                            ?: throw ProviderProtocolException("Unsafe redirect refused")
                        return@use
                    }
                    if (response.code == 304 && cached != null) {
                        return@withContext Payload(json.parseToJsonElement(cached.body), false)
                    }
                    if (!response.isSuccessful) throw ProviderHttpException(response.code)
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) throw ProviderProtocolException("Empty response")
                    cache[url] = CacheEntry(
                        body,
                        response.header("ETag"),
                        response.header("Last-Modified"),
                        System.currentTimeMillis(),
                    )
                    return@withContext Payload(json.parseToJsonElement(body), false)
                }
            }
            throw ProviderProtocolException("Too many redirects")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val freshEnough = cached != null && System.currentTimeMillis() - cached.fetchedAtMillis < STALE_LIMIT_MILLIS
            if (freshEnough) Payload(json.parseToJsonElement(cached.body), true) else throw error
        }
    }

    private fun resourceUrl(
        manifestUrl: String,
        resource: String,
        type: String,
        id: String,
        extras: Map<String, String> = emptyMap(),
    ): HttpUrl? {
        val normalized = urlPolicy.normalizeManifestUrl(manifestUrl) ?: return null
        val manifest = normalized.toHttpUrlOrNull() ?: return null
        val rootSegments = manifest.pathSegments.dropLast(1)
        return manifest.newBuilder().apply {
            encodedPath("/")
            rootSegments.filter(String::isNotBlank).forEach(::addPathSegment)
            addPathSegment(resource)
            addPathSegment(type)
            if (extras.isNotEmpty()) {
                addPathSegment(id)
                val encoded = extras.entries.joinToString("&") { (key, value) ->
                    "${key.encode()}=${value.encode()}"
                }
                addEncodedPathSegment("$encoded.json")
            } else {
                addPathSegment("$id.json")
            }
        }.build()
    }


    private fun parseManifest(root: JsonObject): ProviderManifest {
        val id = root.string("id") ?: throw ProviderProtocolException("Missing id")
        val name = root.string("name") ?: throw ProviderProtocolException("Missing name")
        val resources = root.array("resources").orEmpty().mapNotNull { item ->
            when (item) {
                is JsonPrimitive -> item.contentOrNull?.let(::ProviderResource)
                is JsonObject -> item.string("name")?.let { resourceName ->
                    ProviderResource(
                        name = resourceName,
                        types = item.optionalStrings("types"),
                        idPrefixes = item.optionalStrings("idPrefixes"),
                    )
                }
                else -> null
            }
        }
        val catalogs = root.array("catalogs").orEmpty().mapNotNull { item ->
            (item as? JsonObject)?.let { catalog ->
                val type = catalog.string("type") ?: return@let null
                val catalogId = catalog.string("id") ?: return@let null
                val extraDefinitions = catalog.array("extra").orEmpty()
                val extras = buildSet {
                    addAll(catalog.stringsOrSingle("extraSupported"))
                    addAll(catalog.stringsOrSingle("extraRequired"))
                    extraDefinitions.forEach { extra ->
                        when (extra) {
                            is JsonObject -> extra.string("name")?.let(::add)
                            is JsonPrimitive -> extra.contentOrNull?.let(::add)
                            else -> Unit
                        }
                    }
                }
                val requiredExtras = buildSet {
                    addAll(catalog.stringsOrSingle("extraRequired"))
                    extraDefinitions.forEach { extra ->
                        if ((extra as? JsonObject)?.boolean("isRequired") == true) {
                            extra.string("name")?.let(::add)
                        }
                    }
                }
                val extraOptions = buildMap {
                    extraDefinitions.forEach { extra ->
                        val definition = extra as? JsonObject ?: return@forEach
                        val name = definition.string("name") ?: return@forEach
                        definition.strings("options").takeIf(List<String>::isNotEmpty)?.let { put(name, it) }
                    }
                    catalog.strings("genres").takeIf(List<String>::isNotEmpty)?.let { genres ->
                        putIfAbsent("genre", genres)
                    }
                }
                val extraDefaults = buildMap {
                    extraDefinitions.forEach { extra ->
                        val definition = extra as? JsonObject ?: return@forEach
                        val name = definition.string("name") ?: return@forEach
                        definition.string("default")?.let { put(name, it) }
                    }
                }
                val extraOptionsLimits = buildMap {
                    extraDefinitions.forEach { extra ->
                        val definition = extra as? JsonObject ?: return@forEach
                        val name = definition.string("name") ?: return@forEach
                        definition.int("optionsLimit")?.let { put(name, it) }
                    }
                }
                ProviderCatalog(
                    type = type,
                    id = catalogId,
                    name = catalog.string("name") ?: catalogId,
                    extras = extras,
                    requiredExtras = requiredExtras,
                    extraOptions = extraOptions,
                    extraDefaults = extraDefaults,
                    extraOptionsLimits = extraOptionsLimits,
                    pageSize = catalog.int("pageSize"),
                    showInHome = catalog.boolean("showInHome") ?: true,
                    posterShape = catalog.string("posterShape"),
                )
            }
        }
        return ProviderManifest(
            id = id,
            name = name,
            version = root.string("version") ?: "0",
            description = root.string("description"),
            logoUrl = root.string("logo") ?: root.obj("logo")?.string("url"),
            backgroundUrl = root.string("background") ?: root.obj("background")?.string("url"),
            resources = resources,
            types = root.strings("types").toSet(),
            idPrefixes = root.strings("idPrefixes").toSet(),
            catalogs = catalogs,
            behaviorHints = ProviderBehaviorHints(
                configurable = root.obj("behaviorHints")?.boolean("configurable") == true,
                configurationRequired = root.obj("behaviorHints")?.boolean("configurationRequired") == true,
                adult = root.obj("behaviorHints")?.boolean("adult") == true,
                p2p = root.obj("behaviorHints")?.boolean("p2p") == true,
            ),
        )
    }

    private fun JsonObject.toPreview(
        providerId: String,
        fallbackType: String,
        catalogPosterShape: String? = null,
    ): MediaPreview? {
        val itemId = string("id") ?: return null
        val rawType = string("type") ?: fallbackType
        return MediaPreview(
            id = itemId,
            type = rawType.toMediaType(),
            rawType = rawType,
            name = string("name") ?: "Untitled",
            posterUrl = firstString("poster", "posterUrl") ?: string("_rawPosterUrl"),
            backgroundUrl = firstString("background", "backdrop", "backdropUrl") ?: string("landscapePoster"),
            logoUrl = firstString("logo", "logoUrl") ?: obj("logo")?.string("url"),
            description = firstString("description", "overview"),
            releaseYear = (string("releaseInfo") ?: string("year"))?.take(4)?.toIntOrNull() ?: int("year"),
            genres = stringsOrSingle("genres").ifEmpty { stringsOrSingle("genre") },
            contentRating = firstString("contentRating", "content_rating", "certification"),
            rating = ratingValue(),
            providerIds = setOf(providerId),
            posterShape = string("posterShape") ?: catalogPosterShape,
        )
    }

    private fun JsonObject.toDetail(providerId: String, fallbackType: String): MediaDetail {
        val preview = toPreview(providerId, fallbackType) ?: throw ProviderProtocolException("Missing media id")
        val videos = array("videos").orEmpty().mapNotNull { item ->
            (item as? JsonObject)?.let { video ->
                val episodeId = video.string("id") ?: return@let null
                Episode(
                    id = episodeId,
                    title = video.string("title") ?: video.string("name") ?: "Episode",
                    season = video.int("season"),
                    episode = video.int("episode"),
                    overview = video.string("overview"),
                    thumbnailUrl = video.string("thumbnail"),
                    releasedAtEpochMillis = video.long("released") ?: video.string("released")?.toEpochMillisOrNull(),
                    streams = video.array("streams").orEmpty().mapNotNull { stream ->
                        (stream as? JsonObject)?.toStream(providerId)
                    },
                )
            }
        }
        return MediaDetail(
            preview = preview,
            runtimeMinutes = string("runtime")?.toRuntimeMinutes(),
            cast = strings("cast"),
            directors = strings("director").ifEmpty { strings("directors") },
            episodes = videos,
            embeddedStreams = array("streams").orEmpty().mapNotNull { stream ->
                (stream as? JsonObject)?.toStream(providerId)
            },
        )
    }

    private fun JsonObject.toStream(providerId: String): StreamCandidate? {
        val url = string("url")
        val externalUrl = string("externalUrl")
        val infoHash = string("infoHash")
        val ytId = string("ytId") ?: string("yt_id")
        val sourceUrls = array("sources").orEmpty().mapNotNull { source ->
            when (source) {
                is JsonPrimitive -> source.contentOrNull
                is JsonObject -> source.string("url") ?: source.string("externalUrl")
                else -> null
            }
        }
        val nzbUrl = string("nzbUrl")
        val rarFiles = streamFiles("rarUrls")
        val zipFiles = streamFiles("zipUrls")
        val sevenZipFiles = streamFiles("7zipUrls")
        val tgzFiles = streamFiles("tgzUrls")
        val tarFiles = streamFiles("tarUrls")
        if (
            url == null && externalUrl == null && infoHash == null && ytId == null && nzbUrl == null &&
            rarFiles.isEmpty() && zipFiles.isEmpty() && sevenZipFiles.isEmpty() && tgzFiles.isEmpty() && tarFiles.isEmpty()
        ) return null
        val hints = obj("behaviorHints")
        val proxyHeaders = hints?.obj("proxyHeaders")
        val headers = proxyHeaders?.obj("request")?.entries?.mapNotNull { (key, value) ->
            value.jsonPrimitive.contentOrNull?.let { key to it }
        }?.toMap().orEmpty()
        val subtitles = array("subtitles").orEmpty().mapNotNull { (it as? JsonObject)?.toSubtitle() }
        val tags = stringsOrSingle("tag").ifEmpty { stringsOrSingle("tags") }
        val filename = string("filename") ?: hints?.string("filename")
        return StreamCandidate(
            providerId = providerId,
            name = string("name") ?: "Source",
            title = string("title"),
            description = string("description"),
            url = url,
            externalUrl = externalUrl,
            infoHash = infoHash,
            fileIndex = int("fileIdx") ?: int("fileIndex") ?: int("mapIdx"),
            ytId = ytId,
            sourceUrls = sourceUrls,
            nzbUrl = nzbUrl,
            servers = strings("servers"),
            rarFiles = rarFiles,
            zipFiles = zipFiles,
            sevenZipFiles = sevenZipFiles,
            tgzFiles = tgzFiles,
            tarFiles = tarFiles,
            fileMustInclude = string("fileMustInclude"),
            filename = filename,
            videoHash = hints?.string("videoHash"),
            videoSize = hints?.long("videoSize"),
            tags = tags,
            headers = headers,
            subtitles = subtitles,
            notWebReady = hints?.boolean("notWebReady") == true,
            countryWhitelist = hints?.strings("countryWhitelist").orEmpty(),
        )
    }

    private fun JsonObject.toSubtitle(): SubtitleTrack? {
        val url = string("url") ?: return null
        return SubtitleTrack(
            id = string("id") ?: url.hashCode().toString(),
            language = string("lang") ?: string("language") ?: "und",
            url = url,
            format = string("format") ?: string("ext"),
            headers = obj("behaviorHints")?.obj("proxyHeaders")?.obj("request")?.entries?.mapNotNull { (key, value) ->
                value.jsonPrimitive.contentOrNull?.let { key to it }
            }?.toMap().orEmpty(),
        )
    }

    private fun CatalogQuery.extras(): Map<String, String> = buildMap {
        putAll(extras)
        search?.takeIf(String::isNotBlank)?.let { put("search", it) }
        genre?.takeIf(String::isNotBlank)?.let { put("genre", it) }
        if (skip > 0) put("skip", skip.toString())
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.firstString(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> string(key)?.takeIf(String::isNotBlank) }
    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
    private fun JsonObject.long(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull
    private fun JsonObject.double(key: String): Double? = this[key]?.jsonPrimitive?.doubleOrNull
    private fun JsonObject.boolean(key: String): Boolean? = this[key]?.jsonPrimitive?.booleanOrNull
    private fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray
    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.strings(key: String): List<String> = array(key).orEmpty().mapNotNull {
        (it as? JsonPrimitive)?.contentOrNull
    }
    private fun JsonObject.stringsOrSingle(key: String): List<String> = when (val value = this[key]) {
        is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        is JsonPrimitive -> value.contentOrNull?.let(::listOf).orEmpty()
        else -> emptyList()
    }
    private fun JsonObject.optionalStrings(key: String): Set<String>? = if (containsKey(key)) strings(key).toSet() else null
    private fun JsonObject.ratingValue(): Double? {
        val value = double("imdbRating")
            ?: double("imdb_rating")
            ?: double("rating")
            ?: obj("ratings")?.let { ratings ->
                ratings.double("imdb")
                    ?: ratings.double("imdbRating")
                    ?: ratings.double("rating")
                    ?: ratings.double("value")
            }
        return value?.takeIf { it.isFinite() && it in 0.0..10.0 }
    }
    private fun JsonObject.streamFiles(key: String): List<StreamFile> = array(key).orEmpty().mapNotNull { item ->
        when (item) {
            is JsonPrimitive -> item.contentOrNull?.let(::StreamFile)
            is JsonObject -> item.string("url")?.let { StreamFile(it, item.long("bytes")) }
            else -> null
        }
    }
    private fun String.toMediaType(): MediaType = when (lowercase()) {
        "movie" -> MediaType.MOVIE
        "series" -> MediaType.SERIES
        else -> MediaType.UNKNOWN
    }
    private fun String.qualityHint(): String? = QUALITY.find(this)?.value?.uppercase()
    private fun String.toEpochMillisOrNull(): Long? = runCatching { java.time.Instant.parse(this).toEpochMilli() }.getOrNull()
    private fun String.toRuntimeMinutes(): Int? {
        val hours = Regex("(\\d+)\\s*h", RegexOption.IGNORE_CASE).find(this)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val minutes = Regex("(\\d+)\\s*m", RegexOption.IGNORE_CASE).find(this)?.groupValues?.get(1)?.toIntOrNull()
        return when {
            hours > 0 -> hours * 60 + (minutes ?: 0)
            minutes != null -> minutes
            else -> filter(Char::isDigit).toIntOrNull()
        }
    }
    private fun String.encode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8).replace("+", "%20")

    private class ProviderProtocolException(message: String) : IllegalArgumentException(message)

    private companion object {
        const val STALE_LIMIT_MILLIS = 7L * 24 * 60 * 60 * 1000
        const val MAX_REDIRECTS = 3
        val QUALITY = Regex("(?:2160p|4k|1080p|720p|480p)", RegexOption.IGNORE_CASE)
    }
}

private class ProviderHttpException(val code: Int) : IOException()

@OptIn(InternalCoroutinesApi::class)
internal suspend fun <T> guardedProviderCall(block: suspend () -> ProviderResult<T>): ProviderResult<T> = try {
    block()
} catch (error: CancellationException) {
    throw error
} catch (_: SocketTimeoutException) {
    ProviderResult.Failure(ProviderFailureKind.TIMEOUT, "This provider took too long to respond.")
} catch (_: IOException) {
    ProviderResult.Failure(ProviderFailureKind.NETWORK, "This provider is temporarily unreachable.")
} catch (error: ProviderHttpException) {
    ProviderResult.Failure(ProviderFailureKind.HTTP, "This provider returned HTTP ${error.code}.")
} catch (_: Exception) {
    ProviderResult.Failure(ProviderFailureKind.MALFORMED_RESPONSE, "This provider returned data Lamphaus could not read.")
}

@OptIn(InternalCoroutinesApi::class)
private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : okhttp3.Callback {
        override fun onFailure(call: Call, e: IOException) {
            val token = continuation.tryResumeWithException(e)
            if (token != null) continuation.completeResume(token)
        }

        override fun onResponse(call: Call, response: Response) {
            val token = continuation.tryResume(response)
            if (token != null) {
                continuation.completeResume(token)
            } else {
                response.close()
            }
        }
    })
}
