package com.lamphaus.app.tv

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * D-pad Select hold detection (TV-FND-02, Nuvio LongPressKeyTracker parity):
 * a short press stays a normal click; holding center/Menu for
 * [DEFAULT_HOLD_MILLIS] fires [onLongPress] exactly once and consumes the
 * release so the menu never clicks through.
 */
internal class SelectHoldTracker(
    private val scope: CoroutineScope,
    private val holdMillis: Long = DEFAULT_HOLD_MILLIS,
    private val onLongPress: () -> Unit,
) {
    private var holdJob: Job? = null
    private var fired = false

    /** @return true when the key event must be consumed. */
    fun onKeyDown(): Boolean {
        // Auto-repeat and duplicate downs must not reset the hold timer.
        if (fired) return true
        if (holdJob?.isActive == true) return fired
        holdJob = scope.launch {
            delay(holdMillis)
            if (!fired) {
                fired = true
                onLongPress()
            }
        }
        return fired
    }

    /** The dedicated Menu key opens immediately and consumes its full press. */
    fun onMenuKeyDown(): Boolean {
        if (!fired) {
            holdJob?.cancel()
            holdJob = null
            fired = true
            onLongPress()
        }
        return true
    }

    /** @return true when the release must be consumed. */
    fun onKeyUp(): Boolean {
        val consume = fired
        holdJob?.cancel()
        holdJob = null
        fired = false
        return consume
    }

    fun cancel() {
        holdJob?.cancel()
        holdJob = null
        fired = false
    }

    companion object {
        const val DEFAULT_HOLD_MILLIS = 500L
    }
}
