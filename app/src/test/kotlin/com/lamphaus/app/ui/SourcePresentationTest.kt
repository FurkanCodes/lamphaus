package com.lamphaus.app.ui

import com.lamphaus.core.model.StreamCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourcePresentationTest {
    @Test
    fun `provider formatting is preserved before inferred metadata`() {
        val source = StreamCandidate(
            providerId = "one",
            name = "⚡ Cached · 4K",
            title = "Provider release title",
            description = "📺 Blu-ray · HEVC\n📦 18.4 GB",
            filename = "Movie.2160p.WEB-DL.mkv",
            videoSize = 19_756_849_561,
            url = "https://video.example/movie.mkv",
        )

        val presentation = source.sourcePresentation("Provider One")

        assertTrue(presentation.usesProviderFormatting)
        assertEquals("⚡ Cached · 4K", presentation.title)
        assertEquals("📺 Blu-ray · HEVC\n📦 18.4 GB\nProvider release title", presentation.description)
        assertTrue(presentation.badges.isEmpty())
    }

    @Test
    fun `structured provider tags remain exact`() {
        val source = StreamCandidate(
            providerId = "one",
            name = "Provider One",
            tags = listOf("2160p", "HDR10+"),
            infoHash = "0123456789abcdef",
        )

        val presentation = source.sourcePresentation("Provider One")

        assertTrue(presentation.usesProviderFormatting)
        assertEquals(listOf("2160p", "HDR10+"), presentation.badges)
        assertEquals(SourceTransport.PEER, presentation.transport)
    }

    @Test
    fun `missing provider presentation receives a compact fallback`() {
        val source = StreamCandidate(
            providerId = "one",
            name = "Source",
            filename = "Movie.2160p.BluRay.HEVC.Atmos.mkv",
            videoSize = 19_756_849_561,
            url = "https://video.example/movie.mkv",
        )

        val presentation = source.sourcePresentation("Provider One")

        assertFalse(presentation.usesProviderFormatting)
        assertEquals("Movie.2160p.BluRay.HEVC.Atmos.mkv", presentation.title)
        assertEquals(listOf("4K", "Blu-ray", "HEVC", "Atmos"), presentation.badges)
        assertEquals("18 GB", presentation.size)
        assertEquals(SourceTransport.DIRECT, presentation.transport)
    }
}
