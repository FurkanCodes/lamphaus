package com.lamphaus.app.player

import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lamphaus.app.AppContainer
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Starts the playback a closing player queued (the next episode, or another
 * source) once this browse screen is back in front, the same way a source
 * picked on the sources screen starts. The old player has fully closed by
 * then, so the new one begins from a clean session and display mode.
 */
internal fun ComponentActivity.launchPendingPlayback(container: AppContainer) {
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.RESUMED) {
            container.pendingPlayback.filterNotNull().collect { next ->
                container.pendingPlayback.value = null
                startActivity(PlayerActivity.intent(this@launchPendingPlayback, next))
            }
        }
    }
}
