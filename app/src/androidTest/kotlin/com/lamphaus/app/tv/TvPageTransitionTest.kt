package com.lamphaus.app.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TvPageTransitionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun TV_MOT_01_aNewPageFadesInAndSettlesFullyDrawn() {
        compose.mainClock.autoAdvance = false
        setPage(enabled = true)
        compose.mainClock.advanceTimeByFrame()

        assertTrue("starts faded out", pageBrightness() < 0.5f)

        compose.mainClock.advanceTimeBy(TvMotionTokens.pageEntranceMillis + 100L)
        compose.waitForIdle()

        assertTrue("settles fully drawn", pageBrightness() > 0.95f)
    }

    @Test
    fun TV_MOT_01_withTransitionsOffThePageDrawsAtOnce() {
        compose.mainClock.autoAdvance = false
        setPage(enabled = false)
        compose.mainClock.advanceTimeByFrame()

        assertTrue(pageBrightness() > 0.95f)
    }

    /** The white page's centre over the black screen: 0 hidden, 1 fully drawn. */
    private fun pageBrightness(): Float {
        val pixels = compose.onNodeWithTag(PAGE).captureToImage().toPixelMap()
        return pixels[pixels.width / 2, pixels.height / 2].red
    }

    private fun setPage(enabled: Boolean) {
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(120.dp)
                        .tvPageEntrance(key = "home", enabled = enabled)
                        .background(Color.White)
                        .testTag(PAGE),
                )
            }
        }
    }

    private companion object {
        const val PAGE = "page"
    }
}
