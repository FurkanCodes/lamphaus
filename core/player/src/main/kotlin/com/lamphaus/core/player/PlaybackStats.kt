package com.lamphaus.core.player

import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener

/** Live engine numbers for the player's Info panel. Never carries source details (SHR-PROD-06). */
data class PlaybackStats(
    val videoFormat: Format?,
    val videoDecoder: String?,
    val audioFormat: Format?,
    /** Null while the audio bitstream passes straight to the output route. */
    val audioDecoder: String?,
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

    override fun onAudioInputFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        format: Format,
        decoderReuseEvaluation: DecoderReuseEvaluation?,
    ) {
        // A new format may bypass decoding (passthrough); the next
        // initialization event, if any, names the decoder.
        if (decoderReuseEvaluation == null) audioDecoder = null
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
