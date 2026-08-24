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
    mediaKey: String,
    pendingKey: String?,
    onConsumed: () -> Unit,
): Modifier {
    if (pendingKey == null || mediaKey != pendingKey) return this
    val requester = remember { FocusRequester() }
    LaunchedEffect(pendingKey) {
        withFrameNanos { }
        runCatching { requester.requestFocus() }
        onConsumed()
    }
    return Modifier.focusRequester(requester)
}
