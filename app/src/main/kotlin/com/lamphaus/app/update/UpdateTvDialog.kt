package com.lamphaus.app.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lamphaus.app.R

/**
 * TV update dialog (plan §4, TV-LAY-01, TV-FOC-01/02, TV-NAV-02/04/05).
 * Overscan-safe (58dp margins), scrollable notes, reachable fixed actions,
 * explicit initial focus with opening-key suppression, origin focus restored
 * on dismiss. Never steals focus during playback or another modal — the host
 * only calls this when presentation is allowed.
 */
@Composable
fun TvUpdateDialog(
    state: UpdateUiState,
    installedVersion: String,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onAllowMetered: () -> Unit,
    onDismissError: () -> Unit,
    onFocusRestored: () -> Unit,
) {
    val release = state.release
    when (state.phase) {
        UpdatePhase.Available if release != null && !state.silent && !state.autoDeferredByContext -> {
            TvFocusDialog(onDismiss = { onLater(); onFocusRestored() }) { requester ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 58.dp, vertical = 24.dp)) {
                    Text(stringResource(R.string.update_available_title), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.update_versions, installedVersion, release.versionName),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.update_whats_new),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        release.changelog,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState()),
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { onLater(); onFocusRestored() }) {
                            Text(stringResource(R.string.update_later))
                        }
                        TextButton(
                            onClick = onUpdate,
                            modifier = Modifier.focusRequester(requester),
                        ) {
                            Text(stringResource(R.string.update_action))
                        }
                    }
                }
            }
        }
        UpdatePhase.Downloading, UpdatePhase.Waiting, UpdatePhase.Verifying, UpdatePhase.Installing -> {
            TvFocusDialog(onDismiss = { onCancel(); onFocusRestored() }) { requester ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 58.dp, vertical = 24.dp)) {
                    val label = when (state.phase) {
                        UpdatePhase.Verifying -> stringResource(R.string.update_verifying)
                        UpdatePhase.Waiting -> stringResource(R.string.update_waiting_network)
                        UpdatePhase.Installing -> stringResource(R.string.update_installing)
                        else -> stringResource(R.string.update_downloading)
                    }
                    Text(label, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    if (state.phase == UpdatePhase.Downloading) {
                        LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    if (state.phase == UpdatePhase.Waiting) {
                        Text(stringResource(R.string.update_waiting_network_body))
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { onCancel(); onFocusRestored() }, modifier = Modifier.focusRequester(requester)) {
                            Text(stringResource(R.string.update_cancel))
                        }
                        if (state.phase == UpdatePhase.Waiting) {
                            TextButton(onClick = onAllowMetered) {
                                Text(stringResource(R.string.update_allow_metered))
                            }
                        }
                    }
                }
            }
        }
        UpdatePhase.Ready, UpdatePhase.PermissionRequired -> {
            TvFocusDialog(onDismiss = { onCancel(); onFocusRestored() }) { requester ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 58.dp, vertical = 24.dp)) {
                    val needsPermission = state.phase == UpdatePhase.PermissionRequired
                    Text(
                        stringResource(if (needsPermission) R.string.update_allow_source_title else R.string.update_ready),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    if (needsPermission) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.update_allow_source_body), style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { onCancel(); onFocusRestored() }) {
                            Text(stringResource(R.string.update_later))
                        }
                        TextButton(
                            onClick = if (needsPermission) onOpenSettings else onInstall,
                            modifier = Modifier.focusRequester(requester),
                        ) {
                            Text(stringResource(if (needsPermission) R.string.update_open_settings else R.string.update_install))
                        }
                    }
                }
            }
        }
        UpdatePhase.Error -> {
            TvFocusDialog(onDismiss = { onDismissError(); onFocusRestored() }) { requester ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 58.dp, vertical = 24.dp)) {
                    Text(stringResource(R.string.update_failed_title), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    val errorMessage = when (state.errorKind) {
                        UpdateError.INSTALL_FAILED, UpdateError.MISSING_FILE -> R.string.update_installer_blocked
                        UpdateError.VERIFY_FAILED -> R.string.update_verify_failed
                        UpdateError.WITHDRAWN_OR_STALE -> R.string.update_withdrawn
                        else -> R.string.update_download_failed
                    }
                    Text(stringResource(errorMessage), style = MaterialTheme.typography.bodyMedium)
                    // Verified website fallback: TV shows the URL and QR code
                    // (plan §4). Never suggests uninstalling as recovery.
                    val fallbackUrl = state.release?.releasePageUrl
                    if (fallbackUrl != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(fallbackUrl, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        val qrLabel = stringResource(R.string.update_tv_qr_fallback)
                        com.lamphaus.app.tv.PairingQrCode(
                            payload = fallbackUrl,
                            modifier = Modifier
                                .height(144.dp)
                                .fillMaxWidth()
                                .semantics { contentDescription = qrLabel },
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { onDismissError(); onFocusRestored() }) {
                            Text(stringResource(R.string.update_cancel))
                        }
                        TextButton(
                            onClick = onRetry,
                            modifier = Modifier.focusRequester(requester),
                        ) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }
        }
        UpdatePhase.WaitingForStable if state.manual -> {
            TvFocusDialog(onDismiss = { onDismissError(); onFocusRestored() }) { requester ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 58.dp, vertical = 24.dp)) {
                    Text(stringResource(R.string.update_waiting_stable), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.update_waiting_stable_body), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            onClick = { onDismissError(); onFocusRestored() },
                            modifier = Modifier.focusRequester(requester),
                        ) {
                            Text(stringResource(R.string.update_later))
                        }
                    }
                }
            }
        }
        else -> Unit
    }
}

/** Focus-managed dialog shell: suppresses the opening key, takes explicit focus. */
@Composable
private fun TvFocusDialog(
    onDismiss: () -> Unit,
    content: @Composable (FocusRequester) -> Unit,
) {
    val requester = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        text = { content(requester) },
    )
    // Opening-key suppression: request focus after composition so the Select
    // press that opened the dialog does not immediately activate a button.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(120)
        runCatching { requester.requestFocus() }
    }
}
