package com.lamphaus.app.tv

import com.lamphaus.app.ui.MediaMetadataPresentation
import org.junit.Assert.assertEquals
import org.junit.Test

class SpotlightHomeTest {
    private val spec = PinnedBringIntoViewSpec(leadingEdgePx = 58f)

    @Test
    fun `TV-CNT-03 the focused item lands on the pinned leading edge`() {
        // Right of the edge: scroll forward; left of it: scroll back.
        assertEquals(342f, spec.calculateScrollDistance(offset = 400f, size = 153f, containerSize = 960f))
        assertEquals(-58f, spec.calculateScrollDistance(offset = 0f, size = 153f, containerSize = 960f))
        assertEquals(0f, spec.calculateScrollDistance(offset = 58f, size = 411f, containerSize = 960f))
    }

    @Test
    fun `TV-CNT-03 the width of the focused item does not move the pin`() {
        assertEquals(
            spec.calculateScrollDistance(offset = 300f, size = 153f, containerSize = 960f),
            spec.calculateScrollDistance(offset = 300f, size = 411f, containerSize = 960f),
        )
    }

    @Test
    fun `TV-CNT-03 the meta line orders genres, year, type, content rating and rating`() {
        val presentation = MediaMetadataPresentation(
            year = 2024,
            runtimeMinutes = null,
            contentRating = "TV-MA",
            ratingText = "8.1",
            genres = listOf("Drama", "Crime"),
        )

        assertEquals(
            listOf("Drama, Crime", "2024", "Series", "TV-MA", "★ 8.1"),
            spotlightMetaParts(presentation, typeLabel = "Series"),
        )
        assertEquals(
            emptyList<String>(),
            spotlightMetaParts(MediaMetadataPresentation(null, null, null, null, emptyList()), typeLabel = null),
        )
    }
}
