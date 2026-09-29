package com.lamphaus.app.player

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lamphaus.core.model.Episode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "Up next" prompt contract (PLY-AUTO-01, MOB-A11Y-01, MOB-TYP-03): the next
 * episode's code, title, and synopsis over a Yes/No question, the synopsis
 * left out under spoiler protection, and both answers reachable at 200% font.
 */
@RunWith(AndroidJUnit4::class)
class UpNextPromptTest {
    @get:Rule
    val compose = createComposeRule()

    private val episode = Episode(
        id = "series:1:2",
        title = "The Second Hour",
        season = 1,
        episode = 2,
        overview = "The crew returns to the observatory.",
        releasedAtEpochMillis = 1_000L,
    )

    private fun setContent(
        hideSynopsis: Boolean = false,
        fontScale: Float = 1f,
        onYes: () -> Unit = {},
        onNo: () -> Unit = {},
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = fontScale)) {
                UpNextPrompt(
                    episode = episode,
                    secondsLeft = 42,
                    blurArtwork = false,
                    hideSynopsis = hideSynopsis,
                    isTelevision = false,
                    onYes = onYes,
                    onNo = onNo,
                )
            }
        }
    }

    @Test
    fun showsNextEpisodeAndQuestion() {
        setContent()

        compose.onNodeWithText("Up next").assertIsDisplayed()
        compose.onNodeWithText("S1 · E2 • The Second Hour").assertIsDisplayed()
        compose.onNodeWithText("The crew returns to the observatory.").assertIsDisplayed()
        compose.onNodeWithText("Continue watching?").assertIsDisplayed()
        compose.onNodeWithText("Closes in 42 seconds").assertIsDisplayed()
    }

    @Test
    fun yesAndNoReportTheAnswer() {
        var yes = 0
        var no = 0
        setContent(onYes = { yes++ }, onNo = { no++ })

        compose.onNodeWithText("Yes").performClick()
        compose.onNodeWithText("No").performClick()

        assertEquals(1, yes)
        assertEquals(1, no)
    }

    @Test
    fun spoilerProtectionLeavesTheSynopsisOut() {
        setContent(hideSynopsis = true)

        compose.onNodeWithText("The crew returns to the observatory.").assertDoesNotExist()
        compose.onNodeWithText("S1 · E2 • The Second Hour").assertIsDisplayed()
    }

    @Test
    fun answersRemainReachableAtDoubleFontScale() {
        var yes = 0
        setContent(fontScale = 2f, onYes = { yes++ })

        compose.onNodeWithText("Yes").performScrollTo().performClick()

        assertEquals(1, yes)
    }
}
