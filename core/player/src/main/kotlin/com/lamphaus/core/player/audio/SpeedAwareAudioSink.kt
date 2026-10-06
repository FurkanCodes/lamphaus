package com.lamphaus.core.player.audio

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink

/**
 * A bitstream cannot change tempo, so away from 1x the sink reports encoded
 * surround formats as unsupported and asks Media3 to choose again: the
 * renderer then decodes to PCM, which Sonic can speed up. Back at 1x
 * passthrough returns (Nuvio's behaviour).
 */
@UnstableApi
class SpeedAwareAudioSink(sink: AudioSink) : ForwardingAudioSink(sink) {

    @Volatile
    private var speed = 1f

    @Volatile
    private var listener: AudioSink.Listener? = null

    override fun setListener(listener: AudioSink.Listener) {
        this.listener = listener
        super.setListener(listener)
    }

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        val wasNormal = speed == 1f
        speed = playbackParameters.speed.takeIf { it > 0f } ?: 1f
        super.setPlaybackParameters(playbackParameters)
        if (wasNormal != (speed == 1f)) listener?.onAudioCapabilitiesChanged()
    }

    override fun getFormatSupport(format: Format): Int =
        if (rejectsBitstream(format)) AudioSink.SINK_FORMAT_UNSUPPORTED else super.getFormatSupport(format)

    override fun getFormatOffloadSupport(format: Format): AudioOffloadSupport =
        if (rejectsBitstream(format)) AudioOffloadSupport.DEFAULT_UNSUPPORTED else super.getFormatOffloadSupport(format)

    private fun rejectsBitstream(format: Format): Boolean = speed != 1f && isEncodedSurround(format)

    internal companion object {
        private val ENCODED_MIME_TYPES = setOf(
            MimeTypes.AUDIO_AC3,
            MimeTypes.AUDIO_E_AC3,
            MimeTypes.AUDIO_E_AC3_JOC,
            MimeTypes.AUDIO_AC4,
            MimeTypes.AUDIO_TRUEHD,
            MimeTypes.AUDIO_DTS,
            MimeTypes.AUDIO_DTS_HD,
            MimeTypes.AUDIO_DTS_EXPRESS,
        )
        private val ENCODED_CODEC_MARKERS = listOf("ac-3", "ac-4", "ec-3", "dts", "truehd")

        fun isEncodedSurround(format: Format): Boolean {
            val mime = format.sampleMimeType
            if (mime != null && (mime in ENCODED_MIME_TYPES || mime.startsWith("audio/vnd.dts"))) return true
            val codecs = format.codecs ?: return false
            return ENCODED_CODEC_MARKERS.any { codecs.contains(it, ignoreCase = true) }
        }
    }
}
