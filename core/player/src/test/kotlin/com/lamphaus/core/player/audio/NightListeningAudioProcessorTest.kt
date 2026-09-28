package com.lamphaus.core.player.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertTrue
import org.junit.Test

class NightListeningAudioProcessorTest {
    private val sampleRate = 48_000

    private fun processor(channels: Int) = NightListeningAudioProcessor().apply {
        configure(AudioProcessor.AudioFormat(sampleRate, channels, C.ENCODING_PCM_16BIT))
        flush()
    }

    /** One second of a 1 kHz tone at [dbfs] on every channel, scaled per channel by [channelScale]. */
    private fun tone(dbfs: Float, channels: Int, channelScale: (Int) -> Float = { 1f }): ShortArray {
        val amplitude = Math.pow(10.0, dbfs / 20.0) * 32767
        return ShortArray(sampleRate * channels) { index ->
            val frame = index / channels
            (amplitude * channelScale(index % channels) * sin(2 * PI * 1000 * frame / sampleRate)).toInt().toShort()
        }
    }

    private fun NightListeningAudioProcessor.run(samples: ShortArray): ShortArray {
        val input = ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder())
        samples.forEach { input.putShort(it) }
        input.flip()
        queueInput(input)
        val output = getOutput()
        return ShortArray(output.remaining() / 2) { output.short }
    }

    /** RMS in dBFS of the second half, after the envelope has settled. */
    private fun ShortArray.levelDb(channels: Int, channel: Int = 0): Double {
        val frames = size / channels
        var sum = 0.0
        var count = 0
        for (frame in frames / 2 until frames) {
            val value = this[frame * channels + channel] / 32768.0
            sum += value * value
            count++
        }
        return 20 * log10(sqrt(sum / count))
    }

    @Test
    fun `loud and quiet passages end up much closer together`() {
        val quiet = processor(2).run(tone(-40f, 2)).levelDb(2)
        val loud = processor(2).run(tone(-3f, 2)).levelDb(2)
        // 37 dB apart going in; under 20 dB apart coming out.
        assertTrue("gap ${loud - quiet}", loud - quiet < 20)
        // Quiet passages are lifted, loud ones brought down.
        assertTrue(quiet > -40 - 3 + 5)
        assertTrue(loud < -3 - 3 - 5)
    }

    @Test
    fun `nothing exceeds the ceiling`() {
        val out = processor(2).run(tone(0f, 2))
        val ceiling = (NightListeningAudioProcessor.CEILING * 32768).toInt() + 1
        assertTrue(out.all { abs(it.toInt()) <= ceiling })
    }

    @Test
    fun `surround lifts dialogue in the centre and lowers the lfe`() {
        val out = processor(6).run(tone(-30f, 6))
        val front = out.levelDb(6, channel = 0)
        assertTrue(out.levelDb(6, channel = 2) - front > 2.5)
        assertTrue(front - out.levelDb(6, channel = 3) > 5.5)
    }
}
