package com.lamphaus.app.tv

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SelectHoldTrackerTest {
    @Test
    fun `TV-FND-02 repeated select after hold stays consumed`() = runTest {
        var menuCount = 0
        val tracker = SelectHoldTracker(this, holdMillis = 500) { menuCount++ }

        assertFalse(tracker.onKeyDown())
        advanceTimeBy(500)
        runCurrent()

        assertEquals(1, menuCount)
        assertTrue(tracker.onKeyDown())
        assertTrue(tracker.onKeyUp())
        assertEquals(1, menuCount)
    }

    @Test
    fun `TV-FND-02 menu key opens immediately exactly once`() = runTest {
        var menuCount = 0
        val tracker = SelectHoldTracker(this) { menuCount++ }

        assertTrue(tracker.onMenuKeyDown())
        assertTrue(tracker.onMenuKeyDown())
        assertTrue(tracker.onKeyUp())
        assertEquals(1, menuCount)
    }

    @Test
    fun `TV-FND-02 short select remains an ordinary click`() = runTest {
        var menuCount = 0
        val tracker = SelectHoldTracker(this) { menuCount++ }

        assertFalse(tracker.onKeyDown())
        advanceTimeBy(200)
        assertFalse(tracker.onKeyUp())
        assertEquals(0, menuCount)
    }
}
