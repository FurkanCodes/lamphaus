package com.lamphaus.core.player

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.core.net.toUri
import com.lamphaus.core.model.PlaybackRequest

fun PlaybackRequest.toMediaItem(): MediaItem {
    val subtitleConfigurations = source.subtitles.map { subtitle ->
        MediaItem.SubtitleConfiguration.Builder(subtitle.url.toUri())
            .setId(subtitle.id)
            .setLanguage(subtitle.language)
            .setLabel(subtitle.language)
            .setMimeType(subtitle.subtitleMimeType())
            .setSelectionFlags(0)
            .build()
    }
    // Opaque proxy URLs (e.g. /stream?id=…) carry no extension and no mime:
    // leave mime unset so DefaultMediaSourceFactory sniffs progressive.
    // HLS/DASH without extension or mime cannot play per Media3 docs and
    // must supply source.mimeType upstream (provider fix, not player guess).
    val builder = MediaItem.Builder()
        .setMediaId(videoId)
        .setUri(source.uri)
        .setSubtitleConfigurations(subtitleConfigurations)
    source.mimeType?.let(builder::setMimeType)
    return builder
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setArtworkUri(artworkUrl?.toUri())
                .build(),
        )
        .build()
}

private fun com.lamphaus.core.model.SubtitleTrack.subtitleMimeType(): String = when {
    format.equals("vtt", ignoreCase = true) || url.contains(".vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
    format.equals("ttml", ignoreCase = true) ||
        format.equals("xml", ignoreCase = true) ||
        url.contains(".ttml", ignoreCase = true) ||
        url.contains(".xml", ignoreCase = true) -> MimeTypes.APPLICATION_TTML
    format.equals("ssa", ignoreCase = true) ||
        format.equals("ass", ignoreCase = true) ||
        url.contains(".ssa", ignoreCase = true) ||
        url.contains(".ass", ignoreCase = true) -> MimeTypes.TEXT_SSA
    else -> MimeTypes.APPLICATION_SUBRIP
}
