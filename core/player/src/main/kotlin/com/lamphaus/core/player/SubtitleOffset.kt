package com.lamphaus.core.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RendererConfiguration
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SampleStream

/**
 * ExoPlayer's subtitle offset, the Media3 counterpart of mpv's `sub-delay`
 * (plan §4): positive shows subtitles later, negative earlier. Set once per
 * change by the playback service; read on the playback thread.
 */
object SubtitleOffset {
    @Volatile
    var delayUs: Long = 0L
}

/**
 * Renders a text track at the playback position minus [SubtitleOffset.delayUs],
 * so every subtitle — sidecar, embedded, and libass bitmap cues alike — moves
 * both ways without touching the media. Media3's TextRenderer is final, so it
 * is wrapped rather than extended; only the renderer's own clock is shifted.
 */
@UnstableApi
internal class SubtitleOffsetRenderer(private val inner: Renderer) : Renderer by inner {
    private fun shifted(positionUs: Long): Long = positionUs - SubtitleOffset.delayUs

    override fun render(positionUs: Long, elapsedRealtimeUs: Long) =
        inner.render(shifted(positionUs), elapsedRealtimeUs)

    override fun resetPosition(positionUs: Long, sampleStreamIsResetToKeyFrame: Boolean) =
        inner.resetPosition(shifted(positionUs), sampleStreamIsResetToKeyFrame)

    override fun getDurationToProgressUs(positionUs: Long, elapsedRealtimeUs: Long): Long =
        inner.getDurationToProgressUs(shifted(positionUs), elapsedRealtimeUs)

    override fun enable(
        configuration: RendererConfiguration,
        formats: Array<out Format>,
        stream: SampleStream,
        positionUs: Long,
        joining: Boolean,
        mayRenderStartOfStream: Boolean,
        startPositionUs: Long,
        offsetUs: Long,
        mediaPeriodId: MediaSource.MediaPeriodId,
    ) = inner.enable(
        configuration, formats, stream, shifted(positionUs), joining,
        mayRenderStartOfStream, startPositionUs, offsetUs, mediaPeriodId,
    )
}

/** Wraps every text renderer [this] builds in a [SubtitleOffsetRenderer]. */
@UnstableApi
internal fun RenderersFactory.withSubtitleOffset(): RenderersFactory =
    RenderersFactory { handler, video, audio, text, metadata ->
        createRenderers(handler, video, audio, text, metadata)
            .map { renderer -> if (renderer.trackType == C.TRACK_TYPE_TEXT) SubtitleOffsetRenderer(renderer) else renderer }
            .toTypedArray()
    }
