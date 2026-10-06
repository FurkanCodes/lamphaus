package com.lamphaus.core.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.SeekParameters

/** What kind of seek the viewer made; each lands on keyframes the way Nuvio's do. */
enum class SeekKind {
    /** A step back (±10 s button, double tap): the previous keyframe, decoded at once. */
    STEP_BACK,

    /** A step forward: the next keyframe. */
    STEP_FORWARD,

    /** A seek-bar position, scrub, or accumulated remote seek: the nearest keyframe. */
    JUMP,

    /** A skipped intro or recap: the next keyframe, so nothing skipped is shown. */
    SKIP,

    /** Credits, the start over, and anything that must land on the exact frame. */
    EXACT,
}

/**
 * Seek parameters live on ExoPlayer, which MediaController cannot reach, so
 * the activity sets them on the in-process session player just before it
 * seeks through the controller. Both run on the main thread, in order. MPV
 * sessions keep their own exact seeks.
 */
@UnstableApi
object PlaybackSeeks {

    fun prepare(kind: SeekKind) {
        Media3EngineFactory.sessionPlayer?.setSeekParameters(parametersFor(kind))
    }

    internal fun parametersFor(kind: SeekKind): SeekParameters = when (kind) {
        SeekKind.STEP_BACK -> SeekParameters.PREVIOUS_SYNC
        SeekKind.STEP_FORWARD, SeekKind.SKIP -> SeekParameters.NEXT_SYNC
        SeekKind.JUMP -> SeekParameters.CLOSEST_SYNC
        SeekKind.EXACT -> SeekParameters.EXACT
    }
}
