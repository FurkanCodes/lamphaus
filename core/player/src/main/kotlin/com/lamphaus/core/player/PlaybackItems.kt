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
            .setMimeType(subtitle.url.subtitleMimeType())
            .setSelectionFlags(0)
            .build()
    }
    return MediaItem.Builder()
        .setMediaId(videoId)
        .setUri(source.uri)
        .setMimeType(source.mimeType)
        .setSubtitleConfigurations(subtitleConfigurations)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
            .setArtworkUri(artworkUrl?.toUri())
                .build(),
        )
        .build()
}

private fun String.subtitleMimeType(): String = when {
    endsWith(".vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
    endsWith(".ttml", ignoreCase = true) || endsWith(".xml", ignoreCase = true) -> MimeTypes.APPLICATION_TTML
    else -> MimeTypes.APPLICATION_SUBRIP
}
