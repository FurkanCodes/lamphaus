package com.lamphaus.core.player

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * PERF-10: sidecar downloads are bounded, per-source headers are preserved,
 * failures retry once, and cancellation disconnects the blocking read instead
 * of waiting for the timeout.
 */
class SidecarSubtitleLoaderTest {
    private lateinit var server: HttpServer
    private val requestCount = AtomicInteger()
    private val receivedHeaders = Collections.synchronizedList(mutableListOf<String>())

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newCachedThreadPool()
        server.start()
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    private fun respond(
        path: String,
        status: Int = 200,
        body: ByteArray = VTT.toByteArray(),
        delayMillis: Long = 0,
    ) {
        server.createContext(path) { exchange: HttpExchange ->
            requestCount.incrementAndGet()
            receivedHeaders += exchange.requestHeaders.getFirst("X-Fixture").orEmpty()
            if (delayMillis > 0) Thread.sleep(delayMillis)
            try {
                exchange.sendResponseHeaders(status, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            } catch (_: Exception) {
                // A canceled client can close the connection mid-response.
            } finally {
                exchange.close()
            }
        }
    }

    private fun url(path: String) = "http://127.0.0.1:${server.address.port}$path"

    private val loader = SidecarSubtitleLoader(
        headerSource = { mapOf("X-Fixture" to "fixture-header") },
    )

    @Test
    fun `parses vtt and forwards per-source headers`() = runBlocking {
        respond("/subtitles.vtt")

        val cues = loader.load(url("/subtitles.vtt"))

        assertNotNull(cues)
        assertEquals(2, cues!!.size)
        assertEquals("fixture-header", receivedHeaders.single())
    }

    @Test
    fun `oversized sidecar is a recoverable failure and is not truncated`() = runBlocking {
        val loader = SidecarSubtitleLoader(maxBytes = 1_024)
        respond("/huge.vtt", body = ByteArray(16 * 1_024) { 'a'.code.toByte() })

        val cues = loader.load(url("/huge.vtt"))

        assertNull(cues)
    }

    @Test
    fun `failed request retries once`() = runBlocking {
        respond("/missing.vtt", status = 404)

        val cues = loader.load(url("/missing.vtt"))

        assertNull(cues)
        assertEquals(2, requestCount.get())
    }

    @Test
    fun `cancellation disconnects the blocking read without retrying`() = runBlocking {
        val started = CountDownLatch(1)
        server.createContext("/slow.vtt") { exchange: HttpExchange ->
            requestCount.incrementAndGet()
            started.countDown()
            Thread.sleep(10_000)
            runCatching { exchange.close() }
        }

        val load = async(kotlinx.coroutines.Dispatchers.IO) { loader.load(url("/slow.vtt")) }
        assertTrue("request never reached the server", started.await(2, TimeUnit.SECONDS))
        val canceled = withTimeoutOrNull(5_000) {
            load.cancelAndJoin()
            true
        }

        assertTrue("cancellation did not unblock the read", canceled == true)
        assertEquals(1, requestCount.get())
    }

    private companion object {
        val VTT = """
            WEBVTT

            00:00:01.000 --> 00:00:02.000
            First line

            00:00:03.000 --> 00:00:04.000
            Second line
        """.trimIndent()
    }
}
