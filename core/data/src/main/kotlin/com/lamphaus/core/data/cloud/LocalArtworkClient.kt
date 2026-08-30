package com.lamphaus.core.data.cloud

import com.lamphaus.core.model.ArtworkAsset
import com.lamphaus.core.model.ArtworkCandidates
import com.lamphaus.core.model.ArtworkLookupStatus
import com.lamphaus.core.model.ArtworkProviderId
import com.lamphaus.core.model.ArtworkProviderResult
import com.lamphaus.core.model.ArtworkProviderStatus
import com.lamphaus.core.model.MediaType
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

class LocalArtworkClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val tmdbBase: String = TMDB_BASE,
    private val fanartBase: String = FANART_BASE,
) {
    suspend fun candidates(
        keys: Map<ArtworkProviderId, String>,
        mediaKey: String,
        name: String,
        releaseYear: Int?,
        mediaType: MediaType,
    ): Result<ArtworkCandidates> = withContext(Dispatchers.IO) {
        runCatching {
            if (keys.isEmpty()) throw ArtworkKeysNotConfiguredException()
            val tmdb = keys[ArtworkProviderId.TMDB]?.let {
                resolveTmdb(it, mediaKey, name, releaseYear, mediaType)
            }
            val fanart = keys[ArtworkProviderId.FANART]?.let {
                resolveFanart(it, mediaKey, mediaType, tmdb?.matches.orEmpty())
            }
            val results = buildList {
                listOf(ArtworkProviderId.TMDB, ArtworkProviderId.FANART).forEach { provider ->
                    if (provider !in keys) return@forEach
                    when (provider) {
                        ArtworkProviderId.TMDB -> add(tmdb?.result ?: failed(provider))
                        ArtworkProviderId.FANART -> add(fanart ?: failed(provider))
                        else -> Unit
                    }
                }
                keys.keys
                    .filter { it != ArtworkProviderId.TMDB && it != ArtworkProviderId.FANART }
                    .sortedBy(ArtworkProviderId::value)
                    .forEach { add(failed(it)) }
            }
            ArtworkCandidates(
                posters = results.flatMap { it.posters },
                backdrops = results.flatMap { it.backdrops },
                logos = results.flatMap { it.logos },
                providerResults = results.map { it.result },
            )
        }
    }

    fun providerStatuses(): List<ArtworkProviderStatus> = listOf(
        ArtworkProviderStatus(
            provider = ArtworkProviderId.TMDB,
            displayName = "TMDB",
            purpose = "Posters, backdrops, and logos from The Movie Database.",
            helpText = "Create a TMDB API key in your account settings.",
            keyPageUrl = "https://www.themoviedb.org/settings/api",
            sortOrder = 0,
        ),
        ArtworkProviderStatus(
            provider = ArtworkProviderId.FANART,
            displayName = "Fanart.tv",
            purpose = "Additional posters, backgrounds, and logos.",
            helpText = "Create a Fanart.tv API key in your account settings.",
            keyPageUrl = "https://fanart.tv/get-an-api-key/",
            sortOrder = 1,
        ),
    )

    private data class TmdbMatch(
        val kind: String,
        val id: Int,
        val poster: String?,
        val backdrop: String?,
    )
    private data class ProviderResult(
        val result: ArtworkProviderResult,
        val posters: List<ArtworkAsset> = emptyList(),
        val backdrops: List<ArtworkAsset> = emptyList(),
        val logos: List<ArtworkAsset> = emptyList(),
    )


    private data class TmdbLookup(
        val result: ProviderResult,
        val matches: List<TmdbMatch>,
    )

    private data class HttpResult(val status: Int, val payload: JsonElement?)

    private fun resolveTmdb(
        apiKey: String,
        mediaKey: String,
        name: String,
        releaseYear: Int?,
        mediaType: MediaType,
    ): TmdbLookup {
        val externalId = imdbSuffix(mediaKey)
        val searchUrl = if (externalId != null) {
            "${tmdbBase}/find/${encode(externalId)}?${params("api_key" to apiKey, "external_source" to "imdb_id")}"
        } else {
            val params = mutableListOf("api_key" to apiKey, "query" to name, "include_adult" to "false")
            releaseYear?.let { params += "year" to it.toString() }
            "${tmdbBase}/search/multi?${params(params)}"
        }
        val search = request(searchUrl)
        if (search == null) return TmdbLookup(failed(ArtworkProviderId.TMDB), emptyList())
        if (search.status == 401) return TmdbLookup(invalid(ArtworkProviderId.TMDB), emptyList())
        if (search.status !in 200..299 || search.payload == null) {
            return TmdbLookup(failed(ArtworkProviderId.TMDB), emptyList())
        }

        val matches = parseTmdbMatches(search.payload, mediaType)
        if (matches.isEmpty()) return TmdbLookup(noMatch(ArtworkProviderId.TMDB), emptyList())
        val posters = mutableListOf<ArtworkAsset>()
        val backdrops = mutableListOf<ArtworkAsset>()
        val logos = mutableListOf<ArtworkAsset>()
        matches.forEach { match ->
            match.poster?.let { posters.addUnique(ArtworkAsset(ArtworkProviderId.TMDB, it)) }
            match.backdrop?.let { backdrops.addUnique(ArtworkAsset(ArtworkProviderId.TMDB, it)) }
        }
        var imageLookupFailed = false
        var invalidKey = false
        matches.take(8).forEach { match ->
            val images = request(
                "${tmdbBase}/${match.kind}/${match.id}/images?${params("api_key" to apiKey, "include_image_language" to "en,null")}",
            )
            when {
                images == null -> imageLookupFailed = true
                images.status == 401 -> invalidKey = true
                images.status !in 200..299 || images.payload == null -> imageLookupFailed = true
                else -> parseTmdbImages(images.payload, posters, backdrops, logos)
            }
        }
        val result = when {
            invalidKey -> invalid(ArtworkProviderId.TMDB)
            imageLookupFailed -> failed(ArtworkProviderId.TMDB, posters, backdrops, logos)
            else -> success(ArtworkProviderId.TMDB, posters, backdrops, logos)
        }
        return TmdbLookup(result, matches)
    }

    private fun resolveFanart(
        apiKey: String,
        mediaKey: String,
        mediaType: MediaType,
        tmdbMatches: List<TmdbMatch>,
    ): ProviderResult {
        val externalId = when (mediaType) {
            MediaType.MOVIE -> tmdbMatches.firstOrNull { it.kind == "movie" }?.id?.toString() ?: numericSuffix(mediaKey)
            MediaType.SERIES -> numericSuffix(mediaKey)
            else -> null
        } ?: return missing(ArtworkProviderId.FANART)
        val endpoint = if (mediaType == MediaType.MOVIE) "movies" else "tv"
        val response = request("$fanartBase/$endpoint/${encode(externalId)}", mapOf("api-key" to apiKey))
        if (response == null) return failed(ArtworkProviderId.FANART)
        if (response.status == 401 || response.status == 403) return invalid(ArtworkProviderId.FANART)
        if (response.status == 404) return noMatch(ArtworkProviderId.FANART)
        if (response.status !in 200..299 || response.payload == null) return failed(ArtworkProviderId.FANART)

        val root = response.payload as? JsonObject ?: return noMatch(ArtworkProviderId.FANART)
        val fields = if (mediaType == MediaType.MOVIE) {
            Triple("movieposter", "moviebackground", "hdmovielogo")
        } else {
            Triple("tvposter", "showbackground", "hdtvlogo")
        }
        val posters = parseFanartAssets(root, fields.first)
        val backdrops = parseFanartAssets(root, fields.second)
        val logos = parseFanartAssets(root, fields.third)
        return if (posters.isEmpty() && backdrops.isEmpty() && logos.isEmpty()) {
            noMatch(ArtworkProviderId.FANART)
        } else {
            success(ArtworkProviderId.FANART, posters, backdrops, logos)
        }
    }

    private fun parseTmdbMatches(payload: JsonElement, mediaType: MediaType): List<TmdbMatch> {
        val root = payload as? JsonObject ?: return emptyList()
        val matches = mutableListOf<TmdbMatch>()
        val seen = mutableSetOf<String>()
        fun add(value: JsonElement?, kind: String?) {
            value?.jsonArray?.forEach { element ->
                val item = element as? JsonObject ?: return@forEach
                val resultKind = kind ?: item["media_type"]?.jsonPrimitive?.contentOrNull
                if (resultKind != "movie" && resultKind != "tv") return@forEach
                if (mediaType == MediaType.MOVIE && resultKind != "movie") return@forEach
                if (mediaType == MediaType.SERIES && resultKind != "tv") return@forEach
                val id = item["id"]?.jsonPrimitive?.intOrNull ?: return@forEach
                if (!seen.add("$resultKind:$id")) return@forEach
                matches += TmdbMatch(
                    kind = resultKind,
                    id = id,
                    poster = tmdbPath(item["poster_path"]),
                    backdrop = tmdbPath(item["backdrop_path"]),
                )
            }
        }
        add(root["movie_results"], "movie")
        add(root["tv_results"], "tv")
        add(root["results"], null)
        return matches
    }

    private fun parseTmdbImages(
        payload: JsonElement,
        posters: MutableList<ArtworkAsset>,
        backdrops: MutableList<ArtworkAsset>,
        logos: MutableList<ArtworkAsset>,
    ) {
        val root = payload as? JsonObject ?: return
        addTmdbPaths(root["posters"], posters)
        addTmdbPaths(root["backdrops"], backdrops)
        addTmdbPaths(root["logos"], logos)
    }

    private fun addTmdbPaths(value: JsonElement?, target: MutableList<ArtworkAsset>) {
        value?.jsonArray?.forEach { element ->
            tmdbPath((element as? JsonObject)?.get("file_path"))?.let {
                target.addUnique(ArtworkAsset(ArtworkProviderId.TMDB, it))
            }
        }
    }

    private fun parseFanartAssets(root: JsonObject, field: String): List<ArtworkAsset> =
        root[field]?.jsonArray?.mapNotNull { element ->
            val url = (element as? JsonObject)?.get("url")?.jsonPrimitive?.contentOrNull
            url?.takeIf { it.startsWith("https://") }?.let { ArtworkAsset(ArtworkProviderId.FANART, it) }
        }?.distinctBy(ArtworkAsset::reference).orEmpty()

    private fun request(url: String, headers: Map<String, String> = emptyMap()): HttpResult? = runCatching {
        val request = Request.Builder().url(url).header("accept", "application/json").apply {
            headers.forEach { (name, value) -> header(name, value) }
        }.build()
        httpClient.newCall(request).execute().use { response ->
            HttpResult(
                response.code,
                runCatching { json.parseToJsonElement(response.body.string()) }.getOrNull(),
            )
        }
    }.getOrNull()

    private fun failed(provider: ArtworkProviderId, posters: List<ArtworkAsset> = emptyList(), backdrops: List<ArtworkAsset> = emptyList(), logos: List<ArtworkAsset> = emptyList()) =
        ProviderResult(ArtworkProviderResult(provider, ArtworkLookupStatus.LOOKUP_FAILED), posters, backdrops, logos)

    private fun invalid(provider: ArtworkProviderId) = ProviderResult(ArtworkProviderResult(provider, ArtworkLookupStatus.INVALID_KEY))
    private fun noMatch(provider: ArtworkProviderId) = ProviderResult(ArtworkProviderResult(provider, ArtworkLookupStatus.NO_MATCH))
    private fun missing(provider: ArtworkProviderId) = ProviderResult(ArtworkProviderResult(provider, ArtworkLookupStatus.MISSING_EXTERNAL_ID))
    private fun success(provider: ArtworkProviderId, posters: List<ArtworkAsset>, backdrops: List<ArtworkAsset>, logos: List<ArtworkAsset>) =
        ProviderResult(ArtworkProviderResult(provider, ArtworkLookupStatus.SUCCESS), posters, backdrops, logos)


    private fun MutableList<ArtworkAsset>.addUnique(asset: ArtworkAsset) {
        if (!contains(asset)) add(asset)
    }

    private fun tmdbPath(value: JsonElement?): String? =
        value?.jsonPrimitive?.contentOrNull?.takeIf { it.startsWith("/") }

    private fun imdbSuffix(mediaKey: String): String? = mediaKey.substringAfterLast(':').takeIf { it.matches(Regex("tt\\d+", RegexOption.IGNORE_CASE)) }
    private fun numericSuffix(mediaKey: String): String? = mediaKey.substringAfterLast(':').takeIf { it.matches(Regex("\\d+")) }
    private fun encode(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
    private fun params(vararg values: Pair<String, String>): String = values.joinToString("&") { "${encode(it.first)}=${encode(it.second)}" }
    private fun params(values: List<Pair<String, String>>): String = params(*values.toTypedArray())

    private companion object {
        const val TMDB_BASE = "https://api.themoviedb.org/3"
        const val FANART_BASE = "https://webservice.fanart.tv/v3.2"
    }
}
