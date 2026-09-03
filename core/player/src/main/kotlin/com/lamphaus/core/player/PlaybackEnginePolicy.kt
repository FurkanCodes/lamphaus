package com.lamphaus.core.player

import com.lamphaus.core.model.PlaybackCapabilities
import com.lamphaus.core.model.PlaybackEngineKind

/**
 * Player V2 engine selection and fallback policy (plan §1). Pure and total:
 * the service and the session UI only ever ask, never decide.
 *
 * Media3 (ExoPlayer) is the primary engine. MPV is the automatic fallback for
 * unsupported formats and fatal decoder failures — never for network,
 * authorization, or provider errors, and the handoff never bounces back
 * mid-session.
 */

/** Why an engine could not keep playing the current item. */
enum class EngineFailureKind {
    /** Container or codec the active engine cannot handle at all. */
    UNSUPPORTED_FORMAT,

    /** A decoder refused to initialize (e.g. no HW codec, missing extension). */
    DECODER_INIT_FAILED,

    /** A decoder initialized but failed fatally during playback. */
    DECODER_FAILED,

    NETWORK,
    AUTHORIZATION,
    PROVIDER,
    UNKNOWN,
}

/** What the current item needs, as far as it is known before playback starts. */
data class MediaFormatProfile(
    val container: String? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    /** True when Media3 itself (bundled + software extensions) can decode it. */
    val media3CanPlay: Boolean = true,
    /** Codec categories (e.g. "dolby-vision", "truehd") Media3 handles only partially. */
    val requiresNativeFallback: Boolean = false,
)

object PlaybackEnginePolicy {

    /**
     * Initial engine for a new session. [AUTO] prefers Media3 unless the
     * format profile is known to need the native stack; an explicit override
     * wins when the build actually ships that engine.
     */
    fun resolveInitialEngine(
        requested: PlaybackEngineKind,
        format: MediaFormatProfile?,
        mpvAvailable: Boolean,
    ): PlaybackEngineKind = when (requested) {
        PlaybackEngineKind.MEDIA3 -> PlaybackEngineKind.MEDIA3
        PlaybackEngineKind.MPV -> if (mpvAvailable) PlaybackEngineKind.MPV else PlaybackEngineKind.MEDIA3
        PlaybackEngineKind.AUTO -> when {
            format != null && (format.requiresNativeFallback || !format.media3CanPlay) && mpvAvailable ->
                PlaybackEngineKind.MPV
            else -> PlaybackEngineKind.MEDIA3
        }
    }

    /**
     * True when the failure justifies handing the session to MPV. Network,
     * authorization, and provider errors are the source's problem: switching
     * engines cannot fix them and would only restart playback.
     */
    fun shouldFallbackToMpv(failureKind: EngineFailureKind, activeEngine: PlaybackEngineKind): Boolean =
        activeEngine == PlaybackEngineKind.MEDIA3 &&
            failureKind in setOf(
                EngineFailureKind.UNSUPPORTED_FORMAT,
                EngineFailureKind.DECODER_INIT_FAILED,
                EngineFailureKind.DECODER_FAILED,
            )

    /**
     * Never bounce back to Media3 mid-session: one handoff per item keeps
     * position and track state stable (plan §1). A broken MPV session fails
     * visibly instead of alternating engines.
     */
    fun shouldFallbackToMedia3(failureKind: EngineFailureKind, activeEngine: PlaybackEngineKind): Boolean = false

    fun fallbackReason(failureKind: EngineFailureKind): String? = when (failureKind) {
        EngineFailureKind.UNSUPPORTED_FORMAT -> "format unsupported by Media3"
        EngineFailureKind.DECODER_INIT_FAILED -> "decoder initialization failed"
        EngineFailureKind.DECODER_FAILED -> "decoder failure during playback"
        else -> null
    }

    /**
     * Deterministic capability note for diagnostics: reports what the device
     * cannot do instead of pretending to decode it (plan §1).
     */
    fun describeLimitations(capabilities: PlaybackCapabilities): List<String> = buildList {
        if (!capabilities.supportsDolbyVision) add("dolby-vision: HDR10 base layer fallback")
        if (!capabilities.supportsTrueHdPassthrough) add("truehd: software decode")
        if (!capabilities.supportsDtsPassthrough) add("dts: software decode")
    }
}

/**
 * Everything a handoff must carry to the next engine (plan §1): position,
 * play state, speed, selected tracks, and timing offsets. PiP state and the
 * media session stay with the service — engines change underneath it.
 */
data class EngineHandoffState(
    val positionMillis: Long = 0,
    val playWhenReady: Boolean = true,
    val speed: Float = 1f,
    val audioTrackId: String? = null,
    val subtitleTrackId: String? = null,
    val subtitleDelayMillis: Long = 0,
    val audioDelayMillis: Long = 0,
)
