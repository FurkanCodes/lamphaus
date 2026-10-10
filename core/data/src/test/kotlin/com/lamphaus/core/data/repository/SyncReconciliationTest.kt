package com.lamphaus.core.data.repository

import com.lamphaus.core.data.cloud.CloudCollection
import com.lamphaus.core.data.cloud.CloudDeletion
import com.lamphaus.core.model.LibraryEntry
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.MediaType
import com.lamphaus.core.model.Profile
import com.lamphaus.core.model.ProviderSubscription
import com.lamphaus.core.model.WatchProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncReconciliationTest {
    @Test
    fun `SHR-ARC-05 cloud absence preserves a never-synced local library row`() = runTest {
        val repository = FakeLibraryRepository().apply { saveLibrary(entry("local-only")) }

        repository.reconcileLibrary(PROFILE_ID, emptyList())

        assertEquals(listOf("movie:local-only"), repository.libraryRows.map { it.mediaKey })
    }

    @Test
    fun `SHR-ARC-05 missing previously-cloud-backed library row is deleted`() = runTest {
        val repository = FakeLibraryRepository().apply {
            saveLibrary(entry("remote-row"))
            replaceCloudSyncKeys(
                PROFILE_ID,
                CloudSyncCollection.LIBRARY,
                setOf("movie:remote-row"),
            )
        }

        repository.reconcileLibrary(PROFILE_ID, emptyList())

        assertTrue(repository.libraryRows.isEmpty())
    }

    @Test
    fun `SHR-ARC-05 progress snapshot updates without deleting upload-pending progress`() = runTest {
        val localOnly = progress("local-only")
        val cloudRow = progress("cloud-row")
        val repository = FakeLibraryRepository().apply { saveProgress(localOnly) }

        repository.reconcileProgress(PROFILE_ID, listOf(cloudRow))

        assertEquals(setOf("local-only", "cloud-row"), repository.progressRows.map { it.videoId }.toSet())
        assertEquals(
            setOf("cloud-row"),
            repository.cloudSyncKeys(PROFILE_ID, CloudSyncCollection.PROGRESS),
        )
    }

    @Test
    fun `SHR-ARC-05 an incremental pull removes marked rows and keeps everything it does not mention`() = runTest {
        val watchedElsewhere = progress("watched-elsewhere")
        val repository = FakeLibraryRepository().apply {
            saveLibrary(entry("kept"))
            saveLibrary(entry("removed-elsewhere"))
            saveProgress(watchedElsewhere)
            replaceCloudSyncKeys(PROFILE_ID, CloudSyncCollection.LIBRARY, setOf("movie:kept", "movie:removed-elsewhere"))
            replaceCloudSyncKeys(PROFILE_ID, CloudSyncCollection.PROGRESS, setOf("watched-elsewhere"))
        }

        repository.applyCloudDelta(
            library = listOf(entry("added-elsewhere")),
            progress = emptyList(),
            deletions = listOf(
                CloudDeletion(CloudCollection.LIBRARY, PROFILE_ID, "movie:removed-elsewhere"),
                CloudDeletion(CloudCollection.PROGRESS, PROFILE_ID, "watched-elsewhere"),
            ),
        )

        assertEquals(setOf("movie:kept", "movie:added-elsewhere"), repository.libraryRows.map { it.mediaKey }.toSet())
        assertTrue(repository.progressRows.isEmpty())
        assertEquals(
            setOf("movie:kept", "movie:added-elsewhere"),
            repository.cloudSyncKeys(PROFILE_ID, CloudSyncCollection.LIBRARY),
        )
        assertTrue(repository.cloudSyncKeys(PROFILE_ID, CloudSyncCollection.PROGRESS).isEmpty())
    }

    @Test
    fun `SHR-ARC-05 a row removed and added again elsewhere stays`() = runTest {
        val repository = FakeLibraryRepository().apply { saveLibrary(entry("again")) }

        repository.applyCloudDelta(
            library = listOf(entry("again")),
            progress = listOf(progress("again")),
            deletions = listOf(
                CloudDeletion(CloudCollection.LIBRARY, PROFILE_ID, "movie:again"),
                CloudDeletion(CloudCollection.PROGRESS, PROFILE_ID, "again"),
            ),
        )

        assertEquals(listOf("movie:again"), repository.libraryRows.map { it.mediaKey })
        assertEquals(listOf("again"), repository.progressRows.map { it.videoId })
        assertEquals(setOf("again"), repository.cloudSyncKeys(PROFILE_ID, CloudSyncCollection.PROGRESS))
    }

    @Test
    fun `SHR-ARC-05 an earlier upload of this device's progress never rewinds newer local progress`() = runTest {
        val newerLocal = progress("playing").copy(positionMillis = 80, updatedAtEpochMillis = 20)
        val olderUpload = progress("playing").copy(positionMillis = 40, updatedAtEpochMillis = 10)
        val repository = FakeLibraryRepository().apply { saveProgress(newerLocal) }

        repository.applyCloudDelta(library = emptyList(), progress = listOf(olderUpload), deletions = emptyList())

        assertEquals(80L, repository.progressRows.single().positionMillis)
        assertEquals(setOf("playing"), repository.cloudSyncKeys(PROFILE_ID, CloudSyncCollection.PROGRESS))
    }

    @Test
    fun `SHR-ARC-05 artwork removals leave library and progress alone`() = runTest {
        val repository = FakeLibraryRepository().apply { saveLibrary(entry("poster")) }

        repository.applyCloudDelta(
            library = emptyList(),
            progress = emptyList(),
            deletions = listOf(CloudDeletion(CloudCollection.ARTWORK_OVERRIDE, PROFILE_ID, "movie:poster")),
        )

        assertEquals(listOf("movie:poster"), repository.libraryRows.map { it.mediaKey })
    }

    private fun entry(id: String): LibraryEntry {
        val media = MediaPreview(id, MediaType.MOVIE, "movie", id)
        return LibraryEntry(PROFILE_ID, media.stableKey, media, 1, 1)
    }

    private fun progress(id: String): WatchProgress {
        val media = MediaPreview(id, MediaType.MOVIE, "movie", id)
        return WatchProgress(PROFILE_ID, media.stableKey, id, 50, 100, false, 1, media)
    }

    private class FakeLibraryRepository : LibraryRepository {
        val libraryRows = mutableListOf<LibraryEntry>()
        val progressRows = mutableListOf<WatchProgress>()
        private val snapshots = mutableMapOf<Pair<String, CloudSyncCollection>, Set<String>>()

        override fun profiles(): Flow<List<Profile>> = flowOf(emptyList())
        override suspend fun saveProfile(profile: Profile, pin: CharArray?) = Unit
        override suspend fun verifyPin(profileId: String, pin: CharArray) = false
        override suspend fun deleteProfile(profileId: String) = Unit
        override fun providers(): Flow<List<ProviderSubscription>> = flowOf(emptyList())
        override suspend fun saveProvider(provider: ProviderSubscription) = Unit
        override suspend fun setProviderEnabled(providerId: String, enabled: Boolean) = Unit
        override suspend fun removeProvider(providerId: String) = Unit
        override fun library(profileId: String): Flow<List<LibraryEntry>> = flowOf(libraryRows.toList())
        override suspend fun saveLibrary(entry: LibraryEntry) {
            libraryRows.removeAll { it.profileId == entry.profileId && it.mediaKey == entry.mediaKey }
            libraryRows += entry
        }
        override suspend fun removeLibrary(profileId: String, mediaKey: String) {
            libraryRows.removeAll { it.profileId == profileId && it.mediaKey == mediaKey }
        }
        override fun progress(profileId: String): Flow<List<WatchProgress>> = flowOf(progressRows.toList())
        override suspend fun progressEntry(profileId: String, videoId: String): WatchProgress? =
            progressRows.firstOrNull { it.profileId == profileId && it.videoId == videoId }
        override suspend fun saveProgress(progress: WatchProgress): WatchProgress {
            progressRows.removeAll { it.profileId == progress.profileId && it.videoId == progress.videoId }
            progressRows += progress
            return progress
        }
        override suspend fun removeProgress(profileId: String, videoId: String) {
            progressRows.removeAll { it.profileId == profileId && it.videoId == videoId }
        }

        override suspend fun removeProgress(profileId: String, videoIds: List<String>) {
            progressRows.removeAll { it.profileId == profileId && it.videoId in videoIds }
        }
        override suspend fun cloudSyncKeys(
            profileId: String,
            collection: CloudSyncCollection,
        ): Set<String> = snapshots[profileId to collection].orEmpty()
        override suspend fun replaceCloudSyncKeys(
            profileId: String,
            collection: CloudSyncCollection,
            keys: Set<String>,
        ) {
            snapshots[profileId to collection] = keys
        }
        override suspend fun clearLocalAccountData() {
            libraryRows.clear()
            progressRows.clear()
            snapshots.clear()
        }
    }

    private companion object {
        const val PROFILE_ID = "profile"
    }
}
