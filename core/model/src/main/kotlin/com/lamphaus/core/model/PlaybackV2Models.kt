package com.lamphaus.core.model

import kotlinx.serialization.Serializable

/**
 * Player V2 public model concepts (plan §3). Enum payload shapes are stable
 * wire contracts: they serialize into cloud payloads and Room columns, so
 * renames are breaking. Add members, never rename.
 */

/** Which playback engine runs the session. Resolution of [AUTO] depends on format support. */
@Serializable
enum class PlaybackEngineKind { AUTO, MEDIA3, MPV }

/** Device-local frame-rate matching policy. Resolution switching is TV-only hardware behavior. */
@Serializable
enum class FrameRateMatching { OFF, SEAMLESS_ONLY, ALWAYS }

/** Device-local physical resolution matching. Mobile never changes display resolution. */
@Serializable
enum class ResolutionMatching { OFF, MATCH_SOURCE }

/**
 * Dolby Vision policy. [AUTO] prefers native profile 5/8, converts profile 7
 * to 8.1 where appropriate, falls back to the HDR10 base layer, and only
 * tone-maps through the engine when no HDR path exists.
 */
@Serializable
enum class DolbyVisionHandling { AUTO, NATIVE_ONLY, CONVERT_PROFILE7_TO_81, HDR10_BASE_LAYER, DISABLED }

/** Audio output policy for encoded bitstreams. */
@Serializable
enum class AudioOutputMode { AUTO, FORCE_PASSTHROUGH, FORCE_DECODE }

/** Decoder selection order for a format both hardware and software can handle. */
@Serializable
enum class DecoderPriority { AUTO, HARDWARE_FIRST, SOFTWARE_FIRST }

/** Multichannel downmix behavior when the output route cannot carry the mix. */
@Serializable
enum class DownmixMode { AUTO, NEVER, STEREO }

/** Profile subtitle default applied when no per-title choice exists. */
@Serializable
enum class SubtitleDefaultMode { OFF, FORCED_ONLY, PREFERRED_LANGUAGE }

/** How subtitle edges are rendered when the profile style overrides embedded styling. */
@Serializable
enum class SubtitleEdgeStyle { NONE, DROP_SHADOW, RAISED, DEPRESSED, OUTLINE }

/** Typeface family used when the profile overrides embedded subtitle styling. */
@Serializable
enum class SubtitleFontFamily { SYSTEM, SANS_SERIF, SERIF, MONOSPACE }

/**
 * The profile-owned playback defaults (plan §3 defaults). Serialized into the
 * `profile_playback_preferences` cloud payload; absent fields fall back to
 * these shipped defaults.
 */
@Serializable
data class ProfilePlaybackPreferences(
    /** Primary audio language; empty means Original → stream default → device language. */
    val audioLanguageTag: String = "",
    /** Secondary audio language; empty means none. */
    val secondaryAudioLanguageTag: String = "",
    val subtitleDefaultMode: SubtitleDefaultMode = SubtitleDefaultMode.FORCED_ONLY,
    /** Preferred subtitle language; empty means the device language. */
    val preferredSubtitleLanguageTag: String = "",
    /** Secondary subtitle language; empty means none. */
    val secondarySubtitleLanguageTag: String = "",
    /** Pass HDR/DV color untouched to the display (plan §2). */
    val originalColors: Boolean = true,
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),
    val updatedAtEpochMillis: Long = 0,
)

/** Live-editable subtitle look applied when embedded styling is overridden. */
@Serializable
data class SubtitleStyle(
    /** Caption font size as a percent of the platform default; clamped 50..300. */
    val sizePercent: Int = 100,
    /** 0 = top of the video frame, 1 = bottom. */
    val verticalPositionFraction: Float = 0.92f,
    val fontFamily: SubtitleFontFamily = SubtitleFontFamily.SYSTEM,
    val bold: Boolean = false,
    /** sRGB color with alpha in the high byte, e.g. 0xFFFFFFFF. */
    val textColor: Long = 0xFFFFFFFFL,
    /** 0..1 multiplier stacked on the color's own alpha. */
    val textOpacity: Float = 1f,
    /** sRGB color with alpha in the high byte. */
    val backgroundColor: Long = 0x00000000L,
    /** 0..1 multiplier stacked on the background color's own alpha. */
    val backgroundOpacity: Float = 0f,
    val outlineEnabled: Boolean = true,
    /** sRGB color with alpha in the high byte. */
    val outlineColor: Long = 0xFF000000L,
    /** Outline width in dp; clamped 0..8. */
    val outlineWidthDp: Float = 1.5f,
    val edgeStyle: SubtitleEdgeStyle = SubtitleEdgeStyle.DROP_SHADOW,
    /** Preserve embedded ASS/SSA authoring through libass by default (plan §4). */
    val preserveEmbeddedStyles: Boolean = true,
)

/**
 * Semantic per-title choice synced with the profile: "this movie plays with
 * Japanese audio and English subtitles". Never carries exact track ids.
 */
@Serializable
data class MediaPlaybackSelection(
    val audioLanguageTag: String? = null,
    val subtitleLanguageTag: String? = null,
    val subtitlesForcedOnly: Boolean? = null,
    val updatedAtEpochMillis: Long = 0,
)

/**
 * Exact track selection remembered for one source fingerprint on one device.
 * Never synced (plan §5): track ids and fingerprints are provider-shaped.
 */
data class SourcePlaybackSelection(
    val profileId: String,
    val mediaKey: String,
    val sourceFingerprint: String,
    val audioTrackId: String? = null,
    val subtitleTrackId: String? = null,
    val subtitleDelayMillis: Long = 0,
    val audioDelayMillis: Long = 0,
    val updatedAtEpochMillis: Long = 0,
)

/** Per-output-route audio timing, remembered locally (plan §2). */
data class AudioRouteSettings(
    val routeFingerprint: String,
    val audioDelayMillis: Long = 0,
    val updatedAtEpochMillis: Long = 0,
)

/** One display mode the current screen advertises. */
data class DisplayModeCandidate(
    val width: Int,
    val height: Int,
    val refreshRateHz: Float,
)

/**
 * What the device + route can actually do. Deterministic fallback is decided
 * from this instead of claiming unsupported hardware can decode a format.
 */
data class PlaybackCapabilities(
    val supportsDolbyVision: Boolean = false,
    val supportsHdr10: Boolean = false,
    val supportsHdr10Plus: Boolean = false,
    val supportsHlg: Boolean = false,
    val supportsAc3Passthrough: Boolean = false,
    val supportsEac3Passthrough: Boolean = false,
    val supportsEac3JocPassthrough: Boolean = false,
    val supportsTrueHdPassthrough: Boolean = false,
    val supportsDtsPassthrough: Boolean = false,
    val supportsSeamlessFrameRateSwitch: Boolean = false,
    val displayModes: List<DisplayModeCandidate> = emptyList(),
    val maxPcmChannelCount: Int = 2,
    /** Tallest frame each hardware video decoder handles; absent codecs have none. */
    val hardwareVideoMaxHeight: Map<VideoCodecFamily, Int> = emptyMap(),
    /** Dolby Vision profiles (5, 7, 8, …) a hardware decoder on this device accepts. */
    val dolbyVisionDecoderProfiles: Set<Int> = emptySet(),
)

enum class VideoCodecFamily { AVC, HEVC, AV1, VP9 }

/** Diagnostic-safe snapshot of the active playback session (plan §6). */
data class PlaybackSessionState(
    val requestedEngine: PlaybackEngineKind = PlaybackEngineKind.AUTO,
    val activeEngine: PlaybackEngineKind = PlaybackEngineKind.MEDIA3,
    /** Why [activeEngine] differs from [requestedEngine]; null when it does not. */
    val fallbackReason: String? = null,
    val videoCodecCategory: String? = null,
    val audioCodecCategory: String? = null,
    /** e.g. "passthrough E-AC-3 JOC" or "decode FLAC → PCM 5.1". */
    val audioOutputPath: String? = null,
    val droppedFrames: Int = 0,
)

/**
 * Device-local playback knobs (plan §5): engine override, HDR/DV handling,
 * frame-rate and resolution matching, and audio output policy are
 * intentionally not synced — phones and TVs intentionally differ.
 */
data class DevicePlaybackConfig(
    val engineKind: PlaybackEngineKind = PlaybackEngineKind.AUTO,
    val dolbyVisionHandling: DolbyVisionHandling = DolbyVisionHandling.AUTO,
    val frameRateMatching: FrameRateMatching = FrameRateMatching.SEAMLESS_ONLY,
    val resolutionMatching: ResolutionMatching = ResolutionMatching.OFF,
    val audioOutputMode: AudioOutputMode = AudioOutputMode.AUTO,
    val decoderPriority: DecoderPriority = DecoderPriority.AUTO,
    val downmixMode: DownmixMode = DownmixMode.AUTO,
    /** Night listening (SHR-PROD-15): compress loud and quiet moments, lift dialogue; decodes on the device. */
    val nightListening: Boolean = false,
    /** Native memory buffer (PLY-NET-01): buffered media and download chunks live outside the Java heap. */
    val nativeMemoryBuffer: Boolean = false,
    /** Parallel connections (PLY-NET-01): progressive streams download in ranged chunks over several connections. */
    val parallelConnections: Boolean = false,
    val parallelConnectionCount: Int = StreamingChoices.DEFAULT_CONNECTIONS,
    val parallelChunkSizeKb: Int = StreamingChoices.DEFAULT_CHUNK_SIZE_KB,
    /** Convert to DV8.1 keeps profile 7 mappings as authored (PLY-DV-01). */
    val dolbyVisionPreserveMapping: Boolean = false,
    /** Profile 5 is announced as 8.1 for decoders without native profile 5 (PLY-DV-01). */
    val dolbyVisionProfile5To81: Boolean = false,
    /** HDR10+ dynamic metadata is removed from HEVC streams (PLY-DV-01). */
    val stripHdr10Plus: Boolean = false,
    /** Tunneled playback: hardware audio/video sync (PLY-AUD-01). */
    val tunneledPlayback: Boolean = false,
    /** Skip silent stretches of audio (PLY-AUD-01). */
    val skipSilence: Boolean = false,
    /** Speaker layout the FFmpeg downmix targets, in channels (PLY-AUD-01). */
    val downmixChannels: Int = AudioChoices.DEFAULT_DOWNMIX_CHANNELS,
    /** Keep the original loudness when downmixing instead of normalising (PLY-AUD-01). */
    val downmixKeepVolume: Boolean = true,
    /** Re-encode surround audio to Dolby Digital 5.1 for optical/SPDIF receivers (PLY-AUD-01). */
    val forceAc3Transcode: Boolean = false,
    /** Switch to the other engine when a stream fails to start (PLY-ENG-01). */
    val autoSwitchEngineOnStartupError: Boolean = false,
)

/** Audio choices Settings offers (PLY-AUD-01). */
object AudioChoices {
    const val DEFAULT_DOWNMIX_CHANNELS = 2
    val DOWNMIX_CHANNEL_COUNTS = listOf(2, 4, 6)

    fun downmixChannels(value: Int?): Int = value?.takeIf { it in DOWNMIX_CHANNEL_COUNTS } ?: DEFAULT_DOWNMIX_CHANNELS
}

/** The streaming choices Settings offers (PLY-NET-01), with Nuvio's ranges and defaults. */
object StreamingChoices {
    const val DEFAULT_CONNECTIONS = 2
    val CONNECTION_COUNTS = listOf(2, 3, 4)
    const val DEFAULT_CHUNK_SIZE_KB = 16 * 1024
    val CHUNK_SIZES_KB = listOf(256, 512, 1024, 2048, 4096, 8192, 16384, 24576, 32768, 49152, 65536, 98304, 131072)

    fun connections(value: Int?): Int = value?.coerceIn(CONNECTION_COUNTS.first(), CONNECTION_COUNTS.last()) ?: DEFAULT_CONNECTIONS

    /** The offered size nearest a stored one, so a corrupt value never reaches the player. */
    fun chunkSizeKb(value: Int?): Int = value?.let { stored -> CHUNK_SIZES_KB.minBy { kotlin.math.abs(it - stored) } }
        ?: DEFAULT_CHUNK_SIZE_KB
}
