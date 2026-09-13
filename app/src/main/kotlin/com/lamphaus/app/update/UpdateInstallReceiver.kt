package com.lamphaus.app.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import com.lamphaus.app.LamphausApplication

/**
 * Terminal installer status receiver. Never reports false success: completion
 * is confirmed from the installed package/version on next launch (plan §4).
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != UpdateInstaller.ACTION_SESSION_CALLBACK) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val sessionId = intent.getIntExtra(UpdateInstaller.EXTRA_SESSION_ID, -1)
        val confirmation = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
        // Android supplies a confirmation intent; it does not launch that UI for us.
        // Keep it in state until a resumed host can launch it (SHR-ARC-09/10).
        (context.applicationContext as LamphausApplication).container.updateCoordinator
            .onInstallerStatus(sessionId, status, confirmation)
    }
}
