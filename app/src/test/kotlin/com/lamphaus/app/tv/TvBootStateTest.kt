package com.lamphaus.app.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** TV-MOT-01: the boot sequence plays once per launch and can always be skipped or omitted. */
class TvBootStateTest {
    @Test
    fun `plays on a cold start`() {
        assertTrue(TvBootState(enabled = true).active)
    }

    @Test
    fun `remove animations or a search launch omit it`() {
        assertFalse(TvBootState(enabled = false).active)
    }

    @Test
    fun `finishing hides it and records the launch as played`() {
        var played = false
        val state = TvBootState(enabled = true) { played = true }
        state.finish()
        assertFalse(state.active)
        assertTrue(played)
    }

    @Test
    fun `each key press requests a skip`() {
        val state = TvBootState(enabled = true)
        state.skip()
        state.skip()
        assertEquals(2, state.skipRequests)
    }

    @Test
    fun `timeline stages stay ordered inside the intro`() {
        with(TvBootTokens) {
            assertTrue(traceEnd <= introMillis && fillEnd <= introMillis && coneEnd <= introMillis)
            assertTrue(switchOn < coneStart && coneStart < coneEnd)
            assertTrue(lettersStart + 7 * letterStagger + letterDuration <= introMillis)
            assertTrue(sweepEnd <= introMillis)
        }
    }
}
