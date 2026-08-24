package com.lamphaus.app

import android.app.Application
import android.content.pm.PackageManager
import com.google.android.gms.cast.tv.CastReceiverContext
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance
import com.lamphaus.core.data.sync.MetadataSyncWorker

class LamphausApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.CLOUD_CONFIGURED) {
            FirebaseApp.initializeApp(this)
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance(),
            )
            FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = false
            FirebasePerformance.getInstance().isPerformanceCollectionEnabled = false
        }
        if (
            BuildConfig.CAST_APPLICATION_ID.isNotBlank() &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        ) {
            runCatching { CastReceiverContext.initInstance(this) }
        }
        MetadataSyncWorker.schedule(this)
    }
}

