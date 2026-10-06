package com.lamphaus.core.player.mpv

import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.lamphaus.core.model.AudioOutputDecision
import com.lamphaus.core.model.DolbyVisionAction
import com.lamphaus.core.player.EngineHandoffState
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.json.JSONArray
import java.util.concurrent.CopyOnWriteArrayList

/**
 * MPV engine behind the Media3 [Player] contract via [SimpleBasePlayer]
 * (plan §1): libmpv decodes and renders (libass subtitles included), the
 * media session and the Compose UI keep working unchanged.
 *
 * Threading: SimpleBasePlayer is single-threaded on [looper] (main); mpv
 * calls are serialized through [MpvLibrary]'s lock, and the mpv event pump
 * runs on a daemon thread that posts state refreshes onto [looper].
 *
 * Video output: Nuvio's mpv configuration (PLY-ENG-01) — `vo=gpu` on the
 * Android OpenGL ES context with MediaCodec decoding, so libass subtitles and
 * the OSD draw over the picture, embedded ASS/SSA styling preserved (plan §4).
 */
@UnstableApi
class MpvPlayer(
    looper: Looper,
    /** PEM bundle of trusted authorities for HTTPS ([MpvCertificates]); null disables verification. */
    caFile: String? = null,
) : SimpleBasePlayer(looper) {

    private var handle: Long = 0L
    private var released = false
    private var prepared = false

    private var mediaItem: MediaItem? = null
    private var playWhenReady = false
    private var speed = 1f
    private var volume = 1f

    @Volatile private var timePosMillis = 0L
    @Volatile private var timePosUpdatedAtMillis = 0L
    @Volatile private var durationMillis = C.TIME_UNSET
    @Volatile private var eofReached = false
    @Volatile private var seeking = false
    @Volatile private var pausedForCache = false
    @Volatile private var fileLoaded = false
    /** The file ended on its own (keep-open also reports this through eof-reached). */
    @Volatile private var ended = false
    @Volatile private var playerError: PlaybackException? = null
    /** The first frame of the loaded file was shown and not yet reported. */
    @Volatile private var firstFramePending = false
    @Volatile private var firstFrameShown = false
    /** Playback has started since the last load; mpv reports eof-reached while a file is still opening. */
    @Volatile private var playbackStarted = false
    /** Tags the loaded file's sub-add commands, so replies for a previous file are ignored. */
    @Volatile private var loadGeneration = 0
    /** Add-on subtitles of the loaded file that mpv is downloading now. */
    @Volatile private var subtitlesPending = 0
    /** Add-on subtitles still to start; touched only on the event thread. */
    private val subtitleQueue = ArrayDeque<MediaItem.SubtitleConfiguration>()
    @Volatile private var videoWidth = 0
    @Volatile private var videoHeight = 0
    @Volatile private var containerFrameRate = 0f
    @Volatile private var estimatedFrameRate = 0f
    @Volatile private var tracksSnapshot: Tracks = Tracks.EMPTY
    @Volatile private var selectedAudioId: String? = null
    @Volatile private var selectedSubtitleId: String? = null
    @Volatile private var subtitleDelayMillis = 0L
    @Volatile private var audioDelayMillis = 0L
    /** The session's track parameters: the panels' and defaults policy's overrides, mapped onto mpv ids. */
    @Volatile private var trackParameters: TrackSelectionParameters = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT
    /** Add-on subtitles of the loaded item, added once the file is open. */
    @Volatile private var pendingSubtitles: List<MediaItem.SubtitleConfiguration> = emptyList()
    /** Add-on subtitle URL → the add-on's subtitle id, so mpv's external tracks keep their identity. */
    @Volatile private var addonSubtitleIds: Map<String, String> = emptyMap()
    /** Tracks to restore after a hand-off from Media3, matched once mpv lists its tracks. */
    @Volatile private var pendingRestore: EngineHandoffState? = null

    private val handler = Handler(looper)
    private val observedProperties = CopyOnWriteArrayList(
        listOf(
            "time-pos", "duration", "pause", "speed", "eof-reached", "seeking",
            "paused-for-cache", "track-list", "video-params/w", "video-params/h",
            "container-fps", "estimated-vf-fps", "aid", "sid",
        ),
    )
    private var eventThread: Thread? = null

    val isUsable: Boolean get() = handle != 0L && !released

    init {
        handle = MpvLibrary.create()
        check(handle != 0L) { "libmpv.so is not present; check MpvLibrary.availability first" }
        // Nuvio's mpv setup: the fast profile on the GPU output, OpenGL ES on
        // the Android context, MediaCodec for the codecs it accelerates.
        MpvLibrary.setOptionString(handle, "profile", "fast")
        MpvLibrary.setOptionString(handle, "vo", "gpu")
        MpvLibrary.setOptionString(handle, "gpu-context", "android")
        MpvLibrary.setOptionString(handle, "opengl-es", "yes")
        MpvLibrary.setOptionString(handle, "hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
        // MediaCodec frames go straight to the GPU (AImageReader); the copy
        // path reads every frame back through the CPU and manages only a few
        // frames a second on TV boxes. Goldfish/Ranchu exposes HEVC but cannot
        // decode Dolby Vision profiles, so the emulator decodes in software.
        MpvLibrary.setOptionString(
            handle,
            "hwdec",
            if (com.lamphaus.core.player.DeviceEnvironment.isAndroidEmulator()) "no" else "mediacodec,mediacodec-copy",
        )
        MpvLibrary.setOptionString(handle, "ao", "audiotrack,opensles")
        // Network: Nuvio's 64 MiB forward and back demuxer caches, the same
        // browser identity as Media3, and certificate checks for HTTPS.
        MpvLibrary.setOptionString(handle, "demuxer-max-bytes", "64MiB")
        MpvLibrary.setOptionString(handle, "demuxer-max-back-bytes", "64MiB")
        MpvLibrary.setOptionString(handle, "user-agent", com.lamphaus.core.player.PlaybackNetworking.DEFAULT_USER_AGENT)
        if (caFile != null) {
            MpvLibrary.setOptionString(handle, "tls-verify", "yes")
            MpvLibrary.setOptionString(handle, "tls-ca-file", caFile)
        }
        // Night listening (SHR-PROD-15): FFmpeg's compressor with the same
        // shape as the Media3 path (-30 dB, 4:1, +8 dB makeup), then a limiter.
        if (com.lamphaus.core.player.Media3EngineFactory.deviceConfig.nightListening) {
            MpvLibrary.setOptionString(handle, "af", NIGHT_LISTENING_FILTER)
        }
        // Embedded ASS/SSA styling through libass stays on by default (plan §4).
        MpvLibrary.setOptionString(handle, "sub-ass", "yes")
        MpvLibrary.setOptionString(handle, "keep-open", "yes")
        MpvLibrary.setOptionString(handle, "input-default-bindings", "no")
        MpvLibrary.setOptionString(handle, "osc", "no")
        check(MpvLibrary.initialize(handle)) { "libmpv failed to initialize" }
        observedProperties.forEach { MpvLibrary.observeProperty(handle, it) }
        startEventPump()
    }

    // ── Public engine surface (used by the fallback handoff and panels) ──

    fun load(item: MediaItem, startPositionMillis: Long, headers: Map<String, String>) {
        val args = buildList {
            add("loadfile")
            add(item.localConfiguration?.uri?.toString().orEmpty())
            add("replace")
            // Header fields ride as per-file options, never logged (SHR-PROD-06).
            val headerOption = headers.entries.joinToString(",") { entry ->
                "http-header-fields=${entry.key}: ${entry.value}"
            }
            val options = buildList {
                headerOption.takeIf(String::isNotEmpty)?.let(::add)
                if (startPositionMillis > 0) add("start=${startPositionMillis / 1000.0}")
            }.joinToString(",")
            if (options.isNotEmpty()) add(options)
        }
        fileLoaded = false
        eofReached = false
        ended = false
        playerError = null
        firstFramePending = false
        firstFrameShown = false
        playbackStarted = false
        loadGeneration = (loadGeneration + 1) and 0xFFFF
        subtitlesPending = 0
        videoWidth = 0
        videoHeight = 0
        containerFrameRate = 0f
        estimatedFrameRate = 0f
        tracksSnapshot = Tracks.EMPTY
        mediaItem = item
        val subtitles = item.localConfiguration?.subtitleConfigurations.orEmpty()
        pendingSubtitles = subtitles
        addonSubtitleIds = subtitles.mapNotNull { config -> config.id?.let { config.uri.toString() to it } }.toMap()
        // mpv's own first pick follows the same languages and off state the
        // session asked for, before the defaults policy sees the tracks.
        applyTrackPlan(MpvTrackMapping.plan(trackParameters))
        MpvLibrary.command(handle, args)
        invalidateState()
    }

    /**
     * Restores a Media3 session's timing and tracks (plan §1). Tracks are
     * matched once mpv has opened the file and added the add-on subtitles:
     * the same add-on subtitle, else the same languages.
     */
    fun restore(state: EngineHandoffState) {
        setSubtitleDelayMillis(state.subtitleDelayMillis)
        setAudioDelayMillis(state.audioDelayMillis)
        pendingRestore = state
        if (fileLoaded && pendingSubtitles.isEmpty()) applyAfterLoad(final = true)
    }

    fun setSubtitleDelayMillis(millis: Long) {
        subtitleDelayMillis = millis
        // mpv's sub-delay sign convention matches ours: positive delays text.
        MpvLibrary.setPropertyString(handle, "sub-delay", (millis / 1000.0).toString())
    }

    fun setAudioDelayMillis(millis: Long) {
        audioDelayMillis = millis
        MpvLibrary.setPropertyString(handle, "audio-delay", (millis / 1000.0).toString())
    }

    /**
     * Applies the resolved Dolby Vision action (plan §2). Native and P7
     * conversion need no configuration: mediacodec_embed passes the stream
     * untouched, and mpv/libplacebo handles DV mapping when built with
     * libdovi. Only the tone-map action changes engine configuration —
     * mediacodec_embed cannot tone-map, so the GPU path takes over.
     */
    fun applyDolbyVisionAction(action: DolbyVisionAction) {
        when (action) {
            DolbyVisionAction.TONE_MAP_TO_SDR -> {
                MpvLibrary.setOptionString(handle, "vo", "gpu")
                MpvLibrary.setOptionString(handle, "gpu-context", "android")
                MpvLibrary.setOptionString(handle, "tone-mapping", "bt.2446a")
                MpvLibrary.setOptionString(handle, "target-colorspace-hint", "yes")
            }
            DolbyVisionAction.NATIVE,
            DolbyVisionAction.CONVERT_PROFILE7_TO_81,
            DolbyVisionAction.HDR10_BASE_LAYER,
            DolbyVisionAction.DISABLED,
            -> Unit
        }
    }

    /**
     * Applies the profile subtitle style (plan §4 live editor). With
     * [SubtitleStyle.preserveEmbeddedStyles] the libass authoring wins and
     * only timing/delay apply; otherwise mpv's style properties override.
     */
    fun applySubtitleStyle(style: com.lamphaus.core.model.SubtitleStyle, liftFraction: Float = 0f) {
        val overrideEmbedded = !style.preserveEmbeddedStyles
        // The chrome lift must hold even while embedded ASS authoring wins, so it
        // is applied before the override early-return (PLY-IMM-03).
        MpvLibrary.setPropertyString(
            handle,
            "sub-margin-y",
            "${(liftFraction.coerceIn(0f, 1f) * 100).toInt()}%",
        )
        MpvLibrary.setPropertyString(handle, "sub-ass-override", if (overrideEmbedded) "yes" else "no")
        if (!overrideEmbedded) return
        fun color(argb: Long, opacity: Float): String {
            val alpha = ((argb ushr 24) * opacity).toInt().coerceIn(0, 255)
            return "#%02X%06X".format(alpha, argb and 0xFFFFFF)
        }
        MpvLibrary.setPropertyString(
            handle,
            "sub-font-size",
            (com.lamphaus.core.model.SubtitleStylePolicy.clampSizePercent(style.sizePercent) * 0.55).toString(),
        )
        MpvLibrary.setPropertyString(
            handle,
            "sub-pos",
            (com.lamphaus.core.model.SubtitleStylePolicy.clampPositionFraction(style.verticalPositionFraction) * 100)
                .toInt().toString(),
        )
        MpvLibrary.setPropertyString(handle, "sub-bold", if (style.bold) "yes" else "no")
        MpvLibrary.setPropertyString(
            handle,
            "sub-font",
            when (style.fontFamily) {
                com.lamphaus.core.model.SubtitleFontFamily.SYSTEM -> "sans-serif"
                com.lamphaus.core.model.SubtitleFontFamily.SANS_SERIF -> "sans-serif"
                com.lamphaus.core.model.SubtitleFontFamily.SERIF -> "serif"
                com.lamphaus.core.model.SubtitleFontFamily.MONOSPACE -> "monospace"
            },
        )
        MpvLibrary.setPropertyString(
            handle,
            "sub-color",
            color(style.textColor, com.lamphaus.core.model.SubtitleStylePolicy.clampOpacity(style.textOpacity)),
        )
        val hasBackground = com.lamphaus.core.model.SubtitleStylePolicy.clampOpacity(style.backgroundOpacity) > 0f
        MpvLibrary.setPropertyString(handle, "sub-border-style", if (hasBackground) "4" else "1")
        MpvLibrary.setPropertyString(
            handle,
            "sub-back-color",
            color(
                style.backgroundColor,
                com.lamphaus.core.model.SubtitleStylePolicy.clampOpacity(style.backgroundOpacity),
            ),
        )
        MpvLibrary.setPropertyString(handle, "sub-outline-width", "%.1f".format(style.outlineWidthDp))
        MpvLibrary.setPropertyString(
            handle,
            "sub-shadow",
            when (style.edgeStyle) {
                com.lamphaus.core.model.SubtitleEdgeStyle.NONE -> "0"
                com.lamphaus.core.model.SubtitleEdgeStyle.DROP_SHADOW -> "1"
                com.lamphaus.core.model.SubtitleEdgeStyle.RAISED -> "2"
                com.lamphaus.core.model.SubtitleEdgeStyle.DEPRESSED -> "3"
                com.lamphaus.core.model.SubtitleEdgeStyle.OUTLINE -> "0"
            },
        )
    }

    /** Applies the audio output decision (plan §2): bitstream vs decode path. */
    fun applyAudioOutput(decision: AudioOutputDecision) {
        when (decision) {
            is AudioOutputDecision.Passthrough ->
                MpvLibrary.setPropertyString(handle, "audio-spdif", "ac3,dts,eac3,truehd")
            is AudioOutputDecision.Decode -> {
                MpvLibrary.setPropertyString(handle, "audio-spdif", "")
                MpvLibrary.setPropertyString(
                    handle,
                    "audio-channels",
                    if (decision.toStereo) "stereo" else "original",
                )
            }
        }
    }

    fun selectAudioTrack(mpvTrackId: String) = selectTrack("aid", mpvTrackId)

    fun selectSubtitleTrack(mpvTrackId: String?) {
        if (mpvTrackId == null) {
            selectedSubtitleId = null
            MpvLibrary.setPropertyString(handle, "sid", "no")
        } else {
            selectTrack("sid", mpvTrackId)
        }
        invalidateState()
    }

    private fun selectTrack(property: String, mpvTrackId: String) {
        setTrackProperty(property, mpvTrackId)
        invalidateState()
    }

    /** Sets `aid`/`sid` without a state refresh, for use inside SimpleBasePlayer handlers. */
    private fun setTrackProperty(property: String, value: String) {
        if (property == "aid") {
            selectedAudioId = value
        } else {
            selectedSubtitleId = value.takeUnless { it == "no" }
        }
        MpvLibrary.setPropertyString(handle, property, value)
    }

    private fun applyTrackPlan(plan: MpvTrackPlan) {
        MpvLibrary.setPropertyString(handle, "alang", plan.alang)
        MpvLibrary.setPropertyString(handle, "slang", plan.slang)
        plan.aid?.let { setTrackProperty("aid", it) }
        plan.sid?.let { setTrackProperty("sid", it) }
    }

    /**
     * The file is open: add its add-on subtitles off the event thread (each
     * is a download), then restore a hand-off's tracks or re-apply the
     * session's overrides against mpv's now-known ids.
     */
    private fun onFileLoaded() {
        val subtitles = pendingSubtitles
        if (subtitles.isEmpty()) {
            applyAfterLoad(final = true)
            return
        }
        applyAfterLoad(final = false)
        // Each sub-add downloads its file. mpv does that on its own threads and
        // replies when done, so nothing here waits on the network; a few at a
        // time, preferred languages first, so the video's own download comes
        // first and the likely pick appears early.
        subtitleQueue.clear()
        subtitleQueue.addAll(MpvTrackMapping.subtitleLoadOrder(subtitles, trackParameters.preferredTextLanguages))
        subtitlesPending = 0
        repeat(SUBTITLE_DOWNLOADS) { startNextSubtitle() }
        if (subtitlesPending == 0) onSubtitlesAdded()
    }

    private fun startNextSubtitle() {
        while (true) {
            val config = subtitleQueue.removeFirstOrNull() ?: return
            // "auto": listed but not selected; the defaults policy or the viewer picks.
            val started = MpvLibrary.commandAsync(
                handle,
                loadGeneration,
                listOf("sub-add", config.uri.toString(), "auto", config.label.orEmpty(), config.language.orEmpty()),
            )
            if (started) {
                subtitlesPending += 1
                return
            }
        }
    }

    /** One add-on subtitle is in (or failed): list it, and start the next. */
    private fun onSubtitleAdded() {
        subtitlesPending -= 1
        startNextSubtitle()
        if (subtitlesPending == 0) {
            onSubtitlesAdded()
        } else {
            applyAfterLoad(final = false)
            refreshTracks()
        }
    }

    /** Every add-on subtitle is in (or failed): restore or apply the session's tracks. */
    private fun onSubtitlesAdded() {
        pendingSubtitles = emptyList()
        applyAfterLoad(final = true)
        refreshTracks()
    }

    private fun applyAfterLoad(final: Boolean) {
        val restore = pendingRestore
        if (restore == null) {
            applyTrackPlan(MpvTrackMapping.plan(trackParameters))
            return
        }
        val (aid, sid) = MpvTrackMapping.restoreSelection(trackEntries(), restore, addonSubtitleIds)
        aid?.let { setTrackProperty("aid", it) }
        sid?.let { setTrackProperty("sid", it) }
        if (final) pendingRestore = null
    }

    // ── SimpleBasePlayer plumbing ────────────────────────────────────────

    override fun getState(): State {
        val item = mediaItem
        val error = playerError
        val playbackState = when {
            !prepared || error != null || item == null -> Player.STATE_IDLE
            ended || (eofReached && playbackStarted && !seeking) -> Player.STATE_ENDED
            fileLoaded || seeking || pausedForCache -> {
                if (pausedForCache || seeking) Player.STATE_BUFFERING else Player.STATE_READY
            }
            else -> Player.STATE_BUFFERING
        }
        val tracks = tracksSnapshot
        val builder = State.Builder()
            .setAvailableCommands(
                Player.Commands.Builder()
                    .addAll(
                        Player.COMMAND_PLAY_PAUSE,
                        Player.COMMAND_PREPARE,
                        // Without these Media3 drops the session's media item and surface.
                        Player.COMMAND_SET_MEDIA_ITEM,
                        Player.COMMAND_CHANGE_MEDIA_ITEMS,
                        Player.COMMAND_SET_VIDEO_SURFACE,
                        Player.COMMAND_GET_TIMELINE,
                        Player.COMMAND_GET_METADATA,
                        Player.COMMAND_GET_VOLUME,
                        Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                        Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
                        Player.COMMAND_SEEK_BACK,
                        Player.COMMAND_SEEK_FORWARD,
                        Player.COMMAND_SET_SPEED_AND_PITCH,
                        Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                        Player.COMMAND_GET_TRACKS,
                        Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS,
                        Player.COMMAND_SET_VOLUME,
                        Player.COMMAND_STOP,
                        Player.COMMAND_RELEASE,
                    )
                    .build(),
            )
            .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(playbackState)
            .setPlayerError(error)
            .setNewlyRenderedFirstFrame(firstFramePending)
            .setPlaybackParameters(PlaybackParameters(speed))
            .setVolume(volume)
            .setVideoSize(androidx.media3.common.VideoSize(videoWidth, videoHeight))
            .setTrackSelectionParameters(trackParameters)
        if (item != null) {
            val itemData = MediaItemData.Builder(item.mediaId)
                .setMediaItem(item)
                .setMediaMetadata(item.mediaMetadata)
                .setDurationUs(if (durationMillis == C.TIME_UNSET) C.TIME_UNSET else durationMillis * 1000)
                .setIsSeekable(true)
                .setTracks(tracks)
                .build()
            builder.setPlaylist(listOf(itemData))
                .setCurrentMediaItemIndex(0)
                .setContentPositionMs(PositionSupplier { extrapolatedPositionMillis() })
                .setContentBufferedPositionMs(PositionSupplier { extrapolatedPositionMillis() })
        } else {
            builder.setPlaylist(emptyList<MediaItemData>())
        }
        return builder.build()
    }

    private fun extrapolatedPositionMillis(): Long {
        if (!playWhenReady || pausedForCache || seeking) return timePosMillis
        val elapsed = System.currentTimeMillis() - timePosUpdatedAtMillis
        if (durationMillis != C.TIME_UNSET) {
            return (timePosMillis + elapsed).coerceAtMost(durationMillis)
        }
        return timePosMillis + elapsed
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        this.playWhenReady = playWhenReady
        MpvLibrary.setPropertyString(handle, "pause", if (playWhenReady) "no" else "yes")
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> {
        prepared = true
        val item = mediaItem
        if (playerError != null && item != null) {
            // Retry after a failure: open the same file again where it stopped.
            val uri = item.localConfiguration?.uri?.toString().orEmpty()
            load(item, timePosMillis, com.lamphaus.core.player.PlaybackHeaderRegistry.get(uri))
            return Futures.immediateVoidFuture()
        }
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        prepared = false
        MpvLibrary.command(handle, listOf("stop"))
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        released = true
        // The event thread must be out of mpv before its handle is destroyed.
        MpvLibrary.wakeup(handle)
        eventThread?.interrupt()
        eventThread?.join(EVENT_THREAD_JOIN_MILLIS)
        MpvLibrary.destroy(handle)
        handle = 0L
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> {
        speed = playbackParameters.speed
        MpvLibrary.setPropertyString(handle, "speed", speed.toString())
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        seeking = true
        timePosMillis = positionMs
        timePosUpdatedAtMillis = System.currentTimeMillis()
        MpvLibrary.command(handle, listOf("seek", (positionMs / 1000.0).toString(), "absolute+exact"))
        return Futures.immediateVoidFuture()
    }

    override fun handleSetMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<*> {
        val item = mediaItems.firstOrNull() ?: return Futures.immediateVoidFuture()
        mediaItem = item
        val uri = item.localConfiguration?.uri?.toString().orEmpty()
        load(item, startPositionMs.coerceAtLeast(0), com.lamphaus.core.player.PlaybackHeaderRegistry.get(uri))
        prepared = true
        return Futures.immediateVoidFuture()
    }

    override fun handleAddMediaItems(index: Int, mediaItems: List<MediaItem>): ListenableFuture<*> =
        handleSetMediaItems(mediaItems, 0, C.TIME_UNSET)

    /**
     * A MediaSession hands its player a SurfaceHolder wrapping the
     * controller's Surface (Media3 1.11); an in-process caller may pass the
     * Surface itself.
     */
    override fun handleSetVideoOutput(videoOutput: Any): ListenableFuture<*> {
        val surface = when (videoOutput) {
            is Surface -> videoOutput
            is android.view.SurfaceHolder -> videoOutput.surface
            else -> null
        }
        if (surface != null && surface.isValid) MpvLibrary.attachSurface(handle, surface)
        return Futures.immediateVoidFuture()
    }

    override fun handleClearVideoOutput(videoOutput: Any?): ListenableFuture<*> {
        MpvLibrary.detachSurface(handle)
        return Futures.immediateVoidFuture()
    }

    override fun handleSetVolume(volume: Float): ListenableFuture<*> {
        this.volume = volume
        MpvLibrary.setPropertyString(handle, "volume", (volume * 100).toString())
        return Futures.immediateVoidFuture()
    }

    /**
     * The panels and the defaults policy select tracks through Media3
     * overrides; mpv gets the matching `aid`/`sid` and language lists, and
     * reports the parameters back through [getState] (plan §3).
     */
    override fun handleSetTrackSelectionParameters(
        trackSelectionParameters: TrackSelectionParameters,
    ): ListenableFuture<*> {
        trackParameters = trackSelectionParameters
        applyTrackPlan(MpvTrackMapping.plan(trackSelectionParameters))
        return Futures.immediateVoidFuture()
    }

    // ── mpv event pump ───────────────────────────────────────────────────

    private fun startEventPump() {
        eventThread = Thread(
            {
                while (!released && handle != 0L) {
                    try {
                        val code = MpvLibrary.waitEvent(handle, 0.1)
                        if (released) return@Thread
                        var restarted = false
                        when (code and 0xFF) {
                            EVENT_FILE_LOADED -> {
                                fileLoaded = true
                                onFileLoaded()
                            }
                            EVENT_END_FILE -> when ((code shr 8) and 0xFF) {
                                END_FILE_REASON_EOF -> ended = true
                                END_FILE_REASON_ERROR -> playerError = mpvError(-((code shr 16) and 0xFF))
                            }
                            EVENT_COMMAND_REPLY -> {
                                val generation = (code shr 8) and 0xFFFF
                                if (generation == loadGeneration && subtitlesPending > 0) onSubtitleAdded()
                            }
                            EVENT_PLAYBACK_RESTART -> {
                                restarted = true
                                playbackStarted = true
                            }
                            EVENT_SHUTDOWN -> return@Thread
                        }
                        refreshTrackedProperties()
                        // mpv shows the first frame of a file, paused or not, before it restarts playback.
                        if (restarted && !firstFrameShown && videoWidth > 0) {
                            firstFrameShown = true
                            firstFramePending = true
                        }
                        handler.post {
                            if (!released) invalidateState()
                            // Reported once: the next state no longer carries the first frame.
                            if (firstFramePending) {
                                firstFramePending = false
                                if (!released) invalidateState()
                            }
                        }
                    } catch (_: InterruptedException) {
                        return@Thread
                    }
                }
            },
            "lamphaus-mpv-events",
        ).apply { isDaemon = true; start() }
    }

    /** A file mpv could not open or play, in Media3's terms so the engine switch can judge it. */
    private fun mpvError(mpvErrorCode: Int): PlaybackException {
        val code = when (mpvErrorCode) {
            MPV_ERROR_LOADING_FAILED, MPV_ERROR_NOTHING_TO_PLAY, MPV_ERROR_UNKNOWN_FORMAT ->
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
            MPV_ERROR_AO_INIT_FAILED, MPV_ERROR_VO_INIT_FAILED -> PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
            else -> PlaybackException.ERROR_CODE_UNSPECIFIED
        }
        return PlaybackException("libmpv could not play the stream (error $mpvErrorCode)", null, code)
    }

    private fun refreshTrackedProperties() {
        timePosMillis = (MpvLibrary.getPropertyString(handle, "time-pos")?.toDoubleOrNull() ?: 0.0).secondsToMillis()
        timePosUpdatedAtMillis = System.currentTimeMillis()
        MpvLibrary.getPropertyString(handle, "duration")?.toDoubleOrNull()?.let { durationMillis = it.secondsToMillis() }
        MpvLibrary.getPropertyString(handle, "pause")?.let { playWhenReady = it == "no" }
        MpvLibrary.getPropertyString(handle, "speed")?.toDoubleOrNull()?.let { speed = it.toFloat() }
        MpvLibrary.getPropertyString(handle, "eof-reached")?.let { eofReached = it == "yes" }
        MpvLibrary.getPropertyString(handle, "seeking")?.let { seeking = it == "yes" }
        MpvLibrary.getPropertyString(handle, "paused-for-cache")?.let { pausedForCache = it == "yes" }
        MpvLibrary.getPropertyString(handle, "video-params/w")?.toIntOrNull()?.let { videoWidth = it }
        MpvLibrary.getPropertyString(handle, "video-params/h")?.toIntOrNull()?.let { videoHeight = it }
        containerFrameRate = MpvLibrary.getPropertyString(handle, "container-fps")
            .validFrameRateOrZero()
        estimatedFrameRate = MpvLibrary.getPropertyString(handle, "estimated-vf-fps")
            .validFrameRateOrZero()
        MpvLibrary.getPropertyString(handle, "aid")?.let { if (it != "no") selectedAudioId = it }
        MpvLibrary.getPropertyString(handle, "sid")?.let { selectedSubtitleId = if (it == "no") null else it }
        refreshTracks()
    }

    /** mpv's `track-list`, audio and subtitle entries only. */
    private fun trackEntries(): List<MpvTrackEntry> {
        val raw = MpvLibrary.getPropertyString(handle, "track-list") ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val entry = array.optJSONObject(i) ?: return@mapNotNull null
                val type = entry.optString("type")
                if (type != MpvTrackMapping.AUDIO && type != MpvTrackMapping.SUBTITLE) return@mapNotNull null
                MpvTrackEntry(
                    type = type,
                    id = entry.optInt("id").toString(),
                    language = entry.optString("lang").takeIf(String::isNotEmpty),
                    title = entry.optString("title").takeIf(String::isNotEmpty),
                    codec = entry.optString("codec").takeIf(String::isNotEmpty),
                    external = entry.optBoolean("external"),
                    externalFilename = entry.optString("external-filename").takeIf(String::isNotEmpty),
                    forced = entry.optBoolean("forced"),
                    default = entry.optBoolean("default"),
                    selected = entry.optBoolean("selected"),
                    channelCount = entry.optInt("demux-channel-count", if (type == MpvTrackMapping.AUDIO) 2 else 0),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun refreshTracks() {
        val groups = mutableListOf<Tracks.Group>()
        val addonIds = addonSubtitleIds
        trackEntries().forEach { entry ->
            val format = Format.Builder()
                .setId(MpvTrackMapping.formatId(entry, addonIds))
                .setLabel(entry.title)
                .setLanguage(entry.language)
                .setSampleMimeType(MpvTrackMapping.mimeType(entry.type, entry.codec))
                .setSelectionFlags(MpvTrackMapping.selectionFlags(entry))
                .setChannelCount(entry.channelCount)
                .build()
            val group = TrackGroup(format).copyWithId(MpvTrackMapping.groupId(entry.type, entry.id))
            groups += Tracks.Group(
                group,
                /* isAdaptive = */ false,
                intArrayOf(C.FORMAT_HANDLED),
                booleanArrayOf(entry.selected),
            )
        }
        mpvVideoFormat(videoWidth, videoHeight, containerFrameRate, estimatedFrameRate)?.let { format ->
            groups += Tracks.Group(
                TrackGroup(format).copyWithId("video-current"),
                /* isAdaptive = */ false,
                intArrayOf(C.FORMAT_HANDLED),
                booleanArrayOf(true),
            )
        }
        tracksSnapshot = Tracks(groups)
    }

    private fun Double.secondsToMillis(): Long = (this * 1000).toLong()

    private fun String?.validFrameRateOrZero(): Float =
        this?.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f } ?: 0f

    private companion object {
        const val EVENT_NONE = 0
        const val EVENT_SHUTDOWN = 1
        const val EVENT_FILE_LOADED = 8
        const val EVENT_END_FILE = 7
        const val EVENT_PLAYBACK_RESTART = 21
        const val EVENT_COMMAND_REPLY = 5
        const val SUBTITLE_DOWNLOADS = 4
        const val END_FILE_REASON_EOF = 0
        const val END_FILE_REASON_ERROR = 4
        const val MPV_ERROR_LOADING_FAILED = -13
        const val MPV_ERROR_AO_INIT_FAILED = -14
        const val MPV_ERROR_VO_INIT_FAILED = -15
        const val MPV_ERROR_NOTHING_TO_PLAY = -16
        const val MPV_ERROR_UNKNOWN_FORMAT = -17
        const val EVENT_THREAD_JOIN_MILLIS = 1_000L

    }
}

/** Selected MPV output format exposed through Media3's track contract (QA-06). */
internal fun mpvVideoFormat(
    width: Int,
    height: Int,
    containerFrameRate: Float,
    estimatedFrameRate: Float,
): Format? {
    if (width <= 0 || height <= 0) return null
    val frameRate = estimatedFrameRate.takeIf { it.isFinite() && it > 0f }
        ?: containerFrameRate.takeIf { it.isFinite() && it > 0f }
        ?: Format.NO_VALUE.toFloat()
    return Format.Builder()
        .setId("mpv-video-current")
        .setSampleMimeType("video/x-unknown")
        .setWidth(width)
        .setHeight(height)
        .setFrameRate(frameRate)
        .build()
}

/** mpv `af` chain for night listening; threshold and makeup are linear (0.0316 ≈ -30 dBFS, 2.5 ≈ +8 dB). */
private const val NIGHT_LISTENING_FILTER =
    "lavfi=[acompressor=threshold=0.0316:ratio=4:attack=5:release=250:makeup=2.5,alimiter=limit=0.97]"
