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
    /** The AudioTrack's encoding ([androidx.media3.common.C] ENCODING_*), or null before it opens. */
    val audioOutputEncoding: Int?,
    /** Channels the AudioTrack carries; 0 before it opens. */
    val audioOutputChannels: Int,
    /** Measured cadence for sources whose container carries no frame rate. */
    val measuredFrameRate: Float,
    val bufferedMillis: Long,
    val droppedFrames: Int,
    /** Buffered media and download chunks held outside the Java heap (PLY-NET-01). */
    val nativeBufferBytes: Long = 0L,
    /** Range downloads running right now; 0 when parallel connections are off. */
    val parallelDownloads: Int = 0,
    /** Rate-limited (429/503) range responses this session. */
    val rateLimitedResponses: Int = 0,
)

/** Remembers what only analytics events reveal: decoder names and the audio output format. */
@UnstableApi
internal class PlaybackStatsCollector : AnalyticsListener {
    @Volatile var videoDecoder: String? = null
    @Volatile var audioDecoder: String? = null

    override fun onVideoDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long,
    ) {
        videoDecoder = decoderName
    }

    @Volatile var audioPassthrough: Boolean? = null
    @Volatile var audioOutputEncoding: Int? = null
    @Volatile var audioOutputChannels: Int = 0

    // Decoder renderers (FFmpeg) report the input format after initializing,
    // so the output is read from the AudioTrack itself, not event order.
    override fun onAudioTrackInitialized(
        eventTime: AnalyticsListener.EventTime,
        audioTrackConfig: AudioSink.AudioTrackConfig,
    ) {
        audioPassthrough = !Util.isEncodingLinearPcm(audioTrackConfig.encoding)
        audioOutputEncoding = audioTrackConfig.encoding
        audioOutputChannels = Integer.bitCount(audioTrackConfig.channelConfig)
    }

    override fun onAudioDisabled(eventTime: AnalyticsListener.EventTime, decoderCounters: DecoderCounters) {
        audioDecoder = null
        audioPassthrough = null
        audioOutputEncoding = null
        audioOutputChannels = 0
    }

    override fun onAudioDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long,
    ) {
        audioDecoder = decoderName
    }
}
