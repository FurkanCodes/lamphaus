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
import com.lamphaus.core.player.audio.NightListeningAudioProcessor
import com.lamphaus.core.player.audio.SpeedAwareAudioSink
import com.lamphaus.core.player.network.HeapChunkMemory
import com.lamphaus.core.player.network.NativeChunkMemory
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.upstream.NativeBuffers
import com.lamphaus.core.player.network.ParallelDownloadPolicy
import com.lamphaus.core.player.network.ParallelDownloadSettings
import com.lamphaus.core.player.network.ParallelDownloadStats
import com.lamphaus.core.player.network.ParallelRangeDataSource
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
        val httpDataSource = PlaybackNetworking.httpDataSourceFactory()
        // Native memory buffer (PLY-NET-01): buffered media and download
        // chunks live off the Java heap, sized by the device's physical RAM.
        val nativeMemory = config.nativeMemoryBuffer && NativeBuffers.isAvailable()
        val totalRamBytes = if (nativeMemory) NativeMemoryPolicy.totalRamBytes(context) else 0L
        val parallel = if (config.parallelConnections) {
            parallelSettings(config, Runtime.getRuntime().maxMemory(), totalRamBytes.takeIf { nativeMemory })
        } else {
            null
        }
        // Parallel connections (PLY-NET-01): the playback's own progressive
        // stream downloads in ranged chunks; everything else stays as it was.
        val streamDataSource = if (parallel != null) {
            ParallelRangeDataSource.Factory(
                upstream = httpDataSource,
                settings = parallel,
                prefetchAllowed = { parallelPrefetchOpen },
                eligible = { dataSpec -> PlaybackHeaderRegistry.isProgressiveStream(dataSpec.uri.toString()) },
            )
        } else {
            httpDataSource
        }
        val resolvingDataSource = ResolvingDataSource.Factory(
            DefaultDataSource.Factory(context, streamDataSource),
        ) { dataSpec ->
            val scheme = dataSpec.uri.scheme
            if (scheme != "http" && scheme != "https") return@Factory dataSpec
            val headers = dataSpec.httpRequestHeaders + PlaybackHeaderRegistry.get(dataSpec.uri.toString())
            dataSpec.withRequestHeaders(PlaybackNetworking.withDefaultUserAgent(headers))
        }
        // Audio output policy (plan §2): AUTO and FORCE_PASSTHROUGH follow the
        // route's live capabilities, so passthrough happens only when the
        // receiver can carry the bitstream. FORCE_DECODE restricts output to
        // PCM, and night listening shapes decoded PCM, so it decodes too.
        val forcePcm = config.nightListening || config.audioOutputMode == AudioOutputMode.FORCE_DECODE
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
            ): AudioSink {
                // With a context DefaultAudioSink follows the route and ignores
                // explicit capabilities, so PCM-only output needs the
                // context-less builder.
                @Suppress("DEPRECATION")
                val builder = if (forcePcm) {
                    DefaultAudioSink.Builder().setAudioCapabilities(AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES)
                } else {
                    DefaultAudioSink.Builder(context)
                }
                // AudioTrack playback params stay at Media3's default (off): on,
                // every AudioTrack buffer is sized for 8x speed. Speed changes
                // go through Sonic on decoded PCM instead (SpeedAwareAudioSink).
                return SpeedAwareAudioSink(
                    builder
                        .setAudioProcessors(audioProcessors(config.downmixMode, config.nightListening))
                        .setEnableFloatOutput(enableFloatOutput)
                        .build(),
                )
            }
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
        // Profile 7 remuxes play as Dolby Vision 8.1 where the decoder takes
        // 8 but not 7, instead of falling back to their HDR10 base layer.
        val extractorsFactory = DolbyVisionProfile7ExtractorsFactory.forDevice(
            DefaultExtractorsFactory().withAssMkvSupport(assParsers, assHandler),
            config.dolbyVisionHandling,
            PlaybackCapabilityProbe.displayDolbyVision(context),
        )
        return ExoPlayer.Builder(context, renderersFactory.withAssSupport(assHandler))
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(resolvingDataSource, extractorsFactory)
                    .setSubtitleParserFactory(assParsers)
                    .setLoadErrorHandlingPolicy(StreamingLoadErrorPolicy()),
            )
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            // ALWAYS is owned by PlayerActivity's explicit three-argument
            // Surface.setFrameRate call. Media3 can only request seamless
            // changes, so it must stay off to avoid competing surface votes.
            .setVideoChangeFrameRateStrategy(videoChangeFrameRateStrategy(config))
            .setLoadControl(
                if (nativeMemory) {
                    nativeLoadControl(totalRamBytes, parallel?.let { ParallelDownloadPolicy.overheadBytes(it.connections, it.chunkBytes) } ?: 0L)
                } else {
                    loadControl()
                },
            )
            .apply {
                if (nativeMemory) {
                    setBandwidthMeter(
                        DefaultBandwidthMeter.Builder(context)
                            .setInitialBitrateEstimate(NativeMemoryPolicy.INITIAL_BITRATE_ESTIMATE)
                            .build(),
                    )
                }
            }
            // Holds Wi-Fi out of power save while playing; a dozing radio is a
            // classic cause of short mid-episode stalls on TV boxes (QA-08).
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .apply {
                assHandler.init(this)
                addListener(PlaybackRecoveryListener(this))
                statsCollector = PlaybackStatsCollector().also(::addAnalyticsListener)
                videoCadenceEstimator.reset()
                setVideoFrameMetadataListener { presentationTimeUs, _, _, _ ->
                    videoCadenceEstimator.onFrame(presentationTimeUs)
                }
                addListener(object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        videoCadenceEstimator.reset()
                        // A new playback holds prefetch until it can play (Nuvio).
                        parallelPrefetchOpen = false
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) parallelPrefetchOpen = true
                    }

                    override fun onRenderedFirstFrame() {
                        parallelPrefetchOpen = true
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
    /** Night listening runs first, so its dialogue lift reaches the centre channel before any downmix. */
    private fun audioProcessors(downmixMode: DownmixMode, nightListening: Boolean): Array<AudioProcessor> {
        val night = listOfNotNull(NightListeningAudioProcessor().takeIf { nightListening })
        if (downmixMode != DownmixMode.STEREO) return (night + audioDelayProcessor).toTypedArray()
        val downmix = ChannelMixingAudioProcessor().apply {
            StereoDownmix.supportedChannelCounts.forEach { channels ->
                putChannelMixingMatrix(ChannelMixingMatrix(channels, 2, StereoDownmix.coefficients(channels)))
            }
        }
        return (night + downmix + audioDelayProcessor).toTypedArray()
    }

    /**
     * Nuvio's default load control: Media3's stock durations (50 s target,
     * playback after 1 s, 2 s after a rebuffer) and byte budget, so loading
     * tops up continuously instead of idling the socket between thresholds.
     */
    private fun loadControl(): DefaultLoadControl =
        DefaultLoadControl.Builder()
            .setBackBuffer(StreamingPolicy.STOCK_BACK_BUFFER_MS, true)
            .build()

    /**
     * Native memory buffer (Nuvio's values): off-heap 64 KiB segments, 15 to
     * 45 s of media capped by the RAM-tier byte target minus what parallel
     * downloads hold. The byte target gates everything, back buffer included,
     * and the back buffer gives way first.
     */
    private fun nativeLoadControl(totalRamBytes: Long, parallelOverheadBytes: Long): DefaultLoadControl =
        DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, NativeMemoryPolicy.SEGMENT_BYTES, 0, true))
            .setTargetBufferBytes(NativeMemoryPolicy.targetBufferBytes(totalRamBytes, parallelOverheadBytes))
            .setBufferDurationsMs(
                NativeMemoryPolicy.MIN_BUFFER_MS,
                NativeMemoryPolicy.MAX_BUFFER_MS,
                NativeMemoryPolicy.BUFFER_FOR_PLAYBACK_MS,
                NativeMemoryPolicy.BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            )
            .setPrioritizeTimeOverSizeThresholds(false)
            .setBackBuffer(NativeMemoryPolicy.backBufferMs, true)
            .build()

    /**
     * Parallel prefetch waits until playback is ready or shows its first
     * frame, so start-up bandwidth goes to the bytes needed first.
     */
    @Volatile
    private var parallelPrefetchOpen = false

    /**
     * [nativeRamBytes] is the device's RAM when chunks live off-heap: the
     * RAM-tier budget beyond the sample buffer then sets the prefetch depth.
     */
    internal fun parallelSettings(
        config: DevicePlaybackConfig,
        maxHeapBytes: Long,
        nativeRamBytes: Long?,
    ): ParallelDownloadSettings {
        val connections = config.parallelConnectionCount.coerceIn(ParallelDownloadPolicy.CONNECTION_RANGE)
        val chunkBytes = ParallelDownloadPolicy.chunkBytes(config.parallelChunkSizeKb, maxHeapBytes)
        val nativeBudget = nativeRamBytes?.let(NativeMemoryPolicy::safeLimitBytes)
        val depth = ParallelDownloadPolicy.prefetchDepth(
            connections = connections,
            chunkBytes = chunkBytes,
            nativeBudgetBytes = nativeBudget,
            reservedBufferBytes = nativeBudget ?: 0L,
        )
        return ParallelDownloadSettings(
            connections = connections,
            chunkBytes = chunkBytes,
            depth = depth,
            sessionChunkCap = ParallelDownloadPolicy.sessionChunkCap(depth, maxHeapBytes),
            memory = if (nativeRamBytes != null) NativeChunkMemory(keep = 2) else HeapChunkMemory(keep = 2),
        )
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
            audioOutputEncoding = collector?.audioOutputEncoding,
            audioOutputChannels = collector?.audioOutputChannels ?: 0,
            measuredFrameRate = videoCadenceEstimator.frameRate,
            bufferedMillis = player.totalBufferedDuration,
            droppedFrames = player.videoDecoderCounters?.droppedBufferCount ?: 0,
            nativeBufferBytes = if (NativeBuffers.isAvailable()) NativeBuffers.getAllocatedBytes() else 0L,
            parallelDownloads = ParallelDownloadStats.activeDownloads.get(),
            rateLimitedResponses = ParallelDownloadStats.rateLimitedResponses.get(),
        )
    }

    /** Main-thread snapshot of the decoder's active rendition, including absent manifest FPS. */
    fun currentVideoFormat(): Format? = sessionPlayer?.videoFormat

    fun estimatedVideoFrameRate(): Float = videoCadenceEstimator.frameRate

    /**
     * True when [wanted] changes something only a new player can apply:
     * audio output, decoder priority, downmix, night listening, Dolby Vision
     * handling, or the streaming engine (PLY-NET-01).
     * Frame-rate matching applies live and never needs a rebuild.
     */
    fun needsRebuild(builtWith: DevicePlaybackConfig, wanted: DevicePlaybackConfig): Boolean =
        builtWith.audioOutputMode != wanted.audioOutputMode ||
            builtWith.decoderPriority != wanted.decoderPriority ||
            builtWith.downmixMode != wanted.downmixMode ||
            builtWith.nightListening != wanted.nightListening ||
            builtWith.nativeMemoryBuffer != wanted.nativeMemoryBuffer ||
            builtWith.parallelConnections != wanted.parallelConnections ||
            builtWith.parallelConnectionCount != wanted.parallelConnectionCount ||
            builtWith.parallelChunkSizeKb != wanted.parallelChunkSizeKb ||
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
