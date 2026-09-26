package com.lamphaus.core.player

import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.analytics.AnalyticsListener

/** Live engine numbers for the player's Info panel. Never carries source details (SHR-PROD-06). */
data class PlaybackStats(
    val videoFormat: Format?,
    val videoDecoder: String?,
    val audioFormat: Format?,
    /** The last audio decoder initialized for this item, if any. */
    val audioDecoder: String?,
    /**
     * What the audio output actually receives: true for a bitstream sent to
     * the receiver, false for decoded PCM, null before the output opens.
     */
    val audioPassthrough: Boolean?,
    val bufferedMillis: Long,
    val bandwidthBitsPerSecond: Long,
    val droppedFrames: Int,
)

/** Remembers what only analytics events reveal: decoder names and the bandwidth estimate. */
@UnstableApi
internal class PlaybackStatsCollector : AnalyticsListener {
    @Volatile var videoDecoder: String? = null
    @Volatile var audioDecoder: String? = null
    @Volatile var bandwidthBitsPerSecond: Long = 0

    override fun onVideoDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long,
    ) {
        videoDecoder = decoderName
    }

    @Volatile var audioPassthrough: Boolean? = null

    // Decoder renderers (FFmpeg) report the input format after initializing,
    // so the output is read from the AudioTrack itself, not event order.
    override fun onAudioTrackInitialized(
        eventTime: AnalyticsListener.EventTime,
        audioTrackConfig: AudioSink.AudioTrackConfig,
    ) {
        audioPassthrough = !Util.isEncodingLinearPcm(audioTrackConfig.encoding)
    }

    override fun onAudioDisabled(eventTime: AnalyticsListener.EventTime, decoderCounters: DecoderCounters) {
        audioDecoder = null
        audioPassthrough = null
    }

    override fun onAudioDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long,
    ) {
        audioDecoder = decoderName
    }

    override fun onBandwidthEstimate(
        eventTime: AnalyticsListener.EventTime,
        totalLoadTimeMs: Int,
        totalBytesLoaded: Long,
        bitrateEstimate: Long,
    ) {
        bandwidthBitsPerSecond = bitrateEstimate
    }
}
