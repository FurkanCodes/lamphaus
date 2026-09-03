package com.lamphaus.core.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
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
        set(value) {
            field = value
            PlaybackEngineConfigHolder.config = value
        }

    fun createPlayer(context: Context, config: DevicePlaybackConfig = deviceConfig): ExoPlayer {
        val httpDataSource = DefaultHttpDataSource.Factory()
            .setUserAgent("Lamphaus/1.0")
            .setAllowCrossProtocolRedirects(false)
        val resolvingDataSource = ResolvingDataSource.Factory(
            DefaultDataSource.Factory(context, httpDataSource),
        ) { dataSpec ->
            val headers = PlaybackHeaderRegistry.get(dataSpec.uri.toString())
            if (headers.isEmpty()) dataSpec else dataSpec.withRequestHeaders(dataSpec.httpRequestHeaders + headers)
        }
        val renderersFactory = DefaultRenderersFactory(context)
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
            .setVideoChangeFrameRateStrategy(
                when (config.frameRateMatching) {
                    FrameRateMatching.OFF -> C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
                    FrameRateMatching.SEAMLESS_ONLY, FrameRateMatching.ALWAYS ->
                        C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS
                },
            )
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
}

/** Process-wide config bridge for components built before settings load. */
object PlaybackEngineConfigHolder {
    @Volatile
    var config: DevicePlaybackConfig = DevicePlaybackConfig()
}
