package com.lamphaus.app.tv

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TvSeasonChipsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun selectingSeasonChipNotifiesSelection() {
        var selectedSeason = 1

        compose.setContent {
            LamphausTvTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    colors = SurfaceDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                ) {
                    TvSeasonChips(
                        seasonNumbers = listOf(1, 2),
                        selectedSeason = selectedSeason,
                        onSeasonSelected = { selectedSeason = it },
                    )
                }
            }
        }

        compose.onNodeWithText("Season 2").performClick()

        compose.runOnIdle { assertEquals(2, selectedSeason) }
    }
}
