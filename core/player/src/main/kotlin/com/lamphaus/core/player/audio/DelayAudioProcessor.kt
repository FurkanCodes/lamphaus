package com.lamphaus.core.player.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Audio vs. video delay for the current output route (plan §2): positive
 * values push audio later. Implemented as leading silence injection —
 * works on any decode path and never restarts playback when the delay
 * changes.
 *
 * Delay changes take effect at the next [onFlush] (route switches and seeks
 * both flush), which matches the per-route persistence semantics: the value
 * is remembered per route and re-applied when that route returns.
 */
@UnstableApi
class DelayAudioProcessor : BaseAudioProcessor() {

    /** Delay in milliseconds; clamped to the plan's ±3000 ms range. */
    @Volatile
    var delayMillis: Long = 0L
        set(value) {
            field = value.coerceIn(-3_000L, 3_000L)
        }

    private var pendingSilenceFrames = 0L
    private var silenceWrittenFrames = 0L
    private var active = false

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        resetSilence()
        active = delayMillis != 0L
        return if (active) inputAudioFormat else AudioProcessor.AudioFormat.NOT_SET
    }

    override fun isActive(): Boolean = active

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (!active || remaining == 0) {
            // Inactive processors must still copy through.
            val output = replaceOutputBuffer(remaining)
            if (remaining > 0) output.put(inputBuffer)
            output.flip()
            return
        }
        val output = replaceOutputBuffer(remaining)
        var frames: Long = (remaining / (2 * channels())).toLong()
        while (frames > 0 && silenceWrittenFrames < pendingSilenceFrames) {
            // Emit leading silence first so every subsequent sample lands late.
            val silenceFrames = minOf(frames, pendingSilenceFrames - silenceWrittenFrames)
            repeat((silenceFrames * 2 * channels()).toInt()) { output.put(0.toByte()) }
            silenceWrittenFrames += silenceFrames
            frames -= silenceFrames
        }
        output.put(inputBuffer)
        output.flip()
    }

    override fun onFlush() {
        resetSilence()
        active = delayMillis != 0L
    }

    override fun onReset() {
        resetSilence()
        active = false
    }

    private fun channels(): Int = inputAudioFormat.channelCount

    private fun resetSilence() {
        pendingSilenceFrames = delayMillis * inputAudioFormat.sampleRate / 1000L
        silenceWrittenFrames = 0L
    }
}
