package com.lamphaus.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import com.lamphaus.app.R
import com.lamphaus.core.model.DevicePlaybackConfig
import com.lamphaus.core.model.EncodedAudioFormat
import com.lamphaus.core.model.PlaybackCapabilities
import com.lamphaus.core.model.SourceFit
import com.lamphaus.core.model.SourceFitNote
import com.lamphaus.core.model.SourceFitPolicy
import com.lamphaus.core.model.StreamCandidate
import com.lamphaus.core.model.streamTraits

/**
 * How each source will play on this device, for the source lists. Says
 * nothing until the device has been read or when a source names no traits.
 */
@Immutable
class SourceFitAdvisor(
    private val capabilities: PlaybackCapabilities?,
    private val config: DevicePlaybackConfig,
) {
    fun fitFor(source: StreamCandidate): SourceFit? =
        capabilities?.let { SourceFitPolicy.evaluate(source.streamTraits(), it, config) }
}

val LocalSourceFit = staticCompositionLocalOf { SourceFitAdvisor(null, DevicePlaybackConfig()) }

/** One line for a source card: "Plays natively", or each difference joined. */
@Composable
internal fun sourceFitLabel(fit: SourceFit): String {
    if (fit.native) return stringResource(R.string.source_fit_native)
    val audio = audioFormatName(fit.decodedAudio)
    return fit.notes.map { note ->
        when (note) {
            SourceFitNote.SOFTWARE_VIDEO -> stringResource(R.string.source_fit_software_video)
            SourceFitNote.DOLBY_VISION_AS_HDR10 -> stringResource(R.string.source_fit_dolby_vision_as_hdr10)
            SourceFitNote.HDR_AS_SDR -> stringResource(R.string.source_fit_hdr_as_sdr)
            SourceFitNote.AUDIO_DECODED -> stringResource(R.string.source_fit_audio_decoded, audio)
            SourceFitNote.AUDIO_AS_AC3 -> stringResource(R.string.source_fit_audio_as_ac3, audio)
        }
    }.joinToString("  ·  ")
}

@Composable
private fun audioFormatName(format: EncodedAudioFormat): String = when (format) {
    EncodedAudioFormat.TRUEHD -> stringResource(R.string.audio_format_truehd)
    EncodedAudioFormat.DTS_HD -> stringResource(R.string.audio_format_dts_hd)
    EncodedAudioFormat.DTS -> stringResource(R.string.audio_format_dts)
    EncodedAudioFormat.EAC3_JOC -> stringResource(R.string.audio_format_eac3_joc)
    EncodedAudioFormat.EAC3 -> stringResource(R.string.audio_format_eac3)
    EncodedAudioFormat.AC3, EncodedAudioFormat.NONE -> stringResource(R.string.audio_format_ac3)
}
