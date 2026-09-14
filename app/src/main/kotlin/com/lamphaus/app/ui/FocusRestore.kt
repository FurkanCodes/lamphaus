package com.lamphaus.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

@Composable
internal fun Modifier.mediaFocusRestore(
    focusKey: String,
    pendingKey: String?,
    onConsumed: () -> Unit,
): Modifier {
    if (pendingKey == null || focusKey != pendingKey) return this
    val requester = remember { FocusRequester() }
    LaunchedEffect(pendingKey) {
        withFrameNanos { }
        if (runCatching { requester.requestFocus() }.getOrDefault(false)) {
            onConsumed()
        }
    }
    return this.then(Modifier.focusRequester(requester))
}
