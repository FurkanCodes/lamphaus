package com.lamphaus.app.tv

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** TV-MOT-01: the boot sequence plays once per process and can always be skipped or omitted. */
class TvBootStateTest {
    @Before
    @After
    fun resetGate() {
        TvBootGate.played = false
    }

    @Test
    fun `plays on a cold start`() {
        assertTrue(TvBootState(enabled = true).active)
    }

    @Test
    fun `remove animations or a search launch omit it`() {
        assertFalse(TvBootState(enabled = false).active)
    }

    @Test
    fun `finishing marks the process so it never replays`() {
        val first = TvBootState(enabled = true)
        first.finish()
        assertFalse(first.active)
        assertFalse(TvBootState(enabled = true).active)
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
