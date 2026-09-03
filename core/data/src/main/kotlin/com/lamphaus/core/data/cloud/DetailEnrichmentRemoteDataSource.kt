package com.lamphaus.core.data.cloud

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import com.lamphaus.core.model.DetailEnrichment
import com.lamphaus.core.model.DetailEnrichmentRequest

/**
 * Fetches provider-neutral detail enrichment. Sealed behind an interface so
 * repositories are testable with fakes (SHR-ARC-15).
 */
fun interface DetailEnrichmentRemoteSource {
    suspend fun fetch(request: DetailEnrichmentRequest): DetailEnrichment
}

/**
 * Supabase implementation: calls the `resolve-detail-enrichment` Edge
 * Function, which aggregates TMDB and the user's MDBList integration
 * server-side. The TMDB credential and the MDBList key never reach the
 * client (SHR-PROD-06).
 */
class SupabaseDetailEnrichmentRemoteDataSource(
    private val supabase: SupabaseClient,
    private val json: Json,
) : DetailEnrichmentRemoteSource {
    override suspend fun fetch(request: DetailEnrichmentRequest): DetailEnrichment {
        val body = supabase.functions.buildEdgeFunction(FUNCTION_RESOLVE_DETAIL_ENRICHMENT)
            .invoke(json.encodeToString(request)) {
                contentType(ContentType.Application.Json)
            }
            .bodyAsText()
        return json.decodeFromString(body)
    }

    private companion object {
        const val FUNCTION_RESOLVE_DETAIL_ENRICHMENT = "resolve-detail-enrichment"
    }
}
