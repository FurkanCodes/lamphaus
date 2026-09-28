package com.lamphaus.core.data.trailer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeTrailerExtractorTest {

    private fun video(height: Int, mime: String = "video/mp4; codecs=\"avc1.640028\"", bitrate: Long = 1_000L) =
        YouTubeFormat(url = "https://v/$height/$mime/$bitrate", mimeType = mime, height = height, bitrate = bitrate)

    @Test
    fun `video ranking caps height and prefers h264 at equal height`() {
        val formats = listOf(
            video(1600, "video/webm; codecs=\"vp9\""),
            video(1080, "video/webm; codecs=\"vp9\"", bitrate = 9_000),
            video(1080),
            video(720),
            video(2160, "video/mp4; codecs=\"av01.0.12M.08\""),
        )

        val ranked = rankVideo(formats, maxHeight = 1080)

        assertEquals(1080, ranked.first().height)
        assertTrue(ranked.first().isMp4)
        assertEquals(720, ranked[2].height)
        assertEquals(1600, ranked.last().height)
        assertTrue(ranked.none { "av01" in it.mimeType })
    }

    @Test
    fun `video over the cap is used only when nothing fits`() {
        val ranked = rankVideo(listOf(video(1440), video(2160)), maxHeight = 720)

        assertEquals(listOf(1440, 2160), ranked.map { it.height })
    }

    @Test
    fun `audio ranking keeps the original track before dubs`() {
        val dub = YouTubeFormat("https://a/dub", "audio/mp4; codecs=\"mp4a.40.2\"", bitrate = 256_000, defaultAudio = false)
        val original = YouTubeFormat("https://a/original", "audio/mp4; codecs=\"mp4a.40.2\"", bitrate = 128_000)
        val opus = YouTubeFormat("https://a/opus", "audio/webm; codecs=\"opus\"", bitrate = 160_000)

        assertEquals(listOf(original, opus, dub), rankAudio(listOf(dub, opus, original, video(720))))
    }

    @Test
    fun `player response parses formats status and manifest`() {
        val body = """
            {
              "playabilityStatus": {"status": "OK"},
              "streamingData": {
                "hlsManifestUrl": "https://manifest.googlevideo.com/api/manifest/hls_variant/x",
                "formats": [{"url": "https://p", "mimeType": "video/mp4", "qualityLabel": "360p", "itag": 18}],
                "adaptiveFormats": [
                  {"url": "https://v", "mimeType": "video/mp4; codecs=\"avc1\"", "height": 1080, "fps": 24, "bitrate": 4000000},
                  {"url": "https://a", "mimeType": "audio/mp4", "bitrate": "128000", "audioTrack": {"audioIsDefault": false}},
                  {"mimeType": "video/mp4", "signatureCipher": "s=...", "height": 720}
                ]
              }
            }
        """.trimIndent()

        val player = parsePlayerResponse(body, "visionos")

        assertEquals("OK", player.status)
        assertEquals("https://manifest.googlevideo.com/api/manifest/hls_variant/x", player.hlsManifestUrl)
        assertEquals(360, player.progressive.single().height)
        assertEquals(2, player.adaptive.size)
        assertEquals(1080, player.adaptive.first().height)
        assertEquals(128_000L, player.adaptive[1].bitrate)
        assertEquals(false, player.adaptive[1].defaultAudio)
    }

    @Test
    fun `unplayable response has no streams`() {
        val player = parsePlayerResponse("""{"playabilityStatus":{"status":"LOGIN_REQUIRED"}}""", "ios")

        assertEquals("LOGIN_REQUIRED", player.status)
        assertNull(player.hlsManifestUrl)
        assertTrue(player.adaptive.isEmpty())
    }

    @Test
    fun `alternate cdn urls swap node and server from mn`() {
        val url = "https://rr3---sn-abc123-def4.googlevideo.com/videoplayback?mn=sn-abc123-def4%2Csn-xyz9-uvw8&itag=137"

        val alternates = alternateCdnUrls(url)

        assertEquals(
            listOf("https://rr2---sn-xyz9-uvw8.googlevideo.com/videoplayback?mn=sn-abc123-def4%2Csn-xyz9-uvw8&itag=137"),
            alternates.filterNot { it.startsWith("https://rr1---sn-abc123-def4") },
        )
        assertTrue(alternateCdnUrls("https://example.com/v?mn=a,b").isEmpty())
    }
}
