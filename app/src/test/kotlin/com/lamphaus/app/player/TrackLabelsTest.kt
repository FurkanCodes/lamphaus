package com.lamphaus.app.player

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackLabelsTest {
    @Test
    fun `audio reads codec and layout first, then flags`() {
        assertEquals("English · TrueHD Atmos 7.1", trackTitle("en", "TrueHD Atmos 7.1", 0))
        assertEquals(
            "Dolby TrueHD Atmos · 7.1 · Default",
            trackDetails(C.TRACK_TYPE_AUDIO, MimeTypes.AUDIO_TRUEHD, 8, C.SELECTION_FLAG_DEFAULT, 0, "TrueHD Atmos 7.1"),
        )
    }

    @Test
    fun `forced and SDH are recognised from Matroska track names`() {
        assertEquals("SRT · Forced", trackDetails(C.TRACK_TYPE_TEXT, MimeTypes.APPLICATION_SUBRIP, -1, 0, 0, "Forced"))
        assertEquals("PGS · SDH", trackDetails(C.TRACK_TYPE_TEXT, MimeTypes.APPLICATION_PGS, -1, 0, 0, "English SDH"))
    }

    @Test
    fun `a label equal to the language is not repeated`() {
        assertEquals("French", trackTitle("fr", "French", 0))
        assertEquals("Track 3", trackTitle(null, null, 2))
    }
}
