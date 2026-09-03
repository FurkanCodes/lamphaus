package com.lamphaus.core.player.mpv

import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
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
 * Video output: `vo=mediacodec_embed` with the attached Surface renders
 * hardware-decoded frames directly, preserving HDR metadata (plan §2
 * "original colors"). Subtitles render in-engine through libass with
 * embedded ASS/SSA styling preserved (plan §4).
 */
@UnstableApi
class MpvPlayer(
    looper: Looper,
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
    @Volatile private var endFileErrorCode = 0
    @Volatile private var videoWidth = 0
    @Volatile private var videoHeight = 0
    @Volatile private var tracksSnapshot: Tracks = Tracks.EMPTY
    @Volatile private var selectedAudioId: String? = null
    @Volatile private var selectedSubtitleId: String? = null
    @Volatile private var subtitleDelayMillis = 0L
    @Volatile private var audioDelayMillis = 0L

    private val handler = Handler(looper)
    private val observedProperties = CopyOnWriteArrayList(
        listOf(
            "time-pos", "duration", "pause", "speed", "eof-reached", "seeking",
            "paused-for-cache", "track-list", "video-params/w", "video-params/h",
            "aid", "sid",
        ),
    )
    private var eventThread: Thread? = null

    val isUsable: Boolean get() = handle != 0L && !released

    init {
        handle = MpvLibrary.create()
        check(handle != 0L) { "libmpv.so is not present; check MpvLibrary.availability first" }
        // Render through the Android hardware path straight to the Surface and
        // keep original colors: no tone mapping unless the display is SDR and
        // the user's DV policy asks for it (plan §2).
        MpvLibrary.setOptionString(handle, "vo", "mediacodec_embed,gpu")
        MpvLibrary.setOptionString(handle, "gpu-context", "android")
        MpvLibrary.setOptionString(handle, "hwdec", "auto-safe")
        MpvLibrary.setOptionString(handle, "ao", "audiotrack,opensles")
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
        mediaItem = item
        MpvLibrary.command(handle, args)
        invalidateState()
    }

    fun restore(state: EngineHandoffState) {
        setSubtitleDelayMillis(state.subtitleDelayMillis)
        setAudioDelayMillis(state.audioDelayMillis)
        state.audioTrackId?.let { id -> selectTrack("aid", id) }
        state.subtitleTrackId?.let { id -> selectTrack("sid", id) }
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
        if (property == "aid") selectedAudioId = mpvTrackId else selectedSubtitleId = mpvTrackId
        MpvLibrary.setPropertyString(handle, property, mpvTrackId)
        invalidateState()
    }

    // ── SimpleBasePlayer plumbing ────────────────────────────────────────

    override fun getState(): State {
        val item = mediaItem
        val playbackState = when {
            !prepared -> Player.STATE_IDLE
            endFileErrorCode == END_FILE_EOF -> Player.STATE_ENDED
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
            .setPlaybackParameters(PlaybackParameters(speed))
            .setVolume(volume)
            .setVideoSize(androidx.media3.common.VideoSize(videoWidth, videoHeight))
            .setTrackSelectionParameters(TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT)
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
        if (mediaItem != null) MpvLibrary.command(handle, listOf("revert-seek", "mark-current") as List<String>)
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
        eventThread?.interrupt()
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
        load(item, startPositionMs.coerceAtLeast(0), emptyMap())
        prepared = true
        return Futures.immediateVoidFuture()
    }

    override fun handleAddMediaItems(index: Int, mediaItems: List<MediaItem>): ListenableFuture<*> =
        handleSetMediaItems(mediaItems, 0, C.TIME_UNSET)

    override fun handleSetVideoOutput(videoOutput: Any): ListenableFuture<*> {
        if (videoOutput is Surface) {
            MpvLibrary.attachSurface(handle, videoOutput)
        }
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

    override fun handleSetTrackSelectionParameters(
        trackSelectionParameters: TrackSelectionParameters,
    ): ListenableFuture<*> = Futures.immediateVoidFuture()

    // ── mpv event pump ───────────────────────────────────────────────────

    private fun startEventPump() {
        eventThread = Thread(
            {
                while (!released && handle != 0L) {
                    try {
                        val event = MpvLibrary.waitEvent(handle, 0.1)
                        when (event) {
                            EVENT_FILE_LOADED -> fileLoaded = true
                            EVENT_END_FILE -> {
                                // reason: 0=eof-implicit... mpv: 2 stop, 3 quit, 4 eof, 6 error
                                val reason = endFileReason()
                                endFileErrorCode = reason
                            }
                            EVENT_SHUTDOWN -> return@Thread
                        }
                        refreshTrackedProperties()
                        handler.post { if (!released) invalidateState() }
                    } catch (_: InterruptedException) {
                        return@Thread
                    }
                }
            },
            "lamphaus-mpv-events",
        ).apply { isDaemon = true; start() }
    }

    /** mpv does not hand us the end-file reason through the id-only shim; eof is the common case. */
    private fun endFileReason(): Int =
        MpvLibrary.getPropertyString(handle, "eof-reached")?.toBooleanStrictOrNull()?.let { if (it) END_FILE_EOF else 0 } ?: 0

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
        MpvLibrary.getPropertyString(handle, "aid")?.let { if (it != "no") selectedAudioId = it }
        MpvLibrary.getPropertyString(handle, "sid")?.let { selectedSubtitleId = if (it == "no") null else it }
        refreshTracks()
    }

    private fun refreshTracks() {
        val raw = MpvLibrary.getPropertyString(handle, "track-list") ?: return
        try {
            val array = JSONArray(raw)
            val groups = mutableListOf<androidx.media3.common.Tracks.Group>()
            for (i in 0 until array.length()) {
                val entry = array.optJSONObject(i) ?: continue
                val type = entry.optString("type")
                if (type != "audio" && type != "sub") continue
                val id = entry.optInt("id").toString()
                val selected = entry.optBoolean("selected")
                val language = entry.optString("lang").takeIf(String::isNotEmpty)
                val label = entry.optString("title").takeIf(String::isNotEmpty)
                val format = androidx.media3.common.Format.Builder()
                    .setId("${type}_$id")
                    .setLabel(label)
                    .setLanguage(language)
                    .setSampleMimeType(if (type == "audio") "audio/mp4a-latm" else "text/x-ssa")
                    .setChannelCount(entry.optInt("demux-channel-count", if (type == "audio") 2 else 0))
                    .build()
                val group = androidx.media3.common.TrackGroup(format).copyWithId("$type-$id")
                groups += androidx.media3.common.Tracks.Group(
                    group,
                    /* isAdaptive = */ false,
                    intArrayOf(C.FORMAT_HANDLED),
                    booleanArrayOf(selected),
                )
            }
            if (groups.isNotEmpty()) tracksSnapshot = androidx.media3.common.Tracks(groups)
        } catch (_: Exception) {
            // Track list stays at its last good snapshot; never crashes the session.
        }
    }

    private fun Double.secondsToMillis(): Long = (this * 1000).toLong()

    private companion object {
        const val EVENT_NONE = 0
        const val EVENT_SHUTDOWN = 1
        const val EVENT_FILE_LOADED = 8
        const val EVENT_END_FILE = 7
        const val END_FILE_EOF = 4

    }
}
