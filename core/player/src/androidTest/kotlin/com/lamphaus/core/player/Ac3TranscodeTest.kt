package com.lamphaus.core.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.decoder.ffmpeg.FfmpegAudioDecoder
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PLY-AUD-01 Force AC-3 for optical: the FFmpeg decoder re-encodes 5.1
 * E-AC-3 as 640 kbit/s AC-3 syncframes and leaves stereo as PCM (QA-01).
 * The clips are two-second synthetic tones made with FFmpeg n7.1.
 */
@RunWith(AndroidJUnit4::class)
class Ac3TranscodeTest {
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets

    @Before
    fun setUp() {
        assertTrue(FfmpegLibrary.isAvailable())
    }

    @Test
    fun surroundLeavesTheDecoderAsAc3() {
        val result = decode("transcode_51.eac3", channelCount = 6)
        assertTrue(result.transcoding)
        assertEquals(6, result.channelCount)
        assertEquals(48_000, result.sampleRate)
        val bytes = result.bytes
        // 640 kbit/s at 48 kHz: 1536 samples in 2560-byte syncframes.
        assertEquals(0, bytes.size % AC3_FRAME_BYTES)
        assertTrue("only ${bytes.size / AC3_FRAME_BYTES} frames", bytes.size / AC3_FRAME_BYTES >= 55)
        for (offset in bytes.indices step AC3_FRAME_BYTES) {
            assertEquals(0x0B, bytes[offset].toInt() and 0xFF)
            assertEquals(0x77, bytes[offset + 1].toInt() and 0xFF)
        }
    }

    @Test
    fun stereoStaysPcm() {
        val result = decode("transcode_20.eac3", channelCount = 2)
        assertFalse(result.transcoding)
        assertEquals(2, result.channelCount)
        // Two seconds of 16-bit stereo, give or take the encoder's padding.
        assertTrue(result.bytes.size in 360_000..400_000)
    }

    private class Decoded(
        val bytes: ByteArray,
        val transcoding: Boolean,
        val channelCount: Int,
        val sampleRate: Int,
    )

    private fun decode(asset: String, channelCount: Int): Decoded {
        val stream = assets.open(asset).use { it.readBytes() }
        val format = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_E_AC3)
            .setChannelCount(channelCount)
            .setSampleRate(48_000)
            .build()
        val decoder = FfmpegAudioDecoder(format, 16, 16, 4096, false, true)
        val output = ByteArrayOutputStream()
        try {
            var offset = 0
            var frameIndex = 0L
            var ended = false
            var endQueued = false
            while (!ended) {
                val input = if (endQueued) null else decoder.dequeueInputBuffer()
                if (input != null) {
                    if (offset >= stream.size) {
                        input.addFlag(C.BUFFER_FLAG_END_OF_STREAM)
                        endQueued = true
                    } else {
                        // E-AC-3 frame size: (frmsiz + 1) 16-bit words.
                        val size = (((stream[offset + 2].toInt() and 0x07) shl 8) or (stream[offset + 3].toInt() and 0xFF)) * 2 + 2
                        input.ensureSpaceForWrite(size)
                        input.data!!.put(stream, offset, size)
                        input.flip()
                        input.timeUs = frameIndex++ * 32_000
                        offset += size
                    }
                    decoder.queueInputBuffer(input)
                }
                while (true) {
                    val buffer = decoder.dequeueOutputBuffer() ?: break
                    // An end-of-stream buffer carries no audio, only stale bytes.
                    if (buffer.isEndOfStream) ended = true else buffer.data?.let { data ->
                        val chunk = ByteArray(data.remaining())
                        data.get(chunk)
                        output.write(chunk)
                    }
                    buffer.release()
                }
                if (input == null) Thread.sleep(2)
            }
            return Decoded(output.toByteArray(), decoder.isTranscodingToAc3, decoder.channelCount, decoder.sampleRate)
        } finally {
            decoder.release()
        }
    }

    private companion object {
        const val AC3_FRAME_BYTES = 2560
    }
}
