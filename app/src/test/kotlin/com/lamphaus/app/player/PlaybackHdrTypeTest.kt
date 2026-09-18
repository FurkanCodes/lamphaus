package com.lamphaus.app.player

import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackHdrTypeTest {
    @Test
    fun `QA-06 maps Dolby Vision before generic PQ`() {
        val format = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
            .setColorInfo(hdrColorInfo(C.COLOR_TRANSFER_ST2084))
            .build()

        assertEquals(PlaybackHdrType.DOLBY_VISION, format.playbackHdrType())
    }

    @Test
    fun `QA-06 maps HDR10 and HLG transfer functions`() {
        val hdr10 = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H265)
            .setColorInfo(hdrColorInfo(C.COLOR_TRANSFER_ST2084))
            .build()
        val hlg = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H265)
            .setColorInfo(hdrColorInfo(C.COLOR_TRANSFER_HLG))
            .build()

        assertEquals(PlaybackHdrType.HDR10, hdr10.playbackHdrType())
        assertEquals(PlaybackHdrType.HLG, hlg.playbackHdrType())
    }

    @Test
    fun `QA-06 leaves SDR formats unclassified`() {
        val format = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H264)
            .setColorInfo(hdrColorInfo(C.COLOR_TRANSFER_SDR))
            .build()

        assertNull(format.playbackHdrType())
    }

    private fun hdrColorInfo(transfer: Int): ColorInfo = ColorInfo.Builder()
        .setColorSpace(C.COLOR_SPACE_BT2020)
        .setColorRange(C.COLOR_RANGE_LIMITED)
        .setColorTransfer(transfer)
        .build()
}
