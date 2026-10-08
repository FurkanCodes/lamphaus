package com.lamphaus.app.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.lamphaus.app.LamphausApplication

/**
 * Reopens Lamphaus after the in-app updater replaced it. Android ends the old
 * process during the install; when the version the updater chose is now the
 * installed one, the new version opens its home screen again. Installs from
 * anywhere else (a browser, adb, another store) leave the app closed.
 */
class UpdateRelaunchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val coordinator = (context.applicationContext as LamphausApplication).container.updateCoordinator
        if (!coordinator.installedSelectedUpdate()) return
        val packageManager = context.packageManager
        val launch = if (packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            packageManager.getLeanbackLaunchIntentForPackage(context.packageName)
        } else {
            packageManager.getLaunchIntentForPackage(context.packageName)
        } ?: return
        runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
