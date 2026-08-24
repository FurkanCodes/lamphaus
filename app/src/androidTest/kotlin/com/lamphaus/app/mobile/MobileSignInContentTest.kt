package com.lamphaus.app.mobile

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lamphaus.core.data.preferences.ThemePreference
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MobileSignInContentTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun darkSignInKeepsPrimaryActionsAvailable() {
        compose.setContent {
            LamphausMobileTheme(preference = ThemePreference.DARK, dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ) {
                    MobileSignInScreen(
                        cloudConfigured = false,
                        onGoogleSignIn = {},
                        onEmailLink = {},
                        onDevelopmentSession = {},
                    )
                }
            }
        }

        compose.onNodeWithText("Continue with Google")
            .assertIsDisplayed()
            .assertHasClickAction()
        compose.onNodeWithText("Email address").assertIsDisplayed()
        compose.onNodeWithText("Open development session")
            .assertIsDisplayed()
            .assertHasClickAction()
    }
}
