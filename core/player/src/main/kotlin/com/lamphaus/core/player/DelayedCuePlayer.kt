package com.lamphaus.core.player

import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
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

    /** Listeners registered against this wrapper receive delayed cues only. */
    private val cueListeners = mutableListOf<Player.Listener>()

    override fun addListener(listener: Player.Listener) {
        cueListeners += listener
        inner.addListener(wrappedListener)
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
        inner.release()
    }

    private companion object {
        /** ~4 minutes of cue history bounds memory while covering any seek-back. */
        const val HISTORY_LIMIT = 240
    }
}
