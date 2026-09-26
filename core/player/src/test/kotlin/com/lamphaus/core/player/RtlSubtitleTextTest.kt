package com.lamphaus.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class RtlSubtitleTextTest {
    @Test
    fun `latin subtitles are untouched`() {
        val text = "- Where are you going?"
        assertSame(text, RtlSubtitleText.embed(text))
    }

    @Test
    fun `each RTL line gets its own embedding`() {
        assertEquals(
            "‫- مرحبا!‬\n‫שלום.‬",
            RtlSubtitleText.embed("- مرحبا!\nשלום."),
        )
    }

    @Test
    fun `mixed lines only wrap the RTL ones, and wrapping twice is a no-op`() {
        val once = RtlSubtitleText.embed("Hello\nسلام")
        assertEquals("Hello\n‫سلام‬", once)
        assertEquals(once, RtlSubtitleText.embed(once))
    }
}
