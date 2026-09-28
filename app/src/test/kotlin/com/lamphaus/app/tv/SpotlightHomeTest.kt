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
    fun `TV-MOT-01 moving right aims at where the new card settles`() {
        val widths = SpotlightRowWidths()
        val leaving = SpotlightCardWidth().apply { posterPx = 153f; expandedPx = 411f; measuredPx = 411f }
        val arriving = SpotlightCardWidth().apply { posterPx = 153f; expandedPx = 411f; measuredPx = 153f; focused = true }
        widths.track(0, leaving)
        widths.track(1, arriving)
        val pinned = PinnedBringIntoViewSpec(leadingEdgePx = 58f, settling = widths::pendingBeforeFocused)

        // The leaving card still has 258px to give back, so the target is its settled place.
        assertEquals(173f, pinned.calculateScrollDistance(offset = 489f, size = 153f, containerSize = 960f))
        // Halfway through the collapse the target has not moved.
        leaving.measuredPx = 282f
        assertEquals(173f, pinned.calculateScrollDistance(offset = 360f, size = 282f, containerSize = 960f))
    }

    @Test
    fun `TV-MOT-01 a card narrowing after the focused one does not move the pin`() {
        val widths = SpotlightRowWidths()
        widths.track(0, SpotlightCardWidth().apply { posterPx = 153f; expandedPx = 411f; measuredPx = 153f; focused = true })
        widths.track(1, SpotlightCardWidth().apply { posterPx = 153f; expandedPx = 411f; measuredPx = 411f })

        assertEquals(0f, widths.pendingBeforeFocused())
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
