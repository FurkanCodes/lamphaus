package com.lamphaus.core.player.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * Night listening: evens out loud and quiet moments so dialogue stays
 * audible without explosions waking the house. A feed-forward compressor
 * linked across channels (one gain for every channel, so the image never
 * shifts), with makeup gain and a hard ceiling. On 5.1 and wider it also
 * lifts the centre (dialogue) channel and lowers the LFE before any stereo
 * downmix, so the lift survives it.
 *
 * It sits first in the chain and only ever sees decoded PCM; the engine
 * decodes on the device while night listening is on.
 */
@UnstableApi
class NightListeningAudioProcessor : BaseAudioProcessor() {

    private var channels = 0
    private var attack = 0f
    private var release = 0f
    private var envelope = 0f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        channels = inputAudioFormat.channelCount
        attack = smoothing(ATTACK_MILLIS, inputAudioFormat.sampleRate)
        release = smoothing(RELEASE_MILLIS, inputAudioFormat.sampleRate)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val frameBytes = 2 * channels
        val frames = if (frameBytes > 0) inputBuffer.remaining() / frameBytes else 0
        val output = replaceOutputBuffer(frames * frameBytes)
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        val frame = FloatArray(channels)
        repeat(frames) {
            var peak = 0f
            for (channel in 0 until channels) {
                var sample = input.short / SHORT_SCALE
                if (channels >= SURROUND_CHANNELS) {
                    if (channel == CENTER) sample *= CENTER_GAIN else if (channel == LFE) sample *= LFE_GAIN
                }
                frame[channel] = sample
                peak = max(peak, abs(sample))
            }
            val coefficient = if (peak > envelope) attack else release
            envelope = coefficient * envelope + (1f - coefficient) * peak
            val gain = gainFor(envelope)
            for (channel in 0 until channels) {
                val shaped = (frame[channel] * gain).coerceIn(-CEILING, CEILING)
                output.putShort((shaped * SHORT_SCALE).toInt().toShort())
            }
        }
        // A trailing partial frame cannot be processed; drop it as Media3 does.
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    override fun onFlush() {
        envelope = 0f
    }

    override fun onReset() {
        channels = 0
        envelope = 0f
    }

    internal companion object {
        const val THRESHOLD_DB = -30f
        const val RATIO = 4f
        const val MAKEUP_DB = 8f
        const val ATTACK_MILLIS = 5f
        const val RELEASE_MILLIS = 250f
        const val CEILING = 0.97f
        const val CENTER_GAIN = 1.41f
        const val LFE_GAIN = 0.5f
        private const val SHORT_SCALE = 32768f
        private const val SURROUND_CHANNELS = 6
        private const val CENTER = 2
        private const val LFE = 3

        /** Linear gain for an envelope level: compression above the threshold, then makeup. */
        fun gainFor(level: Float): Float {
            val levelDb = if (level > 1e-6f) 20f * log10(level) else -120f
            val reductionDb = if (levelDb > THRESHOLD_DB) (THRESHOLD_DB - levelDb) * (1f - 1f / RATIO) else 0f
            return 10f.pow((reductionDb + MAKEUP_DB) / 20f)
        }

        private fun smoothing(millis: Float, sampleRate: Int): Float =
            if (sampleRate <= 0) 0f else exp(-1f / (millis / 1000f * sampleRate))
    }
}
