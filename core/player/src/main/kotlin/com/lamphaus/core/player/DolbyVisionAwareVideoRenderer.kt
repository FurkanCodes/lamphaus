package com.lamphaus.core.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.FormatHolder
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import com.lamphaus.core.model.DolbyVisionHandling

/**
 * Media3's video renderer, except that a Dolby Vision stream the policy
 * routes to its base layer is presented to the decoder as that base layer
 * (see [DolbyVisionPolicy]). Everything else is untouched.
 */
@UnstableApi
internal class DolbyVisionAwareVideoRenderer(
    private val selector: MediaCodecSelector,
    builder: Builder,
    private val handling: DolbyVisionHandling,
) : MediaCodecVideoRenderer(builder.setMediaCodecSelector(selector)) {

    override fun supportsFormat(mediaCodecSelector: MediaCodecSelector, format: Format): Int =
        super.supportsFormat(mediaCodecSelector, playable(mediaCodecSelector, format))

    override fun getDecoderInfos(
        mediaCodecSelector: MediaCodecSelector,
        format: Format,
        requiresSecureDecoder: Boolean,
    ): List<MediaCodecInfo> =
        super.getDecoderInfos(mediaCodecSelector, playable(mediaCodecSelector, format), requiresSecureDecoder)

    override fun onInputFormatChanged(formatHolder: FormatHolder) =
        super.onInputFormatChanged(
            formatHolder.apply { format = format?.let { playable(selector, it) } },
        )

    private fun playable(selector: MediaCodecSelector, format: Format): Format {
        if (format.sampleMimeType != MimeTypes.VIDEO_DOLBY_VISION) return format
        val baseLayer = DolbyVisionPolicy.baseLayerMimeType(format.codecs) ?: return format
        val nativeSupport = RendererCapabilities.getFormatSupport(super.supportsFormat(selector, format))
        if (!DolbyVisionPolicy.shouldPlayBaseLayer(handling, nativeSupport == C.FORMAT_HANDLED)) return format
        return format.buildUpon().setSampleMimeType(baseLayer).setCodecs(null).build()
    }
}
