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
import com.lamphaus.core.model.normalizeBcp47Tag
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val cpuDispatcher: CoroutineDispatcher = Dispatchers.Default,
    cacheBudgetBytes: Long = DEFAULT_CACHE_BUDGET_BYTES,
    private val maxCacheEntryBytes: Long = DEFAULT_MAX_CACHE_ENTRY_BYTES,
    private val maxResponseBytes: Long = DEFAULT_MAX_RESPONSE_BYTES,
    maxConcurrentRequests: Int = DEFAULT_MAX_CONCURRENT_REQUESTS,
    maxConcurrentRequestsPerScope: Int = DEFAULT_MAX_CONCURRENT_PER_SCOPE,
) : ProviderClient {
    private data class CacheEntry(
        val body: String,
        val etag: String?,
        val lastModified: String?,
        val fetchedAtMillis: Long,
        /** Approximate retained heap for budget accounting. */
        val sizeBytes: Long,
    )

    private data class Payload(val element: JsonElement, val stale: Boolean)

    private data class Fetched(
        val body: String,
        val etag: String?,
        val lastModified: String?,
        val stale: Boolean,
    )

    private class InFlight {
        val deferred = kotlinx.coroutines.CompletableDeferred<Payload>()
        lateinit var job: Job
        val waiters = AtomicInteger(0)
    }

    // Byte-budgeted LRU (PERF-04). Access order makes the eldest entry the
    // least recently used; the budget accounts for an approximation of String
    // overhead rather than raw payload length alone.
    private val cacheBudgetBytes = cacheBudgetBytes.coerceAtLeast(0)
    private val cacheLock = Any()
    private val cache = java.util.LinkedHashMap<String, CacheEntry>(16, 0.75f, true)
    private var cacheBytes = 0L

    // Identical simultaneous requests share one network call; cancellation of
    // one subscriber must not cancel another subscriber's shared request, and
    // the shared call only stops when nobody waits for it (PERF-04).
    private val inFlight = ConcurrentHashMap<String, InFlight>()
    private val requestScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    // Global and per-provider admission keeps outstanding suspended HTTP
    // requests bounded even when callers fan out (PERF-06).
    private val globalSlot = Semaphore(maxConcurrentRequests.coerceAtLeast(1))
    private val scopeSlots = ConcurrentHashMap<String, Semaphore>()
    private val perScopeLimit = maxConcurrentRequestsPerScope.coerceAtLeast(1)

    override suspend fun manifest(manifestUrl: String): ProviderResult<ProviderManifest> = guardedProviderCall {
        val normalized = urlPolicy.normalizeManifestUrl(manifestUrl)
            ?: return@guardedProviderCall ProviderResult.Failure(ProviderFailureKind.INVALID_URL, "Use a valid HTTPS provider address.")
        val payload = fetch(normalized, scope = normalized)
        withContext(cpuDispatcher) {
            ProviderResult.Success(parseManifest(payload.element.jsonObject), payload.stale)
        }
    }

    override suspend fun discoverProviderUrls(catalogUrl: String): ProviderResult<List<String>> = guardedProviderCall {
        val normalized = urlPolicy.normalizeCatalogUrl(catalogUrl)
            ?: return@guardedProviderCall ProviderResult.Failure(ProviderFailureKind.INVALID_URL, "Use a valid HTTPS provider catalog address.")
        val payload = fetch(normalized, scope = normalized)
        withContext(cpuDispatcher) {
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
    }

    override suspend fun catalog(
        manifestUrl: String,
        providerId: String,
        query: CatalogQuery,
    ): ProviderResult<List<MediaPreview>> = guardedProviderCall {
        val url = resourceUrl(manifestUrl, "catalog", query.type, query.catalogId, query.extras())
            ?: return@guardedProviderCall ProviderResult.Failure(ProviderFailureKind.INVALID_URL, "Provider address is invalid.")
        val payload = fetch(url.toString(), scope = manifestUrl)
        withContext(cpuDispatcher) {
            val items = payload.element.jsonObject.array("metas") ?: JsonArray(emptyList())
            ProviderResult.Success(
                items.mapNotNull { (it as? JsonObject)?.toPreview(providerId, query.type, query.posterShape) },
                payload.stale,
            )
        }
    }

    override suspend fun meta(
        manifestUrl: String,
        providerId: String,
        type: String,
        id: String,
    ): ProviderResult<MediaDetail> = guardedProviderCall {
        val url = resourceUrl(manifestUrl, "meta", type, id)
            ?: return@guardedProviderCall ProviderResult.Failure(ProviderFailureKind.INVALID_URL, "Provider address is invalid.")
        val payload = fetch(url.toString(), scope = manifestUrl)
        withContext(cpuDispatcher) {
            val meta = payload.element.jsonObject.obj("meta") ?: payload.element.jsonObject
            ProviderResult.Success(meta.toDetail(providerId, type), payload.stale)
        }
    }

    override suspend fun streams(
        manifestUrl: String,
        providerId: String,
        type: String,
        id: String,
    ): ProviderResult<List<StreamCandidate>> = guardedProviderCall {
        val url = resourceUrl(manifestUrl, "stream", type, id)
            ?: return@guardedProviderCall ProviderResult.Failure(ProviderFailureKind.INVALID_URL, "Provider address is invalid.")
        // Playable URLs can expire: never revalidate or serve them stale.
        val payload = fetch(url.toString(), scope = manifestUrl, cacheable = false, allowStaleOnError = false)
        withContext(cpuDispatcher) {
            val streams = payload.element.jsonObject.array("streams") ?: JsonArray(emptyList())
            ProviderResult.Success(streams.mapNotNull { (it as? JsonObject)?.toStream(providerId) }, payload.stale)
        }
    }

    override suspend fun subtitles(
        manifestUrl: String,
        type: String,
        id: String,
        extras: Map<String, String>,
    ): ProviderResult<List<SubtitleTrack>> = guardedProviderCall {
        val url = resourceUrl(manifestUrl, "subtitles", type, id, extras)
            ?: return@guardedProviderCall ProviderResult.Failure(ProviderFailureKind.INVALID_URL, "Provider address is invalid.")
        val payload = fetch(url.toString(), scope = manifestUrl, allowStaleOnError = false)
        withContext(cpuDispatcher) {
            val subtitles = payload.element.jsonObject.array("subtitles") ?: JsonArray(emptyList())
            ProviderResult.Success(subtitles.mapNotNull { (it as? JsonObject)?.toSubtitle() }, payload.stale)
        }
    }

    /** Drops cached bodies and admission state for one provider after a config change. */
    override fun invalidateProvider(manifestUrl: String) {
        val prefix = scopeKey(manifestUrl)
        synchronized(cacheLock) {
            val iterator = cache.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.key.startsWith(prefix)) {
                    cacheBytes -= entry.value.sizeBytes
                    iterator.remove()
                }
            }
        }
        scopeSlots.remove(manifestUrl)
    }

    private suspend fun fetch(
        url: String,
        scope: String,
        cacheable: Boolean = true,
        allowStaleOnError: Boolean = true,
    ): Payload {
        val key = cacheKey(scope, url)
        return coalesced(key) {
            performFetch(key, url, cacheable, allowStaleOnError)
        }
    }

    private suspend fun performFetch(
        key: String,
        url: String,
        cacheable: Boolean,
        allowStaleOnError: Boolean,
    ): Payload {
        val cached = if (cacheable) cached(key) else null
        return try {
            val fetched = withAdmission(key) { loadBody(url, key, cached, cacheable) }
            Payload(parseJson(fetched.body), fetched.stale)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val freshEnough = cached != null &&
                allowStaleOnError &&
                System.currentTimeMillis() - cached.fetchedAtMillis < STALE_LIMIT_MILLIS
            if (freshEnough) {
                Payload(parseJson(cached.body), true)
            } else {
                throw error
            }
        }
    }

    /**
     * Loads a body on the IO dispatcher. Redirects are followed manually so
     * every hop is re-validated by [ProviderUrlPolicy]; the response stream is
     * read through a bounded buffer even when Content-Length is absent or
     * wrong (PERF-04).
     */
    private suspend fun loadBody(url: String, key: String, cached: CacheEntry?, cacheable: Boolean): Fetched =
        withContext(ioDispatcher) {
            var currentUrl = url
            repeat(MAX_REDIRECTS + 1) { redirectCount ->
                val request = Request.Builder().url(currentUrl).header("Accept", "application/json").apply {
                    if (cacheable) {
                        cached?.etag?.let { header("If-None-Match", it) }
                        cached?.lastModified?.let { header("If-Modified-Since", it) }
                    }
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
                        // A successful revalidation refreshes the freshness
                        // window and any updated validators.
                        if (cacheable) {
                            store(
                                key,
                                cached.copy(
                                    etag = response.header("ETag") ?: cached.etag,
                                    lastModified = response.header("Last-Modified") ?: cached.lastModified,
                                    fetchedAtMillis = System.currentTimeMillis(),
                                ),
                            )
                        }
                        return@withContext Fetched(cached.body, cached.etag, cached.lastModified, stale = false)
                    }
                    if (!response.isSuccessful) throw ProviderHttpException(response.code)
                    val body = readBoundedBody(response)
                    if (body.isBlank()) throw ProviderProtocolException("Empty response")
                    if (cacheable) {
                        store(
                            key,
                            CacheEntry(
                                body = body,
                                etag = response.header("ETag"),
                                lastModified = response.header("Last-Modified"),
                                fetchedAtMillis = System.currentTimeMillis(),
                                sizeBytes = estimatedCacheSize(body),
                            ),
                        )
                    }
                    return@withContext Fetched(body, null, null, stale = false)
                }
            }
            throw ProviderProtocolException("Too many redirects")
        }

    private fun readBoundedBody(response: Response): String {
        val body = response.body ?: throw ProviderProtocolException("Empty response")
        val declared = body.contentLength()
        if (declared > maxResponseBytes) throw ProviderProtocolException("Response too large")
        val buffer = okio.Buffer()
        var remaining = maxResponseBytes + 1
        val charset = runCatching { body.contentType()?.charset() }.getOrNull() ?: Charsets.UTF_8
        body.source().use { source ->
            while (remaining > 0) {
                val read = source.read(buffer, remaining)
                if (read == -1L) break
                remaining -= read
            }
        }
        if (buffer.size > maxResponseBytes) throw ProviderProtocolException("Response too large")
        return buffer.readString(charset)
    }

    private suspend fun parseJson(body: String): JsonElement = withContext(cpuDispatcher) {
        json.parseToJsonElement(body)
    }

    private suspend fun <T> withAdmission(key: String, block: suspend () -> T): T {
        val scopeKey = key.substringBefore('\u0000')
        val perScope = scopeSlots.computeIfAbsent(scopeKey) { Semaphore(perScopeLimit) }
        return perScope.withPermit { globalSlot.withPermit { block() } }
    }

    private fun cacheKey(scope: String, url: String): String = scopeKey(scope) + url

    private fun scopeKey(scope: String): String = scope + '\u0000'

    private fun estimatedCacheSize(body: String): Long = body.length.toLong() * 2 + 96

    private fun cached(key: String): CacheEntry? = synchronized(cacheLock) { cache[key] }

    private fun store(key: String, entry: CacheEntry) {
        if (entry.sizeBytes > maxCacheEntryBytes) return
        synchronized(cacheLock) {
            cache.put(key, entry)?.let { cacheBytes -= it.sizeBytes }
            cacheBytes += entry.sizeBytes
            val iterator = cache.entries.iterator()
            while (iterator.hasNext() && (cacheBytes > cacheBudgetBytes || cache.size > MAX_CACHE_ENTRIES)) {
                val eldest = iterator.next()
                cacheBytes -= eldest.value.sizeBytes
                iterator.remove()
            }
        }
    }

    /**
     * Coalesces identical simultaneous requests. The shared call runs in the
     * client's application scope: a canceled subscriber only detaches, and the
     * call is canceled (and removed) once the last waiter is gone.
     */
    private suspend fun coalesced(key: String, block: suspend () -> Payload): Payload {
        while (true) {
            inFlight[key]?.let { return awaitShared(key, it) }
            val entry = InFlight()
            entry.job = requestScope.launch(start = CoroutineStart.LAZY) {
                try {
                    entry.deferred.complete(block())
                } catch (error: CancellationException) {
                    entry.deferred.cancel(error)
                    throw error
                } catch (error: Throwable) {
                    entry.deferred.completeExceptionally(error)
                } finally {
                    inFlight.remove(key, entry)
                }
            }
            if (inFlight.putIfAbsent(key, entry) != null) {
                entry.job.cancel()
                continue
            }
            entry.job.start()
            return awaitShared(key, entry)
        }
    }

    private suspend fun awaitShared(key: String, entry: InFlight): Payload {
        entry.waiters.incrementAndGet()
        try {
            return entry.deferred.await()
        } finally {
            if (entry.waiters.decrementAndGet() <= 0) {
                inFlight.remove(key, entry)
                if (!entry.deferred.isCompleted) entry.job.cancel()
            }
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
                is JsonPrimitive -> item.contentOrNull?.trim()?.takeIf(String::isNotBlank)?.let(::ProviderResource)
                is JsonObject -> item.string("name")?.trim()?.takeIf(String::isNotBlank)?.let { resourceName ->
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
                val type = catalog.string("type")?.trim()?.takeIf(String::isNotBlank) ?: return@let null
                val catalogId = catalog.string("id")?.trim()?.takeIf(String::isNotBlank) ?: return@let null
                val extraDefinitions = catalog.array("extra").orEmpty()
                val extraWireNames = linkedMapOf<String, String>()
                fun addExtraName(rawName: String?) {
                    val name = rawName?.trim()?.takeIf(String::isNotBlank) ?: return
                    extraWireNames.putIfAbsent(name.canonicalExtraName(), name)
                }
                catalog.stringsOrSingle("extraSupported").forEach(::addExtraName)
                catalog.stringsOrSingle("extraRequired").forEach(::addExtraName)
                extraDefinitions.forEach { extra ->
                    when (extra) {
                        is JsonObject -> addExtraName(extra.string("name"))
                        is JsonPrimitive -> addExtraName(extra.contentOrNull)
                        else -> Unit
                    }
                }
                val extras = extraWireNames.values.toSet()
                val requiredExtras = buildSet {
                    catalog.stringsOrSingle("extraRequired").forEach { addExtraName(it); add(extraWireNames[it.canonicalExtraName()] ?: it) }
                    extraDefinitions.forEach { extra ->
                        val definition = extra as? JsonObject ?: return@forEach
                        if (definition.boolean("isRequired") == true) {
                            val rawName = definition.string("name")
                            rawName?.let { add(extraWireNames[it.trim().canonicalExtraName()] ?: it.trim()) }
                        }
                    }
                }
                val extraOptions = buildMap {
                    extraDefinitions.forEach { extra ->
                        val definition = extra as? JsonObject ?: return@forEach
                        val rawName = definition.string("name") ?: return@forEach
                        val wireName = extraWireNames[rawName.trim().canonicalExtraName()] ?: rawName.trim()
                        definition.stringsOrSingle("options").takeIf(List<String>::isNotEmpty)?.let { put(wireName, it) }
                    }
                    catalog.stringsOrSingle("genres").takeIf(List<String>::isNotEmpty)?.let { genres ->
                        val wireName = extraWireNames["genre"] ?: "genre"
                        putIfAbsent(wireName, genres)
                    }
                }
                val extraDefaults = buildMap {
                    extraDefinitions.forEach { extra ->
                        val definition = extra as? JsonObject ?: return@forEach
                        val rawName = definition.string("name") ?: return@forEach
                        val default = definition.string("default")?.trim()?.takeIf(String::isNotBlank) ?: return@forEach
                        val wireName = extraWireNames[rawName.trim().canonicalExtraName()] ?: rawName.trim()
                        put(wireName, default)
                    }
                }
                val extraOptionsLimits = buildMap {
                    extraDefinitions.forEach { extra ->
                        val definition = extra as? JsonObject ?: return@forEach
                        val rawName = definition.string("name") ?: return@forEach
                        val wireName = extraWireNames[rawName.trim().canonicalExtraName()] ?: rawName.trim()
                        definition.int("optionsLimit")?.takeIf { it > 0 }?.let { put(wireName, it) }
                    }
                }
                ProviderCatalog(
                    type = type,
                    id = catalogId,
                    name = catalog.string("name")?.trim()?.takeIf(String::isNotBlank) ?: catalogId,
                    extras = extras,
                    requiredExtras = requiredExtras,
                    extraWireNames = extraWireNames,
                    extraOptions = extraOptions,
                    extraDefaults = extraDefaults,
                    extraOptionsLimits = extraOptionsLimits,
                    pageSize = catalog.int("pageSize")?.takeIf { it > 0 },
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
        val releaseYear = firstString("releaseInfo", "year", "released")
            ?.take(4)
            ?.toIntOrNull()
            ?: int("year")
        val appExtras = obj("app_extras")
        val rating = ratingWithSource()
        return MediaPreview(
            id = itemId,
            type = rawType.toMediaType(),
            rawType = rawType,
            name = string("name") ?: "Untitled",
            posterUrl = firstString("poster", "posterUrl") ?: string("_rawPosterUrl"),
            backgroundUrl = firstString("background", "backdrop", "backdropUrl") ?: string("landscapePoster"),
            logoUrl = firstString("logo", "logoUrl") ?: obj("logo")?.string("url"),
            description = firstString("description", "overview"),
            releaseYear = releaseYear,
            genres = stringsOrSingle("genres").ifEmpty { stringsOrSingle("genre") },
            contentRating = firstString("contentRating", "content_rating", "certification")
                ?: appExtras?.firstString("certification"),
            rating = rating?.first,
            ratingSource = rating?.second,
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
                    episode = video.int("episode") ?: video.int("number"),
                    overview = video.string("overview") ?: video.string("description"),
                    thumbnailUrl = video.string("thumbnail"),
                    releasedAtEpochMillis = video.long("released") ?: video.string("released")?.toEpochMillisOrNull(),
                    streams = video.array("streams").orEmpty().mapNotNull { stream ->
                        (stream as? JsonObject)?.toStream(providerId)
                    },
                )
            }
        }
        val appExtras = obj("app_extras")
        val cast = people("cast").ifEmpty { appExtras?.people("cast").orEmpty() }
        val directors = people("director", "directors", "writer", "writers").ifEmpty {
            appExtras?.people("directors", "writers").orEmpty()
        }
        return MediaDetail(
            preview = preview,
            runtimeMinutes = string("runtime")?.toRuntimeMinutes(),
            cast = cast,
            directors = directors,
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
        val tags = stringsOrSingle("tag").ifEmpty { stringsOrSingle("tags") }
        val filename = firstString("filename") ?: hints?.firstString("filename")
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
            videoHash = firstString("videoHash") ?: hints?.firstString("videoHash"),
            videoSize = long("videoSize") ?: hints?.long("videoSize"),
            bingeGroup = hints?.firstString("bingeGroup"),
            mimeType = firstString("mimeType", "mime", "contentType"),
            quality = firstString("quality", "videoQuality")
                ?: tags.firstNotNullOfOrNull { it.qualityHint() }
                ?: listOfNotNull(string("name"), string("title"), string("description"), filename)
                    .firstNotNullOfOrNull { it.qualityHint() },
            tags = tags,
            headers = sanitizeHeaders(hints?.obj("proxyHeaders")?.obj("request")),
            subtitles = array("subtitles").orEmpty().mapNotNull { (it as? JsonObject)?.toSubtitle() },
            notWebReady = hints?.boolean("notWebReady") == true,
            countryWhitelist = hints?.strings("countryWhitelist").orEmpty(),
        )
    }
    private fun JsonObject.toSubtitle(): SubtitleTrack? {
        val url = string("url") ?: return null
        val language = normalizeBcp47Tag(string("lang") ?: string("language"))
        val filename = firstString("subtitleFileName")
        val label = firstString("label") ?: filename ?: firstString("movieReleaseName")
        return SubtitleTrack(
            id = string("id") ?: url.hashCode().toString(),
            language = language.ifBlank { "und" },
            url = url,
            format = firstString("format", "ext")
                ?: filename?.substringAfterLast('.', "")?.takeIf(String::isNotBlank),
            headers = sanitizeHeaders(obj("behaviorHints")?.obj("proxyHeaders")?.obj("request")),
            label = label,
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
        keys.firstNotNullOfOrNull { key -> string(key)?.trim()?.takeIf(String::isNotBlank) }
    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.contentOrNull?.trim()?.toIntOrNull()
    private fun JsonObject.long(key: String): Long? = this[key]?.jsonPrimitive?.contentOrNull?.trim()?.toLongOrNull()
    private fun JsonObject.double(key: String): Double? = this[key]?.jsonPrimitive?.contentOrNull?.trim()?.toDoubleOrNull()
    private fun JsonObject.boolean(key: String): Boolean? = when (val value = this[key]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()) {
        "true", "1" -> true
        "false", "0" -> false
        else -> null
    }
    private fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray
    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.strings(key: String): List<String> = array(key).orEmpty().mapNotNull {
        (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank)
    }
    private fun JsonObject.stringsOrSingle(key: String): List<String> = when (val value = this[key]) {
        is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank) }
        is JsonPrimitive -> value.contentOrNull?.trim()?.takeIf(String::isNotBlank)?.let(::listOf).orEmpty()
        else -> emptyList()
    }
    private fun JsonObject.optionalStrings(key: String): Set<String>? = if (containsKey(key)) strings(key).toSet() else null
    private fun JsonObject.people(vararg keys: String): List<String> =
        keys.flatMap { key -> peopleValue(this[key]) }.distinct()
    private fun peopleValue(value: JsonElement?): List<String> = when (value) {
        is JsonPrimitive -> value.contentOrNull?.trim()?.takeIf(String::isNotBlank)?.let(::listOf).orEmpty()
        is JsonArray -> value.flatMap(::peopleValue)
        is JsonObject -> listOfNotNull(value.firstString("name", "person", "value"))
        else -> emptyList()
    }
    private fun sanitizeHeaders(headers: JsonObject?): Map<String, String> = headers?.entries
        ?.mapNotNull { (rawKey, rawValue) ->
            val key = rawKey.trim()
            val value = (rawValue as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            if (key.isBlank() || value.isBlank() || key.equals("Range", ignoreCase = true)) null else key to value
        }
        ?.toMap()
        .orEmpty()
    private fun String.canonicalExtraName(): String = trim().lowercase()
    /**
     * Rating with its provenance. "imdb" only when an IMDb-named field
     * supplied the value; a generic `rating`/`ratings.value` stays "provider"
     * so the UI can never label it IMDb (SHR-PROD-05).
     */
    private fun JsonObject.ratingWithSource(): Pair<Double, String>? {
        val imdb = double("imdbRating")
            ?: double("imdb_rating")
            ?: obj("ratings")?.let { ratings -> ratings.double("imdb") ?: ratings.double("imdbRating") }
        imdb?.takeIf { it.isValidRating() }?.let { return it to RATING_SOURCE_IMDB }
        val provider = double("rating")
            ?: obj("ratings")?.let { ratings -> ratings.double("rating") ?: ratings.double("value") }
        provider?.takeIf { it.isValidRating() }?.let { return it to RATING_SOURCE_PROVIDER }
        return null
    }

    private fun Double.isValidRating(): Boolean = isFinite() && this in 0.0..10.0
    private fun JsonObject.streamFiles(key: String): List<StreamFile> = array(key).orEmpty().mapNotNull { item ->
        when (item) {
            is JsonPrimitive -> item.contentOrNull?.trim()?.takeIf(String::isNotBlank)?.let(::StreamFile)
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

        /** Retained provider bodies across all providers; representation overhead included. */
        const val DEFAULT_CACHE_BUDGET_BYTES = 8L * 1024 * 1024
        const val DEFAULT_MAX_CACHE_ENTRY_BYTES = 2L * 1024 * 1024
        const val MAX_CACHE_ENTRIES = 256

        /** Hard ceiling for one response body, even without a usable Content-Length. */
        const val DEFAULT_MAX_RESPONSE_BYTES = 8L * 1024 * 1024

        const val DEFAULT_MAX_CONCURRENT_REQUESTS = 8
        const val DEFAULT_MAX_CONCURRENT_PER_SCOPE = 3

        val QUALITY = Regex("(?:2160p|4k|1080p|720p|480p)", RegexOption.IGNORE_CASE)
    }
}


internal const val RATING_SOURCE_IMDB = "imdb"
internal const val RATING_SOURCE_PROVIDER = "provider"
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
