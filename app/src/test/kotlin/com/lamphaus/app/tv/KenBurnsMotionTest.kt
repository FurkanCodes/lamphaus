package com.lamphaus.app.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KenBurnsMotionTest {
    @Test
    fun `stable keys map to deterministic diagonal paths`() {
        assertEquals(KenBurnsPath(+1f, +1f), kenBurnsPathFor("0"))
        assertEquals(KenBurnsPath(+1f, -1f), kenBurnsPathFor("1"))
        assertEquals(KenBurnsPath(-1f, +1f), kenBurnsPathFor("2"))
        assertEquals(KenBurnsPath(-1f, -1f), kenBurnsPathFor("3"))
    }

    @Test
    fun `path selection is repeatable`() {
        assertEquals(kenBurnsPathFor("featured-title"), kenBurnsPathFor("featured-title"))
    }

    @Test
    fun `motion policy requires user setting full motion and artwork`() {
        assertFalse(shouldAnimateKenBurns(userEnabled = false, reducedMotion = false, hasArtwork = true))
        assertFalse(shouldAnimateKenBurns(userEnabled = true, reducedMotion = true, hasArtwork = true))
        assertFalse(shouldAnimateKenBurns(userEnabled = true, reducedMotion = false, hasArtwork = false))
        assertTrue(shouldAnimateKenBurns(userEnabled = true, reducedMotion = false, hasArtwork = true))
    }
}
