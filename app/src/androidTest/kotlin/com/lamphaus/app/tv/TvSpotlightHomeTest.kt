package com.lamphaus.app.tv

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import com.lamphaus.app.ui.CatalogSection
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TvSpotlightHomeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun TV_CNT_03_focusedCardWidensAndDescribesItselfBelowTheRow() {
        val entry = setRow()

        compose.runOnIdle { entry.requestFocus() }
        compose.waitForIdle()

        card(0).assertIsFocused()
        card(0).assertWidthIsEqualTo(TvLayoutTokens.spotlightExpandedWidth)
        card(1).assertWidthIsEqualTo(TvLayoutTokens.posterWidth)
        compose.onNodeWithText("Drama  •  2021  •  Movie  •  PG-13").assertIsDisplayed()
        compose.onNodeWithText("Synopsis 1").assertIsDisplayed()
    }

    @Test
    fun TV_CNT_03_TV_NAV_05_movingRightKeepsTheFocusedCardPinned() {
        val entry = setRow()
        compose.runOnIdle { entry.requestFocus() }
        compose.waitForIdle()
        val pinnedLeft = card(0).getBoundsInRoot().left

        repeat(3) { index ->
            card(index).performKeyInput {
                keyDown(Key.DirectionRight)
                keyUp(Key.DirectionRight)
            }
            compose.waitForIdle()
            card(index + 1).assertIsFocused()
            assertEquals(pinnedLeft, card(index + 1).getBoundsInRoot().left)
        }
        compose.onNodeWithText("Synopsis 4").assertIsDisplayed()
    }

    @Test
    fun TV_CNT_03_selectOpensTheFocusedTitle() {
        var opened: MediaPreview? = null
        val entry = setRow(onMedia = { opened = it })
        compose.runOnIdle { entry.requestFocus() }
        compose.waitForIdle()

        card(0).performKeyInput {
            keyDown(Key.DirectionCenter)
            keyUp(Key.DirectionCenter)
        }
        compose.waitForIdle()

        assertEquals("m0", opened?.id)
    }

    @Test
    fun TV_CNT_05_showcaseRowsWidenWithTheirTitleButWithoutTheDetailsSlot() {
        val entry = setRow(detailsSlot = false)

        compose.runOnIdle { entry.requestFocus() }
        compose.waitForIdle()

        card(0).assertIsFocused()
        card(0).assertWidthIsEqualTo(TvLayoutTokens.spotlightExpandedWidth)
        // Without a logo image the still names the title in text, as in Spotlight.
        compose.onNodeWithText("Title 1", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Synopsis 1").assertDoesNotExist()
    }

    private fun card(index: Int) =
        compose.onNode(hasContentDescription("Title ${index + 1},", substring = true))

    private fun setRow(onMedia: (MediaPreview) -> Unit = {}, detailsSlot: Boolean = true): FocusRequester {
        val entry = FocusRequester()
        val section = CatalogSection(
            id = "row",
            providerId = "provider",
            title = "Popular",
            providerName = "Test add-on",
            items = List(10) { index ->
                MediaPreview(
                    id = "m$index",
                    type = MediaType.MOVIE,
                    rawType = "movie",
                    name = "Title ${index + 1}",
                    description = "Synopsis ${index + 1}",
                    releaseYear = 2021,
                    genres = listOf("Drama"),
                    contentRating = "PG-13",
                )
            },
        )
        compose.setContent {
            var contentHasFocus by remember { mutableStateOf(false) }
            LamphausTvTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    colors = SurfaceDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                ) {
                    TvSpotlightRow(
                        section = section,
                        contentHasFocus = contentHasFocus,
                        onMedia = onMedia,
                        onFocused = { contentHasFocus = true },
                        onLoadMore = {},
                        onRetry = {},
                        restoreMediaKey = null,
                        onFocusRestored = {},
                        firstItemFocusRequester = entry,
                        detailsSlot = detailsSlot,
                    )
                }
            }
        }
        return entry
    }
}
