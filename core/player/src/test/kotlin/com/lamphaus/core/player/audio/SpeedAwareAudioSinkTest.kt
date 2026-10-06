package com.lamphaus.core.player.audio

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.audio.AudioSink
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedAwareAudioSinkTest {
    private val eac3 = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_E_AC3).setChannelCount(6).setSampleRate(48_000).build()
    private val pcm = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW).setPcmEncoding(C.ENCODING_PCM_16BIT)
        .setChannelCount(2).setSampleRate(48_000).build()

    /** A sink that can play everything directly, standing in for a passthrough-capable route. */
    private fun directSink(): AudioSink = Proxy.newProxyInstance(
        AudioSink::class.java.classLoader,
        arrayOf(AudioSink::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "getFormatSupport" -> AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
            "supportsFormat" -> true
            else -> null
        }
    } as AudioSink

    private class CountingListener : AudioSink.Listener {
        var capabilityChanges = 0
        override fun onPositionDiscontinuity() = Unit
        override fun onUnderrun(bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) = Unit
        override fun onSkipSilenceEnabledChanged(skipSilenceEnabled: Boolean) = Unit
        override fun onAudioCapabilitiesChanged() {
            capabilityChanges++
        }
    }

    @Test
    fun `away from 1x a bitstream is decoded to PCM, and passthrough returns at 1x`() {
        val sink = SpeedAwareAudioSink(directSink())
        val listener = CountingListener()
        sink.setListener(listener)

        assertEquals(AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY, sink.getFormatSupport(eac3))
        sink.setPlaybackParameters(PlaybackParameters(1.5f))
        assertEquals(AudioSink.SINK_FORMAT_UNSUPPORTED, sink.getFormatSupport(eac3))
        assertEquals(AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY, sink.getFormatSupport(pcm))
        assertEquals(1, listener.capabilityChanges)

        sink.setPlaybackParameters(PlaybackParameters(2f))
        assertEquals(1, listener.capabilityChanges)

        sink.setPlaybackParameters(PlaybackParameters(1f))
        assertEquals(AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY, sink.getFormatSupport(eac3))
        assertEquals(2, listener.capabilityChanges)
    }
}
