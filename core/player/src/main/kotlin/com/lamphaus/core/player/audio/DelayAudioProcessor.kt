package com.lamphaus.core.player.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Audio vs. video delay for the current output route (plan §2): positive
 * values push audio later, negative values start audio earlier.
 *
 * Positive delay is implemented as leading silence injection; negative delay
 * drops the corresponding prefix of decoded audio. Both work on any decode
 * path and never restart playback when the delay changes. Negative delay is
 * bounded to the same ±3000 ms range; a stream shorter than the requested
 * advance simply has nothing left to drop.
 *
 * The processor always reports the input format so it stays in the audio
 * chain: a delay that is configured, changed, or cleared while playback is
 * running applies at the next [onFlush] (route switches and seeks both
 * flush), which matches the per-route persistence semantics. A zero delay is a
 * pass-through.
 *
 * Input is consumed only when it can be emitted in the right order: while
 * silence is still pending no input sample is copied, so a fresh processor
 * cannot overflow and audio never starts early (PERF-13). Media3's audio sink
 * re-offers a partially consumed input buffer, which is what makes the held
 * framing correct.
 */
@UnstableApi
class DelayAudioProcessor : BaseAudioProcessor() {

    /** Delay in milliseconds; clamped to the plan's ±3000 ms range. */
    @Volatile
    var delayMillis: Long = 0L
        set(value) {
            field = value.coerceIn(-MAX_DELAY_MILLIS, MAX_DELAY_MILLIS)
        }

    /** Configured PCM format; [onConfigure] may arrive before the first flush. */
    private var format: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var pendingSilenceFrames = 0L
    private var silenceWrittenFrames = 0L
    private var pendingDropFrames = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        format = inputAudioFormat
        resetDelay()
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        val bytesPerFrame = 2 * format.channelCount
        if (remaining <= 0 || bytesPerFrame <= 0 || holdingAudio()) {
            // Nothing to hold back: pass the input through unchanged.
            val output = replaceOutputBuffer(remaining)
            if (remaining > 0) output.put(inputBuffer)
            output.flip()
            return
        }

        // Negative delay advances audio by discarding the leading frames.
        // Input the sink re-offers is consumed without producing output until
        // the requested advance is used up.
        if (pendingDropFrames > 0) {
            val framesAvailable = remaining / bytesPerFrame
            if (framesAvailable > 0) {
                val dropFrames = minOf(framesAvailable.toLong(), pendingDropFrames)
                inputBuffer.position(inputBuffer.position() + (dropFrames * bytesPerFrame).toInt())
                pendingDropFrames -= dropFrames
                return
            }
        }

        // Leading silence is emitted before any of this chunk's audio, and the
        // input is deliberately left unconsumed: writing silence plus the input
        // in one output buffer used to overflow a fresh processor, and emitting
        // the input early would shorten the delay (PERF-13). The audio sink
        // re-offers the same buffer until it is fully consumed.
        val framesAvailable = remaining / bytesPerFrame
        if (framesAvailable > 0 && silenceWrittenFrames < pendingSilenceFrames) {
            val silenceFrames = minOf(framesAvailable.toLong(), pendingSilenceFrames - silenceWrittenFrames)
            val silenceBytes = (silenceFrames * bytesPerFrame).toInt()
            val output = replaceOutputBuffer(silenceBytes)
            repeat(silenceBytes) { output.put(0.toByte()) }
            silenceWrittenFrames += silenceFrames
            output.flip()
            return
        }

        val output = replaceOutputBuffer(remaining)
        output.put(inputBuffer)
        output.flip()
    }

    override fun onFlush() {
        format = inputAudioFormat
        resetDelay()
    }

    override fun onReset() {
        format = AudioProcessor.AudioFormat.NOT_SET
        resetDelay()
    }

    private fun holdingAudio(): Boolean =
        pendingDropFrames <= 0 && silenceWrittenFrames >= pendingSilenceFrames

    private fun resetDelay() {
        val frames = kotlin.math.abs(delayMillis) * format.sampleRate / 1000L
        pendingSilenceFrames = if (delayMillis > 0) frames else 0L
        pendingDropFrames = if (delayMillis < 0) frames else 0L
        silenceWrittenFrames = 0L
    }

    private companion object {
        const val MAX_DELAY_MILLIS = 3_000L
    }
}
