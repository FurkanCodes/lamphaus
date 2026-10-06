package com.lamphaus.app.ui

import com.lamphaus.core.model.AudioOutputMode
import com.lamphaus.core.model.DecoderPriority
import com.lamphaus.core.model.DolbyVisionHandling
import com.lamphaus.core.model.DownmixMode
import com.lamphaus.core.model.StreamingChoices

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

    // Nuvio's Dolby Vision handling choices (Phase 5), in its order.
    val dolbyVisionModes = listOf(
        DolbyVisionHandling.AUTO,
        DolbyVisionHandling.CONVERT_PROFILE7_TO_81,
        DolbyVisionHandling.HDR10_BASE_LAYER,
        DolbyVisionHandling.DISABLED,
        DolbyVisionHandling.NATIVE_ONLY,
    )

    fun dolbyVisionLabel(mode: DolbyVisionHandling): String = when (mode) {
        DolbyVisionHandling.AUTO -> "Auto"
        DolbyVisionHandling.CONVERT_PROFILE7_TO_81 -> "Convert to DV8.1"
        DolbyVisionHandling.HDR10_BASE_LAYER -> "HDR10 base layer"
        DolbyVisionHandling.DISABLED -> "Strip Dolby Vision"
        DolbyVisionHandling.NATIVE_ONLY -> "Off (native)"
    }

    const val DOLBY_VISION_DESCRIPTION =
        "Auto picks for this display and decoder: profile 7 remuxes play as Dolby Vision where they can, HDR10 where they can't"

    val decoderPriorities = listOf(DecoderPriority.AUTO, DecoderPriority.SOFTWARE_FIRST)

    fun decoderPriorityLabel(priority: DecoderPriority): String = when (priority) {
        DecoderPriority.AUTO, DecoderPriority.HARDWARE_FIRST -> "Device first"
        DecoderPriority.SOFTWARE_FIRST -> "FFmpeg first"
    }

    const val DECODER_PRIORITY_DESCRIPTION =
        "Which audio decoder to try first; the other remains the fallback"

    const val APPLIES_NEXT_PLAYBACK = "Changes apply from the next playback"

    /** Parallel connection counts and chunk sizes (PLY-NET-01), as Nuvio offers them. */
    val connectionCounts = StreamingChoices.CONNECTION_COUNTS
    val chunkSizesKb = StreamingChoices.CHUNK_SIZES_KB

    /** The option after [current] in [options], wrapping; values not listed start over. */
    fun <T> next(options: List<T>, current: T): T =
        options[(options.indexOf(current) + 1).mod(options.size)]
}
