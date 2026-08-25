package com.lamphaus.app

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/**
 * True when the app is running on an Android TV / leanback-first device.
 *
 * Checks both the hardware feature and the active UI mode so physical TVs,
 * TV emulators and hybrid devices all resolve the same way, independent of
 * which launcher entry started the process.
 */
fun Context.isTelevision(): Boolean =
    packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        (getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)
            ?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
