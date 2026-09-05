package com.lamphaus.core.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.lamphaus.core.model.AudioOutputMode
import com.lamphaus.core.player.audio.DelayAudioProcessor
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.lamphaus.core.model.DecoderPriority
import com.lamphaus.core.model.DevicePlaybackConfig
import com.lamphaus.core.model.FrameRateMatching

/**
 * Builds the Media3 (ExoPlayer) engine from device playback config (plan §1/§2).
 * Extracted from [LamphausPlaybackService] so the construction is testable and
 * the future MPV engine hands off through the same seam.
 */
@UnstableApi
object Media3EngineFactory {

    /**
     * Session-latest device config, published by the Application from
     * UserPreferences. Defaults until the first settings read lands; the
     * service process may start before any activity (media button).
     */
    @Volatile
    var deviceConfig: DevicePlaybackConfig = DevicePlaybackConfig()

    /**
     * Shared audio-delay processor for the session player. One player runs at
     * a time per service, and route changes flush the pipeline, so a single
     * instance gives live per-route delay without touching playback.
     */
    val audioDelayProcessor = DelayAudioProcessor()

    fun createPlayer(context: Context, config: DevicePlaybackConfig = deviceConfig): ExoPlayer {
        val httpDataSource = DefaultHttpDataSource.Factory()
            .setUserAgent("Lamphaus/1.0")
            .setAllowCrossProtocolRedirects(true)
        val resolvingDataSource = ResolvingDataSource.Factory(
            DefaultDataSource.Factory(context, httpDataSource),
        ) { dataSpec ->
            val headers = PlaybackHeaderRegistry.get(dataSpec.uri.toString())
            if (headers.isEmpty()) dataSpec else dataSpec.withRequestHeaders(dataSpec.httpRequestHeaders + headers)
        }
        // Audio output policy (plan §2): AUTO reads the route's real
        // capabilities so passthrough happens only when the receiver can
        // carry the bitstream; FORCE_DECODE restricts capabilities to PCM.
        // FORCE_PASSTHROUGH still respects actual capability — a device
        // cannot carry a format it cannot carry.
        val audioCapabilities = when (config.audioOutputMode) {
            AudioOutputMode.FORCE_DECODE -> AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES
            AudioOutputMode.AUTO, AudioOutputMode.FORCE_PASSTHROUGH ->
                AudioCapabilities.getCapabilities(context)
        }
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setAudioCapabilities(audioCapabilities)
                    .setAudioProcessors(arrayOf(audioDelayProcessor))
                    .setEnableAudioTrackPlaybackParams(true)
                    .build()
        }
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(
                when (config.decoderPriority) {
                    DecoderPriority.SOFTWARE_FIRST -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                    else -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                },
            )
        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setAllowAudioMixedMimeTypeAdaptiveness(true)
                    .setAllowVideoMixedMimeTypeAdaptiveness(true),
            )
        }
        return ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolvingDataSource))
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            // ALWAYS permits non-seamless switches; those are physical
            // display-mode changes driven by display-mode matching (plan §2),
            // not this Media3 surface hint.
            .setVideoChangeFrameRateStrategy(videoChangeFrameRateStrategy(config))
            .setLoadControl(
                DefaultLoadControl.Builder()
                    // Large remuxes can fill a low-memory TV's heap long before the time-based
                    // buffer is reached. Keep a small, byte-bounded buffer and let playback
                    // refill it continuously instead of retaining hundreds of megabytes.
                    .setBufferDurationsMs(10_000, 30_000, 1_000, 2_000)
                    .setTargetBufferBytes(32 * 1024 * 1024)
                    .setPrioritizeTimeOverSizeThresholds(false)
                    .build(),
            )
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    true,
                )
                setHandleAudioBecomingNoisy(true)
                setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT)
            }
    }
    /**
     * Live session player in the same process (set by LamphausPlaybackService).
     * Lets the activity apply the frame-rate hint without recreating the player.
     */
    @Volatile
    var sessionPlayer: ExoPlayer? = null

    /**
     * Live-applies what ExoPlayer supports without recreation: the frame-rate
     * switch hint. Audio caps, decoder priority, and HDR path stay
     * construction-time and apply on next createPlayer (see toggle comment).
     */
    fun applyDeviceConfig(player: ExoPlayer, config: DevicePlaybackConfig) {
        player.videoChangeFrameRateStrategy = videoChangeFrameRateStrategy(config)
    }

    /** Applies the frame-rate hint to the live session player when present. */
    fun applyDeviceConfigToSession(config: DevicePlaybackConfig) {
        sessionPlayer?.let { applyDeviceConfig(it, config) }
    }

    private fun videoChangeFrameRateStrategy(config: DevicePlaybackConfig): Int =
        when (config.frameRateMatching) {
            FrameRateMatching.OFF -> C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
            FrameRateMatching.SEAMLESS_ONLY, FrameRateMatching.ALWAYS ->
                C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS
        }
}

/** Process-wide config bridge for components built before settings load. */
object PlaybackEngineConfigHolder {
    @Volatile
    var config: DevicePlaybackConfig = DevicePlaybackConfig()
}
