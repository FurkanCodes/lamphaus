package com.lamphaus.app.player

import androidx.media3.common.C
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamStatsFormatTest {
    @Test
    fun `frame rate trims zeros and ignores the device locale`() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("23.976 fps", StreamStatsFormat.frameRate(23.976f))
            assertEquals("24 fps", StreamStatsFormat.frameRate(24f))
            assertEquals("29.97 fps", StreamStatsFormat.frameRate(29.97f))
            assertNull(StreamStatsFormat.frameRate(0f))
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun `native Dolby Vision is not named twice`() {
        assertEquals(
            "3840×2160 · 23.976 fps · Dolby Vision",
            StreamStatsFormat.video(3840, 2160, 23.976f, "Dolby Vision", PlaybackHdrType.DOLBY_VISION, -1),
        )
        assertEquals(
            "3840×2160 · 24 fps · HEVC · HDR10 · 42.0 Mbps",
            StreamStatsFormat.video(3840, 2160, 24f, "HEVC", PlaybackHdrType.HDR10, 42_000_000),
        )
    }

    @Test
    fun `output names the bitstream or the decoded layout`() {
        assertEquals("Dolby Digital", StreamStatsFormat.output(true, C.ENCODING_AC3, 6))
        assertEquals("Stereo PCM", StreamStatsFormat.output(false, C.ENCODING_PCM_16BIT, 2))
        assertEquals("5.1 PCM", StreamStatsFormat.output(false, C.ENCODING_PCM_16BIT, 6))
        assertNull(StreamStatsFormat.output(null, null, 0))
    }

    @Test
    fun `audio and decoder read cleanly`() {
        assertEquals("Dolby Digital · 5.1 · 48 kHz · 640 kbps", StreamStatsFormat.audio("Dolby Digital", "5.1", 48_000, 640_000))
        assertEquals("AAC · Stereo · 44.1 kHz", StreamStatsFormat.audio("AAC", "Stereo", 44_100, -1))
        assertEquals("FFmpeg (ac3)", StreamStatsFormat.decoder("ffmpeg7.1-ac3"))
        assertEquals("c2.amlogic.hevc.decoder", StreamStatsFormat.decoder("c2.amlogic.hevc.decoder"))
    }
}
