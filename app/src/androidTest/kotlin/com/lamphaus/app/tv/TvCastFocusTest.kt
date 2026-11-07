package com.lamphaus.app.tv

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import com.lamphaus.app.ui.PersonPageState
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.MediaType
import com.lamphaus.core.model.PersonCredit
import com.lamphaus.core.model.PersonFilmography
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TvCastFocusTest {
    @get:Rule
    val compose = createComposeRule()

    private val cast = listOf(
        PersonCredit(name = "Ada Lovelace", role = "Lead"),
        PersonCredit(personId = "2", name = "Grace Hopper", role = "Support"),
        PersonCredit(personId = "2", name = "Grace Hopper", role = "Support"),
    )
    private val titles = List(3) { MediaPreview(id = "tt$it", type = MediaType.MOVIE, rawType = "movie", name = "Title $it") }

    private fun page(loading: Boolean = false, failed: Boolean = false, credits: List<MediaPreview>? = null) = PersonPageState(
        personId = "2",
        name = "Grace Hopper",
        origin = titles.first(),
        filmography = credits?.let { PersonFilmography(personId = "2", name = "Grace Hopper", credits = it) },
        loading = loading,
        failed = failed,
    )

    @Test
    fun TV_CNT_08_castCardsTakeFocusAndSelectOpensThatPerson() {
        val opened = mutableListOf<PersonCredit>()
        val above = FocusRequester()
        compose.setContent {
            LamphausTvTheme {
                Column {
                    Box(Modifier.size(100.dp).focusRequester(above).focusable().testTag("above"))
                    TvPeopleRail(cast, onOpen = { opened += it })
                }
            }
        }
        compose.runOnIdle { above.requestFocus() }
        compose.onNodeWithTag("above").performKeyInput { pressKey(Key.DirectionDown) }
        val first = compose.onNode(hasText("Ada Lovelace"))
        first.assertIsFocused()

        first.performKeyInput { pressKey(Key.DirectionRight) }
        val second = compose.onNode(hasText("Grace Hopper") and isFocused())
        second.performKeyInput { pressKey(Key.DirectionCenter) }

        compose.waitForIdle()
        assertEquals(listOf("Grace Hopper"), opened.map(PersonCredit::name))
    }

    @Test
    fun TV_NAV_02_returningToACastCardFocusesIt() {
        var restored = false
        compose.setContent {
            LamphausTvTheme {
                TvPeopleRail(cast, onOpen = {}, restoreFocusKey = cast[1].tvFocusKey(), onFocusRestored = { restored = true })
            }
        }

        compose.waitForIdle()

        compose.onNode(hasText("Grace Hopper") and isFocused()).assertIsDisplayed()
        assertEquals(true, restored)
    }

    @Test
    fun TV_NAV_02_returningToASimilarCardFocusesIt() {
        var restored = false
        compose.setContent {
            LamphausTvTheme {
                TvSimilarRail(titles, onOpenMedia = {}, restoreFocusKey = titles[2].stableKey, onFocusRestored = { restored = true })
            }
        }

        compose.waitForIdle()

        compose.onNode(hasText("Title 2") and isFocused()).assertIsDisplayed()
        assertEquals(true, restored)
    }

    @Test
    fun TV_CNT_08_aPersonPageThatFailedKeepsFocusOnRetry() {
        var retries = 0
        compose.setContent {
            LamphausTvTheme {
                TvPersonPage(page(failed = true), onRetry = { retries++ }, onMedia = {}, onFocused = {}, restoreMediaKey = null, onFocusRestored = {})
            }
        }

        compose.waitForIdle()
        val retry = compose.onNodeWithText("Retry")
        retry.assertIsFocused()
        retry.performKeyInput { pressKey(Key.DirectionCenter) }

        compose.waitForIdle()
        assertEquals(1, retries)
    }

    @Test
    fun TV_CNT_08_aPersonPageShowsTheirTitlesAndFocusesTheFirst() {
        val opened = mutableListOf<MediaPreview>()
        compose.setContent {
            LamphausTvTheme {
                TvPersonPage(page(credits = titles), onRetry = {}, onMedia = { opened += it }, onFocused = {}, restoreMediaKey = null, onFocusRestored = {})
            }
        }

        compose.waitForIdle()
        val first = compose.onNode(hasText("Title 0"))
        first.assertIsFocused()
        first.performKeyInput { pressKey(Key.DirectionCenter) }

        compose.waitForIdle()
        assertEquals(listOf(titles.first()), opened)
    }

    @Test
    fun TV_CNT_08_aPersonWithNoTitlesSaysSo() {
        compose.setContent {
            LamphausTvTheme {
                TvPersonPage(page(credits = emptyList()), onRetry = {}, onMedia = {}, onFocused = {}, restoreMediaKey = null, onFocusRestored = {})
            }
        }

        compose.onNodeWithText("No titles to show for this person.").assertIsDisplayed()
    }
}
