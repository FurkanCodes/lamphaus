package com.lamphaus.app.player

import androidx.media3.common.C
import java.util.Locale

/**
 * Text for the Info panel's live stream rows. Pure and locale-stable so the
 * numbers read the same on every device ("23.976 fps", never "23,976").
 */
internal object StreamStatsFormat {

    fun frameRate(fps: Float): String? {
        if (!fps.isFinite() || fps <= 0f) return null
        val text = "%.3f".format(Locale.ROOT, fps).trimEnd('0').trimEnd('.')
        return "$text fps"
    }

    fun hdr(type: PlaybackHdrType?): String? = when (type) {
        PlaybackHdrType.HDR10 -> "HDR10"
        PlaybackHdrType.HLG -> "HLG"
        PlaybackHdrType.DOLBY_VISION -> "Dolby Vision"
        null -> null
    }

    fun video(
        width: Int,
        height: Int,
        fps: Float,
        codec: String?,
        hdr: PlaybackHdrType?,
        bitrate: Int,
    ): String = listOfNotNull(
        "$width×$height".takeIf { width > 0 && height > 0 },
        frameRate(fps),
        codec,
        // The codec already says "Dolby Vision" for native DV.
        hdr(hdr)?.takeUnless { it == codec },
        megabits(bitrate),
    ).joinToString(" · ")

    fun audio(codec: String?, channels: String?, sampleRate: Int, bitrate: Int): String = listOfNotNull(
        codec,
        channels,
        sampleRate.takeIf { it > 0 }?.let { kilohertz(it) },
        bitrate.takeIf { it > 0 }?.let { "${it / 1_000} kbps" },
    ).joinToString(" · ")

    /** What leaves the device: a bitstream for the receiver, or PCM with its channel layout. */
    fun output(passthrough: Boolean?, encoding: Int?, channels: Int): String? = when (passthrough) {
        true -> bitstreamName(encoding)
        false -> listOfNotNull(channelLayout(channels), "PCM").joinToString(" ")
        null -> null
    }

    /** FFmpeg reports names like "ffmpeg7.1-ac3"; platform decoders keep their codec name. */
    fun decoder(name: String): String =
        if (name.startsWith("ffmpeg")) "FFmpeg (${name.substringAfterLast('-')})" else name

    fun megabits(bitsPerSecond: Int): String? =
        bitsPerSecond.takeIf { it > 0 }?.let { "%.1f Mbps".format(Locale.ROOT, it / 1_000_000f) }

    private fun kilohertz(hertz: Int): String =
        "%.1f".format(Locale.ROOT, hertz / 1_000f).removeSuffix(".0") + " kHz"

    private fun bitstreamName(encoding: Int?): String = when (encoding) {
        C.ENCODING_AC3 -> "Dolby Digital"
        C.ENCODING_E_AC3 -> "Dolby Digital Plus"
        C.ENCODING_E_AC3_JOC -> "Dolby Atmos"
        C.ENCODING_DOLBY_TRUEHD -> "Dolby TrueHD"
        C.ENCODING_DTS -> "DTS"
        C.ENCODING_DTS_HD -> "DTS-HD"
        else -> "Bitstream"
    }
}
