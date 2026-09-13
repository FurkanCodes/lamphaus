package com.lamphaus.app.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.view.KeyEvent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lamphaus.app.LamphausApplication
import com.lamphaus.app.R
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class TvUpdateFlowTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun QA07_permissionPromptKeepsSettingsReachableByRemote() {
        var opened = false
        show(UpdateUiState(phase = UpdatePhase.PermissionRequired), onSettings = { opened = true })
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithText(context.getString(R.string.update_allow_source_body)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.update_open_settings)).assertIsFocused().performKeyInput {
            keyDown(Key.DirectionCenter)
            keyUp(Key.DirectionCenter)
        }
        compose.runOnIdle { assertEquals(true, opened) }
    }

    @Test fun QA07_pausedDownloadRemainsVisibleAndCancellable() {
        var cancelled = false
        show(UpdateUiState(phase = UpdatePhase.Waiting), onCancel = { cancelled = true })
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithText(context.getString(R.string.update_waiting_network)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.update_allow_metered)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.update_cancel)).assertIsFocused().performKeyInput {
            keyDown(Key.DirectionCenter)
            keyUp(Key.DirectionCenter)
        }
        compose.runOnIdle { assertEquals(true, cancelled) }
    }

    @Test fun QA07_installPreparationRemainsVisible() {
        show(UpdateUiState(phase = UpdatePhase.Installing))
        compose.onNodeWithText(context.getString(R.string.update_installing)).assertIsDisplayed()
    }

    @Test fun QA05_installFailureShowsTheCorrectRecoveryMessage() {
        show(UpdateUiState(phase = UpdatePhase.Error, errorKind = UpdateError.INSTALL_FAILED))
        compose.onNodeWithText(context.getString(R.string.update_installer_blocked)).assertIsDisplayed()
    }

    @Test fun QA04_realPackageInstallerCallbackRetainsConfirmationUntilHostLaunch() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Permission is isolated to the debug test package, never the installed production app.
        instrumentation.uiAutomation.executeShellCommand(
            "appops set ${context.packageName} REQUEST_INSTALL_PACKAGES allow",
        ).close()
        val installer = UpdateInstaller(context)
        val prefs = UpdatePreferences(context)
        val coordinator = (context as LamphausApplication).container.updateCoordinator
        val apk = File(context.applicationInfo.sourceDir)
        val hash = MessageDigest.getInstance("SHA-256").digest(apk.readBytes())
            .joinToString("") { "%02x".format(it) }
        val session = installer.createSession(apk.length())
        try {
            prefs.installerSessionId = session.sessionId
            assertEquals(true, installer.writeSession(session.sessionId, apk, hash))
            installer.commit(session.sessionId, 999)
            compose.waitUntil(timeoutMillis = 30_000) {
                coordinator.state.value.installConfirmation != null || coordinator.state.value.phase == UpdatePhase.Error
            }
            assertEquals(UpdatePhase.Installing, coordinator.state.value.phase)
            assertNotNull(coordinator.state.value.installConfirmation)
            compose.setContent {
                val model: UpdateViewModel = viewModel(factory = UpdateViewModel.factory(coordinator))
                val state by model.state.collectAsStateWithLifecycle()
                UpdateInstallerHost(model, state)
            }
            compose.waitUntil(timeoutMillis = 15_000) {
                instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()
                    ?.contains("packageinstaller") == true
            }
            assertNull(coordinator.state.value.installConfirmation)
            // Cancel the actual system confirmation; never replace the running test package.
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            compose.waitUntil(timeoutMillis = 15_000) {
                coordinator.state.value.phase == UpdatePhase.Error
            }
            assertEquals(UpdateError.INSTALL_FAILED, coordinator.state.value.errorKind)
        } finally {
            installer.abandon(session.sessionId)
            instrumentation.runOnMainSync { coordinator.cancelDownload() }
        }
    }

    @Test fun QA04_missingConfirmationAndStaleCallbacksCannotReportSuccess() {
        val coordinator = (context as LamphausApplication).container.updateCoordinator
        val prefs = UpdatePreferences(context)
        prefs.installerSessionId = 123456
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            coordinator.onInstallerStatus(123455, PackageInstaller.STATUS_PENDING_USER_ACTION, Intent())
            assertNull(coordinator.state.value.installConfirmation)
            coordinator.onInstallerStatus(123456, PackageInstaller.STATUS_PENDING_USER_ACTION, null)
            assertEquals(UpdateError.INSTALL_FAILED, coordinator.state.value.errorKind)
            coordinator.cancelDownload()
        }
    }

    private fun show(state: UpdateUiState, onSettings: () -> Unit = {}, onCancel: () -> Unit = {}) {
        compose.setContent {
            MaterialTheme {
                TvUpdateDialog(
                    state = state, installedVersion = "test", onUpdate = {}, onLater = {}, onInstall = {},
                    onCancel = onCancel, onRetry = {}, onOpenSettings = onSettings,
                    onAllowMetered = {}, onDismissError = {}, onFocusRestored = {},
                )
            }
        }
    }
}
