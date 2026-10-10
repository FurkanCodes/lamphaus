package com.lamphaus.app.sync

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.lamphaus.app.BuildConfig
import com.lamphaus.core.data.cloud.CloudLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/** Where the account's sync signal reaches this installation. */
interface SyncPushTokens {
    /** A Firebase Cloud Messaging token, or null when this device cannot receive pushes. */
    suspend fun token(): String?

    /** Invalidates this installation's token on leaving the account. */
    suspend fun forget()
}

/**
 * Firebase Cloud Messaging for the sync signal. The push carries only "sync"
 * (SHR-PROD-06), is never shown, and needs no notification permission.
 * Firebase is configured from Gradle properties like the cloud itself; a
 * build without them, or a device without Google Play services, uses the
 * live signal while on screen instead. Tokens exist only for signed-in
 * installations: automatic token creation is off in the manifest.
 */
class FirebaseSyncPush(context: Context) : SyncPushTokens {
    private val context = context.applicationContext

    private fun available(): Boolean =
        BuildConfig.PUSH_CONFIGURED &&
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    override suspend fun token(): String? {
        if (!available() || !initialize(context)) return null
        return try {
            FirebaseMessaging.getInstance().token.await()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            CloudLog.w("sync.push token unavailable — using the live signal on screen", error)
            null
        }
    }

    override suspend fun forget() {
        if (!available() || !initialize(context)) return
        try {
            FirebaseMessaging.getInstance().deleteToken().await()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            CloudLog.w("sync.push token removal failed", error)
        }
    }

    companion object {
        /**
         * Starts Firebase with this build's options; true once it is running.
         * The application calls it first so a push that starts the process
         * finds Firebase ready.
         */
        fun initialize(context: Context): Boolean {
            if (!BuildConfig.PUSH_CONFIGURED) return false
            return runCatching {
                if (FirebaseApp.getApps(context).isEmpty()) {
                    FirebaseApp.initializeApp(
                        context,
                        FirebaseOptions.Builder()
                            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                            .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                            .setApiKey(BuildConfig.FIREBASE_API_KEY)
                            .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                            .build(),
                    )
                }
                true
            }.getOrElse { error ->
                CloudLog.w("sync.push Firebase unavailable", error)
                false
            }
        }
    }
}
