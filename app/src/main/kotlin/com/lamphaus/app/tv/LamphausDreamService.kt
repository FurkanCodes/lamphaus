package com.lamphaus.app.tv

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.service.dreams.DreamService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.lamphaus.app.LamphausApplication
import com.lamphaus.app.ui.rememberReducedMotion
import kotlinx.coroutines.flow.first

/**
 * Lamphaus as the television's system screensaver (TV-AMB-01), where the
 * device lets viewers choose one. It shows the same ambient as the in-app
 * idle cover: the active profile's backdrops, a clock, and the wordmark,
 * never progress or profile details (SHR-PROD-06). It is non-interactive,
 * so any key wakes the TV as usual.
 */
class LamphausDreamService : DreamService(), LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        val container = (application as LamphausApplication).container
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@LamphausDreamService)
            setViewTreeSavedStateRegistryOwner(this@LamphausDreamService)
            setContent {
                val slides by produceState(emptyList<AmbientSlide>()) {
                    value = runCatching {
                        val profileId = container.preferences.current().activeProfileId
                            ?: container.libraryRepository.profiles().first().firstOrNull()?.id
                        if (profileId == null) {
                            emptyList()
                        } else {
                            ambientSlides(
                                container.libraryRepository.progress(profileId).first(),
                                container.libraryRepository.library(profileId).first(),
                            )
                        }
                    }.getOrDefault(emptyList())
                }
                LamphausTvTheme {
                    TvAmbientScreensaver(slides, rememberReducedMotion())
                }
            }
        }
        setContentView(view)
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onDreamingStopped() {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDetachedFromWindow()
    }

    companion object {
        /**
         * The screensaver ships disabled so phones never list it; the TV
         * host turns it on the first time it runs (TV-AMB-01).
         */
        fun enableOnTelevision(context: Context) {
            val component = ComponentName(context, LamphausDreamService::class.java)
            val packageManager = context.packageManager
            if (packageManager.getComponentEnabledSetting(component) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return
            runCatching {
                packageManager.setComponentEnabledSetting(
                    component,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }
}
