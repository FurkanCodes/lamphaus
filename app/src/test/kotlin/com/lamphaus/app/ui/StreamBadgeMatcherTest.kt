package com.lamphaus.app.ui

import com.lamphaus.core.data.repository.StreamBadgeFilter
import com.lamphaus.core.data.repository.StreamBadgeImport
import com.lamphaus.core.model.StreamCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StreamBadgeMatcherTest {
    private fun filter(name: String, pattern: String, image: String = "https://img/$name.png", enabled: Boolean = true) =
        StreamBadgeFilter(id = name, name = name, pattern = pattern, imageURL = image, isEnabled = enabled)

    private val matcher = StreamBadgeMatcher(
        StreamBadgeImport(
            sourceUrl = "https://example.com/badges.json",
            filters = listOf(
                filter("Remux", "(?i)\\bremux\\b"),
                filter("BluRay", "(?i)^(?=.*(?:bluray|blu-ray))(?!.*remux)"),
                filter("4K", "(?i)\\b(2160p|4k)\\b"),
                filter("Atmos", "(?i)atmos", enabled = false),
                filter("Remux again", "(?i)remux", image = "https://img/Remux.png"),
                filter("NoArt", "(?i)hdr", image = ""),
            ),
        ),
    )

    @Test
    fun `every enabled match shows once in file order`() {
        val source = StreamCandidate(
            providerId = "p",
            name = "AIOStreams\n4K",
            description = "Movie.2160p.BluRay.REMUX.Atmos.HDR",
        )
        // BluRay is excluded by its own lookahead; duplicate art shows once;
        // disabled and art-less rules never render.
        assertEquals(listOf("Remux", "4K"), matcher.badgesFor(source).map { it.name })
    }

    @Test
    fun `matches across fields and stays inactive without an import`() {
        val source = StreamCandidate(providerId = "p", name = "Torrentio", title = "Film BluRay 1080p")
        assertEquals(listOf("BluRay"), matcher.badgesFor(source).map { it.name })
        assertFalse(StreamBadgeMatcher(null).isActive)
    }
}
