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
            LamphausMobileTheme {
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
            LamphausMobileTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    EpisodeRow(
                        episode = SpoilerProtectionTestFixtures.episode,
                        media = SpoilerProtectionTestFixtures.media,
                        watched = false,
                        spoilerProtection = SpoilerProtectionSettings(),
                        progress = null,
                        onPlay = { plays++ },
                        onOpenMenu = {},
                    )
                }
            }
        }

        compose.onAllNodesWithText("Secret overview").assertCountEquals(0)
        compose.onNodeWithText("Synopsis hidden").assertIsDisplayed()
        compose.onAllNodesWithText("Reveal spoilers").assertCountEquals(0)
        // Spoiler protection hides artwork and synopsis but must not block
        // the row's play action.
        compose.onNodeWithText("Secret episode")
            .assertHasClickAction()
            .performClick()

        assertEquals(1, plays)
    }
}

private object SpoilerProtectionTestFixtures {
    val media = com.lamphaus.core.model.MediaPreview(
        id = "series-1",
        type = com.lamphaus.core.model.MediaType.SERIES,
        rawType = "series",
        name = "Series",
    )
    val episode = com.lamphaus.core.model.Episode(
        id = "episode-1",
        title = "Secret episode",
        season = 1,
        episode = 1,
        overview = "Secret overview",
    )
}
