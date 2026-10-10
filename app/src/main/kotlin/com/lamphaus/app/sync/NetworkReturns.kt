package com.lamphaus.app.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * Emits when the device regains a network after losing it, so a screen that
 * was showing offline catches up. The callback lives only while collected.
 */
fun networkReturns(context: Context): Flow<Unit> = callbackFlow {
    val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    if (connectivity == null) {
        awaitClose()
        return@callbackFlow
    }
    val callback = object : ConnectivityManager.NetworkCallback() {
        private var lost = false

        override fun onLost(network: Network) {
            lost = true
        }

        override fun onAvailable(network: Network) {
            if (lost) {
                lost = false
                trySend(Unit)
            }
        }
    }
    connectivity.registerDefaultNetworkCallback(callback)
    awaitClose { runCatching { connectivity.unregisterNetworkCallback(callback) } }
}.conflate()
