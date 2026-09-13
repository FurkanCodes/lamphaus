package com.lamphaus.app.update

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState

/** Only a visible, resumed host launches Android confirmation (SHR-ARC-09/10, QA-04). */
@Composable
fun UpdateInstallerHost(viewModel: UpdateViewModel, state: UpdateUiState) {
    val activity = LocalActivity.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    LaunchedEffect(lifecycleState, state.installConfirmation) {
        if (lifecycleState != Lifecycle.State.RESUMED || activity == null) return@LaunchedEffect
        viewModel.onHostResumed()
        state.installConfirmation?.let { confirmation ->
            try {
                activity.startActivity(confirmation)
                viewModel.onConfirmationLaunched()
            } catch (_: Exception) {
                viewModel.onInstallerFailure()
            }
        }
    }
}

/** Re-check permission when Settings returns, even within the update-check cooldown. */
@Composable
fun rememberUpdatePermissionLauncher(viewModel: UpdateViewModel): () -> Unit {
    val activity = LocalActivity.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onHostResumed()
    }
    return {
        if (activity != null) {
            try {
                launcher.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${activity.packageName}".toUri()))
            } catch (_: Exception) {
                // Some TV firmware exposes only the general security page.
                try {
                    launcher.launch(Intent(Settings.ACTION_SECURITY_SETTINGS))
                } catch (_: Exception) {
                    viewModel.onInstallerFailure()
                }
            }
        }
    }
}
