package com.lamphaus.app.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Asks every add-on for subtitles at once (PLY-SUB-01). The stream waits on
 * [await], which ends as soon as it has what it needs, while the lookups run
 * on: what answers later still reaches [arrived], and one add-on that hangs
 * or fails costs nothing but its own subtitles.
 */
internal class AddonSubtitleLookup<T>(scope: CoroutineScope, lookups: List<suspend () -> List<T>>) {
    private val answers = MutableStateFlow(List<List<T>?>(lookups.size) { null })

    /** Every track so far, in add-on order whichever answered first. */
    val arrived: Flow<List<T>> = answers.map(::tracks)

    init {
        lookups.forEachIndexed { index, lookup ->
            scope.launch {
                val tracks = try {
                    lookup()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    emptyList()
                }
                answers.update { current -> current.toMutableList().also { it[index] = tracks } }
            }
        }
    }

    /** Returns once every add-on answered, [satisfied] holds, or [waitMillis] passed. */
    suspend fun await(waitMillis: Long, satisfied: (List<T>) -> Boolean): List<T> {
        withTimeoutOrNull(waitMillis) {
            answers.first { current -> current.none { it == null } || satisfied(tracks(current)) }
        }
        return tracks(answers.value)
    }

    private fun tracks(current: List<List<T>?>): List<T> = current.filterNotNull().flatten()

    companion object {
        /** A viewer with subtitles off gets the fast answers in the panel, never a wait for the rest. */
        private const val UNWANTED_WAIT_MILLIS = 1_000L

        /** Long enough for a subtitle a viewer expects to be on screen from the first line. */
        private const val WANTED_WAIT_MILLIS = 3_000L

        fun waitMillis(wanted: Boolean): Long = if (wanted) WANTED_WAIT_MILLIS else UNWANTED_WAIT_MILLIS
    }
}
