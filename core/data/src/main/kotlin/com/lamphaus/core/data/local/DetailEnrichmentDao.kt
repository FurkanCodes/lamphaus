package com.lamphaus.core.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Narrow seam for the enrichment cache (SHR-ARC-15); implemented by [LamphausDao]. */
@Dao
interface DetailEnrichmentDao {
    @Query("SELECT * FROM detail_enrichment WHERE mediaKey = :mediaKey")
    fun observeDetailEnrichment(mediaKey: String): Flow<DetailEnrichmentEntity?>

    @Query("SELECT * FROM detail_enrichment WHERE mediaKey = :mediaKey LIMIT 1")
    suspend fun detailEnrichment(mediaKey: String): DetailEnrichmentEntity?

    @Upsert
    suspend fun upsertDetailEnrichment(entity: DetailEnrichmentEntity)

    @Query("DELETE FROM detail_enrichment WHERE fetchedAtEpochMillis < :olderThanEpochMillis")
    suspend fun pruneDetailEnrichment(olderThanEpochMillis: Long)
}
