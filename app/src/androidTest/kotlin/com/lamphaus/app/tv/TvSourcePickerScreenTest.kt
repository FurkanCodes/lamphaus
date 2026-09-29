package com.lamphaus.app.tv

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import com.lamphaus.app.ui.SourcePickerState
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.MediaType
import com.lamphaus.core.model.StreamCandidate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * TV source list (TV-FOC-01, TV-CNT-02, SHR-PROD-12): a counted heading,
 * add-on filters with their counts, quality tiles, and the add-on named on
 * each card only while every add-on is listed.
 */
class TvSourcePickerScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val media = MediaPreview(
        id = "tt1",
        type = MediaType.MOVIE,
        rawType = "movie",
        name = "A Measure of Tide",
    )

    private val sources = listOf(
        StreamCandidate(
            providerId = "alpha",
            name = "Alpha\n4K DV",
            description = "A.Measure.of.Tide.2160p.WEB-DL.DV.HDR.HEVC\n💾 18.2 GB  👤 412",
            infoHash = "a".repeat(40),
        ),
        StreamCandidate(
            providerId = "alpha",
            name = "Alpha\n1080p",
            description = "A.Measure.of.Tide.1080p.BluRay.x264\n💾 9.1 GB  👤 97",
            infoHash = "b".repeat(40),
        ),
        StreamCandidate(
            providerId = "beta",
            name = "Beta 720p",
            url = "https://example.invalid/tide-720.mkv",
            videoSize = 1_900_000_000L,
        ),
    )

    private fun setScreen(onSource: (StreamCandidate) -> Unit = {}) {
        compose.setContent {
            var picker by remember {
                mutableStateOf(
                    SourcePickerState(
                        media = media,
                        sources = sources,
                        providerLabels = mapOf("alpha" to "Alpha", "beta" to "Beta"),
                        loading = false,
                    ),
                )
            }
            LamphausTvTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    colors = SurfaceDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                ) {
                    TvSourcePickerScreen(
                        picker = picker,
                        onProvider = { picker = picker.selectProvider(it) },
                        onSource = onSource,
                    )
                }
            }
        }
    }

    @Test
    fun headingAndFiltersCountTheSources() {
        setScreen()

        compose.onNodeWithText("Choose a source").assertIsDisplayed()
        compose.onNodeWithText("3 sources").assertIsDisplayed()
        compose.onNodeWithText("2").assertIsDisplayed()
        compose.onNodeWithText("1").assertIsDisplayed()
    }

    @Test
    fun cardsLeadWithQualityAndDropTheRepeatedAddOnName() {
        setScreen()

        compose.onNodeWithText("4K").assertIsDisplayed()
        compose.onNodeWithText("4K DV").assertIsDisplayed()
        compose.onNodeWithText("Alpha  ·  4K DV").assertDoesNotExist()
    }

    @Test
    fun addOnIsNamedOnCardsOnlyWhileAllAreListed() {
        setScreen()
        compose.onNodeWithText("BETA").assertIsDisplayed()

        compose.onNodeWithText("Beta").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("BETA").assertDoesNotExist()
        compose.onNodeWithText("720p").assertIsDisplayed()
    }

    @Test
    fun selectingACardPlaysThatSource() {
        var chosen: StreamCandidate? = null
        setScreen(onSource = { chosen = it })

        compose.onNodeWithText("A.Measure.of.Tide.1080p.BluRay.x264", substring = true).performClick()

        assertEquals(sources[1], chosen)
    }
}
