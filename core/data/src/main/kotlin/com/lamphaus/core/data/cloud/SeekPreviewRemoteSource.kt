package com.lamphaus.core.data.cloud

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One title to find seek previews for. [id] is an IMDb (`tt…`) or `tmdb:` id. */
@Serializable
data class SeekPreviewRequest(
    val type: String,
    val id: String,
    val season: Int? = null,
    val episode: Int? = null,
    val durationMs: Long,
)

/** What the server answered for a [SeekPreviewRequest]. */
sealed interface SeekPreviewManifest {
    /**
     * A signed sprite manifest; fetching it needs no credential. Its URL is
     * used exactly as given: the signature covers the query. [scale] is the
     * playing duration over the sprites' source duration (Seekr's `scale`).
     */
    data class Available(val vttUrl: String, val scale: Double = 1.0) : SeekPreviewManifest

    /**
     * [limit] and [retryAfterMillis] come with [Reason.RATE_LIMITED]: which of
     * Seekr's daily allowances is used up, and how long until it lifts.
     */
    data class Unavailable(
        val reason: Reason,
        val limit: Limit? = null,
        val retryAfterMillis: Long? = null,
    ) : SeekPreviewManifest

    /** Seekr counts distinct movies and distinct episodes per key and day separately. */
    enum class Limit { MOVIES, EPISODES, ALL }

    enum class Reason {
        /** No Seekr key on the account. */
        NOT_CONNECTED,

        /** Seekr refused the stored key. */
        KEY_REJECTED,

        /** A daily allowance (or the burst limit) is used up; see [Unavailable.limit]. */
        RATE_LIMITED,

        /** Seekr has no previews for this title or episode. */
        NOT_FOUND,
    }
}

/** Throws on transport failure or a momentary server error (retry later). */
fun interface SeekPreviewRemoteSource {
    suspend fun manifest(request: SeekPreviewRequest): SeekPreviewManifest
}

/**
 * Calls the `resolve-seek-previews` Edge Function. The account's Seekr key
 * stays on the server; only the signed manifest URL comes back (SHR-PROD-06),
 * which is what makes one saved key work on every device of the account.
 */
class SupabaseSeekPreviewRemoteSource(
    private val supabase: SupabaseClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : SeekPreviewRemoteSource {

    override suspend fun manifest(request: SeekPreviewRequest): SeekPreviewManifest {
        val response = supabase.functions.buildEdgeFunction(FUNCTION_RESOLVE_SEEK_PREVIEWS)
            .invoke(json.encodeToString(request)) { contentType(ContentType.Application.Json) }
        val body = response.bodyAsText()
        // Signed out (401) behaves like an account without a key.
        if (response.status.value == 401) {
            return SeekPreviewManifest.Unavailable(SeekPreviewManifest.Reason.NOT_CONNECTED)
        }
        if (!response.status.isSuccess()) {
            throw SupabaseFunctionException(
                statusCode = response.status.value,
                responseCode = extractFunctionErrorCode(json, body),
                message = "edge function returned ${response.status.value}: " +
                    CloudLog.clamp(CloudLog.sanitize(body)),
            )
        }
        val wire = json.decodeFromString<WireManifest>(body)
        val vttUrl = wire.vttUrl
        if (wire.available && vttUrl != null && vttUrl.startsWith("https://")) {
            val scale = wire.scale?.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
            return SeekPreviewManifest.Available(vttUrl, scale)
        }
        if (wire.reason == "rate_limited") {
            return SeekPreviewManifest.Unavailable(
                reason = SeekPreviewManifest.Reason.RATE_LIMITED,
                limit = when (wire.scope) {
                    "movie" -> SeekPreviewManifest.Limit.MOVIES
                    "episode" -> SeekPreviewManifest.Limit.EPISODES
                    else -> SeekPreviewManifest.Limit.ALL
                },
                retryAfterMillis = wire.retryAfterSeconds?.takeIf { it > 0 }?.times(1000),
            )
        }
        return SeekPreviewManifest.Unavailable(
            when (wire.reason) {
                "not_connected" -> SeekPreviewManifest.Reason.NOT_CONNECTED
                "key_rejected" -> SeekPreviewManifest.Reason.KEY_REJECTED
                else -> SeekPreviewManifest.Reason.NOT_FOUND
            },
        )
    }

    @Serializable
    private data class WireManifest(
        val available: Boolean = false,
        val vttUrl: String? = null,
        val scale: Double? = null,
        val reason: String? = null,
        val scope: String? = null,
        val retryAfterSeconds: Long? = null,
    )

    private companion object {
        const val FUNCTION_RESOLVE_SEEK_PREVIEWS = "resolve-seek-previews"
    }
}
