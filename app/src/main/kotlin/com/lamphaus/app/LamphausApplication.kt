package com.lamphaus.app

import android.app.Application
import android.content.pm.PackageManager
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.google.android.gms.cast.tv.CastReceiverContext
import com.lamphaus.app.diagnostics.AppDiagnostics
import com.lamphaus.app.sync.FirebaseSyncPush
import com.lamphaus.core.data.perf.PerfTrace
import com.lamphaus.core.player.PlaybackDiagnostics
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath

class LamphausApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {
    val container: AppContainer by lazy { PerfTrace.span(PerfTrace.DEPENDENCY_INIT) { AppContainer(this) } }

    override fun onCreate() {
        super.onCreate()
        // First, so a crash anywhere after this still leaves a report (SHR-PROD-18).
        AppDiagnostics.install(this)
        if (
            BuildConfig.CAST_APPLICATION_ID.isNotBlank() &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        ) {
            runCatching { CastReceiverContext.initInstance(this) }
        }
        // A sync push may be what started this process (push-to-pull sync).
        FirebaseSyncPush.initialize(this)
        // A periodic metadata-sync worker used to be scheduled here while
        // returning success without syncing anything, which paid WorkManager
        // startup plus a wake-up every six hours for no effect (PERF-12).
        // Repository-owned background sync must be reintroduced as a separate
        // functional change with its own retention and auth policy.
        // WorkManager now starts on demand (its startup initializer is
        // removed in the manifest), so only a phone that opted into
        // new-episode alerts ever pays for it (SHR-PROD-16).
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        PlaybackDiagnostics.onTrimMemory(level)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    /**
     * Artwork comes from a handful of hosts, and OkHttp's default allows five
     * requests per host: a row of posters coming into view queued the focused
     * card's still and logo behind it for seconds. Images get their own client
     * with room for a screenful at once (QA-08).
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = {
                            OkHttpClient.Builder()
                                .dispatcher(
                                    Dispatcher().apply {
                                        maxRequests = IMAGE_MAX_REQUESTS
                                        maxRequestsPerHost = IMAGE_MAX_REQUESTS_PER_HOST
                                    },
                                )
                                .build()
                        },
                    ),
                )
            }
            .diskCache {
                // Coil's own directory, so artwork already on disk stays; a
                // little more room than its 2% default keeps rows warm on TV boxes.
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil3_disk_cache").toOkioPath())
                    .maxSizePercent(IMAGE_DISK_CACHE_PERCENT)
                    .build()
            }
            .build()

    private companion object {
        const val IMAGE_MAX_REQUESTS = 64
        const val IMAGE_MAX_REQUESTS_PER_HOST = 24
        const val IMAGE_DISK_CACHE_PERCENT = 0.05
    }
}

