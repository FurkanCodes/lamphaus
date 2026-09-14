package com.lamphaus.app.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MediaFocusRestoreTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun MOB_NAV_10_MOB_A11Y_05_duplicateTitleRestoresExactCardAnchor() {
        var pendingFocusKey by mutableStateOf<String?>(secondCardKey)
        var restoreCount by mutableIntStateOf(0)

        compose.setContent {
            Column {
                Box(
                    Modifier
                        .mediaFocusRestore(firstCardKey, pendingFocusKey) {
                            restoreCount++
                            pendingFocusKey = null
                        }
                        .testTag(firstCardKey)
                        .size(48.dp)
                        .focusable(),
                )
                Box(
                    Modifier
                        .mediaFocusRestore(secondCardKey, pendingFocusKey) {
                            restoreCount++
                            pendingFocusKey = null
                        }
                        .testTag(secondCardKey)
                        .size(48.dp)
                        .focusable(),
                )
            }
        }

        compose.waitForIdle()

        compose.onNodeWithTag(firstCardKey).assertIsNotFocused()
        compose.onNodeWithTag(secondCardKey).assertIsFocused()
        assertEquals(1, restoreCount)
    }

    private companion object {
        const val firstCardKey = "home:hero:series-1"
        const val secondCardKey = "home:catalog-1:series-1"
    }
}
