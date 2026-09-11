package com.lamphaus.app.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.lamphaus.app.R
/**
 * Mobile update presentation (plan §4, MOB-CMP-03/08/09, MOB-LAY-01/06,
 * MOB-A11Y-01–06). Material 3 bottom sheet on compact windows; bounded
 * supporting surface on larger windows. Changelog shown before downloading
 * with Update/Later; every action acknowledges immediately.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatePrompt(
    state: UpdateUiState,
    installedVersion: String,
    widthSizeClass: WindowWidthSizeClass,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    onWebsite: () -> Unit,
    onDismissError: () -> Unit,
) {
    val release = state.release
    when (state.phase) {
        UpdatePhase.Available if release != null && !state.silent && !state.autoDeferredByContext -> {
            val content: @Composable () -> Unit = {
                UpdateAvailableContent(
                    release = release,
                    installedVersion = installedVersion,
                    onUpdate = onUpdate,
                    onLater = onLater,
                )
            }
            if (widthSizeClass == WindowWidthSizeClass.Expanded) {
                UpdateSupportingPane(onDismiss = onLater, content = content)
            } else {
                ModalBottomSheet(
                    onDismissRequest = onLater,
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                ) { content() }
            }
        }
        UpdatePhase.Downloading, UpdatePhase.Waiting, UpdatePhase.Verifying -> {
            val content: @Composable () -> Unit = {
                UpdateProgressContent(state = state, onCancel = onCancel)
            }
            if (widthSizeClass == WindowWidthSizeClass.Expanded) {
                UpdateSupportingPane(onDismiss = onCancel, content = content)
            } else {
                ModalBottomSheet(onDismissRequest = onCancel) { content() }
            }
        }
        UpdatePhase.Ready -> {
            val content: @Composable () -> Unit = {
                UpdateReadyContent(onInstall = onInstall, onCancel = onCancel)
            }
            if (widthSizeClass == WindowWidthSizeClass.Expanded) {
                UpdateSupportingPane(onDismiss = onCancel, content = content)
            } else {
                ModalBottomSheet(onDismissRequest = onCancel) { content() }
            }
        }
        UpdatePhase.PermissionRequired -> {
            ModalBottomSheet(onDismissRequest = onCancel) {
                UpdatePermissionContent(onOpenSettings = onOpenSettings, onCancel = onCancel)
            }
        }
        // Newer beta installed while the feed's newest stable is older: report
        // the wait instead of downgrading (plan §4). Manual checks only.
        UpdatePhase.WaitingForStable if state.manual -> {
            ModalBottomSheet(onDismissRequest = onDismissError) {
                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    Text(
                        stringResource(R.string.update_waiting_stable),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.update_waiting_stable_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismissError) {
                            Text(stringResource(R.string.update_later))
                        }
                    }
                }
            }
        }
        UpdatePhase.Error -> {
            ModalBottomSheet(onDismissRequest = onDismissError) {
                UpdateErrorContent(state = state, onRetry = onRetry, onWebsite = onWebsite, onCancel = onDismissError)
            }
        }
        else -> Unit
    }
}

@Composable
private fun UpdateAvailableContent(
    release: UpdateFeed.Release,
    installedVersion: String,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(24.dp)) {
        Text(
            stringResource(R.string.update_available_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.update_versions, installedVersion, release.versionName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (release.channel == "beta") {
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.update_beta_badge),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            stringResource(R.string.update_size, formatBytes(release.apk.byteLength)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.update_whats_new),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            release.changelog,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onLater) { Text(stringResource(R.string.update_later)) }
            Button(onClick = onUpdate) { Text(stringResource(R.string.update_action)) }
        }
    }
}

@Composable
private fun UpdateProgressContent(state: UpdateUiState, onCancel: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val label = when (state.phase) {
            UpdatePhase.Waiting -> stringResource(R.string.update_waiting_network)
            UpdatePhase.Verifying -> stringResource(R.string.update_verifying)
            else -> stringResource(R.string.update_downloading)
        }
        Text(label, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        if (state.phase == UpdatePhase.Verifying || state.phase == UpdatePhase.Waiting) {
            CircularProgressIndicator()
        } else {
            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onCancel) { Text(stringResource(R.string.update_cancel)) }
    }
}

@Composable
private fun UpdateReadyContent(onInstall: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp)) {
        Text(stringResource(R.string.update_ready), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.update_later)) }
            Button(onClick = onInstall) { Text(stringResource(R.string.update_install)) }
        }
    }
}

@Composable
private fun UpdatePermissionContent(onOpenSettings: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp)) {
        Text(stringResource(R.string.update_allow_source_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.update_allow_source_body), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.update_cancel)) }
            Button(onClick = onOpenSettings) { Text(stringResource(R.string.update_open_settings)) }
        }
    }
}

@Composable
private fun UpdateErrorContent(
    state: UpdateUiState,
    onRetry: () -> Unit,
    onWebsite: () -> Unit,
    onCancel: () -> Unit,
) {
    val message = when (state.errorKind) {
        UpdateError.VERIFY_FAILED -> stringResource(R.string.update_verify_failed)
        UpdateError.WITHDRAWN_OR_STALE -> stringResource(R.string.update_withdrawn)
        UpdateError.INSTALL_FAILED, UpdateError.MISSING_FILE ->
            stringResource(R.string.update_installer_blocked)
        else -> stringResource(R.string.update_download_failed)
    }
    Column(Modifier.fillMaxWidth().padding(24.dp)) {
        Text(stringResource(R.string.update_check_failed), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onWebsite) { Text(stringResource(R.string.update_website_fallback)) }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.update_cancel)) }
            Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}

@Composable
private fun UpdateSupportingPane(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        text = {
            Surface(
                modifier = Modifier.widthIn(max = 560.dp),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 3.dp,
            ) { content() }
        },
    )
}

internal fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1) "%.1f MB".format(mb) else "%d KB".format(bytes / 1024)
}

/**
 * Root host wiring the prompt to system actions. Install UI launches only
 * from a resumed host in the active flow; denial returns without settings
 * loops (MOB-PERM-01/02). Unavailable installer surfaces the website page.
 */
@Composable
fun MobileUpdateHost(
    updateViewModel: UpdateViewModel,
    updateState: UpdateUiState,
    widthSizeClass: WindowWidthSizeClass,
    modalOpen: Boolean,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(modalOpen) {
        updateViewModel.setPresentationBlocked(modalOpen)
    }
    // Automatic prompts stay silent while another modal owns the screen.
    val effective = if (modalOpen && !updateState.manual &&
        (updateState.phase == UpdatePhase.Available)
    ) {
        updateState.copy(silent = true)
    } else {
        updateState
    }
    UpdatePrompt(
        state = effective,
        installedVersion = com.lamphaus.app.BuildConfig.VERSION_NAME,
        widthSizeClass = widthSizeClass,
        onUpdate = { updateViewModel.download() },
        onLater = { updateViewModel.defer() },
        onInstall = {
            when (updateViewModel.install(hostResumed = true, playbackActive = false)) {
                UpdateCoordinator.InstallGate.NEEDS_PERMISSION -> Unit // permission sheet shows
                UpdateCoordinator.InstallGate.DEFERRED_PLAYBACK,
                UpdateCoordinator.InstallGate.DEFERRED_NO_HOST,
                -> updateViewModel.defer()
                else -> Unit
            }
        },
        onCancel = { updateViewModel.cancel() },
        onOpenSettings = {
            runCatching {
                context.startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        "package:${context.packageName}".toUri(),
                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        },
        onRetry = { updateViewModel.retry() },
        onWebsite = {
            val url = updateState.release?.releasePageUrl ?: "https://furkancodes.github.io/lamphaus/download/"
            runCatching {
                context.startActivity(
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        url.toUri(),
                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        },
        onDismissError = { updateViewModel.cancel() },
    )
}
