package com.lamphaus.core.model

/**
 * Builds one playback's [PlaybackReport] from engine callbacks (SHR-PROD-18).
 * Times are elapsed-realtime milliseconds passed in by the caller, so the
 * whole state machine runs on the JVM in tests. Not thread-safe: the player
 * adapter serializes every call.
 */
class PlaybackReportBuilder(
    private val id: String,
    private val startedAtEpochMillis: Long,
    private val startedAtElapsed: Long,
    private val pid: Int,
    private val app: AppFacts,
    private val device: DeviceFacts,
    private val playback: PlaybackFacts,
) {
    private val events = ArrayList<ReportEvent>()
    private val eventCounts = HashMap<ReportEventType, Int>()
    private var omittedEvents = 0
    private var totals = PlaybackTotals()
    private var state = SessionState(engine = playback.startEngine.ifEmpty { null })
    private var media: DecodedMedia? = null
    private var everReady = false
    private var rebufferStartedAt: Long? = null
    private var playingSince: Long? = null
    private var seekBufferingExpected = false
    private var outcome: ReportOutcome? = null
    private var closeReason: String? = null
    private var crash: CrashFacts? = null

    val reportId: String get() = id
    val isOpen: Boolean get() = outcome == null

    init {
        add(startedAtElapsed, ReportEventType.BEGIN, detail = playback.startEngine.ifEmpty { null })
    }

    fun onPhase(phase: EnginePhase, now: Long, position: Long, bufferedAheadMillis: Long, bandwidthBps: Long?) {
        if (phase == state.phase) return
        val previous = state.phase
        state = state.copy(phase = phase, positionMillis = position)
        when (phase) {
            EnginePhase.READY -> {
                endRebuffer(now, position)
                seekBufferingExpected = false
                state = state.copy(errorShowing = false)
                if (!everReady) {
                    everReady = true
                    add(now, ReportEventType.READY, position, value = now - startedAtElapsed)
                }
            }
            EnginePhase.BUFFERING -> {
                val seek = seekBufferingExpected
                seekBufferingExpected = false
                if (everReady && rebufferStartedAt == null && !seek) {
                    rebufferStartedAt = now
                    totals = totals.copy(rebuffers = totals.rebuffers + 1)
                }
                if (everReady) {
                    add(
                        now,
                        ReportEventType.BUFFERING,
                        position,
                        value = bufferedAheadMillis,
                        detail = listOfNotNull(
                            "seek".takeIf { seek },
                            bandwidthBps?.takeIf { it > 0 }?.let { "bw=${formatBitrate(it)}" },
                        ).joinToString(" ").ifEmpty { null },
                    )
                }
            }
            EnginePhase.ENDED -> {
                endRebuffer(now, position)
                add(now, ReportEventType.ENDED, position)
            }
            EnginePhase.IDLE -> if (previous == EnginePhase.BUFFERING) endRebuffer(now, position)
        }
    }

    fun onPlaying(playing: Boolean, now: Long) {
        if (playing == state.playing) return
        state = state.copy(playing = playing)
        if (playing) {
            playingSince = now
        } else {
            playingSince?.let { since -> totals = totals.copy(playedMillis = totals.playedMillis + (now - since)) }
            playingSince = null
        }
    }

    /** The viewer's own play and pause are counted; anything else that paused the stream is an event. */
    fun onPlayWhenReady(playWhenReady: Boolean, byViewer: Boolean, reason: String, now: Long, position: Long) {
        if (byViewer) return
        add(now, if (playWhenReady) ReportEventType.PLAY else ReportEventType.PAUSE, position, detail = reason)
    }

    fun onFirstFrame(now: Long, position: Long) {
        if (state.firstFrame) return
        state = state.copy(firstFrame = true)
        if (totals.startupMillis == null) totals = totals.copy(startupMillis = now - startedAtElapsed)
        add(now, ReportEventType.FIRST_FRAME, position, value = now - startedAtElapsed)
    }

    fun onError(now: Long, position: Long, code: Int, detail: String) {
        state = state.copy(errorShowing = true, positionMillis = position)
        totals = totals.copy(errors = totals.errors + 1)
        add(now, ReportEventType.ERROR, position, value = code.toLong(), detail = detail)
    }

    fun onLoadError(now: Long, detail: String) {
        totals = totals.copy(loadErrors = totals.loadErrors + 1)
        add(now, ReportEventType.LOAD_ERROR, detail = detail)
    }

    /** A notification of [count] frames dropped over [elapsedMillis]; a dense one is a stutter. */
    fun onDroppedFrames(now: Long, count: Int, elapsedMillis: Long) {
        totals = totals.copy(droppedFrames = totals.droppedFrames + count)
        if (count >= STUTTER_MIN_FRAMES && elapsedMillis <= STUTTER_WINDOW_MILLIS) {
            add(now, ReportEventType.DROPPED_FRAMES, state.positionMillis, value = count.toLong(), detail = "${elapsedMillis}ms")
        }
    }

    /** Engines that keep only a running count (libmpv) report it here. */
    fun setDroppedFrames(total: Long) {
        if (total > totals.droppedFrames) totals = totals.copy(droppedFrames = total)
    }

    fun onAudioUnderrun(now: Long, detail: String?) {
        totals = totals.copy(audioUnderruns = totals.audioUnderruns + 1)
        add(now, ReportEventType.AUDIO_UNDERRUN, state.positionMillis, detail = detail)
    }

    fun onSeek(now: Long, from: Long, to: Long) {
        totals = totals.copy(seeks = totals.seeks + 1)
        seekBufferingExpected = true
        state = state.copy(positionMillis = to)
        add(now, ReportEventType.SEEK, from, value = to)
    }

    fun onMedia(next: DecodedMedia, now: Long) {
        val previous = media
        media = DecodedMedia(
            container = next.container ?: previous?.container,
            video = next.video ?: previous?.video,
            audio = next.audio ?: previous?.audio,
        )
        if (next.video != null && next.video != previous?.video) {
            add(now, ReportEventType.FORMAT, state.positionMillis, detail = next.video.describe())
        }
    }

    fun onEngine(engine: String, now: Long) {
        if (engine == state.engine) return
        state = state.copy(engine = engine, firstFrame = false)
        add(now, ReportEventType.ENGINE, state.positionMillis, detail = engine)
    }

    fun onBackground(background: Boolean, now: Long) {
        if (background == state.background) return
        state = state.copy(background = background)
        add(now, if (background) ReportEventType.BACKGROUND else ReportEventType.FOREGROUND, state.positionMillis)
    }

    /** Recovery, fallback, network, memory, and decoder notes from outside the engine callbacks. */
    fun note(type: ReportEventType, now: Long, position: Long? = null, value: Long? = null, detail: String? = null) {
        totals = when (type) {
            ReportEventType.RETRY -> totals.copy(retries = totals.retries + 1)
            ReportEventType.ENGINE_FALLBACK -> totals.copy(engineFallbacks = totals.engineFallbacks + 1)
            else -> totals
        }
        add(now, type, position ?: state.positionMillis, value, detail)
    }

    fun onCrash(facts: CrashFacts) {
        crash = facts
    }

    fun snapshot(nowEpochMillis: Long, now: Long): PlaybackReport = PlaybackReport(
        id = id,
        kind = ReportKind.PLAYBACK,
        startedAtEpochMillis = startedAtEpochMillis,
        updatedAtEpochMillis = nowEpochMillis,
        pid = pid,
        app = app,
        device = device,
        playback = playback,
        media = media,
        state = state,
        events = events.toList(),
        omittedEvents = omittedEvents,
        totals = totals.copy(
            playedMillis = totals.playedMillis + (playingSince?.let { now - it } ?: 0L),
            rebufferMillis = totals.rebufferMillis + (rebufferStartedAt?.let { now - it } ?: 0L),
            openMillis = now - startedAtElapsed,
        ),
        outcome = outcome,
        closeReason = closeReason,
        crash = crash,
    )

    /** Ends the report with an outcome read from where the session stood. */
    fun close(now: Long, nowEpochMillis: Long, reason: String?): PlaybackReport {
        if (outcome == null) {
            closeReason = reason?.let { DiagnosticText.clean(it, 80) }
            add(now, ReportEventType.CLOSE, state.positionMillis, detail = closeReason)
            outcome = when {
                state.errorShowing -> ReportOutcome.FAILED
                state.phase == EnginePhase.ENDED -> ReportOutcome.ENDED
                !everReady && !state.firstFrame -> ReportOutcome.CLOSED_WHILE_LOADING
                state.phase == EnginePhase.BUFFERING -> ReportOutcome.CLOSED_WHILE_BUFFERING
                else -> ReportOutcome.CLOSED
            }
            onPlaying(false, now)
            endRebuffer(now, state.positionMillis)
        }
        return snapshot(nowEpochMillis, now)
    }

    private fun endRebuffer(now: Long, position: Long) {
        val since = rebufferStartedAt ?: return
        rebufferStartedAt = null
        val duration = now - since
        totals = totals.copy(
            rebufferMillis = totals.rebufferMillis + duration,
            longestRebufferMillis = maxOf(totals.longestRebufferMillis, duration),
        )
        add(now, ReportEventType.BUFFERED, position, value = duration)
    }

    private fun add(at: Long, type: ReportEventType, position: Long? = null, value: Long? = null, detail: String? = null) {
        val count = eventCounts[type] ?: 0
        if (count >= capFor(type)) {
            omittedEvents++
            return
        }
        eventCounts[type] = count + 1
        events += ReportEvent(at - startedAtElapsed, type, position, value, detail)
        if (events.size > MAX_EVENTS) {
            // Keep how it started and how it ended; drop from the middle.
            events.removeAt(KEPT_FIRST_EVENTS)
            omittedEvents++
        }
    }

    private fun capFor(type: ReportEventType): Int = when (type) {
        ReportEventType.SEEK, ReportEventType.PLAY, ReportEventType.PAUSE -> 40
        ReportEventType.LOAD_ERROR, ReportEventType.DROPPED_FRAMES, ReportEventType.NETWORK -> 30
        ReportEventType.AUDIO_UNDERRUN, ReportEventType.CODEC_ERROR, ReportEventType.LOW_MEMORY -> 20
        else -> MAX_EVENTS
    }

    companion object {
        const val MAX_EVENTS = 240
        private const val KEPT_FIRST_EVENTS = 40

        /** Media3 reports drops every 50 frames: 10+ within 10 s is visible stutter. */
        private const val STUTTER_MIN_FRAMES = 10
        private const val STUTTER_WINDOW_MILLIS = 10_000L

        fun formatBitrate(bitsPerSecond: Long): String = when {
            bitsPerSecond >= 1_000_000 -> "%.1fMbps".format(java.util.Locale.ROOT, bitsPerSecond / 1_000_000.0)
            else -> "${bitsPerSecond / 1_000}kbps"
        }
    }
}

/** "hevc 3840x2160 23.976fps HDR10 18.4Mbps" */
fun DecodedTrack.describe(): String = listOfNotNull(
    (codecs ?: mime)?.substringAfter('/'),
    if (width != null && height != null) "${width}x$height" else null,
    frameRate?.takeIf { it > 0f }?.let { "%.3ffps".format(java.util.Locale.ROOT, it).replace(Regex("\\.?0+fps$"), "fps") },
    hdr,
    channels?.let { "${it}ch" },
    bitrate?.takeIf { it > 0 }?.let { PlaybackReportBuilder.formatBitrate(it.toLong()) },
).joinToString(" ")
