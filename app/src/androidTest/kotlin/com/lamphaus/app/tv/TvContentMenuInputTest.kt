package com.lamphaus.app.tv

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import com.lamphaus.app.R
import com.lamphaus.app.ui.ContentMenuAction
import com.lamphaus.app.ui.ContentMenuState
import com.lamphaus.app.ui.ContentMenuTarget
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TvContentMenuInputTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun TV_FND_02_TV_FOC_01_TV_NAV_02_centerHoldReleaseOnlyFocusesThenFreshCenterSelects() {
        val fixture = setContentMenuFixture()
        val openingDownTime = SystemClock.uptimeMillis()

        injectKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, openingDownTime)
        compose.waitForIdle()
        assertEquals(1, fixture.nativeEventCount)
        assertEquals(1, fixture.centerKeyEvents)
        awaitMenuFocus(fixture)
        injectKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, openingDownTime, repeatCount = 1)
        injectKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, openingDownTime, repeatCount = 2)
        injectKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, openingDownTime)

        compose.onNodeWithText(viewDetailsLabel).assertIsFocused()
        assertEquals(0, fixture.cardClicks)
        assertEquals(0, fixture.viewDetailsClicks)

        pressKey(KeyEvent.KEYCODE_DPAD_CENTER)

        assertEquals(0, fixture.cardClicks)
        assertEquals(1, fixture.viewDetailsClicks)
    }

    @Test
    fun TV_FND_02_TV_FOC_01_TV_NAV_02_canceledOpeningReleaseCannotArmLaterRepeats() {
        val fixture = setContentMenuFixture()
        val openingDownTime = SystemClock.uptimeMillis()

        injectKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, openingDownTime)
        awaitMenuFocus(fixture)
        injectKey(
            action = KeyEvent.ACTION_UP,
            keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
            downTime = openingDownTime,
            flags = KeyEvent.FLAG_CANCELED,
        )
        injectKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, openingDownTime, repeatCount = 1)
        injectKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, openingDownTime)

        compose.onNodeWithText(viewDetailsLabel).assertIsFocused()
        assertEquals(0, fixture.cardClicks)
        assertEquals(0, fixture.viewDetailsClicks)

        pressKey(KeyEvent.KEYCODE_ENTER)

        assertEquals(0, fixture.cardClicks)
        assertEquals(1, fixture.viewDetailsClicks)
    }

    @Test
    fun TV_FND_02_TV_FOC_01_TV_NAV_02_menuReleaseBackAndSubsequentCardInteractionsStayDistinct() {
        val fixture = setContentMenuFixture()
        val openingDownTime = SystemClock.uptimeMillis()

        injectKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU, openingDownTime)
        awaitMenuFocus(fixture)
        injectKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU, openingDownTime, repeatCount = 1)
        injectKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU, openingDownTime)

        compose.onNodeWithText(viewDetailsLabel).assertIsFocused()
        assertEquals(0, fixture.cardClicks)
        assertEquals(0, fixture.viewDetailsClicks)

        pressKey(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.onNodeWithTag(cardTag).assertIsFocused()

        pressKey(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(1, fixture.cardClicks)

        val nextOpeningDownTime = SystemClock.uptimeMillis()
        injectKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, nextOpeningDownTime)
        awaitMenuFocus(fixture)

        compose.onNodeWithText(viewDetailsLabel).assertIsDisplayed()
        assertEquals(1, fixture.cardClicks)
        assertEquals(0, fixture.viewDetailsClicks)
    }

    private fun setContentMenuFixture(): ContentMenuFixture {
        val fixture = ContentMenuFixture()
        compose.setContent {
            TvContentMenuFixture(fixture)
        }
        compose.waitForIdle()
        compose.onNodeWithTag(cardTag).assertIsFocused()
        return fixture
    }

    private fun awaitMenuFocus(fixture: ContentMenuFixture) {
        compose.waitUntil(timeoutMillis = menuOpenTimeoutMillis) {
            fixture.menuVisible
        }
        compose.waitForIdle()
        compose.onNodeWithText(viewDetailsLabel).assertIsFocused()
    }

    private fun pressKey(keyCode: Int) {
        val downTime = SystemClock.uptimeMillis()
        injectKey(KeyEvent.ACTION_DOWN, keyCode, downTime)
        injectKey(KeyEvent.ACTION_UP, keyCode, downTime)
        compose.waitForIdle()
    }

    private fun injectKey(
        action: Int,
        keyCode: Int,
        downTime: Long,
        repeatCount: Int = 0,
        flags: Int = 0,
    ) {
        val event = KeyEvent(
            downTime,
            SystemClock.uptimeMillis(),
            action,
            keyCode,
            repeatCount,
            0,
            KeyCharacterMap.VIRTUAL_KEYBOARD,
            0,
            flags,
            InputDevice.SOURCE_DPAD,
        )
        check(InstrumentationRegistry.getInstrumentation().uiAutomation.injectInputEvent(event, true))
    }

    @Composable
    private fun TvContentMenuFixture(fixture: ContentMenuFixture) {
        var menuVisible by remember { mutableStateOf(false) }
        var returnFocus by remember { mutableStateOf<FocusRequester?>(null) }
        val initialFocus = remember { FocusRequester() }
        val target = remember { ContentMenuTarget(media = media) }

        LaunchedEffect(Unit) {
            initialFocus.requestFocus()
        }
        LaunchedEffect(menuVisible) {
            if (!menuVisible) {
                withFrameNanos { }
                returnFocus?.requestFocus()
            }
        }

        LamphausTvTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                colors = SurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ),
            ) {
                Box(Modifier.fillMaxSize()) {
                    TvMediaCard(
                        media = media,
                        onClick = { fixture.cardClicks++ },
                        modifier = Modifier
                            .focusRequester(initialFocus)
                            .testTag(cardTag)
                            .onPreviewKeyEvent {
                                fixture.nativeEventCount++
                                if (it.key == Key.DirectionCenter) fixture.centerKeyEvents++
                                false
                            },
                        onMenuRequest = { focusRequester ->
                            returnFocus = focusRequester
                            fixture.menuVisible = true
                            menuVisible = true
                        },
                    )
                    if (menuVisible) {
                        TvContentMenuDialog(
                            menu = ContentMenuState(target = target),
                            inLibrary = false,
                            onOpeningKeyReleased = {},
                            onDismiss = {
                                fixture.menuVisible = false
                                menuVisible = false
                            },
                            onAction = { action ->
                                if (action == ContentMenuAction.ViewDetails) fixture.viewDetailsClicks++
                            },
                        )
                    }
                }
            }
        }
    }

    private class ContentMenuFixture {
        var nativeEventCount by mutableIntStateOf(0)
        var centerKeyEvents by mutableIntStateOf(0)
        var cardClicks by mutableIntStateOf(0)
        var viewDetailsClicks by mutableIntStateOf(0)
        var menuVisible by mutableStateOf(false)
    }

    private companion object {
        const val cardTag = "content-card"
        const val menuOpenTimeoutMillis = 3_000L
        val media = MediaPreview("series-1", MediaType.SERIES, "series", "Series")
        val viewDetailsLabel: String
            get() = InstrumentationRegistry.getInstrumentation().targetContext.getString(
                R.string.content_menu_view_details,
            )
    }
}
