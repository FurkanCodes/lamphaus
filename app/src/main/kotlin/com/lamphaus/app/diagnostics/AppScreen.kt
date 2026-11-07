package com.lamphaus.app.diagnostics

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * The fixed page names a crash report may carry, so a crash says where in the
 * app it happened (SHR-PROD-18). Never a title, query, add-on, or anything else
 * that came from a viewer or a provider (SHR-PROD-06).
 */
internal enum class AppScreen {
    HOME,
    MOVIES,
    SERIES,
    DISCOVER,
    LIBRARY,
    SEARCH,
    SETTINGS,
    DETAILS,
    PERSON,
    PLAYER,
}

/** Names [screen] on crash reports while its activity is resumed, so returning from the player names it again. */
@Composable
internal fun ReportScreen(screen: AppScreen, homeLayout: String? = null) {
    LifecycleResumeEffect(screen, homeLayout) {
        SentryDiagnostics.onScreen(screen.name.lowercase(), homeLayout)
        onPauseOrDispose { }
    }
}
