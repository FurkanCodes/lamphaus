package com.lamphaus.core.model

/**
 * Nuvio's auto-play guard: after a few episodes start on their own in a row,
 * the next one waits for the viewer instead ("Still watching?"), and an
 * unanswered prompt closes the player so a sleeping viewer does not play
 * through a season.
 */
object AutoPlayPolicy {
    /** Automatic starts in a row before the viewer is asked. */
    const val STILL_WATCHING_AFTER = 3

    /** An unanswered prompt closes the player after this long. */
    const val STILL_WATCHING_TIMEOUT_SECONDS = 60

    fun shouldAskStillWatching(autoPlayStreak: Int): Boolean = autoPlayStreak >= STILL_WATCHING_AFTER

    /** The streak the next episode carries: one more for an automatic start, zero for the viewer's. */
    fun nextStreak(currentStreak: Int, automatic: Boolean): Int = if (automatic) currentStreak + 1 else 0
}
