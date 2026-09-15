package com.lamphaus.core.data.repository

import com.lamphaus.core.data.local.LamphausDao
import com.lamphaus.core.data.local.LibraryEntity
import com.lamphaus.core.data.local.ProfileEntity
import com.lamphaus.core.data.local.ProviderEntity
import com.lamphaus.core.data.local.WatchProgressEntity
import com.lamphaus.core.data.security.StringCipher
import com.lamphaus.core.model.MediaType
import com.lamphaus.core.model.Profile
import com.lamphaus.core.model.ProfileKind
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PERF-03/SHR-ARC-11: repository entry points must be safe to call from the
 * main thread. PBKDF2 and JSON mapping belong on the injected CPU dispatcher,
 * keystore encryption/decryption on the injected IO dispatcher, and supplied
 * PIN arrays must be cleared on every path.
 */
class RoomLibraryRepositoryDispatcherTest {

    private class RecordingDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
        val dispatches = AtomicInteger()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatches.incrementAndGet()
            delegate.dispatch(context, block)
        }
    }

    /** Reflection proxy so the dispatcher test cannot silently depend on Room. */
    private class FakeDao {
        var profileRow: ProfileEntity? = null
        var upsertedProfile: ProfileEntity? = null
        var failUpsert = false
        val observeProfiles = MutableStateFlow<List<ProfileEntity>>(emptyList())
        val observeProviders = MutableStateFlow<List<ProviderEntity>>(emptyList())
        val observeLibrary = MutableStateFlow<List<LibraryEntity>>(emptyList())
        val observeProgress = MutableStateFlow<List<WatchProgressEntity>>(emptyList())
        val savedProviders = mutableListOf<ProviderEntity>()
        val savedLibrary = mutableListOf<LibraryEntity>()
        val savedProgress = mutableListOf<WatchProgressEntity>()

        fun build(): LamphausDao = Proxy.newProxyInstance(
            LamphausDao::class.java.classLoader,
            arrayOf(LamphausDao::class.java),
        ) { _, method, args ->
            when (method.name) {
                "toString" -> "FakeDao"
                "hashCode" -> System.identityHashCode(this)
                "equals" -> false
                "observeProfiles" -> observeProfiles
                "observeProviders" -> observeProviders
                "observeLibrary" -> observeLibrary
                "observeProgress" -> observeProgress
                "profile" -> profileRow
                "upsertProfile" -> {
                    if (failUpsert) error("Profile persistence failed")
                    upsertedProfile = args!![0] as ProfileEntity
                }
                "upsertProvider" -> savedProviders += args!![0] as ProviderEntity
                "upsertLibrary" -> savedLibrary += args!![0] as LibraryEntity
                "upsertProgressSticky" -> {
                    savedProgress += args!![0] as WatchProgressEntity
                    args[0]
                }
                else -> error("DAO ${method.name} is not stubbed")
            }
        } as LamphausDao
    }

    private class FakeCipher : StringCipher {
        override fun encrypt(value: String): String = "enc:$value"
        override fun decrypt(value: String): String = value.removePrefix("enc:")
    }

    private fun repository(
        dao: FakeDao,
        io: RecordingDispatcher,
        cpu: RecordingDispatcher,
    ) = RoomLibraryRepository(
        dao = dao.build(),
        stringCipher = FakeCipher(),
        ioDispatcher = io,
        cpuDispatcher = cpu,
    )

    @Test
    fun `provider decryption runs on the io dispatcher`() = runTest {
        val dao = FakeDao()
        dao.observeProviders.value = listOf(
            ProviderEntity("p1", "enc:https://provider.example/manifest.json", "Provider", true, 0, 1),
        )
        val io = RecordingDispatcher(Dispatchers.IO)
        val cpu = RecordingDispatcher(Dispatchers.Default)

        val providers = repository(dao, io, cpu).providers().first()

        assertEquals("https://provider.example/manifest.json", providers.single().manifestUrl)
        assertTrue(io.dispatches.get() > 0)
        assertEquals(0, cpu.dispatches.get())
    }

    @Test
    fun `library and progress decoding run on the cpu dispatcher`() = runTest {
        val dao = FakeDao()
        val preview = """{"id":"m1","type":"movie","rawType":"movie","name":"Night Signal"}"""
        dao.observeLibrary.value = listOf(
            LibraryEntity("profile", "m1", preview, 1, 1),
            LibraryEntity("profile", "broken", "{not json", 2, 2),
        )
        dao.observeProgress.value = listOf(
            WatchProgressEntity("profile", "m1", "m1", 10, 100, false, 1, preview, null),
            WatchProgressEntity("profile", "m2", "m2", 10, 100, false, 1, "{not json", null),
        )
        val io = RecordingDispatcher(Dispatchers.IO)
        val cpu = RecordingDispatcher(Dispatchers.Default)
        val repository = repository(dao, io, cpu)

        val library = repository.library("profile").first()
        val progress = repository.progress("profile").first()

        assertEquals("library dropped malformed rows", 1, library.size)
        assertEquals("Night Signal", library.single().preview.name)
        // Progress rows stay even when a preview snapshot is unreadable; only
        // the snapshot drops (Continue Watching still shows the entry).
        assertEquals(2, progress.size)
        assertNotNull(progress.first().preview)
        assertEquals(null, progress.last().preview)
        assertTrue(cpu.dispatches.get() > 0)
        assertEquals(0, io.dispatches.get())
    }

    @Test
    fun `saveProfile hashes on the cpu dispatcher and clears the pin`() = runTest {
        val dao = FakeDao()
        val io = RecordingDispatcher(Dispatchers.IO)
        val cpu = RecordingDispatcher(Dispatchers.Default)
        val repository = repository(dao, io, cpu)
        val pin = "1234".toCharArray()

        repository.saveProfile(profile(), pin)

        assertTrue(cpu.dispatches.get() > 0)
        assertEquals(0, io.dispatches.get())
        assertTrue(pin.all { it == '\u0000' })
        val stored = dao.upsertedProfile ?: error("Profile was not persisted")
        assertNotNull(stored.pinHash)
        assertNotNull(stored.pinSalt)
    }

    @Test
    fun `saveProfile clears the pin when persistence fails`() = runTest {
        val dao = FakeDao().apply { failUpsert = true }
        val repository = repository(dao, RecordingDispatcher(Dispatchers.IO), RecordingDispatcher(Dispatchers.Default))
        val pin = "4321".toCharArray()

        runCatching { repository.saveProfile(profile(), pin) }

        assertTrue(pin.all { it == '\u0000' })
    }

    @Test
    fun `verifyPin hashes on the cpu dispatcher and clears every early return`() = runTest {
        val dao = FakeDao()
        val io = RecordingDispatcher(Dispatchers.IO)
        val cpu = RecordingDispatcher(Dispatchers.Default)
        val repository = repository(dao, io, cpu)

        // Learn the real salt/hash by saving once, then verify against it.
        val creationPin = "2468".toCharArray()
        repository.saveProfile(profile(), creationPin)
        dao.profileRow = dao.upsertedProfile
        val cpuBefore = cpu.dispatches.get()

        val accepted = "2468".toCharArray()
        val rejected = "1111".toCharArray()
        assertTrue(repository.verifyPin("profile", accepted))
        assertFalse(repository.verifyPin("profile", rejected))
        assertTrue(cpu.dispatches.get() > cpuBefore + 1)
        assertTrue(accepted.all { it == '\u0000' })
        assertTrue(rejected.all { it == '\u0000' })

        val missing = "9999".toCharArray()
        assertFalse(repository.verifyPin("missing", missing))
        assertTrue(missing.all { it == '\u0000' })

        dao.profileRow = dao.profileRow?.copy(pinSalt = null)
        val noSalt = "8888".toCharArray()
        assertFalse(repository.verifyPin("profile", noSalt))
        assertTrue(noSalt.all { it == '\u0000' })
    }

    @Test
    fun `writes encode json on the cpu dispatcher and encrypt providers on io`() = runTest {
        val dao = FakeDao()
        val io = RecordingDispatcher(Dispatchers.IO)
        val cpu = RecordingDispatcher(Dispatchers.Default)
        val repository = repository(dao, io, cpu)

        repository.saveProvider(
            com.lamphaus.core.model.ProviderSubscription("p1", "https://provider.example/manifest.json", "Provider"),
        )
        repository.saveLibrary(
            com.lamphaus.core.model.LibraryEntry(
                profileId = "profile",
                mediaKey = "m1",
                preview = com.lamphaus.core.model.MediaPreview(
                    id = "m1",
                    type = MediaType.MOVIE,
                    rawType = "movie",
                    name = "Night Signal",
                ),
                addedAtEpochMillis = 1,
                updatedAtEpochMillis = 1,
            ),
        )
        repository.saveProgress(
            com.lamphaus.core.model.WatchProgress(
                profileId = "profile",
                mediaKey = "m1",
                videoId = "m1",
                positionMillis = 10,
                durationMillis = 100,
                completed = false,
                updatedAtEpochMillis = 1,
                preview = com.lamphaus.core.model.MediaPreview(
                    id = "m1",
                    type = MediaType.MOVIE,
                    rawType = "movie",
                    name = "Night Signal",
                ),
            ),
        )

        assertEquals("enc:https://provider.example/manifest.json", dao.savedProviders.single().manifestUrl)
        assertTrue(dao.savedLibrary.single().previewJson.contains("\"Night Signal\""))
        assertTrue(dao.savedProgress.single().previewJson?.contains("\"Night Signal\"") == true)
        assertTrue(io.dispatches.get() > 0)
        assertTrue(cpu.dispatches.get() > 0)
    }

    private fun profile() = Profile(
        id = "profile",
        name = "Viewer",
        avatarKey = "moon",
        kind = ProfileKind.ADULT,
        updatedAtEpochMillis = 1,
    )
}
