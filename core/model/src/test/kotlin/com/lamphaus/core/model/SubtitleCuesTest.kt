package com.lamphaus.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleCueParserTest {

    @Test
    fun `parses srt with comma and dot fractions`() {
        val srt = """
            1
            00:01:02,500 --> 00:01:04,000
            <i>Hello</i> there

            2
            00:01:05.250 --> 00:01:07.000
            Second line
        """.trimIndent()
        val cues = SubtitleCueParser.parse(srt)
        assertEquals(2, cues.size)
        assertEquals(62_500L, cues[0].startMillis)
        assertEquals(64_000L, cues[0].endMillis)
        assertEquals("Hello there", cues[0].text)
        assertEquals(65_250L, cues[1].startMillis)
        assertEquals("Second line", cues[1].text)
    }

    @Test
    fun `parses webvtt skipping note and style blocks`() {
        val vtt = """
            WEBVTT

            STYLE
            ::cue { color: red }

            NOTE a comment block
            should be ignored

            intro
            00:00:01.000 --> 00:00:03.000 position:50%
            First cue

            00:00:04.000 --> 00:00:06.000
            Second cue
        """.trimIndent()
        val cues = SubtitleCueParser.parse(vtt)
        assertEquals(2, cues.size)
        assertEquals(1_000L, cues[0].startMillis)
        assertEquals("First cue", cues[0].text)
        assertEquals(4_000L, cues[1].startMillis)
    }

    @Test
    fun `parses ass with format header and strips override tags`() {
        val ass = """
            [Script Info]
            Title: t

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:01:02.50,0:01:04.00,Default,,0,0,0,,{\i1}Kon'nichiwa{\i0} world\Nsecond line
        """.trimIndent()
        val cues = SubtitleCueParser.parse(ass)
        assertEquals(1, cues.size)
        assertEquals(62_500L, cues[0].startMillis)
        assertEquals(64_000L, cues[0].endMillis)
        assertEquals("Kon'nichiwa world\nsecond line", cues[0].text)
    }

    @Test
    fun `parses ttml clock and metric times`() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body>
                <p begin="00:00:01.500" end="00:00:03.000">One &amp; two</p>
                <p begin="4.5s" end="6s">Two</p>
              </body>
            </tt>
        """.trimIndent()
        val cues = SubtitleCueParser.parse(ttml)
        assertEquals(2, cues.size)
        assertEquals(1_500L, cues[0].startMillis)
        assertEquals("One & two", cues[0].text)
        assertEquals(4_500L, cues[1].startMillis)
        assertEquals(6_000L, cues[1].endMillis)
    }

    @Test
    fun `malformed input yields salvageable cues only`() {
        val broken = """
            not a subtitle file
            garbage --> lines
            00:00:01,000 --> 00:00:02,000
            still parses this
        """.trimIndent()
        val cues = SubtitleCueParser.parse(broken)
        assertEquals(1, cues.size)
        assertEquals("still parses this", cues[0].text)
    }
}

class SubtitleCharsetTest {

    @Test
    fun `decodes bom marked payloads`() {
        val utf8 = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "héllo".toByteArray(Charsets.UTF_8)
        assertEquals("héllo", SubtitleCharset.decode(utf8))

        val utf16le = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "héllo".toByteArray(Charsets.UTF_16LE)
        assertEquals("héllo", SubtitleCharset.decode(utf16le))
    }

    @Test
    fun `latin1 payload survives strict utf8 rejection`() {
        val latin1 = "héllo".toByteArray(Charsets.ISO_8859_1)
        assertEquals("héllo", SubtitleCharset.decode(latin1))
    }
}

class SubtitleCueWindowTest {

    private fun cueAt(seconds: Long) = SubtitleCue(seconds * 1_000, seconds * 1_000 + 1_000, "cue $seconds")

    @Test
    fun `window keeps 3 minutes and caps rows with nearest focused`() {
        val cues = (0L until 600L).map(::cueAt) // 10 minutes of cues
        val (window, nearestIndex) = SubtitleCueWindow.select(cues, capturedVideoTimeMillis = 300_000L)
        assertEquals(SubtitleCueWindow.MAX_ROWS, window.size)
        assertTrue(window.all { it.startMillis >= 300_000L - SubtitleCueWindow.WINDOW_MILLIS })
        assertEquals("cue 300", window[nearestIndex].text)
    }

    @Test
    fun `nearest cue falls back to the last before the capture time`() {
        val cues = listOf(cueAt(60), cueAt(120))
        val (window, nearestIndex) = SubtitleCueWindow.select(cues, capturedVideoTimeMillis = 130_000L)
        assertEquals(2, window.size)
        assertEquals(1, nearestIndex)
    }

    @Test
    fun `empty when nothing is near`() {
        val cues = listOf(cueAt(60))
        val (window, nearestIndex) = SubtitleCueWindow.select(cues, capturedVideoTimeMillis = 600_000L)
        assertTrue(window.isEmpty())
        assertEquals(-1, nearestIndex)
    }
}
