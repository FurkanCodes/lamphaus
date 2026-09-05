package com.lamphaus.core.player

import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi

/**
 * Media3-engine subtitle delay (plan §4): a delegating [Player] that shifts
 * cue delivery by [delayMillis] (positive = subtitles later) using scheduled
 * re-emission, so text timing changes without restarting playback and every
 * track type is covered.
 *
 * Semantics: positive delays replay the buffered cue groups later; negative
 * delays clamp to zero on this engine (cues cannot arrive before the decoder
 * emits them) — the MPV engine applies negative delays natively via
 * `sub-delay`, and the timing panel reads the active engine to surface the
 * difference. Delay persists across seeks via the buffered history.
 */
@UnstableApi
class DelayedCuePlayer(
    private val inner: Player,
) : Player by inner {

    private val handler = Handler(Looper.getMainLooper())

    /** History of cue groups, ordered by inner presentation time. */
    private val history = ArrayDeque<CueGroup>()

    @Volatile
    var delayMillis: Long = 0L
        set(value) {
            field = value.coerceIn(-180_000L, 180_000L)
            replayFromHistory()
        }

    /** Listeners registered against this wrapper; cues are delayed, all else forwards synchronously. */
    private val cueListeners = mutableListOf<Player.Listener>()

    override fun addListener(listener: Player.Listener) {
        val wasEmpty = cueListeners.isEmpty()
        cueListeners += listener
        if (wasEmpty) inner.addListener(wrappedListener)
    }

    override fun removeListener(listener: Player.Listener) {
        cueListeners -= listener
        if (cueListeners.isEmpty()) inner.removeListener(wrappedListener)
    }

    private val wrappedListener = object : Player.Listener {
        override fun onCues(cueGroup: CueGroup) {
            history.addLast(cueGroup)
            while (history.size > HISTORY_LIMIT) history.removeFirst()
            scheduleDelivery(cueGroup)
        }

        @UnstableApi
        @Deprecated("Use onCues(CueGroup) instead")
        override fun onCues(cues: List<Cue>) {
            cueListeners.toList().forEach { it.onCues(cues) }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            cueListeners.toList().forEach { it.onEvents(player, events) }
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            cueListeners.toList().forEach { it.onTimelineChanged(timeline, reason) }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            cueListeners.toList().forEach { it.onMediaItemTransition(mediaItem, reason) }
        }

        override fun onTracksChanged(tracks: Tracks) {
            cueListeners.toList().forEach { it.onTracksChanged(tracks) }
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            cueListeners.toList().forEach { it.onMediaMetadataChanged(mediaMetadata) }
        }

        override fun onPlaylistMetadataChanged(mediaMetadata: MediaMetadata) {
            cueListeners.toList().forEach { it.onPlaylistMetadataChanged(mediaMetadata) }
        }

        override fun onIsLoadingChanged(isLoading: Boolean) {
            cueListeners.toList().forEach { it.onIsLoadingChanged(isLoading) }
        }

        @UnstableApi
        @Deprecated("Use onIsLoadingChanged instead")
        override fun onLoadingChanged(isLoading: Boolean) {
            cueListeners.toList().forEach { it.onLoadingChanged(isLoading) }
        }

        override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
            cueListeners.toList().forEach { it.onAvailableCommandsChanged(availableCommands) }
        }

        override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) {
            cueListeners.toList().forEach { it.onTrackSelectionParametersChanged(parameters) }
        }

        @UnstableApi
        @Deprecated("Use onPlaybackStateChanged and onPlayWhenReadyChanged instead")
        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            cueListeners.toList().forEach { it.onPlayerStateChanged(playWhenReady, playbackState) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            cueListeners.toList().forEach { it.onPlaybackStateChanged(playbackState) }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            cueListeners.toList().forEach { it.onPlayWhenReadyChanged(playWhenReady, reason) }
        }

        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
            cueListeners.toList().forEach { it.onPlaybackSuppressionReasonChanged(playbackSuppressionReason) }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            cueListeners.toList().forEach { it.onIsPlayingChanged(isPlaying) }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            cueListeners.toList().forEach { it.onRepeatModeChanged(repeatMode) }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            cueListeners.toList().forEach { it.onShuffleModeEnabledChanged(shuffleModeEnabled) }
        }

        override fun onPlayerError(error: PlaybackException) {
            cueListeners.toList().forEach { it.onPlayerError(error) }
        }

        override fun onPlayerErrorChanged(error: PlaybackException?) {
            cueListeners.toList().forEach { it.onPlayerErrorChanged(error) }
        }

        @UnstableApi
        @Deprecated("Use onPositionDiscontinuity(PositionInfo, PositionInfo, int) instead")
        override fun onPositionDiscontinuity(reason: Int) {
            cueListeners.toList().forEach { it.onPositionDiscontinuity(reason) }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            cueListeners.toList().forEach { it.onPositionDiscontinuity(oldPosition, newPosition, reason) }
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            cueListeners.toList().forEach { it.onPlaybackParametersChanged(playbackParameters) }
        }

        override fun onSeekBackIncrementChanged(seekBackIncrementMs: Long) {
            cueListeners.toList().forEach { it.onSeekBackIncrementChanged(seekBackIncrementMs) }
        }

        override fun onSeekForwardIncrementChanged(seekForwardIncrementMs: Long) {
            cueListeners.toList().forEach { it.onSeekForwardIncrementChanged(seekForwardIncrementMs) }
        }

        override fun onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs: Long) {
            cueListeners.toList().forEach { it.onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs) }
        }

        @UnstableApi
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            cueListeners.toList().forEach { it.onAudioSessionIdChanged(audioSessionId) }
        }

        override fun onAudioAttributesChanged(audioAttributes: AudioAttributes) {
            cueListeners.toList().forEach { it.onAudioAttributesChanged(audioAttributes) }
        }

        override fun onVolumeChanged(volume: Float) {
            cueListeners.toList().forEach { it.onVolumeChanged(volume) }
        }

        override fun onSkipSilenceEnabledChanged(skipSilenceEnabled: Boolean) {
            cueListeners.toList().forEach { it.onSkipSilenceEnabledChanged(skipSilenceEnabled) }
        }

        override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) {
            cueListeners.toList().forEach { it.onDeviceInfoChanged(deviceInfo) }
        }

        override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) {
            cueListeners.toList().forEach { it.onDeviceVolumeChanged(volume, muted) }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            cueListeners.toList().forEach { it.onVideoSizeChanged(videoSize) }
        }

        override fun onSurfaceSizeChanged(width: Int, height: Int) {
            cueListeners.toList().forEach { it.onSurfaceSizeChanged(width, height) }
        }

        override fun onRenderedFirstFrame() {
            cueListeners.toList().forEach { it.onRenderedFirstFrame() }
        }

        @UnstableApi
        override fun onMetadata(metadata: Metadata) {
            cueListeners.toList().forEach { it.onMetadata(metadata) }
        }
    }

    /** Schedules the group for delayed delivery; clearing groups pass through immediately. */
    private fun scheduleDelivery(group: CueGroup) {
        val delay = delayMillis
        when {
            delay <= 0L -> deliver(group)
            group.cues.isEmpty() -> {
                // The clear marks the end of the previous cue; deliver it late.
                handler.postDelayed({ deliverDelayedEnd(group) }, delay)
            }
            else -> handler.postDelayed({ deliver(group) }, delay)
        }
    }

    private fun deliverDelayedEnd(endGroup: CueGroup) {
        if (delayMillis <= 0L) {
            deliver(endGroup)
            return
        }
        // Re-emit the last non-empty group's end now that its delay elapsed.
        val lastVisible = history.lastOrNull { it.cues.isNotEmpty() }
        if (lastVisible != null && inner.currentPosition * 1000 < lastVisible.presentationTimeUs + endGroup.presentationTimeUs) {
            deliver(CueGroup(emptyList(), endGroup.presentationTimeUs))
        } else {
            deliver(endGroup)
        }
    }

    private fun deliver(group: CueGroup) {
        cueListeners.toList().forEach { it.onCues(group) }
    }

    /** Replays buffered groups against the current delay (delay changed). */
    private fun replayFromHistory() {
        val nowUs = inner.currentPosition * 1000
        val target = nowUs - delayMillis * 1000
        val active = history.lastOrNull { it.presentationTimeUs <= target }
        deliver(active ?: CueGroup(emptyList(), nowUs))
    }

    override fun getCurrentCues(): CueGroup {
        if (delayMillis <= 0L) return inner.currentCues
        val target = inner.currentPosition * 1000 - delayMillis * 1000
        return history.lastOrNull { it.presentationTimeUs <= target } ?: CueGroup(emptyList(), inner.currentPosition * 1000)
    }

    override fun release() {
        handler.removeCallbacksAndMessages(null)
        cueListeners.clear()
    }

    private companion object {
        /** ~4 minutes of cue history bounds memory while covering any seek-back. */
        const val HISTORY_LIMIT = 240
    }
}
