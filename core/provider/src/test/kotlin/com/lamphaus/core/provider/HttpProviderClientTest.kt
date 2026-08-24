package com.lamphaus.core.provider

import com.lamphaus.core.model.CatalogQuery
import com.lamphaus.core.model.MediaDetail
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.MediaType
import com.lamphaus.core.model.ProviderFailureKind
import com.lamphaus.core.model.ProviderManifest
import com.lamphaus.core.model.ProviderResource
import com.lamphaus.core.model.ProviderResult
import com.lamphaus.core.model.ProviderSubscription
import com.lamphaus.core.model.StreamCandidate
import com.lamphaus.core.model.SubtitleTrack
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HttpProviderClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: HttpProviderClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = HttpProviderClient(ProviderUrlPolicy(allowDebugLocalhost = true))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `manifest parser preserves neutral capabilities`() = runTest {
        server.enqueue(jsonResponse(MANIFEST))

        val result = client.manifest(server.url("/manifest.json").toString())

        val manifest = (result as ProviderResult.Success).value
        assertEquals("fixture.provider", manifest.id)
        assertEquals(setOf("movie", "series"), manifest.types)
        assertEquals(setOf("search", "skip"), manifest.catalogs.single().extras)
        assertEquals("/manifest.json", server.takeRequest().path)
    }

    @Test
    fun `manifest parser preserves inherited filters and behavior hints`() = runTest {
        server.enqueue(
            jsonResponse(
                """{"id":"configured","name":"Configured","version":"1","types":["movie"],"idPrefixes":["tt"],"resources":[{"name":"meta"},{"name":"stream","types":[],"idPrefixes":["custom:"]}],"behaviorHints":{"configurationRequired":true,"p2p":true}}""",
            ),
        )

        val manifest = (client.manifest(server.url("/manifest.json").toString()) as ProviderResult.Success).value

        assertEquals(null, manifest.resources[0].types)
        assertEquals(setOf("custom:"), manifest.resources[1].idPrefixes)
        assertTrue(manifest.behaviorHints.configurationRequired)
        assertTrue(manifest.behaviorHints.p2p)
    }

    @Test
    fun `catalog encodes filters pagination and parses previews`() = runTest {
        server.enqueue(
            jsonResponse(
                """{"metas":[{"id":"m1","type":"movie","name":"Night Signal","genres":["Drama"],"year":"2026","imdbRating":"8.2","background":"https://images.example/backdrop.jpg","logo":"https://images.example/logo.png"}]}""",
            ),
        )

        val result = client.catalog(
            manifestUrl = server.url("/manifest.json").toString(),
            providerId = "fixture.provider",
            query = CatalogQuery("movie", "featured", search = "night sky", skip = 20),
        )

        val items = (result as ProviderResult.Success).value
        assertEquals("Night Signal", items.single().name)
        assertEquals(2026, items.single().releaseYear)
        assertEquals(8.2, items.single().rating)
        assertEquals("https://images.example/backdrop.jpg", items.single().backgroundUrl)
        assertEquals(setOf("fixture.provider"), items.single().providerIds)
        assertEquals(
            "/catalog/movie/featured/search=night%20sky&skip=20.json",
            server.takeRequest().path,
        )
    }

    @Test
    fun `manifest parser preserves required catalog extras`() = runTest {
        server.enqueue(
            jsonResponse(
                """{"id":"fixture","name":"Fixture","version":"1","catalogs":[{"type":"movie","id":"year","name":"New","extra":[{"name":"genre","isRequired":true},{"name":"skip"}]}]}""",
            ),
        )

        val result = client.manifest(server.url("/manifest.json").toString())

        val catalog = (result as ProviderResult.Success).value.catalogs.single()
        assertEquals(setOf("genre", "skip"), catalog.extras)
        assertEquals(setOf("genre"), catalog.requiredExtras)
    }

    @Test
    fun `manifest parser preserves catalog filter options`() = runTest {
        server.enqueue(
            jsonResponse(
                """{"id":"fixture","name":"Fixture","version":"1","catalogs":[{"type":"movie","id":"top","name":"Popular","genres":["Action","Comedy"],"extra":[{"name":"genre","options":["Action","Comedy"]}]}]}""",
            ),
        )

        val manifest = (client.manifest(server.url("/manifest.json").toString()) as ProviderResult.Success).value

        assertEquals(listOf("Action", "Comedy"), manifest.catalogs.single().extraOptions["genre"])
    }

    @Test
    fun `catalog normalizes provider supplied preview metadata variants`() = runTest {
        server.enqueue(
            jsonResponse(
                """{"metas":[{"id":"m1","type":"movie","name":"Night Signal","posterUrl":"https://images.example/poster.jpg","backdrop":"https://images.example/backdrop.jpg","overview":"A mystery.","genre":"Drama","content_rating":"PG-13","ratings":{"imdb":"8.4"}}]}""",
            ),
        )

        val item = (client.catalog(
            server.url("/manifest.json").toString(),
            "fixture.provider",
            CatalogQuery("movie", "featured"),
        ) as ProviderResult.Success).value.single()

        assertEquals("https://images.example/poster.jpg", item.posterUrl)
        assertEquals("https://images.example/backdrop.jpg", item.backgroundUrl)
        assertEquals("A mystery.", item.description)
        assertEquals(listOf("Drama"), item.genres)
        assertEquals("PG-13", item.contentRating)
        assertEquals(8.4, item.rating)
    }

    @Test
    fun `stream parser keeps direct external hash youtube sources and embedded subtitles`() = runTest {
        server.enqueue(
            jsonResponse(
                """{"streams":[
                    {"name":"HTTPS 1080p","url":"https://cdn.example/video.m3u8","behaviorHints":{"proxyHeaders":{"request":{"Authorization":"Bearer test"}}},"subtitles":[{"id":"en","lang":"en","url":"https://cdn.example/en.vtt","format":"vtt"}]},
                    {"name":"Torrent","infoHash":"0123456789abcdef0123456789abcdef01234567","fileIdx":2,"behaviorHints":{"bingeGroup":"show.1080p"}},
                    {"name":"Video","ytId":"abc123"},
                    {"name":"Mirror","externalUrl":"https://player.example/watch/1"}
                ]}""",
            ),
        )

        val result = client.streams(server.url("/manifest.json").toString(), "fixture", "movie", "m1")
        val streams = (result as ProviderResult.Success).value

        assertEquals(4, streams.size)
        assertEquals("Bearer test", streams[0].headers["Authorization"])
        assertEquals("en", streams[0].subtitles.single().language)
        assertEquals(2, streams[1].fileIndex)
        assertEquals("abc123", streams[2].ytId)
        assertEquals("https://player.example/watch/1", streams[3].externalUrl)
    }

    @Test
    fun `stream parser preserves provider formatting and structured hints`() = runTest {
        server.enqueue(
            jsonResponse(
                """{"streams":[{
                    "name":"⚡ 4K · Cached",
                    "title":"Provider title",
                    "description":"📺 Blu-ray · HEVC\n📦 18.4 GB",
                    "tag":["2160p","HDR10+"],
                    "yt_id":"video-id",
                    "mapIdx":3,
                    "behaviorHints":{"filename":"Movie.2160p.BluRay.HEVC.mkv","videoSize":19756849561}
                }]}""",
            ),
        )

        val source = (client.streams(
            server.url("/manifest.json").toString(),
            "fixture",
            "movie",
            "m1",
        ) as ProviderResult.Success).value.single()

        assertEquals("⚡ 4K · Cached", source.name)
        assertEquals("Provider title", source.title)
        assertEquals("📺 Blu-ray · HEVC\n📦 18.4 GB", source.description)
        assertEquals(listOf("2160p", "HDR10+"), source.tags)
        assertEquals("Movie.2160p.BluRay.HEVC.mkv", source.filename)
        assertEquals(19756849561, source.videoSize)
        assertEquals("video-id", source.ytId)
        assertEquals(3, source.fileIndex)
    }

    @Test
    fun `stream parser keeps archive and Usenet sources`() = runTest {
        server.enqueue(
            jsonResponse(
                """{"streams":[
                    {"name":"Archive","rarUrls":[{"url":"https://files.example/movie.rar","bytes":1000}],"fileMustInclude":"/.mkv$/i"},
                    {"name":"Usenet","nzbUrl":"nzb://example/item","servers":["news.example"]}
                ]}""",
            ),
        )

        val streams = (client.streams(server.url("/manifest.json").toString(), "fixture", "movie", "m1") as ProviderResult.Success).value

        assertEquals(2, streams.size)
        assertEquals("https://files.example/movie.rar", streams[0].rarFiles.single().url)
        assertEquals("/.mkv$/i", streams[0].fileMustInclude)
        assertEquals("nzb://example/item", streams[1].nzbUrl)
        assertEquals(listOf("news.example"), streams[1].servers)
    }

    @Test
    fun `metadata parser keeps embedded streams episode dates and runtimes`() = runTest {
        server.enqueue(
            jsonResponse(
                """{"meta":{"id":"show","type":"series","name":"Show","runtime":"1h 32min","streams":[{"name":"Movie","url":"https://cdn.example/movie.mp4"}],"videos":[{"id":"show:1:1","title":"Pilot","released":"2026-08-24T10:00:00.000Z","streams":[{"name":"Episode","url":"https://cdn.example/episode.mp4"}]}]}}""",
            ),
        )

        val detail = (client.meta(server.url("/manifest.json").toString(), "fixture", "series", "show") as ProviderResult.Success).value

        assertEquals(92, detail.runtimeMinutes)
        assertEquals("https://cdn.example/movie.mp4", detail.embeddedStreams.single().url)
        assertEquals("https://cdn.example/episode.mp4", detail.episodes.single().streams.single().url)
        assertTrue(detail.episodes.single().releasedAtEpochMillis != null)
    }

    @Test
    fun `cached catalog is returned stale after a temporary network failure`() = runTest {
        server.enqueue(jsonResponse("""{"metas":[{"id":"m1","type":"movie","name":"Cached"}]}"""))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val address = server.url("/manifest.json").toString()
        val query = CatalogQuery("movie", "featured")

        client.catalog(address, "fixture.provider", query)
        val fallback = client.catalog(address, "fixture.provider", query)

        assertTrue((fallback as ProviderResult.Success).isStale)
        assertEquals("Cached", fallback.value.single().name)
    }

    @Test
    fun `safe redirects are followed`() = runTest {
        server.enqueue(MockResponse().setResponseCode(307).addHeader("Location", "/redirected.json"))
        server.enqueue(jsonResponse(MANIFEST))

        val result = client.manifest(server.url("/manifest.json").toString())

        assertTrue(result is ProviderResult.Success)
        assertEquals("/manifest.json", server.takeRequest().path)
        assertEquals("/redirected.json", server.takeRequest().path)
    }

    @Test
    fun `unsafe redirect downgrade is refused`() = runTest {
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "http://unexpected.example/manifest.json"))

        val result = client.manifest(server.url("/manifest.json").toString())

        assertEquals(ProviderFailureKind.MALFORMED_RESPONSE, (result as ProviderResult.Failure).kind)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `aggregation keeps deterministic order deduplicates and isolates failure`() = runTest {
        val first = preview("shared", "First", "one")
        val richerDuplicate = preview("shared", "First", "two").copy(description = "Details")
        val fake = FakeProviderClient(
            mapOf(
                "one" to ProviderResult.Success(listOf(first)),
                "two" to ProviderResult.Success(listOf(richerDuplicate)),
                "broken" to ProviderResult.Failure(ProviderFailureKind.TIMEOUT, "Provider timed out."),
            ),
        )
        val manifest = ProviderManifest(
            id = "fixture",
            name = "Fixture",
            version = "1",
            resources = listOf(ProviderResource("catalog", setOf("movie"))),
            types = setOf("movie"),
        )
        val providers = listOf("one", "two", "broken").mapIndexed { index, id ->
            ResolvedProvider(ProviderSubscription(id, "https://$id.example/manifest.json", id, sortOrder = index), manifest)
        }

        val slice = ProviderAggregator(fake).catalog(providers, CatalogQuery("movie", "featured"))

        assertEquals(1, slice.items.size)
        assertEquals(setOf("one", "two"), slice.items.single().providerIds)
        assertEquals("Details", slice.items.single().description)
        assertEquals(setOf("broken"), slice.failures.keys)
    }

    private fun preview(id: String, name: String, provider: String) = MediaPreview(
        id = id,
        type = MediaType.MOVIE,
        rawType = "movie",
        name = name,
        providerIds = setOf(provider),
    )

    private fun jsonResponse(body: String) = MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(body)

    private class FakeProviderClient(
        private val catalogs: Map<String, ProviderResult<List<MediaPreview>>>,
    ) : ProviderClient {
        override suspend fun manifest(manifestUrl: String) = error("Not used")
        override suspend fun discoverProviderUrls(catalogUrl: String) = error("Not used")
        override suspend fun catalog(manifestUrl: String, providerId: String, query: CatalogQuery) =
            catalogs.getValue(providerId)
        override suspend fun meta(manifestUrl: String, providerId: String, type: String, id: String): ProviderResult<MediaDetail> =
            error("Not used")
        override suspend fun streams(manifestUrl: String, providerId: String, type: String, id: String): ProviderResult<List<StreamCandidate>> =
            error("Not used")
        override suspend fun subtitles(
            manifestUrl: String,
            type: String,
            id: String,
            extras: Map<String, String>,
        ): ProviderResult<List<SubtitleTrack>> =
            error("Not used")
    }

    private companion object {
        val MANIFEST = """
            {
              "id":"fixture.provider",
              "name":"Fixture",
              "version":"1.0.0",
              "types":["movie","series"],
              "resources":[{"name":"catalog","types":["movie","series"]},"meta","stream","subtitles"],
              "catalogs":[{"type":"movie","id":"featured","name":"Featured","extra":[{"name":"search"},{"name":"skip"}]}]
            }
        """.trimIndent()
    }
}
