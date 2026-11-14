package com.lamphaus.core.player

import androidx.media3.common.C
import androidx.media3.exoplayer.Renderer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Proxy

class SubtitleOffsetRendererTest {
    private val renderPositions = mutableListOf<Long>()
    private val resetPositions = mutableListOf<Long>()

    private val textRenderer = Proxy.newProxyInstance(
        Renderer::class.java.classLoader,
        arrayOf(Renderer::class.java),
    ) { _, method, args ->
        when (method.name) {
            "render" -> renderPositions += args[0] as Long
            "resetPosition" -> resetPositions += args[0] as Long
            "getTrackType" -> return@newProxyInstance C.TRACK_TYPE_TEXT
        }
        null
    } as Renderer

    @After
    fun resetOffset() {
        SubtitleOffset.delayUs = 0L
    }

    @Test
    fun `a positive offset shows subtitles later`() {
        SubtitleOffset.delayUs = 2_000_000L
        SubtitleOffsetRenderer(textRenderer).render(10_000_000L, 0L)
        assertEquals(listOf(8_000_000L), renderPositions)
    }

    @Test
    fun `a negative offset shows subtitles earlier`() {
        SubtitleOffset.delayUs = -1_500_000L
        val renderer = SubtitleOffsetRenderer(textRenderer)
        renderer.render(10_000_000L, 0L)
        renderer.resetPosition(4_000_000L, true)
        assertEquals(listOf(11_500_000L), renderPositions)
        assertEquals(listOf(5_500_000L), resetPositions)
    }

    @Test
    fun `no offset leaves the renderer's clock alone`() {
        SubtitleOffsetRenderer(textRenderer).render(10_000_000L, 0L)
        assertEquals(listOf(10_000_000L), renderPositions)
        assertEquals(C.TRACK_TYPE_TEXT, SubtitleOffsetRenderer(textRenderer).trackType)
    }
}
