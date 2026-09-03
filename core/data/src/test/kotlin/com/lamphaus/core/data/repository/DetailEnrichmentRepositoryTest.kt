package com.lamphaus.core.data.repository

import com.lamphaus.core.data.cloud.CloudNotConfiguredException
import com.lamphaus.core.data.local.DetailEnrichmentDao
import com.lamphaus.core.data.local.DetailEnrichmentEntity
import com.lamphaus.core.model.DetailEnrichment
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.MediaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailEnrichmentRepositoryTest {

    private class FakeDetailEnrichmentDao : DetailEnrichmentDao {
        val rows = mutableMapOf<String, DetailEnrichmentEntity>()
        val flow = MutableStateFlow<DetailEnrichmentEntity?>(null)
        var upserts = 0
        var prunedOlderThan: Long? = null

        override fun observeDetailEnrichment(mediaKey: String): Flow<DetailEnrichmentEntity?> = flow

        override suspend fun detailEnrichment(mediaKey: String): DetailEnrichmentEntity? = rows[mediaKey]

        override suspend fun upsertDetailEnrichment(entity: DetailEnrichmentEntity) {
            upserts += 1
            rows[entity.mediaKey] = entity
            flow.value = entity
        }

        override suspend fun pruneDetailEnrichment(olderThanEpochMillis: Long) {
            prunedOlderThan = olderThanEpochMillis
        }
    }

    private class FakeRemote(
        private val result: Result<DetailEnrichment>,
    ) : com.lamphaus.core.data.cloud.DetailEnrichmentRemoteSource {
        var calls = 0

        override suspend fun fetch(request: com.lamphaus.core.model.DetailEnrichmentRequest): DetailEnrichment {
            calls += 1
            return result.getOrThrow()
        }
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val now = 1_000_000_000L

    private fun media(id: String = "tt0133093") = MediaPreview(
        id = id,
        type = MediaType.MOVIE,
        rawType = "movie",
        name = "Example",
    )

    private fun enrichment(mediaKey: String, fetchedAt: Long = now) = DetailEnrichment(
        mediaKey = mediaKey,
        cast = listOf(com.lamphaus.core.model.PersonCredit(name = "Actor")),
        fetchedAtEpochMillis = fetchedAt,
    )

    private fun repository(
        dao: FakeDetailEnrichmentDao,
        remote: FakeRemote?,
        clock: () -> Long = { now },
    ) = DefaultDetailEnrichmentRepository(dao, remote, json, clock)

    @Test
    fun `imdb id maps to canonical imdb media key`() = runTest {
        val dao = FakeDetailEnrichmentDao()
        val remote = FakeRemote(Result.success(enrichment("imdb:tt0133093")))
        repository(dao, remote).refresh(media())

        assertEquals("imdb:tt0133093", dao.rows.keys.single())
    }

    @Test
    fun `refresh when cache is inside the ttl skips the network`() = runTest {
        val dao = FakeDetailEnrichmentDao()
        val mediaKey = "imdb:tt0133093"
        dao.rows[mediaKey] = DetailEnrichmentEntity(mediaKey, json.encodeToString(DetailEnrichment.serializer(), enrichment(mediaKey)), now - 1_000)
        val remote = FakeRemote(Result.success(enrichment(mediaKey)))

        val result = repository(dao, remote).refresh(media())

        assertTrue(result.isSuccess)
        assertEquals(0, remote.calls)
    }

    @Test
    fun `refresh when cache is older than the ttl hits the network`() = runTest {
        val dao = FakeDetailEnrichmentDao()
        val mediaKey = "imdb:tt0133093"
        val staleAt = now - 13 * 60 * 60 * 1000L
        dao.rows[mediaKey] = DetailEnrichmentEntity(mediaKey, json.encodeToString(DetailEnrichment.serializer(), enrichment(mediaKey, staleAt)), staleAt)
        val remote = FakeRemote(Result.success(enrichment(mediaKey)))

        val result = repository(dao, remote).refresh(media())

        assertTrue(result.isSuccess)
        assertEquals(1, remote.calls)
        assertEquals(now, dao.rows.getValue(mediaKey).fetchedAtEpochMillis)
    }

    @Test
    fun `failed refresh preserves the stale row and reports failure`() = runTest {
        val dao = FakeDetailEnrichmentDao()
        val mediaKey = "imdb:tt0133093"
        val staleAt = now - 13 * 60 * 60 * 1000L
        val staleJson = json.encodeToString(DetailEnrichment.serializer(), enrichment(mediaKey, staleAt))
        dao.rows[mediaKey] = DetailEnrichmentEntity(mediaKey, staleJson, staleAt)
        val remote = FakeRemote(Result.failure(IllegalStateException("network down")))

        val result = repository(dao, remote).refresh(media())

        assertTrue(result.isFailure)
        assertEquals(staleJson, dao.rows.getValue(mediaKey).payloadJson)
    }

    @Test
    fun `missing remote reports cloud not configured`() = runTest {
        val dao = FakeDetailEnrichmentDao()

        val result = repository(dao, remote = null).refresh(media())

        assertTrue(result.exceptionOrNull() is CloudNotConfiguredException)
    }

    @Test
    fun `observe decodes cached payload and treats corrupt payloads as absent`() = runTest {
        val dao = FakeDetailEnrichmentDao()
        val repository = repository(dao, null)
        val mediaKey = "imdb:tt0133093"

        assertNull(repository.observe(mediaKey).firstOrNull())

        dao.flow.value = DetailEnrichmentEntity(mediaKey, "not json", now)
        assertNull(repository.observe(mediaKey).firstOrNull())

        dao.flow.value = DetailEnrichmentEntity(mediaKey, json.encodeToString(DetailEnrichment.serializer(), enrichment(mediaKey)), now)
        assertEquals("Actor", repository.observe(mediaKey).firstOrNull()?.cast?.single()?.name)
    }

    @Test
    fun `successful network refresh prunes rows past the retention window`() = runTest {
        val dao = FakeDetailEnrichmentDao()
        val remote = FakeRemote(Result.success(enrichment("imdb:tt0133093")))

        repository(dao, remote).refresh(media())

        assertEquals(now - 30L * 24 * 60 * 60 * 1000, dao.prunedOlderThan)
    }
}
