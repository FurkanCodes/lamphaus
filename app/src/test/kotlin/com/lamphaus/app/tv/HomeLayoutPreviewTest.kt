package com.lamphaus.app.tv

import com.lamphaus.app.ui.TvHomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutPreviewTest {
    @Test
    fun `TV-CNT-07 every frame keeps the same shapes so each step eases instead of jumping`() {
        TvHomeLayout.entries.forEach { layout ->
            val frames = homeLayoutPreviewFrames(layout)
            assertEquals(layout.name, previewSteps(layout).size, frames.size)
            assertEquals(layout.name, 1, frames.map { it.size }.distinct().size)
            frames.forEach { frame ->
                frame.indices.forEach { index -> assertEquals(frame[index].ink, frames.first()[index].ink) }
            }
        }
    }

    @Test
    fun `TV-CNT-07 TV-FOC-01 exactly one shape owns focus in every frame`() {
        TvHomeLayout.entries.forEach { layout ->
            homeLayoutPreviewFrames(layout).forEach { frame ->
                assertEquals(layout.name, 1, frame.count { it.focus == 1f })
            }
        }
    }

    @Test
    fun `TV-CNT-03 the widening layouts pin the focused card at the row start`() {
        listOf(TvHomeLayout.SPOTLIGHT, TvHomeLayout.SHOWCASE, TvHomeLayout.MARQUEE).forEach { layout ->
            val steps = previewSteps(layout)
            homeLayoutPreviewFrames(layout).forEachIndexed { index, frame ->
                val focused = frame.single { it.focus == 1f }
                if (steps[index].inHero) {
                    assertEquals(273f, focused.width)
                } else {
                    assertEquals(layout.name, 137f, focused.width)
                    assertEquals(layout.name, 28f, focused.x)
                }
            }
        }
    }

    @Test
    fun `TV-CNT-01 Classic scales the focused poster instead of widening it`() {
        homeLayoutPreviewFrames(TvHomeLayout.CLASSIC).drop(1).forEach { frame ->
            val focused = frame.single { it.focus == 1f }
            assertEquals(51f, focused.width)
            assertTrue(focused.scale > 1f)
        }
    }

    @Test
    fun `TV-CNT-05 Showcase hides the rows above the focused one`() {
        val steps = previewSteps(TvHomeLayout.SHOWCASE)
        val secondRow = steps.indexOf(PreviewFocus(1, 0))
        val frame = homeLayoutPreviewFrames(TvHomeLayout.SHOWCASE)[secondRow]
        val focused = frame.single { it.focus == 1f }
        // Every card above the focused row is hidden; the focused row is fully shown.
        assertTrue(frame.filter { it.height == focused.height && it.y < focused.y }.all { it.opacity == 0f })
        assertEquals(1f, focused.opacity)
    }

    @Test
    fun `TV-CNT-06 Marquee scrolls its hero away once the rows have focus`() {
        val frames = homeLayoutPreviewFrames(TvHomeLayout.MARQUEE)
        val heroInHero = frames.first().first()
        val heroInRows = frames[1].first()
        assertEquals(10.67f, heroInHero.y, 0.01f)
        assertTrue(heroInRows.y < heroInHero.y)
        assertEquals(0f, heroInRows.opacity)
    }

    @Test
    fun `TV-CNT-03 a row restores the card it last had focus on`() {
        val steps = previewSteps(TvHomeLayout.SPOTLIGHT)
        assertEquals(PreviewFocus(0, 2), steps.last())
        val frame = homeLayoutPreviewFrames(TvHomeLayout.SPOTLIGHT).last()
        val focused = frame.single { it.focus == 1f }
        assertEquals(28f, focused.x)
    }

    @Test
    fun `TV-MOT-01 easing halfway lands between two frames`() {
        val from = PreviewBlock(0f, 0f, 51f, 77f, tone = 0.1f)
        val to = PreviewBlock(28f, 10f, 137f, 77f, tone = 0.5f, focus = 1f)
        val half = from.lerpTo(to, 0.5f)
        assertEquals(14f, half.x)
        assertEquals(94f, half.width)
        assertEquals(0.5f, half.focus)
    }
}
