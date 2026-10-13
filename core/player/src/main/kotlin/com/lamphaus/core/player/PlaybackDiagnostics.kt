package com.lamphaus.core.player

import android.annotation.SuppressLint
import android.content.ComponentCallbacks2
import android.content.Context
import android.media.MediaCodec
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import com.lamphaus.core.model.AppFacts
import com.lamphaus.core.model.CrashFacts
import com.lamphaus.core.model.DecodedMedia
import com.lamphaus.core.model.DecodedTrack
import com.lamphaus.core.model.DeviceFacts
import com.lamphaus.core.model.DevicePlaybackConfig
import com.lamphaus.core.model.DiagnosticText
import com.lamphaus.core.model.EnginePhase
import com.lamphaus.core.model.PlaybackFacts
import com.lamphaus.core.model.PlaybackReport
import com.lamphaus.core.model.PlaybackReportBuilder
import com.lamphaus.core.model.ReportEventType
import com.lamphaus.core.player.mpv.MpvPlayer
import java.io.IOException
import java.util.UUID

/**
 * Playback diagnostics (SHR-PROD-18): records the session player into one
 * [PlaybackReport] per playback. The player screen opens and closes each
 * report, the playback service attaches whichever engine the session runs,
 * and recovery, engine fallback, network, and memory notes arrive from their
 * owners. Reports hold no addresses, headers, titles, add-on names, or
 * exception messages (SHR-PROD-06).
 *
 * Threading: callbacks arrive on the main thread; [crashSnapshot] may come
 * from any thread, so the builder is only touched under [lock].
 */
// Holds only the application context, which lives as long as the process.
@SuppressLint("StaticFieldLeak")
@androidx.annotation.OptIn(UnstableApi::class)
object PlaybackDiagnostics {

    /** Where reports are written; [closed] reports are final. */
    fun interface Sink {
        fun save(report: PlaybackReport, closed: Boolean)
    }

    private const val TAG = "Lamphaus.Playback"
    private const val FLUSH_DELAY_MILLIS = 2_000L
    private const val CHECKPOINT_MILLIS = 30_000L

    private val lock = Any()
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    @Volatile private var sink: Sink? = null
    @SuppressLint("StaticFieldLeak")
    @Volatile private var context: Context? = null
    private var app = AppFacts()
    private var deviceProvider: () -> DeviceFacts = { DeviceFacts() }
    private val device by lazy { deviceProvider() }

    private var builder: PlaybackReportBuilder? = null
    private var attached: Player? = null
    private var bandwidthBps: Long? = null
    private var network: NetworkWatch? = null
    private var flushPending = false

    /** Called once by the application, before any playback. */
    fun install(context: Context, app: AppFacts, device: () -> DeviceFacts, sink: Sink) {
        this.context = context.applicationContext
        this.app = app
        this.deviceProvider = device
        this.sink = sink
    }

    val currentReportId: String? get() = synchronized(lock) { builder?.reportId }

    /**
     * Opens a report for the playback about to start, closing any report
     * still open. Returns its id, or null before [install].
     */
    fun begin(engineSetting: String, startEngine: String, streamType: String, resumed: Boolean, labeledTraits: String): String? {
        val appContext = context ?: return null
        synchronized(lock) { builder }?.let { close(it.reportId, "replaced") }
        val id = UUID.randomUUID().toString()
        val opened = PlaybackReportBuilder(
            id = id,
            startedAtEpochMillis = System.currentTimeMillis(),
            startedAtElapsed = SystemClock.elapsedRealtime(),
            pid = Process.myPid(),
            app = app,
            device = device,
            playback = PlaybackFacts(
                engineSetting = engineSetting,
                startEngine = startEngine,
                streamType = streamType,
                resumed = resumed,
                labeledTraits = labeledTraits,
                settings = Media3EngineFactory.deviceConfig.diagnosticSettings(),
            ),
        )
        synchronized(lock) { builder = opened }
        network = NetworkWatch(appContext) { description -> note(ReportEventType.NETWORK, detail = description) }
            .also { it.start(handler) }
        handler.removeCallbacks(checkpoint)
        handler.postDelayed(checkpoint, CHECKPOINT_MILLIS)
        flush()
        return id
    }

    /** Closes report [reportId] with what the viewer did ([reason]); later calls are ignored. */
    fun close(reportId: String?, reason: String) {
        val report = synchronized(lock) {
            val open = builder?.takeIf { it.reportId == reportId } ?: return
            builder = null
            (attached as? MpvPlayer)?.droppedFrameCount()?.let(open::setDroppedFrames)
            open.close(SystemClock.elapsedRealtime(), System.currentTimeMillis(), reason)
        }
        network?.stop()
        network = null
        handler.removeCallbacks(checkpoint)
        handler.removeCallbacks(flushRunnable)
        flushPending = false
        Log.i(TAG, report.summary())
        sink?.save(report, closed = true)
    }

    /** Report [reportId]'s player screen left the foreground without closing (TV Home, another app), or returned. */
    fun background(reportId: String?, background: Boolean) = record(urgent = true) { now ->
        if (this.reportId == reportId) onBackground(background, now)
    }

    /** The session runs [player] now: the playback service calls this for every engine it installs. */
    fun attach(player: Player) {
        if (attached === player) return
        attached?.let { previous ->
            runCatching { previous.removeListener(listener) }
            runCatching { (previous as? ExoPlayer)?.removeAnalyticsListener(analytics) }
        }
        attached = player
        bandwidthBps = null
        player.addListener(listener)
        (player as? ExoPlayer)?.addAnalyticsListener(analytics)
        record { now -> onEngine(engineName(player), now) }
    }

    /** Recovery, fallback, network, and memory notes; ignored while no playback is open. */
    fun note(type: ReportEventType, value: Long? = null, detail: String? = null, position: Long? = null) {
        val urgent = type == ReportEventType.RETRY || type == ReportEventType.RECOVERY_GAVE_UP ||
            type == ReportEventType.ENGINE_FALLBACK || type == ReportEventType.DOLBY_VISION_BASE_LAYER
        record(urgent) { now -> note(type, now, position, value, detail?.let { DiagnosticText.clean(it) }) }
    }

    fun onTrimMemory(level: Int) {
        if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) return
        val runtime = Runtime.getRuntime()
        val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        note(ReportEventType.LOW_MEMORY, value = level.toLong(), detail = "heap=${usedMb}/${runtime.maxMemory() / (1024 * 1024)}MB")
    }

    /**
     * The open report with [error] attached, for the crash handler to write
     * before the process dies; it stays open so the next launch closes it
     * with Android's exit record.
     */
    fun crashSnapshot(error: Throwable, onMainThread: Boolean): PlaybackReport? = synchronized(lock) {
        val open = builder ?: return null
        val now = System.currentTimeMillis()
        open.onCrash(CrashFacts(now, onMainThread, DiagnosticText.exceptions(error)))
        open.snapshot(now, SystemClock.elapsedRealtime())
    }

    /** progressive, hls, dash, smooth, or rtsp: only the shape of the stream, never where it is. */
    fun streamType(uri: String, mimeType: String?): String = runCatching {
        when (Util.inferContentTypeForUriAndMimeType(uri.toUri(), mimeType)) {
            C.CONTENT_TYPE_HLS -> "hls"
            C.CONTENT_TYPE_DASH -> "dash"
            C.CONTENT_TYPE_SS -> "smooth"
            C.CONTENT_TYPE_RTSP -> "rtsp"
            else -> "progressive"
        }
    }.getOrDefault("other")

    // ── Recording ───────────────────────────────────────────────────────

    private fun record(urgent: Boolean = false, block: PlaybackReportBuilder.(now: Long) -> Unit) {
        synchronized(lock) {
            val open = builder ?: return
            open.block(SystemClock.elapsedRealtime())
        }
        if (urgent) flush() else scheduleFlush()
    }

    private val flushRunnable = Runnable {
        flushPending = false
        flush()
    }

    private val checkpoint = object : Runnable {
        override fun run() {
            flush()
            handler.postDelayed(this, CHECKPOINT_MILLIS)
        }
    }

    private fun scheduleFlush() {
        if (flushPending) return
        flushPending = true
        handler.postDelayed(flushRunnable, FLUSH_DELAY_MILLIS)
    }

    /** Writes the open report as it stands, so a crash or kill still leaves it behind. */
    private fun flush() {
        val report = synchronized(lock) {
            val open = builder ?: return
            (attached as? MpvPlayer)?.droppedFrameCount()?.let(open::setDroppedFrames)
            open.snapshot(System.currentTimeMillis(), SystemClock.elapsedRealtime())
        }
        sink?.save(report, closed = false)
    }

    private fun position(): Long = attached?.currentPosition?.coerceAtLeast(0L) ?: 0L

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val phase = when (playbackState) {
                Player.STATE_BUFFERING -> EnginePhase.BUFFERING
                Player.STATE_READY -> EnginePhase.READY
                Player.STATE_ENDED -> EnginePhase.ENDED
                else -> EnginePhase.IDLE
            }
            val ahead = attached?.totalBufferedDuration ?: 0L
            record(urgent = phase != EnginePhase.READY) { now -> onPhase(phase, now, position(), ahead, bandwidthBps) }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) = record { now -> onPlaying(isPlaying, now) }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = record { now ->
            onPlayWhenReady(
                playWhenReady,
                byViewer = reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
                reason = playWhenReadyReason(reason),
                now = now,
                position = position(),
            )
        }

        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
            if (playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE) return
            note(ReportEventType.SUPPRESSED, value = playbackSuppressionReason.toLong(), detail = suppressionReason(playbackSuppressionReason))
        }

        override fun onPlayerError(error: PlaybackException) {
            // ExoPlayer's errors arrive through [analytics], ahead of the
            // recovery listener, so the timeline reads error, then retry.
            if (attached !is ExoPlayer) recordError(error)
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (reason != Player.DISCONTINUITY_REASON_SEEK) return
            record { now -> onSeek(now, oldPosition.positionMs, newPosition.positionMs) }
        }

        override fun onRenderedFirstFrame() = record(urgent = true) { now -> onFirstFrame(now, position()) }

        override fun onTracksChanged(tracks: Tracks) = record { now -> onMedia(tracks.decodedMedia(), now) }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            // libmpv lists no video track; its picture size stands in.
            if (attached !is MpvPlayer || videoSize.width <= 0) return
            record { now -> onMedia(DecodedMedia(video = DecodedTrack(width = videoSize.width, height = videoSize.height)), now) }
        }
    }

    private fun recordError(error: PlaybackException) = record(urgent = true) { now ->
        onError(now, position(), error.errorCode, error.diagnosticDetail(attached))
    }

    private val analytics = object : AnalyticsListener {
        override fun onPlayerError(eventTime: AnalyticsListener.EventTime, error: PlaybackException) = recordError(error)

        override fun onLoadError(
            eventTime: AnalyticsListener.EventTime,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData,
            error: IOException,
            wasCanceled: Boolean,
        ) {
            // A cancelled load is a seek or a stop, not a failure.
            if (wasCanceled) return
            val status = generateSequence<Throwable>(error) { it.cause }
                .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
                .firstOrNull()?.responseCode
            val detail = listOfNotNull(
                trackTypeName(mediaLoadData.trackType),
                error.javaClass.simpleName,
                status?.let { "http=$it" },
                "after=${loadEventInfo.loadDurationMs}ms",
            ).joinToString(" ")
            record { now -> onLoadError(now, detail) }
        }

        override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) =
            record { now -> onDroppedFrames(now, droppedFrames, elapsedMs) }

        override fun onAudioUnderrun(
            eventTime: AnalyticsListener.EventTime,
            bufferSize: Int,
            bufferSizeMs: Long,
            elapsedSinceLastFeedMs: Long,
        ) = record { now -> onAudioUnderrun(now, "buffer=${bufferSizeMs}ms sinceFeed=${elapsedSinceLastFeedMs}ms") }

        override fun onBandwidthEstimate(
            eventTime: AnalyticsListener.EventTime,
            totalLoadTimeMs: Int,
            totalBytesLoaded: Long,
            bitrateEstimate: Long,
        ) {
            bandwidthBps = bitrateEstimate
        }

        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) = note(ReportEventType.DECODER, value = initializationDurationMs, detail = "video $decoderName")

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) = note(ReportEventType.DECODER, value = initializationDurationMs, detail = "audio $decoderName")

        override fun onAudioTrackInitialized(eventTime: AnalyticsListener.EventTime, audioTrackConfig: AudioSink.AudioTrackConfig) =
            note(
                ReportEventType.DECODER,
                detail = buildString {
                    append(if (Util.isEncodingLinearPcm(audioTrackConfig.encoding)) "output pcm" else "output passthrough")
                    append(" encoding=").append(audioTrackConfig.encoding)
                    append(' ').append(Integer.bitCount(audioTrackConfig.channelConfig)).append("ch")
                    if (audioTrackConfig.tunneling) append(" tunneled")
                    if (audioTrackConfig.offload) append(" offload")
                },
            )

        override fun onVideoCodecError(eventTime: AnalyticsListener.EventTime, videoCodecError: Exception) =
            note(ReportEventType.CODEC_ERROR, detail = "video ${videoCodecError.codecDetail()}")

        override fun onAudioCodecError(eventTime: AnalyticsListener.EventTime, audioCodecError: Exception) =
            note(ReportEventType.CODEC_ERROR, detail = "audio ${audioCodecError.codecDetail()}")

        override fun onAudioSinkError(eventTime: AnalyticsListener.EventTime, audioSinkError: Exception) {
            val code = when (audioSinkError) {
                is AudioSink.InitializationException -> " state=${audioSinkError.audioTrackState}"
                is AudioSink.WriteException -> " code=${audioSinkError.errorCode}"
                else -> ""
            }
            note(ReportEventType.CODEC_ERROR, detail = "audio-sink ${audioSinkError.javaClass.simpleName}$code")
        }
    }

    // ── Descriptions ────────────────────────────────────────────────────

    private fun engineName(player: Player): String = if (player is MpvPlayer) "MPV" else "MEDIA3"

    /**
     * The error's code name, HTTP status, renderer format support, decoder,
     * and cause classes. Exception messages stay out: Media3's can quote a
     * file's own track titles, and causes can name the host.
     */
    private fun PlaybackException.diagnosticDetail(player: Player?): String {
        val causes = generateSequence(cause) { it.cause.takeIf { next -> next !== it } }.take(6).toList()
        val status = causes.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()?.responseCode
        val decoder = causes.firstNotNullOfOrNull { cause ->
            when (cause) {
                is MediaCodecRenderer.DecoderInitializationException -> cause.codecInfo?.name ?: cause.mimeType
                else -> null
            }
        }
        val renderer = (this as? ExoPlaybackException)?.takeIf { it.type == ExoPlaybackException.TYPE_RENDERER }?.let { error ->
            listOfNotNull(
                error.rendererName,
                error.rendererFormat?.sampleMimeType,
                C.getFormatSupportString(error.rendererFormatSupport),
            ).joinToString("/")
        }
        val mpvCode = if (player is MpvPlayer) message?.let { Regex("""error (-?\d+)""").find(it)?.groupValues?.get(1) } else null
        return listOfNotNull(
            errorCodeName,
            status?.let { "http=$it" },
            renderer,
            decoder?.let { "decoder=$it" },
            mpvCode?.let { "mpv=$it" },
            causes.joinToString(">") { it.javaClass.simpleName }.ifEmpty { null },
        ).joinToString(" ")
    }

    private fun Exception.codecDetail(): String = buildString {
        append(javaClass.simpleName)
        (this@codecDetail as? MediaCodec.CodecException)?.let { error ->
            append(" code=").append(error.errorCode)
            if (error.isTransient) append(" transient")
            if (error.isRecoverable) append(" recoverable")
        }
    }

    private fun Tracks.decodedMedia(): DecodedMedia {
        fun selected(type: Int): Format? = groups.firstOrNull { it.type == type && it.isSelected }
            ?.let { group -> (0 until group.length).firstOrNull(group::isTrackSelected)?.let(group::getTrackFormat) }
        val video = selected(C.TRACK_TYPE_VIDEO)
        val audio = selected(C.TRACK_TYPE_AUDIO)
        return DecodedMedia(
            container = (video ?: audio)?.containerMimeType,
            video = video?.decodedTrack(),
            audio = audio?.decodedTrack(),
        )
    }

    private fun Format.decodedTrack() = DecodedTrack(
        mime = sampleMimeType,
        codecs = codecs,
        width = width.takeIf { it > 0 },
        height = height.takeIf { it > 0 },
        frameRate = frameRate.takeIf { it > 0f },
        bitrate = bitrate.takeIf { it > 0 } ?: averageBitrate.takeIf { it > 0 } ?: peakBitrate.takeIf { it > 0 },
        hdr = when {
            sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION -> "DOLBY_VISION"
            colorInfo?.colorTransfer == C.COLOR_TRANSFER_ST2084 -> "HDR10"
            colorInfo?.colorTransfer == C.COLOR_TRANSFER_HLG -> "HLG"
            else -> null
        },
        channels = channelCount.takeIf { it > 0 },
        sampleRate = sampleRate.takeIf { it > 0 },
    )

    private fun playWhenReadyReason(reason: Int): String = when (reason) {
        Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> "audio focus loss"
        Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> "audio output removed"
        Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE -> "remote"
        Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM -> "end of item"
        Player.PLAY_WHEN_READY_CHANGE_REASON_SUPPRESSED_TOO_LONG -> "suppressed too long"
        else -> "reason $reason"
    }

    private fun suppressionReason(reason: Int): String = when (reason) {
        Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS -> "transient audio focus loss"
        Player.PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_OUTPUT -> "unsuitable audio output"
        else -> "reason $reason"
    }

    private fun trackTypeName(trackType: Int): String = when (trackType) {
        C.TRACK_TYPE_VIDEO -> "video"
        C.TRACK_TYPE_AUDIO -> "audio"
        C.TRACK_TYPE_TEXT -> "text"
        C.TRACK_TYPE_UNKNOWN -> "unknown"
        else -> "media"
    }

    private fun PlaybackReport.summary(): String = buildString {
        append("report ").append(id).append(' ').append(outcome)
        append(" engine=").append(state.engine)
        append(" startup=").append(totals.startupMillis ?: "-").append("ms")
        append(" rebuffers=").append(totals.rebuffers).append('/').append(totals.rebufferMillis).append("ms")
        append(" errors=").append(totals.errors)
        append(" retries=").append(totals.retries)
        append(" dropped=").append(totals.droppedFrames)
        append(" underruns=").append(totals.audioUnderruns)
        closeReason?.let { append(" close=").append(it) }
    }

    /** Network changes during a playback: transport and whether it reaches the internet, nothing more. */
    private class NetworkWatch(context: Context, private val onChange: (String) -> Unit) : ConnectivityManager.NetworkCallback() {
        private val connectivity = context.getSystemService(ConnectivityManager::class.java)
        private var last: String? = null

        fun start(handler: Handler) {
            val manager = connectivity ?: return
            runCatching {
                manager.getNetworkCapabilities(manager.activeNetwork)?.let { report(describe(it)) } ?: report("none")
                manager.registerDefaultNetworkCallback(this, handler)
            }
        }

        fun stop() {
            runCatching { connectivity?.unregisterNetworkCallback(this) }
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) =
            report(describe(networkCapabilities))

        override fun onLost(network: Network) = report("lost")

        private fun report(description: String) {
            if (description == last) return
            last = description
            onChange(description)
        }

        private fun describe(capabilities: NetworkCapabilities): String {
            val transport = when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
                else -> "other"
            }
            val vpn = if (transport != "vpn" && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) " vpn" else ""
            val validated = if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) "" else " unvalidated"
            return transport + vpn + validated
        }
    }
}

/** The device's playback settings, for a report: what each one is set to, nothing else. */
internal fun DevicePlaybackConfig.diagnosticSettings(): Map<String, String> = buildMap {
    put("engine", engineKind.name)
    put("autoSwitchEngine", autoSwitchEngineOnStartupError.toString())
    put("dolbyVision", dolbyVisionHandling.name)
    if (dolbyVisionPreserveMapping) put("dvPreserveMapping", "true")
    if (dolbyVisionProfile5To81) put("dvProfile5To81", "true")
    if (stripHdr10Plus) put("stripHdr10Plus", "true")
    put("frameRateMatching", frameRateMatching.name)
    put("resolutionMatching", resolutionMatching.name)
    put("audioOutput", audioOutputMode.name)
    put("decoderPriority", decoderPriority.name)
    put("downmix", downmixMode.name)
    if (nightListening) put("nightListening", "true")
    if (tunneledPlayback) put("tunneled", "true")
    if (skipSilence) put("skipSilence", "true")
    if (forceAc3Transcode) put("forceAc3", "true")
    put("nativeMemory", nativeMemoryBuffer.toString())
    put("parallel", if (parallelConnections) "$parallelConnectionCount×${parallelChunkSizeKb}KB" else "off")
}
