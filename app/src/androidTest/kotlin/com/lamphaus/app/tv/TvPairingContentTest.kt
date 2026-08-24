package com.lamphaus.app.tv

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import org.junit.Rule
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvPairingContentTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun pairingStartsWithOneClearRemoteAction() {
        val packageManager = InstrumentationRegistry.getInstrumentation().targetContext.packageManager
        assumeTrue(packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK))

        compose.setContent {
            LamphausTvTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    colors = SurfaceDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                ) {
                    TvPairingContent(
                        shortCode = "ABC123",
                        qrPayload = null,
                        showDevelopmentAction = true,
                        onRefresh = {},
                        onDevelopmentSession = {},
                    )
                }
            }
        }

        compose.onNodeWithTag(TvTestTags.PairingRefresh)
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertIsFocused()
        compose.onNodeWithTag(TvTestTags.PairingDevelopment)
            .assertIsDisplayed()
            .assertHasClickAction()
        compose.onNodeWithText("ABC  123").assertIsDisplayed()
    }
}
