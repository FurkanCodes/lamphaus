package com.lamphaus.app.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lamphaus.app.R

/**
 * Default player "Ask every time" (PLY-EXT-01): one question before the
 * stream starts. Lamphaus is focused first; Back leaves without playing.
 */
@Composable
internal fun PlayerChoice(
    isTelevision: Boolean,
    onInternal: () -> Unit,
    onExternal: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(horizontal = if (isTelevision) 58.dp else 24.dp),
        ) {
            Text(
                stringResource(R.string.player_choice_title),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = onInternal, modifier = Modifier.focusRequester(first)) {
                    Text(stringResource(R.string.player_choice_internal))
                }
                OutlinedButton(onClick = onExternal) {
                    Text(stringResource(R.string.player_choice_external), color = Color.White)
                }
            }
        }
    }
}
