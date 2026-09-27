package com.lamphaus.app.update

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateFeedSourcesTest {
    @Test
    fun `raw branch feed is read from the contents api first`() {
        val sources = UpdateFeedSources.forFeedUrl(
            "https://raw.githubusercontent.com/furkancodes/lamphaus/release-metadata/updates/v1/index.json",
        )
        assertEquals(
            listOf(
                "https://api.github.com/repos/furkancodes/lamphaus/contents/updates/v1/index.json?ref=release-metadata",
                "https://raw.githubusercontent.com/furkancodes/lamphaus/release-metadata/updates/v1/index.json",
            ),
            sources.map { it.url },
        )
        assertEquals("application/vnd.github.raw+json", sources.first().accept)
    }

    @Test
    fun `other feed hosts are used as given`() {
        assertEquals(
            listOf("https://updates.example/feed.json"),
            UpdateFeedSources.forFeedUrl("https://updates.example/feed.json").map { it.url },
        )
    }
}
