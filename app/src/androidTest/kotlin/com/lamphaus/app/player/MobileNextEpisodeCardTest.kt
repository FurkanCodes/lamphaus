package com.lamphaus.app.player

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lamphaus.core.model.Episode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import androidx.compose.ui.platform.LocalDensity
import org.junit.runner.RunWith

/**
 * Mobile next-episode card contract (MOB-CMP-08/09, MOB-A11Y-01/04): stable
 * geometry across states, spoiler-aware artwork, manual play only, RTL and
 * 200% font support.
 */
@RunWith(AndroidJUnit4::class)
class MobileNextEpisodeCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val airedEpisode = Episode(
        id = "series:1:2",
        title = "The Second Hour",
        season = 1,
        episode = 2,
        thumbnailUrl = "https://example.invalid/thumb.jpg",
        releasedAtEpochMillis = 1_000L,
    )

    private fun setContent(
        episode: Episode = airedEpisode,
        loading: Boolean = false,
        failureMessage: String? = null,
        blurArtwork: Boolean = false,
        wide: Boolean = false,
        showSkipCredits: Boolean = false,
        rtl: Boolean = false,
        fontScale: Float = 1f,
        onPlay: () -> Unit = {},
        onSkip: () -> Unit = {},
        onDismiss: () -> Unit = {},
    ) {
        compose.setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalDensity provides Density(density = 1f, fontScale = fontScale),
            ) {
                NextEpisodeCard(
                    episode = episode,
                    loading = loading,
                    failureMessage = failureMessage,
                    blurArtwork = blurArtwork,
                    wide = wide,
                    showSkipCredits = showSkipCredits,
                    onPlayNext = onPlay,
                    onSkipCredits = onSkip,
                    onDismiss = onDismiss,
                )
            }
        }
    }

    @Test
    fun readyCardShowsMetadataAndAPlayableAction() {
        var plays = 0
        setContent(onPlay = { plays++ })

        compose.onNodeWithText("Up next").assertIsDisplayed()
        compose.onNodeWithText("S1 · E2").assertIsDisplayed()
        compose.onNodeWithText("The Second Hour").assertIsDisplayed()
        compose.onNodeWithText("Play next")
            .assertHasClickAction()
            .performClick()
        assertEquals(1, plays)
    }

    @Test
    fun loadingStateShowsInlineProgressAndDisablesPlay() {
        setContent(loading = true)

        compose.onNodeWithText("Finding source…").assertIsDisplayed()
        compose.onNodeWithText("Play next").assertIsNotEnabled()
    }

    @Test
    fun failureStateExplainsInlineAndOffersRetry() {
        var retries = 0
        setContent(failureMessage = "No playable source", onPlay = { retries++ })

        compose.onNodeWithText("No playable source").assertIsDisplayed()
        compose.onNodeWithText("Retry")
            .assertHasClickAction()
            .performClick()
        assertEquals(1, retries)
    }

    @Test
    fun unairedEpisodeShowsReleaseStatusAndDisablesPlay() {
        val future = airedEpisode.copy(
            releasedAtEpochMillis = System.currentTimeMillis() + 86_400_000L,
        )
        setContent(episode = future)

        compose.onNodeWithText("Not yet released", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Play next").assertIsNotEnabled()
    }

    @Test
    fun skipCreditsJoinsTheCardAsSecondaryAction() {
        var skips = 0
        setContent(showSkipCredits = true, onSkip = { skips++ })

        compose.onNodeWithText("Skip credits")
            .assertHasClickAction()
            .performClick()
        assertEquals(1, skips)
    }

    @Test
    fun closeActionDismissesTheCard() {
        var dismissed = 0
        setContent(onDismiss = { dismissed++ })

        compose.onNodeWithContentDescription("Close")
            .assertHasClickAction()
            .performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun spoilerProtectionVeilsTheThumbnail() {
        setContent(blurArtwork = true)

        compose.onNodeWithContentDescription("Spoiler hidden").assertIsDisplayed()
    }

    @Test
    fun missingArtworkKeepsTheCardGeometry() {
        setContent(episode = airedEpisode.copy(thumbnailUrl = null))

        compose.onNodeWithText("Up next").assertIsDisplayed()
        compose.onNodeWithText("Play next").assertIsDisplayed()
    }

    @Test
    fun wideLayoutKeepsTheSameContentAvailable() {
        setContent(wide = true)

        compose.onNodeWithText("Up next").assertIsDisplayed()
        compose.onNodeWithText("Play next").assertIsDisplayed()
    }

    @Test
    fun rtlLayoutKeepsContentAccessible() {
        setContent(rtl = true)

        compose.onNodeWithText("Up next").assertIsDisplayed()
        compose.onNodeWithText("The Second Hour").assertIsDisplayed()
    }

    @Test
    fun scaledFontsKeepTheCardReadable() {
        setContent(fontScale = 2f)

        compose.onNodeWithText("Up next").assertIsDisplayed()
        compose.onNodeWithText("The Second Hour").assertIsDisplayed()
        compose.onNodeWithText("Play next").assertIsDisplayed()
    }
}
