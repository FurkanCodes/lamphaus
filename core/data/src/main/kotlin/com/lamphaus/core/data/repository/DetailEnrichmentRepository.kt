package com.lamphaus.core.data.repository

import com.lamphaus.core.data.cloud.CloudNotConfiguredException
import com.lamphaus.core.data.cloud.DetailEnrichmentRemoteSource
import com.lamphaus.core.data.local.DetailEnrichmentDao
import com.lamphaus.core.data.local.DetailEnrichmentEntity
import com.lamphaus.core.model.DetailEnrichment
import com.lamphaus.core.model.DetailEnrichmentRequest
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.enrichmentMediaKey
import com.lamphaus.core.model.wireName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Room-backed cache in front of [DetailEnrichmentRemoteSource]
 * (SHR-ARC-05/06/13): cached content is exposed immediately, refreshed when
 * missing or older than the TTL, and stale rows survive a failed refresh so
 * the affected sections degrade instead of disappearing (SHR-PROD-04).
 */
interface DetailEnrichmentRepository {
    fun observe(mediaKey: String): Flow<DetailEnrichment?>

    /**
     * Writes the freshest enrichment into the cache. Returns success without
     * network when the cached row is still inside the TTL, unless [force].
     */
    suspend fun refresh(
        media: MediaPreview,
        force: Boolean = false,
    ): Result<Unit>

    /**
     * Drops every cached row. Credential changes (artwork TMDB key, integration
     * credentials, enabled rating sources) change what the edge would return;
     * a 12h-TTL cache would otherwise serve stale empty sections (SHR-ARC-05).
     */
    suspend fun invalidate()
}

class DefaultDetailEnrichmentRepository(
    private val dao: DetailEnrichmentDao,
    private val remote: DetailEnrichmentRemoteSource?,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
    private val clock: () -> Long = System::currentTimeMillis,
) : DetailEnrichmentRepository {

    override fun observe(mediaKey: String): Flow<DetailEnrichment?> =
        dao.observeDetailEnrichment(mediaKey).map { it?.decodeOrNull() }

    override suspend fun invalidate() {
        dao.clearDetailEnrichment()
    }

    override suspend fun refresh(
        media: MediaPreview,
        force: Boolean,
    ): Result<Unit> {
        val remote = remote ?: return Result.failure(CloudNotConfiguredException())
        val mediaKey = media.enrichmentMediaKey()
        val cached = dao.detailEnrichment(mediaKey)
        if (!force && cached != null && clock() - cached.fetchedAtEpochMillis < CACHE_TTL_MILLIS) {
            return Result.success(Unit)
        }
        return runCatching {
            val fresh = remote.fetch(
                DetailEnrichmentRequest(
                    mediaKey = mediaKey,
                    type = media.type.wireName(),
                    id = media.id,
                    name = media.name,
                    releaseYear = media.releaseYear,
                ),
            )
            dao.upsertDetailEnrichment(
                DetailEnrichmentEntity(
                    mediaKey = mediaKey,
                    payloadJson = json.encodeToString(DetailEnrichment.serializer(), fresh),
                    fetchedAtEpochMillis = clock(),
                ),
            )
            // Keep the cache bounded; enrichment rows are re-fetchable.
            dao.pruneDetailEnrichment(clock() - PRUNE_AFTER_MILLIS)
        }
    }

    private fun DetailEnrichmentEntity.decodeOrNull(): DetailEnrichment? = try {
        json.decodeFromString(DetailEnrichment.serializer(), payloadJson)
    } catch (_: SerializationException) {
        // A corrupt payload is treated as absent rather than crashing the screen.
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private companion object {
        /** Spec: persist for considerably longer than Nuvio's in-memory cache (~12h). */
        const val CACHE_TTL_MILLIS = 12 * 60 * 60 * 1000L

        /** Rows older than this can never satisfy a read again; drop them. */
        const val PRUNE_AFTER_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}
