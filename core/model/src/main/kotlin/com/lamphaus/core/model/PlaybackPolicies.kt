package com.lamphaus.core.model

/**
 * HDR/Dolby Vision policy resolution (plan §2) and audio output policy
 * (plan §2 audio). Pure decisions consumed by the Media3 factory and the
 * MPV engine; both total over their inputs.
 */

/** What the current stream's Dolby Vision layer looks like. */
enum class DolbyVisionProfile {
    /** No Dolby Vision in the stream. */
    NONE,

    /** IPTPQc2 payload; needs a DV-capable native display. */
    PROFILE_5,

    /** Cross-compat with HDR10; natively playable on most DV displays. */
    PROFILE_8,

    /** Blu-ray dual-layer; the enhancement layer needs conversion to 8.1. */
    PROFILE_7,

    /** Unknown profile but DV metadata present. */
    UNKNOWN,
}

/** The rendering action an engine should take for Dolby Vision content. */
enum class DolbyVisionAction {
    /** Feed DV to the display untouched (original colors, plan §2). */
    NATIVE,

    /** Rewrite profile 7 as 8.1: drop the enhancement layer and convert each RPU. */
    CONVERT_PROFILE7_TO_81,

    /** Discard the DV layer and render the HDR10 base layer. */
    HDR10_BASE_LAYER,

    /** No HDR path exists: tone-map to the SDR output (MPV/libplacebo only). */
    TONE_MAP_TO_SDR,

    /** User disabled DV handling entirely; render the base layer as-is. */
    DISABLED,
}

/** What the extractor does with one Dolby Vision video track (Nuvio's handling). */
enum class DolbyVisionTrackAction {
    /** Leave the track as it is. */
    PASS_THROUGH,

    /** Profile 7 → 8.1 signalling with libdovi mode 1: the RPU keeps its mapping as a MEL. */
    CONVERT_TO_MEL,

    /** Profile 7 → 8.1 with libdovi mode 2, falling back to mode 1 per RPU. */
    CONVERT_TO_81,

    /** Profile 7 → 8.1 keeping every mapping as authored (Nuvio's "Preserve DV mapping"). */
    CONVERT_TO_81_PRESERVING_MAPPING,

    /** Profile 5 announced as 8.1, samples untouched (Nuvio's "Convert DV5 to DV8.1"). */
    SIGNAL_PROFILE5_AS_81,

    /** Profile 5 announced as 8.1 with every RPU rewritten (that switch with Convert to DV8.1). */
    CONVERT_PROFILE5_TO_81,

    /** Drop the RPU and enhancement layer: the HDR10 base layer plays on the HEVC decoder. */
    STRIP_TO_BASE_LAYER,
}

object DolbyVisionPolicy {

    /**
     * Nuvio's per-track Dolby Vision decision. Auto plays a profile 7 remux
     * as is where the decoder takes 7, converts it where the decoder takes 8,
     * and strips it to HDR10 otherwise; any profile 7 or 8 track on a display
     * known to lack Dolby Vision plays its HDR10 base layer. Profile 5 has no
     * base layer and keeps its Dolby Vision decoder, unless [profile5To81]
     * signals it as 8.1 (rewriting its RPUs too with Convert to DV8.1). An
     * unknown display ([displayDolbyVision] null) is treated as Dolby Vision
     * capable. [preserveMapping] keeps mappings when Convert to DV8.1 converts.
     */
    fun trackAction(
        handling: DolbyVisionHandling,
        profile: Int?,
        decoderProfiles: Set<Int>,
        displayDolbyVision: Boolean?,
        profile5To81: Boolean = false,
        preserveMapping: Boolean = false,
    ): DolbyVisionTrackAction {
        if (profile == 5) {
            return when {
                !profile5To81 -> DolbyVisionTrackAction.PASS_THROUGH
                handling == DolbyVisionHandling.CONVERT_PROFILE7_TO_81 -> DolbyVisionTrackAction.CONVERT_PROFILE5_TO_81
                handling == DolbyVisionHandling.AUTO -> DolbyVisionTrackAction.SIGNAL_PROFILE5_AS_81
                else -> DolbyVisionTrackAction.PASS_THROUGH
            }
        }
        if (profile != 7 && profile != 8) return DolbyVisionTrackAction.PASS_THROUGH
        return when (handling) {
            DolbyVisionHandling.NATIVE_ONLY -> DolbyVisionTrackAction.PASS_THROUGH
            DolbyVisionHandling.HDR10_BASE_LAYER, DolbyVisionHandling.DISABLED ->
                DolbyVisionTrackAction.STRIP_TO_BASE_LAYER
            DolbyVisionHandling.CONVERT_PROFILE7_TO_81 -> when {
                profile != 7 -> DolbyVisionTrackAction.PASS_THROUGH
                preserveMapping -> DolbyVisionTrackAction.CONVERT_TO_81_PRESERVING_MAPPING
                else -> DolbyVisionTrackAction.CONVERT_TO_81
            }
            DolbyVisionHandling.AUTO -> when {
                displayDolbyVision == false -> DolbyVisionTrackAction.STRIP_TO_BASE_LAYER
                profile == 8 -> DolbyVisionTrackAction.PASS_THROUGH
                7 in decoderProfiles -> DolbyVisionTrackAction.PASS_THROUGH
                8 in decoderProfiles -> DolbyVisionTrackAction.CONVERT_TO_MEL
                else -> DolbyVisionTrackAction.STRIP_TO_BASE_LAYER
            }
        }
    }

    /** True when a profile 7 remux plays as Dolby Vision: natively or converted (SHR-PROD-12). */
    fun playsProfile7AsDolbyVision(
        handling: DolbyVisionHandling,
        decoderProfiles: Set<Int>,
        displayDolbyVision: Boolean?,
    ): Boolean = when (trackAction(handling, 7, decoderProfiles, displayDolbyVision)) {
        DolbyVisionTrackAction.CONVERT_TO_MEL,
        DolbyVisionTrackAction.CONVERT_TO_81,
        DolbyVisionTrackAction.CONVERT_TO_81_PRESERVING_MAPPING,
        -> 8 in decoderProfiles || 7 in decoderProfiles
        DolbyVisionTrackAction.PASS_THROUGH -> 7 in decoderProfiles
        DolbyVisionTrackAction.STRIP_TO_BASE_LAYER,
        DolbyVisionTrackAction.SIGNAL_PROFILE5_AS_81,
        DolbyVisionTrackAction.CONVERT_PROFILE5_TO_81,
        -> false
    }

    /**
     * Resolution order (plan §2): user override → native 5/8 when display and
     * decoder allow → P7 conversion where appropriate → HDR10 base layer →
     * tone-map only when the output is SDR and no HDR path exists.
     */
    fun resolve(
        handling: DolbyVisionHandling,
        profile: DolbyVisionProfile,
        displaySupportsDolbyVision: Boolean,
        outputIsHdr: Boolean,
        libDoviAvailable: Boolean,
    ): DolbyVisionAction {
        if (profile == DolbyVisionProfile.NONE) {
            return if (outputIsHdr) DolbyVisionAction.NATIVE else DolbyVisionAction.DISABLED
        }
        if (handling == DolbyVisionHandling.DISABLED) return DolbyVisionAction.DISABLED
        if (handling == DolbyVisionHandling.HDR10_BASE_LAYER) return DolbyVisionAction.HDR10_BASE_LAYER

        if (handling == DolbyVisionHandling.CONVERT_PROFILE7_TO_81) {
            if (profile == DolbyVisionProfile.PROFILE_7 && libDoviAvailable) {
                return DolbyVisionAction.CONVERT_PROFILE7_TO_81
            }
            return DolbyVisionAction.HDR10_BASE_LAYER
        }

        // AUTO and NATIVE_ONLY share the native ladder; NATIVE_ONLY refuses
        // conversion and tone mapping.
        val nativeOk = displaySupportsDolbyVision &&
            profile in setOf(DolbyVisionProfile.PROFILE_5, DolbyVisionProfile.PROFILE_8)
        if (nativeOk) return DolbyVisionAction.NATIVE
        if (handling == DolbyVisionHandling.NATIVE_ONLY) return DolbyVisionAction.HDR10_BASE_LAYER

        if (profile == DolbyVisionProfile.PROFILE_7 && libDoviAvailable) {
            return DolbyVisionAction.CONVERT_PROFILE7_TO_81
        }
        // PROFILE_7's base layer is HDR10; other profiles fall back only when
        // the display cannot carry DV at all.
        if (profile == DolbyVisionProfile.PROFILE_7) return DolbyVisionAction.HDR10_BASE_LAYER
        return if (outputIsHdr) DolbyVisionAction.NATIVE else DolbyVisionAction.TONE_MAP_TO_SDR
    }
}

/** The encoded audio format the route would need to carry for passthrough. */
enum class EncodedAudioFormat {
    AC3,
    EAC3,
    EAC3_JOC,
    TRUEHD,
    DTS,
    DTS_HD,
    NONE,
}

/** The audio output decision for the current route and format. */
sealed interface AudioOutputDecision {
    /** Bitstream goes to the route untouched. */
    data class Passthrough(val format: EncodedAudioFormat) : AudioOutputDecision

    /** Engine decodes to PCM; [toStereo] downmixes multichannel to 2.0. */
    data class Decode(val toStereo: Boolean) : AudioOutputDecision
}

object AudioRoutePolicy {

    /** True when the route advertised support for the encoding (HDMI/ receiver EDID). */
    internal fun routeSupports(capabilities: PlaybackCapabilities, format: EncodedAudioFormat): Boolean =
        when (format) {
            EncodedAudioFormat.AC3 -> capabilities.supportsAc3Passthrough
            EncodedAudioFormat.EAC3 -> capabilities.supportsEac3Passthrough
            EncodedAudioFormat.EAC3_JOC -> capabilities.supportsEac3JocPassthrough
            EncodedAudioFormat.TRUEHD -> capabilities.supportsTrueHdPassthrough
            EncodedAudioFormat.DTS, EncodedAudioFormat.DTS_HD -> capabilities.supportsDtsPassthrough
            EncodedAudioFormat.NONE -> false
        }

    /**
     * Output decision (plan §2 audio): AUTO is capability-aware passthrough
     * for Atmos/TrueHD/E-AC-3/DTS and safe decode otherwise; FORCE_DECODE
     * never bitstreams and honors the downmix setting; FORCE_PASSTHROUGH
     * still respects actual route capability — the device cannot carry a
     * format it cannot carry (plan §1, deterministic over aspirational).
     */
    fun resolve(
        mode: AudioOutputMode,
        downmixMode: DownmixMode,
        capabilities: PlaybackCapabilities,
        format: EncodedAudioFormat,
        streamChannelCount: Int = 6,
    ): AudioOutputDecision {
        val passthroughFormat = format.takeIf { it != EncodedAudioFormat.NONE && routeSupports(capabilities, it) }
        fun decodeDecision() = AudioOutputDecision.Decode(
            toStereo = when (downmixMode) {
                DownmixMode.STEREO -> true
                DownmixMode.NEVER -> false
                DownmixMode.AUTO -> capabilities.maxPcmChannelCount < streamChannelCount
            },
        )
        return when (mode) {
            AudioOutputMode.FORCE_DECODE -> AudioOutputDecision.Decode(toStereo = downmixMode == DownmixMode.STEREO)
            AudioOutputMode.AUTO, AudioOutputMode.FORCE_PASSTHROUGH ->
                passthroughFormat?.let { AudioOutputDecision.Passthrough(it) } ?: decodeDecision()
        }
    }
}

/** Subtitle style clamps (plan §4 live editor ranges). */
object SubtitleStylePolicy {
    fun clampSizePercent(raw: Int): Int = raw.coerceIn(50, 300)
    fun clampPositionFraction(raw: Float): Float = raw.coerceIn(0f, 1f)
    fun clampOpacity(raw: Float): Float = raw.coerceIn(0f, 1f)
    fun clampOutlineWidthDp(raw: Float): Float = raw.coerceIn(0f, 8f)
}
