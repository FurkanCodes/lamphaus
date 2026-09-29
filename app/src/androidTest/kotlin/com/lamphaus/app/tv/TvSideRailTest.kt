package com.lamphaus.app.tv

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Surface
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** TV-NAV-01/02/05: the side rail opens from any depth and hands focus back to the exact card. */
class TvSideRailTest {
    @get:Rule
    val compose = createComposeRule()

    private val navigationRequesters = TvDestination.entries.associateWith { FocusRequester() }
    private val profileRequester = FocusRequester()
    private val firstCard = FocusRequester()
    private val selected = mutableListOf<TvDestination>()
    private var returnsToContent = 0

    /** Mirrors the shell's Back: remember the page focus, then focus the active rail item. */
    private var openRailLikeBack: () -> Unit = {}

    private fun setRailAndPage(rows: Int = 14, cards: Int = 8) = setRail { RowsPage(rows, cards) }

    private fun setRailAndGrid(items: Int = 60) = setRail {
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            contentPadding = PaddingValues(horizontal = TvLayoutTokens.screenHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(TvLayoutTokens.itemSpacing),
            verticalArrangement = Arrangement.spacedBy(TvLayoutTokens.rowSpacing),
        ) {
            items(items) { index ->
                Box(
                    Modifier
                        .then(if (index == 0) Modifier.focusRequester(firstCard) else Modifier)
                        .height(120.dp)
                        .focusable()
                        .testTag("cell-${index / 5}-${index % 5}"),
                )
            }
        }
    }

    @Composable
    private fun RowsPage(rows: Int, cards: Int) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = TvLayoutTokens.screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(TvLayoutTokens.rowSpacing),
        ) {
            items(rows) { row ->
                val rowFocus = rememberTvRowFocus()
                LazyRow(
                    modifier = Modifier.tvRowFocus(rowFocus),
                    horizontalArrangement = Arrangement.spacedBy(TvLayoutTokens.itemSpacing),
                ) {
                    items(cards) { card ->
                        Box(
                            Modifier
                                .tvRowItem(rowFocus, card)
                                .then(if (row == 0 && card == 0) Modifier.focusRequester(firstCard) else Modifier)
                                .size(TvLayoutTokens.posterWidth, 120.dp)
                                .focusable()
                                .testTag("card-$row-$card"),
                        )
                    }
                }
            }
        }
    }

    private fun setRail(page: @Composable () -> Unit) {
        compose.setContent {
            var focusDestination by remember { mutableStateOf<TvDestination?>(null) }
            val focusReturn = rememberTvContentFocusReturn()
            val focusManager = LocalFocusManager.current
            openRailLikeBack = {
                focusReturn.save()
                focusDestination = TvDestination.HOME
            }
            LamphausTvTheme {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(start = TvRailTokens.contentStartOffset, top = TvRailTokens.contentTopPadding)
                                .tvContentFocusBoundary(
                                    topNavigationRequester = FocusRequester.Cancel,
                                    leftNavigationRequester = FocusRequester.Cancel,
                                )
                                .tvContentFocusMemory(focusReturn)
                                .tvRailOpensOnLeft(focusManager) {
                                    focusReturn.save()
                                    focusDestination = TvDestination.HOME
                                },
                        ) {
                            page()
                            TvContentFocusAnchor(focusReturn, onFailed = {})
                        }
                        TvSideRail(
                            selectedDestination = TvDestination.HOME,
                            activeProfile = null,
                            focusDestination = focusDestination,
                            requesters = navigationRequesters,
                            profileRequester = profileRequester,
                            onFocusHandled = { focusDestination = null },
                            onHasFocus = {},
                            onDestination = { selected += it },
                            onReturnToContent = {
                                returnsToContent++
                                focusReturn.restore()
                            },
                            onProfileSwitcher = {},
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { firstCard.requestFocus() }
        compose.waitForIdle()
    }

    private fun press(tag: String, key: Key, times: Int = 1) {
        repeat(times) {
            compose.onNodeWithTag(tag, useUnmergedTree = true).performKeyInput { pressKey(key) }
            compose.waitForIdle()
        }
    }

    private fun pressOnFocused(description: String, key: Key) {
        compose.onNodeWithContentDescription(description).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    /** Names the node that holds focus instead, so a wrong return is easy to read. */
    private fun assertCardFocused(tag: String) {
        val focused = compose.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes().map {
            it.config.getOrNull(SemanticsProperties.TestTag)
                ?: it.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
                ?: "node ${it.id} at ${it.boundsInRoot}"
        }
        assertEquals(listOf(tag), focused)
    }

    private fun walkDown(rows: Int) {
        repeat(rows) { row -> press("card-$row-0", Key.DirectionDown) }
    }

    @Test
    fun leftAtTheStartOfADeepRowOpensTheRailOnTheActivePage() {
        setRailAndPage()
        walkDown(10)
        compose.onNodeWithTag("card-10-0").assertIsFocused()

        press("card-10-0", Key.DirectionLeft)

        compose.onNodeWithContentDescription("Home").assertIsFocused()
    }

    @Test
    fun rightFromTheRailReturnsToTheExactCardItLeft() {
        setRailAndPage()
        walkDown(10)
        press("card-10-0", Key.DirectionRight, times = 3)
        press("card-10-3", Key.DirectionLeft, times = 3)
        compose.onNodeWithTag("card-10-0").assertIsFocused()
        press("card-10-0", Key.DirectionLeft)
        compose.onNodeWithContentDescription("Home").assertIsFocused()

        pressOnFocused("Home", Key.DirectionRight)

        assertCardFocused("card-10-0")
        assertEquals(1, returnsToContent)
    }

    @Test
    fun leftInsideARowMovesToThePreviousCardInsteadOfOpeningTheRail() {
        setRailAndPage()
        walkDown(6)
        press("card-6-0", Key.DirectionRight, times = 2)

        press("card-6-2", Key.DirectionLeft)

        compose.onNodeWithTag("card-6-1").assertIsFocused()
    }

    @Test
    fun movingThroughTheRailNeverSwitchesThePageUntilSelect() {
        setRailAndPage()
        press("card-0-0", Key.DirectionLeft)
        compose.onNodeWithContentDescription("Home").assertIsFocused()

        pressOnFocused("Home", Key.DirectionDown)
        compose.onNodeWithContentDescription("Movies").assertIsFocused()
        pressOnFocused("Movies", Key.DirectionDown)
        compose.onNodeWithContentDescription("Series").assertIsFocused()
        assertEquals(emptyList<TvDestination>(), selected)

        pressOnFocused("Series", Key.DirectionCenter)

        assertEquals(listOf(TvDestination.SERIES), selected)
    }

    @Test
    fun selectOnTheActivePageClosesTheRailBackToTheCard() {
        setRailAndPage()
        walkDown(4)
        press("card-4-0", Key.DirectionLeft)

        pressOnFocused("Home", Key.DirectionCenter)

        assertCardFocused("card-4-0")
        assertEquals(emptyList<TvDestination>(), selected)
    }

    @Test
    fun backFromADeepGridCellReturnsToThatColumn() {
        setRailAndGrid()
        repeat(6) { line -> press("cell-$line-0", Key.DirectionDown) }
        press("cell-6-0", Key.DirectionRight, times = 3)
        compose.onNodeWithTag("cell-6-3").assertIsFocused()

        compose.runOnIdle { openRailLikeBack() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Home").assertIsFocused()
        pressOnFocused("Home", Key.DirectionRight)

        assertCardFocused("cell-6-3")
    }

    @Test
    fun theRailKeepsFocusAtItsEnds() {
        setRailAndPage()
        press("card-0-0", Key.DirectionLeft)
        compose.onNodeWithContentDescription("Home").assertIsFocused()

        pressOnFocused("Home", Key.DirectionLeft)
        compose.onNodeWithContentDescription("Home").assertIsFocused()

        compose.runOnIdle { navigationRequesters.getValue(TvDestination.SETTINGS).requestFocus() }
        compose.waitForIdle()
        pressOnFocused("Settings", Key.DirectionDown)
        compose.onNodeWithContentDescription("Settings").assertIsFocused()
    }
}
