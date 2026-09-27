package com.lamphaus.app.tv

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.lamphaus.app.R
import com.lamphaus.core.model.Profile

private val SwitcherAvatarSize = 72.dp
private val SwitcherTileWidth = 112.dp

/**
 * "Who's watching?" opened from the navigation avatar (TV-NAV-01). The dialog
 * keeps focus inside itself; Back dismisses it and the caller returns focus to
 * the avatar (TV-NAV-02). There is no on-screen Back (TV-FND-02).
 */
@Composable
internal fun TvProfileSwitcher(
    profiles: List<Profile>,
    activeProfileId: String?,
    onProfile: (String) -> Unit,
    onManageProfiles: () -> Unit,
    onDismiss: () -> Unit,
) {
    val activeFocus = remember { FocusRequester() }
    val activeIndex = profiles.indexOfFirst { it.id == activeProfileId }.coerceAtLeast(0)
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { activeFocus.requestFocus() }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TvLayoutTokens.screenHorizontalPadding, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Text(
                text = stringResource(R.string.who_is_watching),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(TvLayoutTokens.itemSpacing)) {
                profiles.forEachIndexed { index, profile ->
                    TvProfileSwitcherTile(
                        profile = profile,
                        active = profile.id == activeProfileId,
                        modifier = if (index == activeIndex) Modifier.focusRequester(activeFocus) else Modifier,
                        onClick = { onProfile(profile.id) },
                    )
                }
            }
            TvAction(
                label = stringResource(R.string.manage_profiles),
                icon = Icons.Outlined.ManageAccounts,
                modifier = if (profiles.isEmpty()) Modifier.focusRequester(activeFocus) else Modifier,
                onClick = onManageProfiles,
            )
        }
    }
}

@Composable
private fun TvProfileSwitcherTile(
    profile: Profile,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .width(SwitcherTileWidth)
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick)
            .focusable()
            .semantics(mergeDescendants = true) { selected = active },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TvProfileAvatar(
            name = profile.name,
            avatarKey = profile.avatarKey,
            focused = focused,
            selected = active,
            modifier = Modifier
                .size(SwitcherAvatarSize)
                .graphicsLayer {
                    val scale = if (focused) TvMotionTokens.focusedArtworkScale else 1f
                    scaleX = scale
                    scaleY = scale
                },
        )
        Text(
            text = profile.name,
            style = MaterialTheme.typography.titleSmall,
            color = if (focused || active) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
