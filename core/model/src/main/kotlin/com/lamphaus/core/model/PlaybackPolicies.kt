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

    /** Convert profile 7's enhancement layer to 8.1 via libdovi. */
    CONVERT_PROFILE7_TO_81,

    /** Discard the DV layer and render the HDR10 base layer. */
    HDR10_BASE_LAYER,

    /** No HDR path exists: tone-map to the SDR output (MPV/libplacebo only). */
    TONE_MAP_TO_SDR,

    /** User disabled DV handling entirely; render the base layer as-is. */
    DISABLED,
}

object DolbyVisionPolicy {

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
    private fun routeSupports(capabilities: PlaybackCapabilities, format: EncodedAudioFormat): Boolean =
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
