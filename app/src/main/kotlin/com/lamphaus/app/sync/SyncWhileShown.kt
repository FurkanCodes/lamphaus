package com.lamphaus.app.sync

import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/**
 * Ties account sync to the screen being shown (SHR-ARC-10): a start catches
 * up and begins listening; a stop — playback, another app, the screen off —
 * ends every sync connection until the next start.
 */
@Composable
fun SyncWhileShown(onStarted: () -> Unit, onStopped: () -> Unit) {
    LifecycleEventEffect(Lifecycle.Event.ON_START, onEvent = onStarted)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP, onEvent = onStopped)
}
