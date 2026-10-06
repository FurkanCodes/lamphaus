package com.lamphaus.core.player.network

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** PLY-NET-01: parallel range downloads against a local server (QA-01, QA-05). */
@RunWith(AndroidJUnit4::class)
class ParallelRangeDataSourceTest {
    private val content = Random(42).nextBytes(3 * 1024 * 1024 + 12_345)
    private val server = MockWebServer()
    private val ranges = Collections.synchronizedList(mutableListOf<String>())
    private val rateLimitsLeft = AtomicInteger(0)
    private var supportsRanges = true

    private val chunkBytes = 256L * 1024L

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val range = request.getHeader("Range")
                if (range != null && rateLimitsLeft.getAndDecrement() > 0) {
                    return MockResponse().setResponseCode(429).setHeader("Retry-After", "0")
                }
                if (!supportsRanges || range == null) {
                    return MockResponse().setResponseCode(200).setBody(Buffer().write(content))
                }
                ranges += range
                val (start, endInclusive) = range.removePrefix("bytes=").split("-").let { parts ->
                    val from = parts[0].toLong()
                    val to = parts.getOrNull(1)?.takeIf(String::isNotEmpty)?.toLong() ?: (content.size - 1L)
                    from to minOf(to, content.size - 1L)
                }
                return MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Range", "bytes $start-$endInclusive/${content.size}")
                    .setHeader("Accept-Ranges", "bytes")
                    .setBody(Buffer().write(content, start.toInt(), (endInclusive - start + 1).toInt()))
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        RangeSessions.clear()
        server.shutdown()
    }

    private fun factory(prefetch: Boolean = true, memory: ChunkMemory = HeapChunkMemory(keep = 2)) = ParallelRangeDataSource.Factory(
        upstream = OkHttpDataSource.Factory(OkHttpClient()),
        settings = ParallelDownloadSettings(
            connections = 2,
            chunkBytes = chunkBytes,
            depth = 3,
            sessionChunkCap = 7,
            memory = memory,
        ),
        prefetchAllowed = { prefetch },
        eligible = { true },
    )

    private fun readAll(
        position: Long = 0L,
        prefetch: Boolean = true,
        factory: ParallelRangeDataSource.Factory = factory(prefetch),
    ): ByteArray {
        val source = factory.createDataSource()
        val uri = Uri.parse(server.url("/movie.mkv").toString())
        val length = source.open(DataSpec.Builder().setUri(uri).setPosition(position).build())
        assertEquals(content.size - position, length)
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = source.read(buffer, 0, buffer.size)
            if (read == C.RESULT_END_OF_INPUT) break
            out.write(buffer, 0, read)
        }
        source.close()
        return out.toByteArray()
    }

    @Test
    fun readsTheWholeFileInRangedChunks() {
        assertArrayEquals(content, readAll())
        // The probe read to the first boundary; later chunks were ranged requests.
        assertEquals("bytes=0-${chunkBytes - 1}", ranges.first())
        assertTrue("expected several chunk requests, got $ranges", ranges.size >= content.size / chunkBytes)
    }

    @Test
    fun offHeapChunksReadBackIntact() {
        assertArrayEquals(content, readAll(factory = factory(memory = NativeChunkMemory(keep = 2))))
    }

    @Test
    fun aServerWithoutRangesPlaysOverOneConnection() {
        supportsRanges = false
        assertArrayEquals(content, readAll())
    }

    @Test
    fun aSeekReadsFromItsPositionAndReusesTheSession() {
        // One player's factory: its settings key the shared session.
        val player = factory()
        assertArrayEquals(content, readAll(factory = player))
        val requestsBefore = ranges.size
        // The session still holds the last chunks it read.
        val position = 11L * chunkBytes
        assertArrayEquals(content.copyOfRange(position.toInt(), content.size), readAll(position, factory = player))
        assertTrue("reopen fetched ${ranges.drop(requestsBefore)}", ranges.size - requestsBefore <= 1)
    }

    @Test
    fun aMidChunkSeekStreamsToTheBoundary() {
        val position = chunkBytes + 1_000L
        assertArrayEquals(content.copyOfRange(position.toInt(), content.size), readAll(position, prefetch = false))
        assertEquals("bytes=$position-${2 * chunkBytes - 1}", ranges.first())
    }

    @Test
    fun rateLimitedChunksBackOffAndComplete() {
        val before = ParallelDownloadStats.rateLimitedResponses.get()
        rateLimitsLeft.set(0)
        val source = factory().createDataSource()
        val uri = Uri.parse(server.url("/movie.mkv").toString())
        source.open(DataSpec.Builder().setUri(uri).build())
        // The probe succeeded; the first chunk requests are refused once.
        rateLimitsLeft.set(2)
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = source.read(buffer, 0, buffer.size)
            if (read == C.RESULT_END_OF_INPUT) break
            out.write(buffer, 0, read)
        }
        source.close()
        assertArrayEquals(content, out.toByteArray())
        assertTrue(ParallelDownloadStats.rateLimitedResponses.get() > before)
    }
}
