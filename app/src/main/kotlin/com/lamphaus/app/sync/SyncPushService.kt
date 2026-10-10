package com.lamphaus.app.sync

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.lamphaus.app.LamphausApplication
import com.lamphaus.app.isTelevision
import com.lamphaus.app.widget.ContinueWatchingWidget
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Receives the account's "sync" push: another device changed something, so
 * this one pulls it now, even when the app is closed. Firebase runs this off
 * the main thread and allows it a short while, so the pull is bounded.
 */
class SyncPushService : FirebaseMessagingService() {
    private val container get() = (application as LamphausApplication).container

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data[KEY_TYPE] != TYPE_SYNC) return
        val pulled = runBlocking {
            withTimeoutOrNull(PULL_TIMEOUT_MILLIS) {
                val userId = signedInUserId() ?: return@withTimeoutOrNull false
                container.accountSync.pull(userId, AccountSync.Reason.SIGNAL) != null
            }
        } == true
        // The phone widget draws from local data, so it shows the pulled change (MOB-WGT-07).
        if (pulled && !isTelevision()) runBlocking { ContinueWatchingWidget.refresh(applicationContext) }
    }

    override fun onNewToken(token: String) {
        container.applicationScope.launch {
            signedInUserId()?.let { container.accountSync.registerSignals(it) }
        }
    }

    /**
     * The signed-in user, with the session restored: a push can start the
     * process, or arrive while the app is in the background with its session
     * parked until the next screen.
     */
    private suspend fun signedInUserId(): String? = container.accountGateway.restoreSessionForBackgroundWork()

    private companion object {
        const val KEY_TYPE = "t"
        const val TYPE_SYNC = "sync"
        const val PULL_TIMEOUT_MILLIS = 15_000L
    }
}
