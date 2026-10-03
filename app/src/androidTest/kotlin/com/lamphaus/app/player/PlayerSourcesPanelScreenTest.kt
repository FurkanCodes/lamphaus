package com.lamphaus.app.player

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lamphaus.core.model.StreamCandidate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The player's Sources panel reads like the details screen (SHR-PROD-12,
 * TV-FOC-01): add-on tabs with counts, details-style cards, the playing
 * source marked and focused, and choosing another one reports it.
 */
@RunWith(AndroidJUnit4::class)
class PlayerSourcesPanelScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun option(providerId: String, providerName: String, name: String, url: String) = PlayerSourceOption(
        stream = StreamCandidate(providerId = providerId, name = name, url = url),
        url = url,
        providerName = providerName,
    )

    private val options = listOf(
        option("alpha", "Alpha", "Alpha 2160p", "https://example.invalid/a1.mkv"),
        option("alpha", "Alpha", "Alpha 1080p", "https://example.invalid/a2.mkv"),
        option("beta", "Beta", "Beta 720p", "https://example.invalid/b1.mkv"),
    )

    private fun show(isTelevision: Boolean, onSelect: (PlayerSourceOption) -> Unit = {}) {
        compose.setContent {
            PlaybackTheme(isTelevision) {
                PlayerSourcesPanel(
                    state = PlayerSourcesState(options = options),
                    currentUri = "https://example.invalid/a2.mkv",
                    isTelevision = isTelevision,
                    onSelect = onSelect,
                    onClose = {},
                )
            }
        }
    }

    @Test
    fun tvTabsFilterAndThePlayingSourceTakesFocus() {
        show(isTelevision = true)

        compose.onNodeWithText("All sources").assertIsDisplayed()
        compose.onNodeWithText("Alpha 1080p").assertIsFocused()
        compose.onNodeWithText("Beta").performClick()
        compose.onNodeWithText("Beta 720p").assertIsDisplayed()
        compose.onNodeWithText("Alpha 2160p").assertDoesNotExist()
    }

    @Test
    fun mobileChoosingAnotherSourceReportsIt() {
        var chosen: PlayerSourceOption? = null
        show(isTelevision = false) { chosen = it }

        compose.onNodeWithText("Playing now").assertIsDisplayed()
        compose.onNodeWithText("Beta 720p").performClick()

        assertEquals("https://example.invalid/b1.mkv", chosen?.url)
    }
}
