package com.lamphaus.app.tv

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performKeyInput
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import com.lamphaus.core.model.Episode
import com.lamphaus.core.model.SpoilerProtectionSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TvSpoilerProtectionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun focusDoesNotRevealAndSelectStillPlays() {
        var playCount = 0
        val requester = FocusRequester()

        compose.setContent {
            LamphausTvTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    colors = SurfaceDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                ) {
                    TvEpisodeCard(
                        episode = episode,
                        watched = false,
                        spoilerProtection = SpoilerProtectionSettings(),
                        fallbackArtworkUrl = null,
                        onClick = { playCount++ },
                        modifier = Modifier.focusRequester(requester).testTag("episode"),
                    )
                }
            }
        }

        compose.runOnIdle { requester.requestFocus() }
        compose.waitForIdle()
        compose.onNodeWithTag("episode").assertIsFocused()
        compose.onAllNodesWithText("Secret overview").assertCountEquals(0)
        compose.onNodeWithText("Synopsis hidden").assertIsDisplayed()

        compose.onNodeWithTag("episode").performKeyInput {
            keyDown(Key.DirectionCenter)
            keyUp(Key.DirectionCenter)
        }
        compose.waitForIdle()
        assertEquals(1, playCount)
    }

    private companion object {
        val episode = Episode(
            id = "episode-1",
            title = "Secret episode",
            season = 1,
            episode = 1,
            overview = "Secret overview",
        )
    }
}
