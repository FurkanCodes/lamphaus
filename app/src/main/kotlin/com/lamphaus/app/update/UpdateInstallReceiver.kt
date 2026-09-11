package com.lamphaus.app.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller

/**
 * Terminal installer status receiver. Never reports false success: completion
 * is confirmed from the installed package/version on next launch (plan §4).
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != UpdateInstaller.ACTION_SESSION_CALLBACK) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val sessionId = intent.getIntExtra(UpdateInstaller.EXTRA_SESSION_ID, -1)
        val prefs = UpdatePreferences(context.applicationContext)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // System shows confirmation UI; the host re-checks on return.
            }
            PackageInstaller.STATUS_SUCCESS -> {
                prefs.installerSessionId = -1
            }
            else -> {
                if (sessionId >= 0) {
                    runCatching {
                        context.packageManager.packageInstaller.abandonSession(sessionId)
                    }
                }
                prefs.installerSessionId = -1
            }
        }
    }
}
