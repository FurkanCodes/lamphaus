package com.lamphaus.app.tv

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isEditable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TvNavigationBehaviorTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun destinationBoundsKeepSearchAfterLibrary() {
        assertEquals(
            listOf(TvDestination.HOME, TvDestination.DISCOVER, TvDestination.LIBRARY, TvDestination.SEARCH),
            TvDestination.entries.filterNot { it == TvDestination.SETTINGS },
        )
    }

    @Test
    fun contentExitReturnsToTheActiveDestinationForEveryTab() {
        var activeDestination by mutableStateOf(TvDestination.HOME)
        val navigationRequesters = TvDestination.entries.associateWith { FocusRequester() }
        val contentRequester = FocusRequester()

        compose.setContent {
            val destination = activeDestination
            LamphausTvTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    colors = SurfaceDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .width(180.dp)
                                .height(48.dp)
                                .focusRequester(navigationRequesters.getValue(destination))
                                .focusProperties { down = contentRequester }
                                .focusable()
                                .testTag("navigation"),
                        )
                        Column(
                            modifier = Modifier
                                .tvContentFocusBoundary(navigationRequesters.getValue(destination))
                                .padding(top = 24.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(180.dp)
                                    .height(48.dp)
                                    .focusRequester(contentRequester)
                                    .focusable()
                                    .testTag("first-content"),
                            )
                            Box(
                                modifier = Modifier
                                    .width(180.dp)
                                    .height(48.dp)
                                    .focusable()
                                    .testTag("second-content"),
                            )
                        }
                    }
                    LaunchedEffect(destination) {
                        navigationRequesters.getValue(destination).requestFocus()
                    }
                }
            }
        }

        TvDestination.entries.forEach { destination ->
            activeDestination = destination
            compose.waitForIdle()
            compose.onNodeWithTag("navigation").assertIsFocused()
            compose.onNodeWithTag("navigation").performKeyInput {
                keyDown(Key.DirectionDown)
                keyUp(Key.DirectionDown)
            }
            compose.onNodeWithTag("first-content").assertIsFocused()
            compose.onNodeWithTag("first-content").performKeyInput {
                keyDown(Key.DirectionDown)
                keyUp(Key.DirectionDown)
                keyDown(Key.DirectionUp)
                keyUp(Key.DirectionUp)
            }
            compose.onNodeWithTag("first-content").assertIsFocused()
            compose.onNodeWithTag("first-content").performKeyInput {
                keyDown(Key.DirectionUp)
                keyUp(Key.DirectionUp)
            }
            compose.onNodeWithTag("navigation").assertIsFocused()
        }
    }

    @Test
    fun selectEntersEditModeAndImeActionRestoresBrowseFocus() {
        val fieldRequester = FocusRequester()

        compose.setContent {
            LamphausTvTheme {
                Surface {
                    TvEditableTextField(
                        value = "",
                        onValueChange = {},
                        label = "Search field",
                        placeholder = "Search",
                        contentPadding = PaddingValues(16.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier
                            .focusRequester(fieldRequester)
                            .testTag("editable-field"),
                    )
                }
                LaunchedEffect(Unit) {
                    fieldRequester.requestFocus()
                }
            }
        }

        val field = compose.onNodeWithTag("editable-field")
        compose.waitForIdle()
        field.assertIsFocused().assert(isEditable().not())
        field.performKeyInput {
            keyDown(Key.DirectionCenter)
            keyUp(Key.DirectionCenter)
        }
        compose.waitForIdle()
        field.assertIsFocused().assert(isEditable())

        field.performImeAction()
        compose.waitForIdle()
        field.assertIsFocused().assert(isEditable().not())
    }

    @Test
    fun selectEntersEditModeAndBackRestoresBrowseFocus() {
        val fieldRequester = FocusRequester()

        compose.setContent {
            LamphausTvTheme {
                Surface {
                    TvEditableTextField(
                        value = "",
                        onValueChange = {},
                        label = "Add-on address",
                        placeholder = "HTTPS manifest address",
                        contentPadding = PaddingValues(16.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier
                            .focusRequester(fieldRequester)
                            .testTag("editable-field"),
                    )
                }
                LaunchedEffect(Unit) {
                    fieldRequester.requestFocus()
                }
            }
        }

        val field = compose.onNodeWithTag("editable-field")
        compose.waitForIdle()
        field.assertIsFocused().assert(isEditable().not())
        field.performKeyInput {
            keyDown(Key.DirectionCenter)
            keyUp(Key.DirectionCenter)
        }
        compose.waitForIdle()
        field.assertIsFocused().assert(isEditable())

        pressBack()
        compose.waitUntil(3_000) {
            runCatching {
                field.assert(isEditable().not())
                true
            }.getOrDefault(false)
        }
        field.assertIsFocused().assert(isEditable().not())
    }

    @Test
    fun browseDownCallbackCoversSearchAndInvalidAddonPaths() {
        val searchRequester = FocusRequester()
        val addonRequester = FocusRequester()
        var searchDownCount by mutableIntStateOf(0)
        var addonDownCount by mutableIntStateOf(0)

        compose.setContent {
            LamphausTvTheme {
                Surface {
                    Column {
                        TvEditableTextField(
                            value = "",
                            onValueChange = {},
                            label = "Search field",
                            placeholder = "Search",
                            contentPadding = PaddingValues(16.dp),
                            onNavigateDown = {
                                searchDownCount++
                                true
                            },
                            modifier = Modifier
                                .focusRequester(searchRequester)
                                .testTag("search-field"),
                        )
                        TvEditableTextField(
                            value = "not-a-valid-address",
                            onValueChange = {},
                            label = "Add-on address",
                            placeholder = "HTTPS manifest address",
                            contentPadding = PaddingValues(16.dp),
                            onNavigateDown = {
                                addonDownCount++
                                true
                            },
                            modifier = Modifier
                                .focusRequester(addonRequester)
                                .testTag("addon-field"),
                        )
                    }
                }
                LaunchedEffect(Unit) {
                    searchRequester.requestFocus()
                }
            }
        }

        compose.waitForIdle()
        compose.onNodeWithTag("search-field").performKeyInput {
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
        }
        assertEquals(1, searchDownCount)

        addonRequester.requestFocus()
        compose.waitForIdle()
        compose.onNodeWithTag("addon-field").performKeyInput {
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
        }
        assertEquals(1, addonDownCount)
    }
}
