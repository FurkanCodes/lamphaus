package com.lamphaus.app.ui

import com.lamphaus.core.model.AudioOutputMode
import com.lamphaus.core.model.DecoderPriority
import com.lamphaus.core.model.DolbyVisionHandling
import com.lamphaus.core.model.DownmixMode

/**
 * Audio and video engine choices shown in both TV and mobile Settings, so the
 * two surfaces offer the same options with the same words. Only choices the
 * engine actually implements are listed.
 */
internal object PlaybackEngineOptions {

    val audioOutputModes = listOf(AudioOutputMode.AUTO, AudioOutputMode.FORCE_PASSTHROUGH, AudioOutputMode.FORCE_DECODE)

    fun audioOutputLabel(mode: AudioOutputMode): String = when (mode) {
        AudioOutputMode.AUTO -> "Auto"
        AudioOutputMode.FORCE_PASSTHROUGH -> "Passthrough"
        AudioOutputMode.FORCE_DECODE -> "Decode on device"
    }

    const val AUDIO_OUTPUT_DESCRIPTION =
        "Send Dolby and DTS to your receiver when it supports them, or decode them here"

    val downmixModes = listOf(DownmixMode.AUTO, DownmixMode.STEREO, DownmixMode.NEVER)

    fun downmixLabel(mode: DownmixMode): String = when (mode) {
        DownmixMode.AUTO -> "Auto"
        DownmixMode.STEREO -> "Stereo"
        DownmixMode.NEVER -> "Never"
    }

    const val DOWNMIX_DESCRIPTION = "Fold surround sound to two speakers when audio is decoded on this device"

    // Auto already rewrites profile 7 as 8.1 where that is what makes Dolby
    // Vision play; the stored conversion value plays the same, so it is not
    // offered separately.
    val dolbyVisionModes = listOf(
        DolbyVisionHandling.AUTO,
        DolbyVisionHandling.NATIVE_ONLY,
        DolbyVisionHandling.HDR10_BASE_LAYER,
    )

    fun dolbyVisionLabel(mode: DolbyVisionHandling): String = when (mode) {
        DolbyVisionHandling.AUTO, DolbyVisionHandling.CONVERT_PROFILE7_TO_81 -> "Auto"
        DolbyVisionHandling.NATIVE_ONLY -> "Dolby Vision only"
        DolbyVisionHandling.HDR10_BASE_LAYER, DolbyVisionHandling.DISABLED -> "HDR10"
    }

    const val DOLBY_VISION_DESCRIPTION =
        "Auto plays profile 7 remuxes as Dolby Vision when this device decodes profile 8, and HDR10 when it can't"

    val decoderPriorities = listOf(DecoderPriority.AUTO, DecoderPriority.SOFTWARE_FIRST)

    fun decoderPriorityLabel(priority: DecoderPriority): String = when (priority) {
        DecoderPriority.AUTO, DecoderPriority.HARDWARE_FIRST -> "Device first"
        DecoderPriority.SOFTWARE_FIRST -> "FFmpeg first"
    }

    const val DECODER_PRIORITY_DESCRIPTION =
        "Which audio decoder to try first; the other remains the fallback"

    const val APPLIES_NEXT_PLAYBACK = "Changes apply from the next playback"

    /** The option after [current] in [options], wrapping; values not listed start over. */
    fun <T> next(options: List<T>, current: T): T =
        options[(options.indexOf(current) + 1).mod(options.size)]
}
