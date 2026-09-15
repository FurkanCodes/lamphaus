package com.lamphaus.app

import android.app.Application
import android.content.pm.PackageManager
import com.google.android.gms.cast.tv.CastReceiverContext
import com.lamphaus.core.data.perf.PerfTrace

class LamphausApplication : Application() {
    val container: AppContainer by lazy { PerfTrace.span(PerfTrace.DEPENDENCY_INIT) { AppContainer(this) } }

    override fun onCreate() {
        super.onCreate()
        if (
            BuildConfig.CAST_APPLICATION_ID.isNotBlank() &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        ) {
            runCatching { CastReceiverContext.initInstance(this) }
        }
        // A periodic metadata-sync worker used to be scheduled here while
        // returning success without syncing anything, which paid WorkManager
        // startup plus a wake-up every six hours for no effect (PERF-12).
        // Repository-owned background sync must be reintroduced as a separate
        // functional change with its own retention and auth policy.
    }
}

