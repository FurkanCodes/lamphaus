package com.lamphaus.app.sync

import com.lamphaus.core.data.cloud.CloudChanges
import com.lamphaus.core.data.cloud.CloudCollection
import com.lamphaus.core.data.cloud.CloudDeletion
import com.lamphaus.core.data.cloud.CloudSyncGateway
import com.lamphaus.core.data.cloud.LocalCloudSyncGateway
import com.lamphaus.core.data.preferences.SyncedSettings
import com.lamphaus.core.data.repository.CloudSyncCollection
import com.lamphaus.core.data.repository.LibraryRepository
import com.lamphaus.core.model.ArtworkAsset
import com.lamphaus.core.model.ArtworkOverride
import com.lamphaus.core.model.ArtworkProviderId
import com.lamphaus.core.model.LibraryEntry
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.MediaType
import com.lamphaus.core.model.Profile
import com.lamphaus.core.model.ProfileKind
import com.lamphaus.core.model.ProviderSubscription
import com.lamphaus.core.model.WatchProgress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountSyncTest {
    private val gateway = FakeGateway()
    private val library = FakeLibraryRepository()
    private val cursors = FakeCursors()
    private val settings = FakeSettings()
    private val pushTokens = FakePushTokens()
    private var signedIn: String? = USER
    private var now = 1_000_000L
    private val sync = AccountSync(
        gateway = gateway,
        libraryRepository = library,
        cursors = cursors,
        settings = settings,
        pushTokens = pushTokens,
        installationId = "installation-1",
        television = true,
        signedInUserId = { signedIn },
        elapsedMillis = { now },
    )

    @Test
    fun `SHR-ARC-13 a device without local rows downloads the whole account and keeps its cursor`() = runTest {
        cursors.values[USER] = 41
        gateway.responses += changes(cursor = 42, full = true, profiles = listOf(profile()), library = listOf(entry("a")))

        val outcome = sync.pull(USER, AccountSync.Reason.SIGN_IN)

        assertEquals(1, outcome?.profileCount)
        assertEquals(listOf(0L to true), gateway.requests)
        assertEquals(listOf("movie:a"), library.libraryRows.map { it.mediaKey })
        assertEquals(setOf("movie:a"), library.cloudSyncKeys(PROFILE, CloudSyncCollection.LIBRARY))
        assertEquals(42L, cursors.values[USER])
        assertEquals(listOf(true to null), settings.applied)
    }

    @Test
    fun `SHR-ARC-13 later pulls ask only for changes and apply them`() = runTest {
        gateway.responses += changes(cursor = 5, full = true, profiles = listOf(profile()), library = listOf(entry("a"), entry("b")))
        sync.pull(USER, AccountSync.Reason.SIGN_IN)
        val pulledSettings = SyncedSettings(updatedAtEpochMillis = 7)
        gateway.responses += changes(
            cursor = 9,
            library = listOf(entry("c")),
            settings = pulledSettings,
            deletions = listOf(CloudDeletion(CloudCollection.LIBRARY, PROFILE, "movie:a")),
        )

        sync.pull(USER, AccountSync.Reason.SIGNAL)

        assertEquals(5L to false, gateway.requests.last())
        assertEquals(setOf("movie:b", "movie:c"), library.libraryRows.map { it.mediaKey }.toSet())
        assertEquals(9L, cursors.values[USER])
        assertEquals(true to pulledSettings, settings.applied.last())
    }

    @Test
    fun `SHR-ARC-13 artwork overrides stay in memory and follow changes`() = runTest {
        gateway.responses += changes(cursor = 5, full = true, profiles = listOf(profile()), overrides = listOf(override("a"), override("b")))
        sync.pull(USER, AccountSync.Reason.SIGN_IN)
        assertEquals(setOf("movie:a", "movie:b"), sync.artworkOverrides(PROFILE).first().map { it.mediaKey }.toSet())

        gateway.responses += changes(
            cursor = 6,
            overrides = listOf(override("c")),
            deletions = listOf(CloudDeletion(CloudCollection.ARTWORK_OVERRIDE, PROFILE, "movie:a")),
        )
        sync.pull(USER, AccountSync.Reason.SIGNAL)

        assertEquals(setOf("movie:b", "movie:c"), sync.artworkOverrides(PROFILE).first().map { it.mediaKey }.toSet())
        sync.rememberArtworkOverride(override("d"))
        assertEquals(setOf("movie:b", "movie:c", "movie:d"), sync.artworkOverrides(PROFILE).first().map { it.mediaKey }.toSet())
    }

    @Test
    fun `SHR-ARC-13 a screen that starts right after a pull reuses it`() = runTest {
        library.profileRows += profile()
        gateway.responses += changes(cursor = 5)
        sync.pull(USER, AccountSync.Reason.SIGN_IN)

        now += AccountSync.SCREEN_PULL_INTERVAL_MILLIS - 1
        assertNotNull(sync.pull(USER, AccountSync.Reason.SCREEN))
        assertEquals(1, gateway.requests.size)

        now += 1
        gateway.responses += changes(cursor = 5)
        sync.pull(USER, AccountSync.Reason.SCREEN)
        assertEquals(2, gateway.requests.size)
    }

    @Test
    fun `SHR-ARC-13 a burst of signals collapses into the pulls that can still see something new`() = runTest {
        library.profileRows += profile()
        val release = CompletableDeferred<Unit>()
        gateway.gate = release
        gateway.responses += changes(cursor = 1)
        gateway.responses += changes(cursor = 2)

        launch { sync.pull(USER, AccountSync.Reason.SIGNAL) } // starts now, waits on the network
        runCurrent()
        now += 5
        launch { sync.pull(USER, AccountSync.Reason.SIGNAL) } // arrived during the first pull: needs its own
        runCurrent()
        now += 1
        launch { sync.pull(USER, AccountSync.Reason.SIGNAL) } // answered by the second pull, which starts later
        runCurrent()
        now += 1
        release.complete(Unit)
        runCurrent()

        assertEquals(2, gateway.requests.size)
        assertEquals(2L, cursors.values[USER])
    }

    @Test
    fun `SHR-ARC-13 a failed pull changes nothing and reports failure`() = runTest {
        library.profileRows += profile()
        cursors.values[USER] = 3

        assertNull(sync.pull(USER, AccountSync.Reason.SIGNAL))
        assertEquals(3L, cursors.values[USER])
        assertTrue(settings.applied.isEmpty())
    }

    @Test
    fun `SHR-ARC-05 leaving the account mid-pull writes nothing of it`() = runTest {
        gateway.responses += changes(cursor = 9, full = true, profiles = listOf(profile()), library = listOf(entry("a")))
        gateway.onPull = { signedIn = null }

        assertNull(sync.pull(USER, AccountSync.Reason.SIGN_IN))
        assertTrue(library.libraryRows.isEmpty())
        assertNull(cursors.values[USER])
        assertNull(sync.pull(USER, AccountSync.Reason.SIGNAL))
        assertEquals(1, gateway.requests.size)
    }

    @Test
    fun `SHR-PROD-06 devices without push register for the live signal`() = runTest {
        sync.registerSignals(USER)
        assertEquals(AccountSync.SignalMode.LIVE, sync.signalMode.value)
        assertEquals(Triple("installation-1", null, true), gateway.endpoints.last())

        pushTokens.token = "token-abc"
        sync.registerSignals(USER)
        assertEquals(AccountSync.SignalMode.PUSH, sync.signalMode.value)
        assertEquals(Triple("installation-1", "token-abc", true), gateway.endpoints.last())
    }

    @Test
    fun `SHR-PROD-06 leaving the account forgets its overrides and push token`() = runTest {
        gateway.responses += changes(cursor = 5, full = true, profiles = listOf(profile()), overrides = listOf(override("a")))
        sync.pull(USER, AccountSync.Reason.SIGN_IN)
        pushTokens.token = "token-abc"
        sync.registerSignals(USER)

        sync.forgetAccount()

        assertTrue(sync.artworkOverrides(PROFILE).first().isEmpty())
        assertNull(sync.signalMode.value)
        assertEquals(1, pushTokens.forgotten)
    }

    private fun changes(
        cursor: Long,
        full: Boolean = false,
        profiles: List<Profile> = emptyList(),
        library: List<LibraryEntry> = emptyList(),
        settings: SyncedSettings? = null,
        overrides: List<ArtworkOverride> = emptyList(),
        deletions: List<CloudDeletion> = emptyList(),
    ) = CloudChanges(
        cursor = cursor,
        full = full,
        profileCount = 1,
        hasSettings = true,
        profiles = profiles,
        library = library,
        progress = emptyList(),
        settings = settings,
        artworkOverrides = overrides,
        artworkOverridesComplete = full,
        deletions = deletions,
    )

    private fun profile() = Profile(PROFILE, "Home", "a", ProfileKind.ADULT)

    private fun entry(id: String): LibraryEntry {
        val media = MediaPreview(id, MediaType.MOVIE, "movie", id)
        return LibraryEntry(PROFILE, media.stableKey, media, 1, 1)
    }

    private fun override(id: String) = ArtworkOverride(
        profileId = PROFILE,
        mediaKey = "movie:$id",
        poster = ArtworkAsset(ArtworkProviderId.TMDB, "/$id.jpg"),
        backdrop = null,
        logo = null,
        updatedAtEpochMillis = 1,
    )

    private class FakeGateway : CloudSyncGateway by LocalCloudSyncGateway() {
        val responses = ArrayDeque<CloudChanges>()
        val requests = mutableListOf<Pair<Long, Boolean>>()
        val endpoints = mutableListOf<Triple<String, String?, Boolean>>()
        var gate: CompletableDeferred<Unit>? = null
        var onPull: () -> Unit = {}

        override suspend fun pullChanges(userId: String, since: Long, allArtworkOverrides: Boolean): Result<CloudChanges> {
            requests += since to allArtworkOverrides
            gate?.await()
            onPull()
            return responses.removeFirstOrNull()?.let { Result.success(it) }
                ?: Result.failure(IllegalStateException("offline"))
        }

        override suspend fun registerSyncEndpoint(installationId: String, pushToken: String?, television: Boolean): Result<Unit> {
            endpoints += Triple(installationId, pushToken, television)
            return Result.success(Unit)
        }
    }

    private class FakeCursors : SyncCursors {
        val values = mutableMapOf<String, Long>()
        override suspend fun cursor(userId: String) = values[userId] ?: 0
        override suspend fun setCursor(userId: String, cursor: Long) {
            values[userId] = cursor
        }
    }

    private class FakeSettings : SyncedSettingsSync {
        val applied = mutableListOf<Pair<Boolean, SyncedSettings?>>()
        override suspend fun applyPulled(userId: String, hasSettings: Boolean, remote: SyncedSettings?) {
            applied += hasSettings to remote
        }
        override suspend fun push(userId: String) = Unit
    }

    private class FakePushTokens : SyncPushTokens {
        var token: String? = null
        var forgotten = 0
        override suspend fun token() = token
        override suspend fun forget() {
            forgotten++
        }
    }

    private class FakeLibraryRepository : LibraryRepository {
        val profileRows = mutableListOf<Profile>()
        val libraryRows = mutableListOf<LibraryEntry>()
        val progressRows = mutableListOf<WatchProgress>()
        private val keys = mutableMapOf<Pair<String, CloudSyncCollection>, Set<String>>()

        override fun profiles(): Flow<List<Profile>> = flowOf(profileRows.toList())
        override suspend fun saveProfile(profile: Profile, pin: CharArray?) {
            profileRows.removeAll { it.id == profile.id }
            profileRows += profile
        }
        override suspend fun verifyPin(profileId: String, pin: CharArray) = false
        override suspend fun deleteProfile(profileId: String) = Unit
        override fun providers(): Flow<List<ProviderSubscription>> = flowOf(emptyList())
        override suspend fun saveProvider(provider: ProviderSubscription) = Unit
        override suspend fun setProviderEnabled(providerId: String, enabled: Boolean) = Unit
        override suspend fun removeProvider(providerId: String) = Unit
        override fun library(profileId: String): Flow<List<LibraryEntry>> =
            flowOf(libraryRows.filter { it.profileId == profileId })
        override suspend fun saveLibrary(entry: LibraryEntry) {
            libraryRows.removeAll { it.profileId == entry.profileId && it.mediaKey == entry.mediaKey }
            libraryRows += entry
        }
        override suspend fun removeLibrary(profileId: String, mediaKey: String) {
            libraryRows.removeAll { it.profileId == profileId && it.mediaKey == mediaKey }
        }
        override fun progress(profileId: String): Flow<List<WatchProgress>> =
            flowOf(progressRows.filter { it.profileId == profileId })
        override suspend fun progressEntry(profileId: String, videoId: String) =
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
        override suspend fun cloudSyncKeys(profileId: String, collection: CloudSyncCollection) =
            keys[profileId to collection].orEmpty()
        override suspend fun replaceCloudSyncKeys(profileId: String, collection: CloudSyncCollection, keys: Set<String>) {
            this.keys[profileId to collection] = keys
        }
        override suspend fun clearLocalAccountData() = Unit
    }

    private companion object {
        const val USER = "11111111-1111-1111-1111-111111111111"
        const val PROFILE = "aaaaaaaa-0000-0000-0000-000000000001"
    }
}
