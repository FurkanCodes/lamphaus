package com.lamphaus.app.mobile

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lamphaus.app.ui.SpoilerBlurLayer
import com.lamphaus.core.data.preferences.ThemePreference
import com.lamphaus.core.model.SpoilerProtectionSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MobileSpoilerProtectionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun hiddenLayerRemovesProtectedCopyFromSemantics() {
        compose.setContent {
            LamphausMobileTheme(preference = ThemePreference.DARK, dynamicColor = false) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SpoilerBlurLayer(
                        hidden = true,
                        veilColor = Color.Black,
                        semanticLabel = "Spoiler hidden",
                        veilContent = { androidx.compose.material3.Text("Spoiler hidden") },
                    ) {
                        androidx.compose.material3.Text("Secret overview")
                    }
                }
            }
        }

        compose.onAllNodesWithText("Secret overview").assertCountEquals(0)
        compose.onNodeWithText("Spoiler hidden").assertIsDisplayed()
    }

    @Test
    fun episodeSpoilersDoNotBlockIndependentPlayAction() {
        var plays = 0
        compose.setContent {
            LamphausMobileTheme(preference = ThemePreference.DARK, dynamicColor = false) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    EpisodeRow(
                        episode = SpoilerProtectionTestFixtures.episode,
                        watched = false,
                        spoilerProtection = SpoilerProtectionSettings(),
                        onPlay = { plays++ },
                    )
                }
            }
        }

        compose.onAllNodesWithText("Secret overview").assertCountEquals(0)
        compose.onNodeWithText("Synopsis hidden").assertIsDisplayed()
        compose.onAllNodesWithText("Reveal spoilers").assertCountEquals(0)
        compose.onNodeWithContentDescription("Play Secret episode")
            .assertHasClickAction()
            .performClick()

        assertEquals(1, plays)
    }
}

private object SpoilerProtectionTestFixtures {
    val episode = com.lamphaus.core.model.Episode(
        id = "episode-1",
        title = "Secret episode",
        season = 1,
        episode = 1,
        overview = "Secret overview",
    )
}
