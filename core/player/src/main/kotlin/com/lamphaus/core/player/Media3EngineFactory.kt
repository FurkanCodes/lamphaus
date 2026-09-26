package com.lamphaus.core.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import com.lamphaus.core.model.VideoCadenceEstimator
import androidx.media3.common.C
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.lamphaus.core.model.AudioOutputMode
import com.lamphaus.core.player.audio.DelayAudioProcessor
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.extractor.DefaultExtractorsFactory
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.kt.withAssMkvSupport
import io.github.peerless2012.ass.media.kt.withAssSupport
import io.github.peerless2012.ass.media.parser.AssSubtitleParserFactory
import io.github.peerless2012.ass.media.type.AssRenderType
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.lamphaus.core.model.DecoderPriority
import com.lamphaus.core.model.DownmixMode
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
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
        val bufferPlan = StreamingPolicy.bufferPlan(Runtime.getRuntime().maxMemory())
        val httpDataSource = PlaybackNetworking.httpDataSourceFactory()
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
            override fun buildVideoRenderers(
                context: Context,
                extensionRendererMode: Int,
                mediaCodecSelector: MediaCodecSelector,
                enableDecoderFallback: Boolean,
                eventHandler: android.os.Handler,
                eventListener: VideoRendererEventListener,
                allowedVideoJoiningTimeMs: Long,
                out: java.util.ArrayList<Renderer>,
            ) {
                super.buildVideoRenderers(
                    context, extensionRendererMode, mediaCodecSelector, enableDecoderFallback,
                    eventHandler, eventListener, allowedVideoJoiningTimeMs, out,
                )
                // Same settings as Media3's own renderer, plus the DV policy.
                val index = out.indexOfFirst { it.javaClass == MediaCodecVideoRenderer::class.java }
                if (index < 0) return
                out[index] = DolbyVisionAwareVideoRenderer(
                    mediaCodecSelector,
                    MediaCodecVideoRenderer.Builder(context)
                        .setAllowedJoiningTimeMs(allowedVideoJoiningTimeMs)
                        .setEnableDecoderFallback(enableDecoderFallback)
                        .setEventHandler(eventHandler)
                        .setEventListener(eventListener)
                        .setMaxDroppedFramesToNotify(MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY),
                    config.dolbyVisionHandling,
                )
            }

            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setAudioCapabilities(audioCapabilities)
                    .setAudioProcessors(audioProcessors(config.downmixMode))
                    .setEnableAudioTrackPlaybackParams(true)
                    .build()
        }
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector(MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
                if (PlaybackEnginePolicy.shouldSkipMediaCodecForDolbyVision(
                        isEmulator = DeviceEnvironment.isAndroidEmulator(),
                        mimeType = mimeType,
                    )
                ) {
                    emptyList()
                } else {
                    MediaCodecSelector.DEFAULT.getDecoderInfos(
                        mimeType,
                        requiresSecureDecoder,
                        requiresTunnelingDecoder,
                    )
                }
            })
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
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    // Never downmix: keep multichannel tracks eligible even
                    // when the device reports fewer output channels.
                    .setConstrainAudioChannelCountToDeviceCapabilities(
                        config.downmixMode != DownmixMode.NEVER,
                    ),
            )
        }
        // Styled ASS/SSA through libass (Nuvio's approach): embedded Matroska
        // and sidecar tracks are rendered to positioned bitmap cues, so they
        // travel the normal subtitle path — delay, HDR, and the session included.
        val assHandler = AssHandler(AssRenderType.CUES)
        val assParsers = AssSubtitleParserFactory(assHandler)
        return ExoPlayer.Builder(context, renderersFactory.withAssSupport(assHandler))
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    resolvingDataSource,
                    DefaultExtractorsFactory().withAssMkvSupport(assParsers, assHandler),
                )
                    .setSubtitleParserFactory(assParsers)
                    .setLoadErrorHandlingPolicy(StreamingLoadErrorPolicy()),
            )
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            // ALWAYS is owned by PlayerActivity's explicit three-argument
            // Surface.setFrameRate call. Media3 can only request seamless
            // changes, so it must stay off to avoid competing surface votes.
            .setVideoChangeFrameRateStrategy(videoChangeFrameRateStrategy(config))
            .setLoadControl(loadControl(bufferPlan))
            // Holds Wi-Fi out of power save while playing; a dozing radio is a
            // classic cause of short mid-episode stalls on TV boxes (QA-08).
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .apply {
                assHandler.init(this)
                addListener(AutoRetryListener(this))
                statsCollector = PlaybackStatsCollector().also(::addAnalyticsListener)
                videoCadenceEstimator.reset()
                setVideoFrameMetadataListener { presentationTimeUs, _, _, _ ->
                    videoCadenceEstimator.onFrame(presentationTimeUs)
                }
                addListener(object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        videoCadenceEstimator.reset()
                    }
                })
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
     * Decoded audio passes through the optional stereo fold-down, then the
     * route delay. Bitstream passthrough bypasses both (it cannot be mixed).
     */
    private fun audioProcessors(downmixMode: DownmixMode): Array<AudioProcessor> {
        if (downmixMode != DownmixMode.STEREO) return arrayOf(audioDelayProcessor)
        val downmix = ChannelMixingAudioProcessor().apply {
            StereoDownmix.supportedChannelCounts.forEach { channels ->
                putChannelMixingMatrix(ChannelMixingMatrix(channels, 2, StereoDownmix.coefficients(channels)))
            }
        }
        return arrayOf(downmix, audioDelayProcessor)
    }

    /**
     * Time wins over the byte budget until the minimum buffer is met, so a
     * high-bitrate remux cannot starve itself (Nuvio's setting). Media3 still
     * stops early when the heap nears its limit.
     */
    private fun loadControl(plan: PlaybackBufferPlan): DefaultLoadControl =
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                plan.minBufferMs,
                plan.maxBufferMs,
                plan.bufferForPlaybackMs,
                plan.bufferForPlaybackAfterRebufferMs,
            )
            .setTargetBufferBytes(plan.targetBufferBytes)
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(plan.backBufferMs, true)
            .build()

    /**
     * Nuvio-style recovery for errors that escape the load retries: re-prepare
     * at the same position after a short pause, at most twice per stretch of
     * healthy playback (SHR-PROD-04).
     */
    private class AutoRetryListener(private val player: ExoPlayer) : Player.Listener {
        private val handler = android.os.Handler(player.applicationLooper)
        private var attempts = 0
        private var lastRetryAt = 0L

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastRetryAt > StreamingPolicy.AUTO_RETRY_RESET_MS) attempts = 0
            val status = generateSequence<Throwable>(error) { it.cause }
                .filterIsInstance<androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException>()
                .firstOrNull()?.responseCode
            if (!StreamingPolicy.shouldAutoRetry(error.errorCode, status, attempts)) return
            attempts++
            lastRetryAt = now
            handler.postDelayed({
                if (player.playerError == null) return@postDelayed
                if (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    player.seekToDefaultPosition()
                }
                player.prepare()
            }, StreamingPolicy.AUTO_RETRY_DELAY_MS)
        }
    }

    /**
     * Live session player in the same process (set by LamphausPlaybackService).
     * Lets the activity apply the frame-rate hint without recreating the player.
     */
    @Volatile
    var sessionPlayer: ExoPlayer? = null

    private val videoCadenceEstimator = VideoCadenceEstimator()

    private var statsCollector: PlaybackStatsCollector? = null

    /** Main-thread snapshot for the Info panel; null when MPV owns the session. */
    fun playbackStats(): PlaybackStats? {
        val player = sessionPlayer ?: return null
        val collector = statsCollector
        return PlaybackStats(
            videoFormat = player.videoFormat,
            videoDecoder = collector?.videoDecoder,
            audioFormat = player.audioFormat,
            audioDecoder = collector?.audioDecoder,
            audioPassthrough = collector?.audioPassthrough,
            bufferedMillis = player.totalBufferedDuration,
            bandwidthBitsPerSecond = collector?.bandwidthBitsPerSecond ?: 0,
            droppedFrames = player.videoDecoderCounters?.droppedBufferCount ?: 0,
        )
    }

    /** Main-thread snapshot of the decoder's active rendition, including absent manifest FPS. */
    fun currentVideoFormat(): Format? = sessionPlayer?.videoFormat

    fun estimatedVideoFrameRate(): Float = videoCadenceEstimator.frameRate

    /**
     * True when [wanted] changes something only a new player can apply:
     * audio output, decoder priority, downmix, or Dolby Vision handling.
     * Frame-rate matching applies live and never needs a rebuild.
     */
    fun needsRebuild(builtWith: DevicePlaybackConfig, wanted: DevicePlaybackConfig): Boolean =
        builtWith.audioOutputMode != wanted.audioOutputMode ||
            builtWith.decoderPriority != wanted.decoderPriority ||
            builtWith.downmixMode != wanted.downmixMode ||
            builtWith.dolbyVisionHandling != wanted.dolbyVisionHandling

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

    internal fun videoChangeFrameRateStrategy(config: DevicePlaybackConfig): Int =
        when (config.frameRateMatching) {
            FrameRateMatching.SEAMLESS_ONLY -> C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS
            FrameRateMatching.OFF, FrameRateMatching.ALWAYS -> C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
        }
}

/** Process-wide config bridge for components built before settings load. */
object PlaybackEngineConfigHolder {
    @Volatile
    var config: DevicePlaybackConfig = DevicePlaybackConfig()
}
