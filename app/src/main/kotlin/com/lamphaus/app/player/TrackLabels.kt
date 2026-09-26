package com.lamphaus.app.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import java.util.Locale

/**
 * Track names in the audio and subtitle panels, e.g. "English" over
 * "Dolby TrueHD Atmos · 7.1 · Default". Matroska files often carry only a
 * free-text track name, so forced, SDH, commentary, and Atmos are also read
 * from it (Nuvio-style).
 */
internal fun Format.trackTitle(index: Int): String = trackTitle(language, label, index)

internal fun Format.trackDetails(trackType: Int): String? =
    trackDetails(trackType, sourceMimeType(), channelCount, selectionFlags, roleFlags, label)

/**
 * Subtitles parsed during extraction report Media3's internal cue format;
 * the original subtitle format (SRT, ASS, PGS, …) is carried in `codecs`.
 */
internal fun Format.sourceMimeType(): String? =
    if (sampleMimeType == MimeTypes.APPLICATION_MEDIA3_CUES) codecs ?: sampleMimeType else sampleMimeType

internal fun trackTitle(language: String?, label: String?, index: Int): String {
    val languageName = language
        ?.takeIf { it.isNotBlank() && it != C.LANGUAGE_UNDETERMINED }
        ?.let { Locale.forLanguageTag(it).displayLanguage }
        ?.takeIf(String::isNotBlank)
    val extraLabel = label?.trim()?.takeIf { it.isNotBlank() && !it.equals(languageName, ignoreCase = true) }
    return when {
        languageName != null && extraLabel != null -> "$languageName · $extraLabel"
        languageName != null -> languageName
        extraLabel != null -> extraLabel
        else -> "Track ${index + 1}"
    }
}

internal fun trackDetails(
    trackType: Int,
    sampleMimeType: String?,
    channelCount: Int,
    selectionFlags: Int,
    roleFlags: Int,
    label: String?,
): String? {
    val name = label.orEmpty().lowercase(Locale.ROOT)
    return buildList {
        sampleMimeType?.let { add(friendlyCodecName(it, atmos = "atmos" in name)) }
        if (trackType == C.TRACK_TYPE_AUDIO) channelLayout(channelCount)?.let(::add)
        if (selectionFlags and C.SELECTION_FLAG_FORCED != 0 || "forced" in name) add("Forced")
        if (roleFlags and C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND != 0 || "sdh" in name || "hearing" in name) add("SDH")
        if (roleFlags and C.ROLE_FLAG_CAPTION != 0) add("Captions")
        if (roleFlags and C.ROLE_FLAG_COMMENTARY != 0 || "commentary" in name) add("Commentary")
        if (roleFlags and C.ROLE_FLAG_DESCRIBES_VIDEO != 0) add("Audio description")
        if (selectionFlags and C.SELECTION_FLAG_DEFAULT != 0) add("Default")
    }.distinct().joinToString(" · ").ifBlank { null }
}

internal fun channelLayout(channels: Int): String? = when (channels) {
    Format.NO_VALUE, 0 -> null
    1 -> "Mono"
    2 -> "Stereo"
    3 -> "2.1"
    6 -> "5.1"
    7 -> "6.1"
    8 -> "7.1"
    else -> "$channels ch"
}

internal fun friendlyCodecName(mimeType: String, atmos: Boolean = false): String = when (mimeType.lowercase(Locale.ROOT)) {
    MimeTypes.AUDIO_E_AC3_JOC -> "Dolby Atmos"
    MimeTypes.AUDIO_E_AC3 -> if (atmos) "Dolby Atmos" else "Dolby Digital Plus"
    MimeTypes.AUDIO_AC3 -> "Dolby Digital"
    MimeTypes.AUDIO_TRUEHD -> if (atmos) "Dolby TrueHD Atmos" else "Dolby TrueHD"
    MimeTypes.AUDIO_DTS, "audio/dts" -> "DTS"
    MimeTypes.AUDIO_DTS_HD, "audio/dts-hd" -> "DTS-HD"
    MimeTypes.AUDIO_DTS_EXPRESS -> "DTS Express"
    MimeTypes.AUDIO_DTS_X -> "DTS:X"
    MimeTypes.AUDIO_AAC -> "AAC"
    MimeTypes.AUDIO_OPUS -> "Opus"
    MimeTypes.AUDIO_FLAC -> "FLAC"
    MimeTypes.AUDIO_MPEG -> "MP3"
    MimeTypes.AUDIO_VORBIS -> "Vorbis"
    MimeTypes.TEXT_VTT -> "WebVTT"
    MimeTypes.APPLICATION_SUBRIP -> "SRT"
    MimeTypes.TEXT_SSA, "text/x-ass" -> "ASS"
    MimeTypes.APPLICATION_TTML -> "TTML"
    MimeTypes.APPLICATION_PGS -> "PGS"
    MimeTypes.APPLICATION_VOBSUB -> "VobSub"
    MimeTypes.APPLICATION_DVBSUBS -> "DVB"
    MimeTypes.VIDEO_H265 -> "HEVC"
    MimeTypes.VIDEO_H264 -> "AVC"
    MimeTypes.VIDEO_AV1 -> "AV1"
    MimeTypes.VIDEO_VP9 -> "VP9"
    MimeTypes.VIDEO_DOLBY_VISION -> "Dolby Vision"
    MimeTypes.VIDEO_MPEG2 -> "MPEG-2"
    else -> mimeType.substringAfter('/').uppercase(Locale.ROOT)
}
