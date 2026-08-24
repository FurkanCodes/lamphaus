package com.lamphaus.app.ui

import com.lamphaus.core.model.StreamCandidate
import com.lamphaus.core.model.StreamFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceResolverTest {
    @Test
    fun `HTTPS sources play internally`() {
        val result = resolveSource(candidate(url = "https://cdn.example/video.m3u8"), false)

        assertEquals(SourceResolution.Internal("https://cdn.example/video.m3u8"), result)
    }

    @Test
    fun `hash source preserves file selection and trackers`() {
        val result = resolveSource(
            candidate(
                infoHash = "0123456789abcdef",
                fileIndex = 2,
                sourceUrls = listOf("tracker:https://tracker.example/announce"),
            ),
            false,
        ) as SourceResolution.External

        assertTrue(result.uri.startsWith("magnet:?xt=urn:btih:0123456789abcdef"))
        assertTrue(result.uri.contains("so=2"))
        assertTrue(result.uri.contains("tr=https%3A%2F%2Ftracker.example%2Fannounce"))
    }

    @Test
    fun `tracker hints are not mistaken for playable media`() {
        val result = resolveSource(
            candidate(sourceUrls = listOf("https://tracker.example/announce")),
            false,
        )

        assertTrue(result is SourceResolution.Unsupported)
    }

    @Test
    fun `archive and Usenet targets are handed to external apps`() {
        val archive = resolveSource(
            candidate(rarFiles = listOf(StreamFile("https://files.example/movie.rar"))),
            false,
        )
        val nzb = resolveSource(candidate(nzbUrl = "nzb://example/item"), false)

        assertEquals(SourceResolution.External("https://files.example/movie.rar"), archive)
        assertEquals(SourceResolution.External("nzb://example/item"), nzb)
    }

    @Test
    fun `unsafe addresses are rejected`() {
        val result = resolveSource(candidate(externalUrl = "file:///sdcard/private.mp4"), false)

        assertTrue(result is SourceResolution.Unsupported)
    }

    @Test
    fun `duplicate looking sources still receive stable unique list keys`() {
        val source = candidate(url = "https://cdn.example/video.mp4")

        assertNotEquals(sourceItemKey(source, 0), sourceItemKey(source, 1))
    }

    private fun candidate(
        url: String? = null,
        externalUrl: String? = null,
        infoHash: String? = null,
        fileIndex: Int? = null,
        sourceUrls: List<String> = emptyList(),
        nzbUrl: String? = null,
        rarFiles: List<StreamFile> = emptyList(),
    ) = StreamCandidate(
        providerId = "provider",
        name = "Source",
        url = url,
        externalUrl = externalUrl,
        infoHash = infoHash,
        fileIndex = fileIndex,
        sourceUrls = sourceUrls,
        nzbUrl = nzbUrl,
        rarFiles = rarFiles,
    )
}
