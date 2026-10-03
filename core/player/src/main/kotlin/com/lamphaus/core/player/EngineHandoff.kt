package com.lamphaus.core.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks

/**
 * Snapshots a Media3 session into [EngineHandoffState] and classifies fatal
 * errors into [EngineFailureKind] (plan §1). Pure mapping — no engine code.
 */
object EngineHandoff {

    /**
     * Media3 error-code → failure kind. Anything decoder or codec-shaped is a
     * legitimate MPV fallback trigger; IO/auth/provider errors are not.
     */
    fun failureKindFrom(errorCode: Int): EngineFailureKind = when (errorCode) {
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        -> EngineFailureKind.DECODER_INIT_FAILED

        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
        -> EngineFailureKind.DECODER_FAILED

        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        -> EngineFailureKind.UNSUPPORTED_FORMAT

        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_TIMEOUT,
        -> EngineFailureKind.NETWORK

        PlaybackException.ERROR_CODE_AUTHENTICATION_EXPIRED,
        PlaybackException.ERROR_CODE_PREMIUM_ACCOUNT_REQUIRED,
        PlaybackException.ERROR_CODE_PERMISSION_DENIED,
        -> EngineFailureKind.AUTHORIZATION

        else -> EngineFailureKind.UNKNOWN
    }

    /**
     * Captures everything the replacement engine must restore. Track formats
     * carry the selected tracks' ids (kept by add-on subtitles on both
     * engines) and languages; the MPV engine numbers container tracks its own
     * way, so it matches those by language.
     */
    fun snapshot(player: Player): EngineHandoffState {
        val audio = selectedFormat(player, C.TRACK_TYPE_AUDIO)
        val subtitle = selectedFormat(player, C.TRACK_TYPE_TEXT)
        return EngineHandoffState(
            positionMillis = player.currentPosition.coerceAtLeast(0),
            playWhenReady = player.playWhenReady,
            speed = player.playbackParameters.speed,
            audioTrackId = audio?.id,
            subtitleTrackId = subtitle?.id,
            audioLanguage = audio?.language,
            subtitleLanguage = subtitle?.language,
            subtitleForced = subtitle != null && subtitle.selectionFlags and C.SELECTION_FLAG_FORCED != 0,
            subtitlesOff = subtitle == null &&
                C.TRACK_TYPE_TEXT in player.trackSelectionParameters.disabledTrackTypes,
        )
    }

    private fun selectedFormat(player: Player, trackType: Int): Format? {
        val tracks: Tracks = player.currentTracks
        tracks.groups.forEach { group ->
            if (group.type != trackType) return@forEach
            for (index in 0 until group.length) {
                if (group.isTrackSelected(index)) {
                    return group.getTrackFormat(index)
                }
            }
        }
        return null
    }
}
