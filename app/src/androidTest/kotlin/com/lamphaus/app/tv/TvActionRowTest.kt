package com.lamphaus.app.tv

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The details actions in the 462dp the header leaves them (TV-NAV-05,
 * TV-CNT-02): a long "Resume S12 · E10" squeezed Edit artwork until its label
 * wrapped, and the taller action took Down from Play instead of the seasons.
 */
@RunWith(AndroidJUnit4::class)
class TvActionRowTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun TV_NAV_05_longLabelsKeepOneHeightAndDownLeavesTheRow() {
        val play = FocusRequester()
        compose.setContent {
            Column(Modifier.width(462.dp)) {
                TvActionRow(row = rememberTvRowFocus()) {
                    TvAction("Resume S12 · E10", Icons.Outlined.PlayArrow, {}, Modifier.focusRequester(play).testTag("play"))
                    TvAction("Add to library", Icons.Outlined.Add, {}, Modifier.testTag("library"))
                    TvAction("Edit artwork", Icons.Outlined.Palette, {}, Modifier.testTag("artwork"))
                }
                Box(Modifier.size(120.dp, 40.dp).focusable().testTag("seasons"))
            }
        }
        compose.runOnIdle { play.requestFocus() }

        val playBounds = compose.onNodeWithTag("play").getBoundsInRoot()
        val artworkBounds = compose.onNodeWithTag("artwork").getBoundsInRoot()
        assertEquals(playBounds.bottom, artworkBounds.bottom)
        assertEquals(compose.onNodeWithTag("library").getBoundsInRoot().bottom, artworkBounds.bottom)

        compose.onNodeWithTag("play").performKeyInput { keyDown(Key.DirectionDown); keyUp(Key.DirectionDown) }
        compose.onNodeWithTag("seasons").assertIsFocused()
    }
}
