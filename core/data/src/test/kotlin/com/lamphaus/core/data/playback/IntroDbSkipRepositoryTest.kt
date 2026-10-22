package com.lamphaus.core.data.playback

import com.lamphaus.core.model.Episode
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.MediaType
import com.lamphaus.core.model.PlaybackSegment
import com.lamphaus.core.model.PlaybackSegmentType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Collections

class IntroDbSkipRepositoryTest {

    // IntroDB and TheIntroDB are asked in parallel, so record from either thread.
    private val requests: MutableList<Url> = Collections.synchronizedList(mutableListOf())
    private val userAgents: MutableList<String?> = Collections.synchronizedList(mutableListOf())

    /** TheIntroDB answers with [theIntroDbBody]; IntroDB has nothing, so TheIntroDB's segments are used. */
    private fun repository(theIntroDbBody: String) = IntroDbSkipRepository(
        client = HttpClient(
            MockEngine { request ->
                requests += request.url
                userAgents += request.headers[HttpHeaders.UserAgent]
                if (request.url.host == "api.theintrodb.org") {
                    respond(theIntroDbBody, HttpStatusCode.OK)
                } else {
                    respond("", HttpStatusCode.NotFound)
                }
            },
        ) {
            install(HttpTimeout)
        },
        userAgent = "Lamphaus/1.0.0",
    )

    @Test
    fun `episodes are looked up on TheIntroDB v4`() = runTest {
        // Live v4 body for Breaking Bad S1E1: keyed by tvdb_id, without tmdb_id.
        val repository = repository(
            """{"tvdb_id":81189,"type":"tv","season":1,"episode":1,"intro":[{"start_ms":228664,"end_ms":246143}],"credits":[{"start_ms":3431000,"end_ms":null}]}""",
        )
        val segments = repository.segments(
            MediaPreview(id = "tt0903747", type = MediaType.SERIES, rawType = "series", name = "Breaking Bad"),
            Episode(id = "tt0903747:1:1", title = "Pilot", season = 1, episode = 1),
        )

        val request = requests.single { it.host == "api.theintrodb.org" }
        assertEquals("https://api.theintrodb.org/v4/media", "${request.protocol.name}://${request.host}${request.encodedPath}")
        assertEquals("tt0903747", request.parameters["imdb_id"])
        assertEquals("1", request.parameters["season"])
        assertEquals("1", request.parameters["episode"])
        assertEquals(listOf("Lamphaus/1.0.0", "Lamphaus/1.0.0"), userAgents)
        assertEquals(
            listOf(
                PlaybackSegment(PlaybackSegmentType.INTRO, 228_664, 246_143),
                PlaybackSegment(PlaybackSegmentType.ENDING, 3_431_000, null),
            ),
            segments,
        )
    }

    @Test
    fun `movies addressed by TMDB send only the TMDB id to v4`() = runTest {
        // Live v4 body for Fight Club; an intro that starts at the beginning has a null start.
        val repository = repository(
            """{"tvdb_id":247,"type":"movie","intro":[{"start_ms":null,"end_ms":119000}],"credits":[{"start_ms":8177000,"end_ms":8348000}]}""",
        )
        val segments = repository.segments(
            MediaPreview(id = "tmdb:movie:550", type = MediaType.MOVIE, rawType = "movie", name = "Fight Club"),
            episode = null,
        )

        val request = requests.single()
        assertEquals("/v4/media", request.encodedPath)
        assertEquals(setOf("tmdb_id"), request.parameters.names())
        assertEquals("550", request.parameters["tmdb_id"])
        assertEquals(
            listOf(
                PlaybackSegment(PlaybackSegmentType.INTRO, 0, 119_000),
                PlaybackSegment(PlaybackSegmentType.ENDING, 8_177_000, 8_348_000),
            ),
            segments,
        )
    }
}
