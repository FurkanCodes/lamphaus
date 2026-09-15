package com.lamphaus.core.data.repository

import com.lamphaus.core.data.local.LamphausDao
import com.lamphaus.core.data.local.WatchProgressEntity
import com.lamphaus.core.data.local.LibraryEntity
import java.lang.reflect.Proxy
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PERF-11 scale smoke check. This measures only the repository's CPU-side
 * decode/mapping cost on the host JVM at 100 / 1,000 / 10,000 synthetic rows;
 * it deliberately excludes Room/SQLite query time, which needs a device. The
 * printed numbers feed `docs/performance/results.md`; the assertion is a
 * generous regression guard against accidental super-linear behavior, not a
 * performance gate. Run with `--tests '...LibraryScaleDecodeTest'` and read the
 * `PERF-11` lines from the test output.
 */
class LibraryScaleDecodeTest {

    private class FakeDao(
        private val progress: List<WatchProgressEntity>,
        private val library: List<LibraryEntity>,
    ) {
        fun build(): LamphausDao = Proxy.newProxyInstance(
            LamphausDao::class.java.classLoader,
            arrayOf(LamphausDao::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "toString" -> "FakeDao"
                "hashCode" -> System.identityHashCode(this)
                "equals" -> false
                "observeProgress" -> MutableStateFlow(progress)
                "observeLibrary" -> MutableStateFlow(library)
                else -> error("DAO ${method.name} is not stubbed")
            }
        } as LamphausDao
    }

    private fun previewJson(index: Int): String = """
        {"id":"m$index","type":"movie","rawType":"movie","name":"Synthetic title $index",
         "description":"Synthetic description for scale measurement $index",
         "releaseYear":2026,"genres":["Drama","Science fiction"],"contentRating":"PG-13",
         "rating":7.5,"providerIds":["fixture"]}
    """.trimIndent()

    private fun progressRows(count: Int): List<WatchProgressEntity> = List(count) { index ->
        WatchProgressEntity(
            profileId = "profile",
            mediaKey = "m$index",
            videoId = "m$index",
            positionMillis = 1_000L,
            durationMillis = 100_000L,
            completed = false,
            updatedAtEpochMillis = index.toLong(),
            previewJson = previewJson(index),
            episodeLabel = null,
        )
    }

    private fun libraryRows(count: Int): List<LibraryEntity> = List(count) { index ->
        LibraryEntity(
            profileId = "profile",
            mediaKey = "m$index",
            previewJson = previewJson(index),
            addedAtEpochMillis = index.toLong(),
            updatedAtEpochMillis = index.toLong(),
        )
    }

    @Test
    fun `progress decode stays linear at 100 1000 and 10000 rows`() {
        val timings = mutableListOf<Pair<Int, Long>>()
        for (count in listOf(100, 1_000, 10_000)) {
            val repository = RoomLibraryRepository(
                dao = FakeDao(progressRows(count), libraryRows(count)).build(),
                stringCipher = object : com.lamphaus.core.data.security.StringCipher {
                    override fun encrypt(value: String) = value
                    override fun decrypt(value: String) = value
                },
                ioDispatcher = Dispatchers.IO,
                cpuDispatcher = Dispatchers.Default,
            )
            // Warm up JIT/inline caches once, then measure the same decode.
            var decoded = 0
            repeat(2) {
                val elapsed = measureTimeMillis {
                    decoded = runBlocking { repository.progress("profile").first().size }
                }
                if (it == 1) timings += count to elapsed
            }
            assertEquals(count, decoded)
        }

        timings.forEach { (rows, millis) -> println("PERF-11 progress decode rows=$rows millis=$millis") }
        val tenThousand = timings.first { it.first == 10_000 }.second
        assertTrue(
            "10,000-row decode took ${tenThousand}ms; expected well under a second on the host JVM",
            tenThousand < 5_000,
        )
    }

    @Test
    fun `library decode stays linear at 100 1000 and 10000 rows`() {
        for (count in listOf(100, 1_000, 10_000)) {
            val repository = RoomLibraryRepository(
                dao = FakeDao(progressRows(count), libraryRows(count)).build(),
                stringCipher = object : com.lamphaus.core.data.security.StringCipher {
                    override fun encrypt(value: String) = value
                    override fun decrypt(value: String) = value
                },
                ioDispatcher = Dispatchers.IO,
                cpuDispatcher = Dispatchers.Default,
            )
            repeat(2) {
                val elapsed = measureTimeMillis { runBlocking { repository.library("profile").first() } }
                if (it == 1) println("PERF-11 library decode rows=$count millis=$elapsed")
            }
        }
    }
}