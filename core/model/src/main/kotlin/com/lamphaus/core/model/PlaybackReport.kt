package com.lamphaus.core.model

import kotlinx.serialization.Serializable

/**
 * A device-local diagnostic record of one playback, or of one app crash
 * (SHR-PROD-18). Reports describe the device, the engine, the decoded formats,
 * and what happened; they never hold URLs, hosts, headers, add-on names,
 * titles, exception messages, or account data (SHR-PROD-06).
 */
@Serializable
data class PlaybackReport(
    /** Random UUID; also the upload's primary key, so a resend is harmless. */
    val id: String,
    val kind: ReportKind = ReportKind.PLAYBACK,
    val startedAtEpochMillis: Long,
    /** Last write; after a crash this is the last moment the report knew about. */
    val updatedAtEpochMillis: Long = startedAtEpochMillis,
    /** The process that wrote it, matched against Android's exit records. */
    val pid: Int = 0,
    val app: AppFacts = AppFacts(),
    val device: DeviceFacts = DeviceFacts(),
    val playback: PlaybackFacts? = null,
    val media: DecodedMedia? = null,
    /** Where the session stood at its last write. */
    val state: SessionState = SessionState(),
    val events: List<ReportEvent> = emptyList(),
    /** Events dropped from the middle once the report reached its cap. */
    val omittedEvents: Int = 0,
    val totals: PlaybackTotals = PlaybackTotals(),
    /** Null while the playback is still open. */
    val outcome: ReportOutcome? = null,
    val closeReason: String? = null,
    val exit: ProcessExitFacts? = null,
    val crash: CrashFacts? = null,
    /** This device's recent playbacks, filled in when the report is sent. */
    val recent: RecentPlaybacks? = null,
    /** Device-local delivery state; never part of what is sent. */
    val delivery: ReportDelivery = ReportDelivery.PENDING,
)

@Serializable
enum class ReportKind { PLAYBACK, CRASH }

@Serializable
enum class ReportOutcome {
    /** The stream played to its end. */
    ENDED,

    /** The viewer left while it played or was paused. */
    CLOSED,

    /** The viewer left before the first frame. */
    CLOSED_WHILE_LOADING,

    /** The viewer left while it rebuffered. */
    CLOSED_WHILE_BUFFERING,

    /** An error was on screen when it closed. */
    FAILED,

    /** The process ended while the player was in the background and paused. */
    CLOSED_IN_BACKGROUND,

    /** The process ended while the playback was open. */
    PROCESS_DIED,
}

@Serializable
enum class ReportDelivery { PENDING, SENT }

@Serializable
data class AppFacts(
    val versionName: String = "",
    val versionCode: Int = 0,
    val buildType: String = "",
)

@Serializable
data class DeviceFacts(
    val manufacturer: String = "",
    val model: String = "",
    val sdk: Int = 0,
    val television: Boolean = false,
    val abi: String = "",
    val ramMb: Long = 0,
    val lowRam: Boolean = false,
)

/** How the playback was asked to run: engine, stream shape, and the device's playback settings. */
@Serializable
data class PlaybackFacts(
    /** The Playback engine setting (AUTO, MEDIA3, MPV). */
    val engineSetting: String = "",
    /** The engine the stream started on. */
    val startEngine: String = "",
    /** progressive, hls, dash, or other: never the address. */
    val streamType: String = "",
    val resumed: Boolean = false,
    /** What the add-on's label claims (2160p, HDR, DV, remux, audio), parsed; never the label itself. */
    val labeledTraits: String = "",
    val settings: Map<String, String> = emptyMap(),
)

@Serializable
data class DecodedMedia(
    val container: String? = null,
    val video: DecodedTrack? = null,
    val audio: DecodedTrack? = null,
)

@Serializable
data class DecodedTrack(
    val mime: String? = null,
    val codecs: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: Float? = null,
    val bitrate: Int? = null,
    /** HDR10, HLG, or DOLBY_VISION; null for SDR. */
    val hdr: String? = null,
    val channels: Int? = null,
    val sampleRate: Int? = null,
)

@Serializable
data class SessionState(
    val phase: EnginePhase = EnginePhase.IDLE,
    val playing: Boolean = false,
    val background: Boolean = false,
    val errorShowing: Boolean = false,
    val firstFrame: Boolean = false,
    val engine: String? = null,
    val positionMillis: Long = 0,
)

@Serializable
enum class EnginePhase { IDLE, BUFFERING, READY, ENDED }

@Serializable
data class ReportEvent(
    /** Milliseconds since the playback began. */
    val at: Long,
    val type: ReportEventType,
    val position: Long? = null,
    val value: Long? = null,
    val detail: String? = null,
)

@Serializable
enum class ReportEventType {
    BEGIN,
    ENGINE,
    READY,
    FIRST_FRAME,
    BUFFERING,
    BUFFERED,
    PLAY,
    PAUSE,
    SUPPRESSED,
    SEEK,
    ERROR,
    LOAD_ERROR,
    CODEC_ERROR,
    RETRY,
    RECOVERY_GAVE_UP,
    STALL_SKIP,
    STALL_GAVE_UP,
    DOLBY_VISION_BASE_LAYER,
    ENGINE_FALLBACK,
    DECODER,
    FORMAT,
    DROPPED_FRAMES,
    AUDIO_UNDERRUN,
    NETWORK,
    LOW_MEMORY,
    BACKGROUND,
    FOREGROUND,
    ENDED,
    CLOSE,
}

@Serializable
data class PlaybackTotals(
    /** From the start to the first frame; null when none was shown. */
    val startupMillis: Long? = null,
    val rebuffers: Int = 0,
    val rebufferMillis: Long = 0,
    val longestRebufferMillis: Long = 0,
    val playedMillis: Long = 0,
    val droppedFrames: Long = 0,
    val audioUnderruns: Int = 0,
    val errors: Int = 0,
    val loadErrors: Int = 0,
    val retries: Int = 0,
    val engineFallbacks: Int = 0,
    val seeks: Int = 0,
    /** From the start to the last write or the close. */
    val openMillis: Long = 0,
)

/** Why Android ended the process, read from its exit records on the next launch. */
@Serializable
data class ProcessExitFacts(
    val atEpochMillis: Long = 0,
    val pid: Int = 0,
    /** CRASH, CRASH_NATIVE, ANR, LOW_MEMORY, SIGNALED, USER_REQUESTED, … */
    val reason: String = "",
    val status: Int = 0,
    val importance: Int = 0,
    val description: String? = null,
    val pssKb: Long = 0,
    val rssKb: Long = 0,
    /** The main thread's stack for an ANR. */
    val mainThread: List<String> = emptyList(),
)

@Serializable
data class CrashFacts(
    val atEpochMillis: Long = 0,
    val mainThread: Boolean = false,
    /** The exception and its causes, outermost first: class names and stack frames, never messages. */
    val exceptions: List<ExceptionFacts> = emptyList(),
)

@Serializable
data class ExceptionFacts(
    val type: String,
    val frames: List<String> = emptyList(),
)

/** A denominator for one bad report: how this device's other recent playbacks went. */
@Serializable
data class RecentPlaybacks(
    val days: Int,
    val playbacks: Int,
    val bad: Int,
)
