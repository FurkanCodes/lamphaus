package com.lamphaus.app.tv

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

class TvRowFocusTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun TV_NAV_05_rowEndKeepsFocusAndEnteringLandsOnTheFirstItem() {
        val above = FocusRequester()
        compose.setContent {
            Column {
                // A wide control above sits over the row's later items.
                Row {
                    androidx.compose.foundation.layout.Box(Modifier.size(20.dp))
                    androidx.compose.foundation.layout.Box(
                        Modifier.size(400.dp, 40.dp).focusRequester(above).focusable().testTag("wide"),
                    )
                }
                val row = rememberTvRowFocus()
                LazyRow(Modifier.tvRowFocus(row)) {
                    itemsIndexed(List(3) { it }) { index, _ ->
                        androidx.compose.foundation.layout.Box(
                            Modifier.size(100.dp).tvRowItem(row, index).focusable().testTag("item$index"),
                        )
                    }
                }
            }
        }
        compose.runOnIdle { above.requestFocus() }
        compose.onNodeWithTag("wide").performKeyInput { keyDown(Key.DirectionDown); keyUp(Key.DirectionDown) }
        compose.onNodeWithTag("item0").assertIsFocused()

        repeat(4) { compose.onNodeWithTag("item0").performKeyInput { keyDown(Key.DirectionRight); keyUp(Key.DirectionRight) } }
        compose.onNodeWithTag("item2").assertIsFocused()

        compose.onNodeWithTag("item2").performKeyInput { keyDown(Key.DirectionUp); keyUp(Key.DirectionUp) }
        compose.onNodeWithTag("wide").assertIsFocused()
        compose.onNodeWithTag("wide").performKeyInput { keyDown(Key.DirectionDown); keyUp(Key.DirectionDown) }
        compose.onNodeWithTag("item2").assertIsFocused()
    }

    @Test
    fun TV_FOC_01_aVanishingLoadMoreHandsFocusToTheLastItem() {
        var hasMore by mutableStateOf(true)
        compose.setContent {
            val trailing = rememberTrailingActionFocus(showAction = hasMore, itemCount = 2)
            LazyRow {
                itemsIndexed(List(2) { it }) { index, _ ->
                    androidx.compose.foundation.layout.Box(
                        Modifier.size(100.dp).trailingItem(trailing, index).focusable().testTag("item$index"),
                    )
                }
                if (trailing.visible(hasMore)) {
                    item {
                        androidx.compose.foundation.layout.Box(
                            Modifier.size(100.dp).trailingAction(trailing).focusable().testTag("more"),
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("more").requestFocus()
        compose.onNodeWithTag("more").assertIsFocused()

        hasMore = false
        compose.waitForIdle()
        compose.onNodeWithTag("item1").assertIsFocused()
    }
}
