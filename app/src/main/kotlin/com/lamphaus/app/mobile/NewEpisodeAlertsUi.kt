package com.lamphaus.app.mobile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lamphaus.app.R

/**
 * Turns new-episode alerts on (SHR-PROD-16). The explanation has already
 * been shown by the caller; only then is the system permission requested
 * (MOB-NOT-02, MOB-PERM-01). [onResult] receives whether alerts are on.
 */
@Composable
internal fun rememberEnableNewEpisodeAlerts(onResult: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        currentOnResult(granted)
    }
    return {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || notificationPermissionGranted(context)) {
            currentOnResult(true)
        } else {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** Whether the system currently lets Lamphaus post notifications. */
internal fun notificationsAllowed(context: Context): Boolean =
    notificationPermissionGranted(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()

private fun notificationPermissionGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/** Opens Lamphaus's notification settings, where a system-owned denial is undone (MOB-SET-05). */
internal fun openAppNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/**
 * The one-time invitation on a followed series' details page: benefit
 * first, then the choice (MOB-ONB-02, MOB-NOT-02). "Not now" never opens
 * the system prompt and the card never returns.
 */
@Composable
internal fun NewEpisodePromptCard(onTurnOn: () -> Unit, onNotNow: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(MobileTokens.radiusSection),
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(R.string.new_episodes_prompt_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Text(
                stringResource(R.string.new_episodes_prompt_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = onNotNow) { Text(stringResource(R.string.not_now)) }
                Button(onClick = onTurnOn) { Text(stringResource(R.string.turn_on)) }
            }
        }
    }
}
