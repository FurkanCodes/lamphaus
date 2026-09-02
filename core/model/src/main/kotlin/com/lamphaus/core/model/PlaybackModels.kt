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
data class PlaybackSettings(
    val skipIntroEnabled: Boolean = true,
    val skipEndingEnabled: Boolean = true,
    val nextEpisodeEnabled: Boolean = true,
)

@Serializable
enum class PlaybackSegmentType {
    INTRO,
    ENDING,
}

@Serializable
data class PlaybackSegment(
    val type: PlaybackSegmentType,
    val startMillis: Long,
    /** Null means the segment runs to the end of the media. */
    val endMillis: Long? = null,
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
    val episode: Episode? = null,
    val nextEpisode: Episode? = null,
    /** Compact episode metadata used to keep consecutive playback inside one player session. */
    val episodeQueue: List<Episode> = emptyList(),
    /** The selected add-on is preferred when resolving the following episode. */
    val sourceProviderId: String? = null,
    val sourceBingeGroup: String? = null,
    /** Catalog item snapshot, persisted with watch progress for Continue Watching. */
    val preview: MediaPreview? = null,
)

fun List<Episode>.nextEpisodeAfter(current: Episode?, nowEpochMillis: Long = System.currentTimeMillis()): Episode? {
    if (current == null) return null
    val ordered = playbackOrder()
    val currentIndex = ordered.indexOfFirst { it.id == current.id }
    if (currentIndex < 0) return null
    return ordered.drop(currentIndex + 1).firstOrNull { candidate ->
        candidate.releasedAtEpochMillis?.let { it <= nowEpochMillis } ?: true
    }
}

fun List<Episode>.playbackQueueFrom(current: Episode?, maximumSize: Int = 50): List<Episode> {
    if (current == null || maximumSize <= 0) return emptyList()
    val ordered = playbackOrder()
    val currentIndex = ordered.indexOfFirst { it.id == current.id }
    if (currentIndex < 0) return emptyList()
    return ordered.drop(currentIndex).take(maximumSize)
}

private fun List<Episode>.playbackOrder(): List<Episode> = distinctBy(Episode::id).sortedWith(
    compareBy<Episode> { it.season ?: Int.MAX_VALUE }
        .thenBy { it.episode ?: Int.MAX_VALUE }
        .thenBy(Episode::id),
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
