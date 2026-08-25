package com.lamphaus.app

import android.app.Application
import android.content.pm.PackageManager
import com.google.android.gms.cast.tv.CastReceiverContext
import com.lamphaus.core.data.sync.MetadataSyncWorker

class LamphausApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        if (
            BuildConfig.CAST_APPLICATION_ID.isNotBlank() &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        ) {
            runCatching { CastReceiverContext.initInstance(this) }
        }
        MetadataSyncWorker.schedule(this)
    }
}

