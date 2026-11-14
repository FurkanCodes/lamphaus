package com.lamphaus.core.player

import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PictureAdjustmentTest {
    private fun apply(matrix: FloatArray, r: Float, g: Float, b: Float): FloatArray =
        FloatArray(3) { row -> matrix[row] * r + matrix[4 + row] * g + matrix[8 + row] * b + matrix[12 + row] }

    @Test
    fun `PLY-PIC-01 neutral leaves every colour as it is`() {
        val identity = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        assertArrayEquals(identity, pictureMatrix(PictureAdjustment.NEUTRAL), 1e-6f)
    }

    @Test
    fun `PLY-PIC-01 brightness lifts every channel`() {
        val out = apply(pictureMatrix(PictureAdjustment(brightness = 20)), 0.3f, 0.4f, 0.5f)
        assertArrayEquals(floatArrayOf(0.5f, 0.6f, 0.7f), out, 1e-5f)
    }

    @Test
    fun `PLY-PIC-01 contrast pivots on mid grey`() {
        val matrix = pictureMatrix(PictureAdjustment(contrast = 50))
        assertArrayEquals(floatArrayOf(0.5f, 0.5f, 0.5f), apply(matrix, 0.5f, 0.5f, 0.5f), 1e-5f)
        assertEquals(0.8f, apply(matrix, 0.7f, 0.7f, 0.7f)[0], 1e-5f)
    }

    @Test
    fun `PLY-PIC-01 no saturation turns colour into its luma`() {
        val out = apply(pictureMatrix(PictureAdjustment(saturation = -100)), 1f, 0f, 0f)
        assertArrayEquals(floatArrayOf(0.2126f, 0.2126f, 0.2126f), out, 1e-5f)
    }

    @Test
    fun `PLY-PIC-01 hue turns colour but never grey`() {
        val matrix = pictureMatrix(PictureAdjustment(hue = 67))
        assertArrayEquals(floatArrayOf(0.4f, 0.4f, 0.4f), apply(matrix, 0.4f, 0.4f, 0.4f), 1e-5f)
        assertTrue(apply(matrix, 1f, 0f, 0f)[0] < 0.5f)
    }

    @Test
    fun `PLY-PIC-01 ExoPlayer leaves HDR, Dolby Vision, and tunneled playback alone`() {
        val sdr = Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).build()
        val hdr10 = sdr.buildUpon().setColorInfo(
            ColorInfo.Builder().setColorTransfer(C.COLOR_TRANSFER_ST2084).build(),
        ).build()
        val dolbyVision = Format.Builder().setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION).build()
        assertTrue(PictureAdjustment.isAvailable(mpv = false, tunneled = false, hasVideo = true, videoFormat = sdr))
        assertFalse(PictureAdjustment.isAvailable(mpv = false, tunneled = false, hasVideo = true, videoFormat = hdr10))
        assertFalse(PictureAdjustment.isAvailable(mpv = false, tunneled = false, hasVideo = true, videoFormat = dolbyVision))
        assertFalse(PictureAdjustment.isAvailable(mpv = false, tunneled = true, hasVideo = true, videoFormat = sdr))
        assertTrue(PictureAdjustment.isAvailable(mpv = true, tunneled = false, hasVideo = true, videoFormat = hdr10))
        assertTrue(PictureAdjustment.isAvailable(mpv = true, tunneled = false, hasVideo = true, videoFormat = null))
        assertFalse(PictureAdjustment.isAvailable(mpv = true, tunneled = false, hasVideo = false, videoFormat = null))
        assertFalse(PictureAdjustment.isAvailable(mpv = false, tunneled = false, hasVideo = true, videoFormat = null))
    }
}
