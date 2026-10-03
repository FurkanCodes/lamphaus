package com.lamphaus.core.data.cloud

import com.lamphaus.core.model.PersonCreditsRequest
import com.lamphaus.core.model.PersonFilmography
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

/** Fetches one person's titles; an interface so repositories test with fakes (SHR-ARC-15). */
fun interface PersonCreditsRemoteSource {
    suspend fun fetch(request: PersonCreditsRequest): PersonFilmography
}

/**
 * Supabase implementation: the `resolve-person-credits` Edge Function asks
 * TMDB with the account's own key, which never reaches the client
 * (SHR-PROD-06).
 */
class SupabasePersonCreditsRemoteDataSource(
    private val supabase: SupabaseClient,
    private val json: Json,
) : PersonCreditsRemoteSource {
    override suspend fun fetch(request: PersonCreditsRequest): PersonFilmography {
        val response = supabase.functions.buildEdgeFunction(FUNCTION_RESOLVE_PERSON_CREDITS)
            .invoke(json.encodeToString(PersonCreditsRequest.serializer(), request)) {
                contentType(ContentType.Application.Json)
            }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw SupabaseFunctionException(
                statusCode = response.status.value,
                responseCode = extractFunctionErrorCode(json, body),
                message = "edge function returned ${response.status.value}: " +
                    CloudLog.clamp(CloudLog.sanitize(body)),
            )
        }
        return json.decodeFromString(PersonFilmography.serializer(), body)
    }

    private companion object {
        const val FUNCTION_RESOLVE_PERSON_CREDITS = "resolve-person-credits"
    }
}
