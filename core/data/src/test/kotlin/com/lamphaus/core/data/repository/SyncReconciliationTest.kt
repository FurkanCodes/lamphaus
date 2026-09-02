package com.lamphaus.core.data.repository

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
