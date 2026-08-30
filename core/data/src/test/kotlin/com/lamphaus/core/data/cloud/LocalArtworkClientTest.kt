package com.lamphaus.core.data.cloud

import com.lamphaus.core.model.ArtworkLookupStatus
import com.lamphaus.core.model.ArtworkProviderId
import com.lamphaus.core.model.MediaType
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocalArtworkClientTest {
    private lateinit var server: HttpServer
    private val requests = mutableListOf<Pair<String, String?>>()

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            requests += exchange.requestURI.toString() to exchange.requestHeaders.getFirst("api-key")
            val body = when {
                path.endsWith("/find/tt123") -> """
                    {"movie_results":[{"id":123,"poster_path":"/poster.jpg","backdrop_path":"/backdrop.jpg"}]}
                """.trimIndent()
                path.endsWith("/movie/123/images") -> """
                    {"posters":[{"file_path":"/poster-large.jpg"}],"backdrops":[],"logos":[]}
                """.trimIndent()
                path.endsWith("/movies/123") -> """
                    {"movieposter":[{"url":"https://assets.fanart.tv/poster.jpg"}],"moviebackground":[],"hdmovielogo":[]}
                """.trimIndent()
                else -> "{}"
            }.toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.executor = Executors.newCachedThreadPool()
        server.start()
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun `local resolver calls artwork providers directly with local keys`() = runBlocking {
        val baseUrl = "http://127.0.0.1:${server.address.port}"
        val client = LocalArtworkClient(
            httpClient = OkHttpClient(),
            tmdbBase = "$baseUrl/tmdb",
            fanartBase = "$baseUrl/fanart",
        )

        val result = client.candidates(
            keys = mapOf(
                ArtworkProviderId.TMDB to "tmdb-secret",
                ArtworkProviderId.FANART to "fanart-secret",
            ),
            mediaKey = "imdb:tt123",
            name = "Fixture",
            releaseYear = 2024,
            mediaType = MediaType.MOVIE,
        ).getOrThrow()

        assertEquals(ArtworkLookupStatus.SUCCESS, result.providerResults[0].status)
        assertEquals(ArtworkLookupStatus.SUCCESS, result.providerResults[1].status)
        assertTrue(result.posters.any { it.reference == "/poster-large.jpg" })
        assertTrue(result.posters.any { it.reference == "https://assets.fanart.tv/poster.jpg" })
        assertEquals(3, requests.size)
        assertTrue(requests[0].first.contains("/tmdb/find/tt123"))
        assertTrue(requests[0].first.contains("api_key=tmdb-secret"))
        assertTrue(requests[1].first.contains("api_key=tmdb-secret"))
        assertEquals("fanart-secret", requests[2].second)
        assertTrue(requests[2].first.endsWith("/fanart/movies/123"))
    }
}
