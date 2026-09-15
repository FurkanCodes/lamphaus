package com.lamphaus.core.provider

import com.lamphaus.core.model.CatalogQuery
import com.lamphaus.core.model.ProviderFailureKind
import com.lamphaus.core.model.ProviderResult
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * PERF-04/PERF-06: provider bodies stay inside a byte budget, responses are
 * bounded even without a usable Content-Length, identical simultaneous
 * requests coalesce without letting one canceled subscriber kill another
 * subscriber's request, playable listings are never served stale, and
 * outstanding requests are admitted under global and per-provider limits.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HttpProviderClientBoundsTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client(
        cacheBudgetBytes: Long = 8L * 1024 * 1024,
        maxCacheEntryBytes: Long = 2L * 1024 * 1024,
        maxResponseBytes: Long = 8L * 1024 * 1024,
        maxConcurrentRequests: Int = 8,
        maxConcurrentRequestsPerScope: Int = 3,
    ) = HttpProviderClient(
        urlPolicy = ProviderUrlPolicy(allowDebugLocalhost = true),
        cacheBudgetBytes = cacheBudgetBytes,
        maxCacheEntryBytes = maxCacheEntryBytes,
        maxResponseBytes = maxResponseBytes,
        maxConcurrentRequests = maxConcurrentRequests,
        maxConcurrentRequestsPerScope = maxConcurrentRequestsPerScope,
    )

    @Test
    fun `identical simultaneous requests coalesce into one network call`() = runTest {
        server.enqueue(jsonResponse(MANIFEST))
        val client = client()
        val url = server.url("/manifest.json").toString()

        val results = coroutineScope {
            List(4) { async { client.manifest(url) } }.awaitAll()
        }

        assertTrue(results.all { it is ProviderResult.Success })
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `cancelling one waiter keeps the shared request for the other`() = runTest {
        server.enqueue(jsonResponse(MANIFEST).setBodyDelay(200, TimeUnit.MILLISECONDS))
        val client = client()
        val url = server.url("/manifest.json").toString()

        val first = launch { client.manifest(url) }
        runCurrent()
        checkNotNull(server.takeRequest(1, TimeUnit.SECONDS)) { "Shared request never reached the server" }
        val second = async { client.manifest(url) }
        runCurrent()

        first.cancelAndJoin()
        val result = second.await()

        assertTrue(result is ProviderResult.Success)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `byte budget evicts least recently used provider bodies`() = runTest {
        // Each body retains roughly 2x its length plus representation overhead.
        val body = """{"id":"fixture","name":"${"n".repeat(200)}","version":"1"}"""
        val client = client(cacheBudgetBytes = 600)
        val first = server.url("/first.json").toString()
        val second = server.url("/second.json").toString()
        repeat(3) { server.enqueue(jsonResponse(body)) }

        client.manifest(first)
        client.manifest(second)
        client.manifest(first)

        assertEquals(3, server.requestCount)
    }

    @Test
    fun `a body over the per-entry cap is served but never cached`() = runTest {
        val client = client(maxCacheEntryBytes = 64)
        server.enqueue(jsonResponse(MANIFEST))
        server.enqueue(jsonResponse(MANIFEST))
        val url = server.url("/manifest.json").toString()

        client.manifest(url)
        client.manifest(url)

        assertEquals(2, server.requestCount)
        assertNull(server.takeRequest().getHeader("If-None-Match"))
        assertNull(server.takeRequest().getHeader("If-None-Match"))
    }

    @Test
    fun `declared oversized response fails locally without reading the body`() = runTest {
        val client = client(maxResponseBytes = 64)
        server.enqueue(jsonResponse(MANIFEST))

        val result = client.manifest(server.url("/manifest.json").toString())

        assertEquals(ProviderFailureKind.MALFORMED_RESPONSE, (result as ProviderResult.Failure).kind)
    }

    @Test
    fun `chunked oversized response fails locally even without Content-Length`() = runTest {
        val client = client(maxResponseBytes = 64)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setChunkedBody("x".repeat(512), 64),
        )

        val result = client.manifest(server.url("/manifest.json").toString())

        assertEquals(ProviderFailureKind.MALFORMED_RESPONSE, (result as ProviderResult.Failure).kind)
    }

    @Test
    fun `successful revalidation refreshes validators without parsing stale state`() = runTest {
        val client = client()
        server.enqueue(jsonResponse(MANIFEST).addHeader("ETag", "\"v1\""))
        server.enqueue(MockResponse().setResponseCode(304))
        server.enqueue(MockResponse().setResponseCode(304))
        val url = server.url("/manifest.json").toString()

        val first = client.manifest(url) as ProviderResult.Success
        val second = client.manifest(url) as ProviderResult.Success
        val third = client.manifest(url) as ProviderResult.Success

        assertEquals(false, first.isStale)
        assertEquals(false, second.isStale)
        assertEquals(false, third.isStale)
        assertNull(server.takeRequest().getHeader("If-None-Match"))
        assertEquals("\"v1\"", server.takeRequest().getHeader("If-None-Match"))
        assertEquals("\"v1\"", server.takeRequest().getHeader("If-None-Match"))
    }

    @Test
    fun `playable stream listings are never served stale`() = runTest {
        val client = client()
        server.enqueue(jsonResponse("""{"streams":[{"name":"Source","url":"https://cdn.example/movie.m3u8"}]}"""))
        val url = server.url("/manifest.json").toString()

        val first = client.streams(url, "fixture", "movie", "m1")
        server.shutdown()
        val second = client.streams(url, "fixture", "movie", "m1")

        assertTrue(first is ProviderResult.Success)
        assertEquals(ProviderFailureKind.NETWORK, (second as ProviderResult.Failure).kind)
    }

    @Test
    fun `provider invalidation drops cached bodies`() = runTest {
        val client = client()
        server.enqueue(jsonResponse(MANIFEST).addHeader("ETag", "\"v1\""))
        server.enqueue(MockResponse().setResponseCode(304))
        server.enqueue(jsonResponse(MANIFEST).addHeader("ETag", "\"v2\""))
        val url = server.url("/manifest.json").toString()

        client.manifest(url)
        client.manifest(url)
        client.invalidateProvider(url)
        client.manifest(url)

        assertNull(server.takeRequest().getHeader("If-None-Match"))
        assertEquals("\"v1\"", server.takeRequest().getHeader("If-None-Match"))
        assertNull(server.takeRequest().getHeader("If-None-Match"))
    }

    @Test
    fun `per-provider admission bounds simultaneous requests`() = runTest {
        val active = AtomicInteger()
        val peak = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val now = active.incrementAndGet()
                peak.updateAndGet { maxOf(it, now) }
                try {
                    Thread.sleep(100)
                } finally {
                    active.decrementAndGet()
                }
                return jsonResponse("""{"metas":[]}""")
            }
        }
        val client = client(maxConcurrentRequestsPerScope = 2)
        val url = server.url("/manifest.json").toString()

        coroutineScope {
            List(6) { index ->
                async { client.catalog(url, "fixture", CatalogQuery("movie", "featured$index")) }
            }.awaitAll()
        }

        assertTrue("per-provider limit was exceeded: ${peak.get()}", peak.get() <= 2)
        assertEquals(6, server.requestCount)
    }

    private fun jsonResponse(body: String) = MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(body)

    private companion object {
        val MANIFEST = """
            {
              "id":"fixture.provider",
              "name":"Fixture",
              "version":"1.0.0",
              "types":["movie"],
              "resources":[{"name":"catalog","types":["movie"]},"meta","stream","subtitles"],
              "catalogs":[{"type":"movie","id":"featured","name":"Featured"}]
            }
        """.trimIndent()
    }
}
