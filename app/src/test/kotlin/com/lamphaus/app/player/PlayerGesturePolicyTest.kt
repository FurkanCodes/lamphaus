package com.lamphaus.app.player

import org.junit.Assert.assertEquals
import org.junit.Test

/** Gesture-to-value math behind the touch layer (QA-01, QA-03). */
class PlayerGesturePolicyTest {

    @Test
    fun seekDelta_scalesWithWidth() {
        assertEquals(0L, PlayerGesturePolicy.seekDeltaMillis(0f, 1080f))
        assertEquals(45_000L, PlayerGesturePolicy.seekDeltaMillis(540f, 1080f))
        assertEquals(-45_000L, PlayerGesturePolicy.seekDeltaMillis(-540f, 1080f))
    }

    @Test
    fun seekDelta_ignoresUnmeasuredWidth() {
        assertEquals(0L, PlayerGesturePolicy.seekDeltaMillis(120f, 0f))
    }

    @Test
    fun levelFor_clampsToRange() {
        assertEquals(0.5f, PlayerGesturePolicy.levelFor(0.5f, 0f, 1000f), 0.001f)
        assertEquals(0.6f, PlayerGesturePolicy.levelFor(0.5f, 100f, 1000f), 0.001f)
        assertEquals(1f, PlayerGesturePolicy.levelFor(0.9f, 500f, 1000f), 0.001f)
        assertEquals(0f, PlayerGesturePolicy.levelFor(0.1f, -500f, 1000f), 0.001f)
        assertEquals(0.5f, PlayerGesturePolicy.levelFor(0.5f, 100f, 0f), 0.001f)
    }
}
