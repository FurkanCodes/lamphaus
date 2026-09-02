package com.lamphaus.app.mobile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val SIGNED_IN_SURFACE_TAG = "startup-signed-in"

/**
 * The startup surface contract: during startup only the branded loading
 * screen is rendered (navigation absent); once startup completes, the
 * signed-in app with its navigation appears and the loading screen is gone.
 */
@RunWith(AndroidJUnit4::class)
class MobileStartupSurfaceTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun navigationIsAbsentDuringStartupAndAppearsWithHomeAfterCompletion() {
        var phase by mutableStateOf(MobileStartupPhase.Startup)
        compose.setContent {
            LamphausMobileTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ) {
                    MobileStartupSurface(
                        phase = phase,
                        reducedMotion = true,
                        signIn = {
                            MobileSignInScreen(
                                cloudConfigured = false,
                                onGoogleSignIn = {},
                                onEmailLink = {},
                                onDevelopmentSession = {},
                            )
                        },
                        signedIn = {
                            Box(Modifier.fillMaxSize().testTag(SIGNED_IN_SURFACE_TAG)) {
                                MobileNavBar(
                                    destination = MobileDestination.HOME,
                                    onProfile = {},
                                    onSelect = {},
                                )
                            }
                        },
                    )
                }
            }
        }

        // Startup: only the branded loading surface; no navigation anywhere.
        compose.onNodeWithText("Lighting your library…").assertIsDisplayed()
        compose.onNodeWithTag(SIGNED_IN_SURFACE_TAG).assertDoesNotExist()
        compose.onNodeWithText("Home").assertDoesNotExist()

        // Completed signed-in startup: navigation appears with Home content.
        phase = MobileStartupPhase.SignedIn
        compose.waitForIdle()
        compose.onNodeWithTag(SIGNED_IN_SURFACE_TAG).assertIsDisplayed()
        compose.onNodeWithText("Home").assertIsDisplayed()
        compose.onNodeWithText("Lighting your library…").assertDoesNotExist()
    }

    @Test
    fun signedOutStartupShowsSignInWithoutNavigation() {
        var phase by mutableStateOf(MobileStartupPhase.SignedOut)
        compose.setContent {
            LamphausMobileTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ) {
                    MobileStartupSurface(
                        phase = phase,
                        reducedMotion = true,
                        signIn = {
                            MobileSignInScreen(
                                cloudConfigured = false,
                                onGoogleSignIn = {},
                                onEmailLink = {},
                                onDevelopmentSession = {},
                            )
                        },
                        signedIn = {
                            Box(Modifier.fillMaxSize().testTag(SIGNED_IN_SURFACE_TAG)) {
                                MobileNavBar(
                                    destination = MobileDestination.HOME,
                                    onProfile = {},
                                    onSelect = {},
                                )
                            }
                        },
                    )
                }
            }
        }

        compose.onNodeWithText("Continue with Google").assertIsDisplayed()
        compose.onNodeWithTag(SIGNED_IN_SURFACE_TAG).assertDoesNotExist()
        compose.onNodeWithText("Lighting your library…").assertDoesNotExist()
    }
}
