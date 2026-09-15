package com.lamphaus.core.player.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PERF-13: a fresh processor with a positive delay must not overflow its
 * output buffer, must hold audio back until the full silence has been
 * emitted, and must not lose or reorder a single sample (input is consumed
 * only when it can be emitted in order, matching Media3's partially consumed
 * input-buffer contract). Negative delay explicitly drops the leading audio
 * instead of silently doing nothing.
 */
class DelayAudioProcessorTest {

    private fun newProcessor(
        delayMillis: Long,
        sampleRate: Int = 48_000,
        channels: Int = 2,
    ): DelayAudioProcessor = DelayAudioProcessor().apply {
        this.delayMillis = delayMillis
        configure(AudioProcessor.AudioFormat(sampleRate, channels, C.ENCODING_PCM_16BIT))
    }

    private fun patternBytes(count: Int): ByteArray = ByteArray(count) { index -> (index % 251).toByte() }

    /**
     * Emulates `DefaultAudioSink`: the same input buffer is re-offered while it
     * still has remaining bytes and the processor keeps producing output.
     */
    private fun DelayAudioProcessor.processAll(input: ByteArray): ByteArray {
        val buffer = ByteBuffer.wrap(input)
        val collected = ByteArrayOutputStream()
        var guard = 0
        while (buffer.hasRemaining()) {
            check(guard++ < 1_000_000) { "Processor made no progress" }
            queueInput(buffer)
            val output = getOutput()
            val chunk = ByteArray(output.remaining())
            if (chunk.isNotEmpty()) {
                output.get(chunk)
                collected.write(chunk)
            }
        }
        return collected.toByteArray()
    }

    private fun frameBytes(channels: Int): Int = 2 * channels

    @Test
    fun `zero delay is a byte-exact passthrough`() {
        val input = patternBytes(4_000)
        val output = newProcessor(delayMillis = 0).processAll(input)

        assertArrayEquals(input, output)
    }

    @Test
    fun `positive delay never overflows a fresh processor and keeps every sample`() {
        // 100 ms at 48 kHz stereo is 4800 frames; a single small chunk used to
        // request only the input size and then write silence plus all input,
        // which either overflowed or started audio hundreds of frames early.
        val channels = 2
        val processor = newProcessor(delayMillis = 100, channels = channels)
        val input = patternBytes(1_000 * frameBytes(channels))

        val output = processor.processAll(input)

        val silenceBytes = 4_800 * frameBytes(channels)
        assertTrue(output.size >= silenceBytes + input.size)
        assertTrue(output.copyOf(silenceBytes).all { it == 0.toByte() })
        assertArrayEquals(input, output.copyOfRange(silenceBytes, silenceBytes + input.size))
    }

    @Test
    fun `positive delay injects silence once across many chunks`() {
        val channels = 2
        val processor = newProcessor(delayMillis = 50, channels = channels)
        val chunkFrames = 700
        val frames = 4_000
        val input = patternBytes(frames * frameBytes(channels))

        val collected = ByteArrayOutputStream()
        var offset = 0
        while (offset < input.size) {
            val end = minOf(offset + chunkFrames * frameBytes(channels), input.size)
            collected.write(processor.processAll(input.copyOfRange(offset, end)))
            offset = end
        }

        val silenceBytes = 2_400 * frameBytes(channels)
        val output = collected.toByteArray()
        assertTrue(output.size >= silenceBytes + input.size)
        assertTrue(output.copyOf(silenceBytes).all { it == 0.toByte() })
        assertArrayEquals(input, output.copyOfRange(silenceBytes, silenceBytes + input.size))
    }

    @Test
    fun `negative delay drops the leading audio and reports active`() {
        val channels = 2
        val processor = newProcessor(delayMillis = -100, channels = channels)
        assertTrue(processor.isActive())
        val droppedBytes = 4_800 * frameBytes(channels)
        val input = patternBytes(6_000 * frameBytes(channels))

        val output = processor.processAll(input)

        assertEquals(input.size - droppedBytes, output.size)
        assertArrayEquals(input.copyOfRange(droppedBytes, input.size), output)
    }

    @Test
    fun `negative delay larger than the stream consumes everything`() {
        val processor = newProcessor(delayMillis = -3_000)

        val output = processor.processAll(patternBytes(2_000))

        assertEquals(0, output.size)
    }

    @Test
    fun `flush reinjects the configured delay`() {
        val channels = 2
        val processor = newProcessor(delayMillis = 20, channels = channels)
        val silenceBytes = 960 * frameBytes(channels)
        val input = patternBytes(1_500 * frameBytes(channels))

        val first = processor.processAll(input)
        processor.flush()
        val second = processor.processAll(input)

        assertEquals(silenceBytes + input.size, first.size)
        assertEquals(silenceBytes + input.size, second.size)
        assertTrue(second.copyOf(silenceBytes).all { it == 0.toByte() })
        assertArrayEquals(input, second.copyOfRange(silenceBytes, second.size))
    }

    @Test
    fun `mono low rate chunks stay aligned`() {
        val processor = newProcessor(delayMillis = 10, sampleRate = 8_000, channels = 1)
        // 10 ms at 8 kHz mono is 80 frames, i.e. 160 bytes of 16-bit silence.
        val silenceBytes = 160
        val input = patternBytes(40)

        val output = processor.processAll(input)

        assertEquals(silenceBytes + input.size, output.size)
        assertTrue(output.copyOf(silenceBytes).all { it == 0.toByte() })
        assertArrayEquals(input, output.copyOfRange(silenceBytes, output.size))
    }

    @Test
    fun `delay change applies at the next flush`() {
        val channels = 2
        val processor = newProcessor(delayMillis = 0, channels = channels)
        val input = patternBytes(1_000 * frameBytes(channels))

        // Zero delay passes through untouched.
        assertArrayEquals(input, processor.processAll(input))

        processor.delayMillis = 30
        processor.flush()
        val delayed = processor.processAll(input)

        val silenceBytes = 1_440 * frameBytes(channels)
        assertEquals(silenceBytes + input.size, delayed.size)
        assertTrue(delayed.copyOf(silenceBytes).all { it == 0.toByte() })
        assertArrayEquals(input, delayed.copyOfRange(silenceBytes, delayed.size))
    }

    @Test
    fun `processor stays in the chain so a later delay can apply`() {
        val processor = newProcessor(delayMillis = 0)
        assertTrue(processor.isActive())

        processor.delayMillis = 40
        processor.flush()
        val output = processor.processAll(patternBytes(2_400))

        val silenceBytes = 1_920 * frameBytes(2)
        assertEquals(silenceBytes + 2_400, output.size)
    }
}
