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
        UpdatePhase.Downloading, UpdatePhase.Verifying -> {
            TvFocusDialog(onDismiss = { onCancel(); onFocusRestored() }) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 58.dp, vertical = 24.dp)) {
                    val label = if (state.phase == UpdatePhase.Verifying) {
                        stringResource(R.string.update_verifying)
                    } else {
                        stringResource(R.string.update_downloading)
                    }
                    Text(label, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { onCancel(); onFocusRestored() }) {
                            Text(stringResource(R.string.update_cancel))
                        }
                    }
                }
            }
        }
        UpdatePhase.Ready -> {
            TvFocusDialog(onDismiss = { onCancel(); onFocusRestored() }) { requester ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 58.dp, vertical = 24.dp)) {
                    Text(stringResource(R.string.update_ready), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { onCancel(); onFocusRestored() }) {
                            Text(stringResource(R.string.update_later))
                        }
                        TextButton(
                            onClick = onInstall,
                            modifier = Modifier.focusRequester(requester),
                        ) {
                            Text(stringResource(R.string.update_install))
                        }
                    }
                }
            }
        }
        UpdatePhase.Error -> {
            TvFocusDialog(onDismiss = { onDismissError(); onFocusRestored() }) { requester ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 58.dp, vertical = 24.dp)) {
                    Text(stringResource(R.string.update_check_failed), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.update_download_failed), style = MaterialTheme.typography.bodyMedium)
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
