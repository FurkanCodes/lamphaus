package com.lamphaus.core.data.playback

import com.lamphaus.core.data.cloud.SeekPreviewManifest
import com.lamphaus.core.data.cloud.SeekPreviewRemoteSource
import com.lamphaus.core.data.cloud.SeekPreviewRequest
import com.lamphaus.core.model.Episode
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.MediaType
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SeekPreviewRepositoryTest {

    private val manifestUrl = "https://sprites.example/t/603/index.vtt?sig=abc"

    private val vtt = """
        WEBVTT

        00:00:00.000 --> 00:00:10.000
        https://sprites.example/t/603/sheet-0.jpg?sig=1#xywh=0,0,320,180

        00:00:10.000 --> 00:00:20.000
        sheet-0.jpg?sig=1#xywh=320,0,320,180

        00:01:00.000 --> 00:01:10.000 align:start
        https://sprites.example/t/603/sheet-1.jpg?sig=2#xywh=0,180,320,180
    """.trimIndent()

    private class FakeRemote(var answer: SeekPreviewManifest) : SeekPreviewRemoteSource {
        val requests = mutableListOf<SeekPreviewRequest>()
        override suspend fun manifest(request: SeekPreviewRequest): SeekPreviewManifest {
            requests += request
            return answer
        }
    }

    private class FakeDownloader(private val body: String?) : SeekPreviewDownloader {
        val urls = mutableListOf<String>()
        override suspend fun text(url: String): String? {
            urls += url
            return body
        }
        override suspend fun file(url: String, target: File): Boolean = false
    }

    private fun movie(id: String = "tt0133093") = MediaPreview(id = id, type = MediaType.MOVIE, rawType = "movie", name = "The Matrix")

    private fun series(id: String = "tt0903747") = MediaPreview(id = id, type = MediaType.SERIES, rawType = "series", name = "Show")

    @Test
    fun `vtt parses times, relative tiles, and cue settings`() {
        val cues = SeekPreviewVtt.parse(vtt, manifestUrl)
        assertEquals(3, cues.size)
        assertEquals(10_000L, cues[1].startMillis)
        assertEquals("https://sprites.example/t/603/sheet-0.jpg?sig=1", cues[1].tile.sheetUrl)
        assertEquals(SeekPreviewTile("https://sprites.example/t/603/sheet-1.jpg?sig=2", 0, 180, 320, 180), cues[2].tile)
        assertEquals(70_000L, cues[2].endMillis)
    }

    @Test
    fun `vtt drops non https and malformed tiles`() {
        val hostile = """
            WEBVTT

            00:00.000 --> 00:10.000
            file:///data/data/secret.jpg#xywh=0,0,320,180

            00:10.000 --> 00:20.000
            https://sprites.example/a.jpg#xywh=0,0,-1,180

            00:20.000 --> 00:30.000
            https://sprites.example/a.jpg
        """.trimIndent()
        assertEquals(emptyList<SeekPreviewCue>(), SeekPreviewVtt.parse(hostile, "https://sprites.example/x.vtt"))
    }

    @Test
    fun `cue lookup picks the cue covering the position and clamps at the ends`() {
        val track = SeekPreviewTrack(SeekPreviewVtt.parse(vtt, manifestUrl))
        assertEquals(0L, track.cueAt(-5)?.startMillis)
        assertEquals(10_000L, track.cueAt(18_500)?.startMillis)
        assertEquals(10_000L, track.cueAt(30_000)?.startMillis)
        assertEquals(60_000L, track.cueAt(9_999_999)?.startMillis)
        assertEquals(2, track.sheetUrls.size)
    }

    @Test
    fun `scale maps the playing position onto the sprites' timeline`() {
        // Playing runs 2% long (scale 1.02): 61.2 s here is 60 s in the sprites.
        val track = SeekPreviewTrack(SeekPreviewVtt.parse(vtt, manifestUrl), scale = 1.02)
        assertEquals(60_000L, track.cueAt(61_200)?.startMillis)
        assertEquals(10_000L, track.cueAt(61_100)?.startMillis)
    }

    @Test
    fun `scale from the server reaches the track`() = runTest {
        val repository = SeekPreviewRepository(FakeRemote(SeekPreviewManifest.Available(manifestUrl, scale = 2.0)), FakeDownloader(vtt))
        val track = repository.track(movie(), null, 8_160_000)
        assertEquals(10_000L, track?.cueAt(20_000)?.startMillis)
    }

    @Test
    fun `available manifest loads once from its signed URL untouched and is cached`() = runTest {
        val remote = FakeRemote(SeekPreviewManifest.Available(manifestUrl))
        val downloader = FakeDownloader(vtt)
        val repository = SeekPreviewRepository(remote, downloader)
        assertNotNull(repository.track(movie(), null, 8_160_000))
        assertNotNull(repository.track(movie(), null, 8_160_000))
        assertEquals(1, remote.requests.size)
        assertEquals(manifestUrl, downloader.urls.single())
        assertEquals(SeekPreviewRequest(type = "movie", id = "tt0133093", durationMs = 8_160_000), remote.requests.single())
    }

    @Test
    fun `series need season and episode and send the show id`() = runTest {
        val remote = FakeRemote(SeekPreviewManifest.Available(manifestUrl))
        val repository = SeekPreviewRepository(remote, FakeDownloader(vtt))
        assertNull(repository.track(series(), Episode(id = "e", title = "E", season = null, episode = 3), 1_000))
        assertNotNull(repository.track(series(), Episode(id = "e", title = "E", season = 2, episode = 3), 1_000))
        assertEquals(
            SeekPreviewRequest(type = "series", id = "tt0903747", season = 2, episode = 3, durationMs = 1_000),
            remote.requests.single(),
        )
    }

    @Test
    fun `provider scoped ids never reach the server`() = runTest {
        val remote = FakeRemote(SeekPreviewManifest.Available(manifestUrl))
        val repository = SeekPreviewRepository(remote, FakeDownloader(vtt))
        assertNull(repository.track(movie("kitsu:42"), null, 1_000))
        assertNull(repository.track(movie("tt"), null, 1_000))
        assertNotNull(repository.track(movie("tmdb:movie:603"), null, 1_000))
        assertEquals(listOf("tmdb:movie:603"), remote.requests.map(SeekPreviewRequest::id))
    }

    @Test
    fun `no key pauses lookups until the integration changes here`() = runTest {
        val remote = FakeRemote(SeekPreviewManifest.Unavailable(SeekPreviewManifest.Reason.NOT_CONNECTED))
        val repository = SeekPreviewRepository(remote, FakeDownloader(vtt), clock = { 0L })
        assertNull(repository.track(movie(), null, 1_000))
        assertNull(repository.track(movie("tt1"), null, 1_000))
        assertEquals(1, remote.requests.size)
        remote.answer = SeekPreviewManifest.Available(manifestUrl)
        repository.invalidate()
        assertNotNull(repository.track(movie("tt1"), null, 1_000))
    }

    @Test
    fun `a key saved on another device is found after the pause`() = runTest {
        var now = 0L
        val remote = FakeRemote(SeekPreviewManifest.Unavailable(SeekPreviewManifest.Reason.NOT_CONNECTED))
        val repository = SeekPreviewRepository(remote, FakeDownloader(vtt), clock = { now })
        assertNull(repository.track(movie(), null, 1_000))
        remote.answer = SeekPreviewManifest.Available(manifestUrl)
        now = 16 * 60 * 1000L
        assertNotNull(repository.track(movie(), null, 1_000))
    }

    @Test
    fun `rate limit pauses lookups for an hour`() = runTest {
        var now = 0L
        val remote = FakeRemote(SeekPreviewManifest.Unavailable(SeekPreviewManifest.Reason.RATE_LIMITED))
        val repository = SeekPreviewRepository(remote, FakeDownloader(vtt), clock = { now })
        assertNull(repository.track(movie(), null, 1_000))
        now = 59 * 60 * 1000L
        assertNull(repository.track(movie(), null, 1_000))
        assertEquals(1, remote.requests.size)
        now = 61 * 60 * 1000L
        remote.answer = SeekPreviewManifest.Available(manifestUrl)
        assertNotNull(repository.track(movie(), null, 1_000))
    }

    @Test
    fun `transient failures are retried on the next seek`() = runTest {
        var fail = true
        val remote = SeekPreviewRemoteSource {
            if (fail) error("offline") else SeekPreviewManifest.Available(manifestUrl)
        }
        val repository = SeekPreviewRepository(remote, FakeDownloader(vtt))
        assertNull(repository.track(movie(), null, 1_000))
        fail = false
        assertNotNull(repository.track(movie(), null, 1_000))
    }

    @Test
    fun `without a cloud there is never a lookup`() = runTest {
        assertNull(SeekPreviewRepository(remote = null, downloader = FakeDownloader(vtt)).track(movie(), null, 1_000))
    }
}
