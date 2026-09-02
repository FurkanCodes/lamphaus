package com.lamphaus.app.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Contract for the hero carousel layer transforms (MOB-CLR-07, MOB-MOT-01,
 * MOB-MOT-03): the settled layer stays opaque and zooms up while the incoming
 * neighbor fades in, the roles swap without a pop at swipe completion, and
 * reduced motion removes the shared swipe zoom from artwork and its attached
 * fade together while the crossfade survives.
 */
class MobileHeroLayerTransformTest {

    @Test
    fun `settled layer is opaque and unzoomed at rest`() {
        val transform = heroSettledLayerTransform(progress = 0f, reducedMotion = false)

        assertEquals(1f, transform.alpha)
        assertEquals(1f, transform.scale)
    }

    @Test
    fun `settled layer zooms up with half and full swipe progress`() {
        val half = heroSettledLayerTransform(progress = 0.5f, reducedMotion = false)
        assertEquals(1f, half.alpha)
        assertEquals(1f + 0.06f * 0.5f, half.scale)

        val completed = heroSettledLayerTransform(progress = 1f, reducedMotion = false)
        assertEquals(1f, completed.alpha)
        assertEquals(1f + 0.06f, completed.scale)
    }

    @Test
    fun `neighbor layer fades in while its zoom settles downward`() {
        val half = heroNeighborLayerTransform(progress = 0.5f, reducedMotion = false)
        assertEquals(0.5f, half.alpha)
        assertEquals(1f + 0.06f * 0.5f, half.scale)

        val completed = heroNeighborLayerTransform(progress = 1f, reducedMotion = false)
        assertEquals(1f, completed.alpha)
        assertEquals(1f, completed.scale)
    }

    @Test
    fun `completed neighbor matches a freshly settled layer without a pop`() {
        val completedNeighbor = heroNeighborLayerTransform(progress = 1f, reducedMotion = false)
        val freshSettled = heroSettledLayerTransform(progress = 0f, reducedMotion = false)

        assertEquals(freshSettled, completedNeighbor)
    }

    @Test
    fun `reduced motion removes swipe zoom from artwork and fade together`() {
        val settled = heroSettledLayerTransform(progress = 0.5f, reducedMotion = true)
        assertEquals(1f, settled.alpha)
        assertEquals(1f, settled.scale)

        // The crossfade survives; only the scaling is dropped (MOB-MOT-03).
        val neighbor = heroNeighborLayerTransform(progress = 0.5f, reducedMotion = true)
        assertEquals(0.5f, neighbor.alpha)
        assertEquals(1f, neighbor.scale)

        val completedNeighbor = heroNeighborLayerTransform(progress = 1f, reducedMotion = true)
        assertEquals(1f, completedNeighbor.alpha)
        assertEquals(1f, completedNeighbor.scale)
    }
}
