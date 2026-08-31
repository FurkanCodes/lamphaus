package com.lamphaus.core.model

import kotlinx.serialization.Serializable

@Serializable
data class PlaybackSource(
    val uri: String,
    val mimeType: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<SubtitleTrack> = emptyList(),
)

@Serializable
data class PlaybackRequest(
    val mediaKey: String,
    val videoId: String,
    val title: String,
    val subtitle: String? = null,
    val artworkUrl: String? = null,
    val source: PlaybackSource,
    val startPositionMillis: Long = 0,
    val nextEpisode: Episode? = null,
    /** Catalog item snapshot, persisted with watch progress for Continue Watching. */
    val preview: MediaPreview? = null,
)

@Serializable
data class TrackPreference(
    val preferredAudioLanguages: List<String> = emptyList(),
    val preferredSubtitleLanguages: List<String> = emptyList(),
    val subtitlesEnabled: Boolean = false,
)

sealed interface PlaybackState {
    data object Idle : PlaybackState
    data object Buffering : PlaybackState
    data class Ready(
        val playing: Boolean,
        val positionMillis: Long,
        val durationMillis: Long,
    ) : PlaybackState
    data class Failed(val safeMessage: String, val canRetry: Boolean) : PlaybackState
}

