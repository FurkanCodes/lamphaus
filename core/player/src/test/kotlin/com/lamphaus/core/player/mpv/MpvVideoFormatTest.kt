package com.lamphaus.core.player.mpv

import androidx.media3.common.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MpvVideoFormatTest {
    @Test
    fun `QA-06 estimated cadence wins over container metadata`() {
        val format = mpvVideoFormat(3840, 2160, 24f, 23.976f)

        assertEquals(3840, format?.width)
        assertEquals(2160, format?.height)
        assertEquals(23.976f, format?.frameRate)
    }

    @Test
    fun `QA-06 container cadence is used until estimate is available`() {
        val format = mpvVideoFormat(1920, 1080, 25f, 0f)

        assertEquals(25f, format?.frameRate)
    }

    @Test
    fun `QA-06 missing cadence remains unknown instead of zero`() {
        val format = mpvVideoFormat(1920, 1080, Float.NaN, 0f)

        assertEquals(Format.NO_VALUE.toFloat(), format?.frameRate)
    }

    @Test
    fun `QA-06 invalid video dimensions do not publish a track`() {
        assertNull(mpvVideoFormat(0, 1080, 24f, 24f))
    }
}
