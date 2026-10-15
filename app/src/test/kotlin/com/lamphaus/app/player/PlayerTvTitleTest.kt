package com.lamphaus.app.player

import com.lamphaus.core.model.Episode
import com.lamphaus.core.model.PlaybackRequest
import com.lamphaus.core.model.PlaybackSource
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerTvTitleTest {
    private fun request(subtitle: String?, episodeTitle: String?) = PlaybackRequest(
        mediaKey = "series:1",
        videoId = "series:1:1:2",
        title = "Night Observatory",
        subtitle = subtitle,
        source = PlaybackSource(uri = "https://example.invalid/video.mp4"),
        episode = episodeTitle?.let { Episode(id = "series:1:1:2", title = it, season = 1, episode = 2) },
    )

    @Test
    fun `PLY-CHR-05 the episode title is not repeated after a subtitle that carries it`() {
        assertEquals("S1 · E2 · Pilot", tvEpisodeLine(request("S1 · E2 · Pilot", "Pilot")))
    }

    @Test
    fun `PLY-CHR-05 the episode title joins a subtitle that lacks it`() {
        assertEquals("S1 · E2 • Pilot", tvEpisodeLine(request("S1 · E2", "Pilot")))
        assertEquals("Pilot", tvEpisodeLine(request(null, "Pilot")))
    }

    @Test
    fun `PLY-CHR-05 a movie shows only its subtitle`() {
        assertEquals("2024", tvEpisodeLine(request("2024", null)))
        assertEquals("", tvEpisodeLine(request(null, null)))
    }
}
