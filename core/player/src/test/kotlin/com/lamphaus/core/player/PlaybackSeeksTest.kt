package com.lamphaus.core.player

import androidx.media3.exoplayer.SeekParameters
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSeeksTest {
    @Test
    fun `steps land on the neighbouring keyframe, jumps on the nearest, credits exactly`() {
        assertEquals(SeekParameters.PREVIOUS_SYNC, PlaybackSeeks.parametersFor(SeekKind.STEP_BACK))
        assertEquals(SeekParameters.NEXT_SYNC, PlaybackSeeks.parametersFor(SeekKind.STEP_FORWARD))
        assertEquals(SeekParameters.CLOSEST_SYNC, PlaybackSeeks.parametersFor(SeekKind.JUMP))
        assertEquals(SeekParameters.NEXT_SYNC, PlaybackSeeks.parametersFor(SeekKind.SKIP))
        assertEquals(SeekParameters.EXACT, PlaybackSeeks.parametersFor(SeekKind.EXACT))
    }
}
