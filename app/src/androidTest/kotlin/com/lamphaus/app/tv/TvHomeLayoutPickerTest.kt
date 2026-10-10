package com.lamphaus.app.tv

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.test.espresso.Espresso.pressBack
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import com.lamphaus.app.ui.TvHomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TvHomeLayoutPickerTest {
    @get:Rule
    val compose = createComposeRule()

    private var selected: TvHomeLayout? = null
    private var dismissed = false

    @Test
    fun TV_CNT_07_focusStartsOnTheCurrentLayoutAndThePreviewFollowsFocus() {
        setPicker(current = TvHomeLayout.SPOTLIGHT)

        option("Spotlight").assertIsFocused().assertIsSelected()
        compose.onNodeWithText(SPOTLIGHT_DESCRIPTION).assertIsDisplayed()

        option("Spotlight").press(Key.DirectionDown)

        option("Showcase").assertIsFocused()
        compose.onNodeWithText(SHOWCASE_DESCRIPTION).assertIsDisplayed()
        assertEquals(null, selected)
    }

    @Test
    fun TV_CNT_07_selectAppliesTheFocusedLayout() {
        setPicker(current = TvHomeLayout.SPOTLIGHT)

        option("Spotlight").press(Key.DirectionDown)
        option("Showcase").press(Key.DirectionDown)
        option("Marquee").assertIsFocused().press(Key.DirectionCenter)

        assertEquals(TvHomeLayout.MARQUEE, selected)
    }

    @Test
    fun TV_CNT_07_TV_NAV_02_backClosesWithoutChanging() {
        setPicker(current = TvHomeLayout.CLASSIC)
        option("Classic").assertIsFocused()

        pressBack()
        compose.waitForIdle()

        assertTrue(dismissed)
        assertEquals(null, selected)
    }

    private fun option(label: String) = compose.onNodeWithText(label)

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.press(key: Key) = apply {
        performKeyInput {
            keyDown(key)
            keyUp(key)
        }
        compose.waitForIdle()
    }

    private fun setPicker(current: TvHomeLayout) {
        compose.setContent {
            LamphausTvTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    colors = SurfaceDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                ) {
                    TvHomeLayoutPicker(
                        current = current,
                        onSelect = { selected = it },
                        onDismiss = { dismissed = true },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private companion object {
        const val SPOTLIGHT_DESCRIPTION =
            "No featured banner. The selected title widens in its row, and its details appear underneath."
        const val SHOWCASE_DESCRIPTION =
            "The selected title fills the top of the screen with its details. Its row stays below, and the title widens in place."
    }
}
