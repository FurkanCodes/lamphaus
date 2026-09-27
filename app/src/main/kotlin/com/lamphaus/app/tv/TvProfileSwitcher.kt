package com.lamphaus.app.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import com.lamphaus.app.ui.ProfileAvatar
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
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // An opaque full-screen surface: over the translucent default scrim
        // the page's posters showed through the title and the action, making
        // them unreadable (TV-FND-01, TV-CLR-01).
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = TvLayoutTokens.screenHorizontalPadding, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically),
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
            // The avatar carries the focus treatment; the default highlight
            // drew a second grey box behind the tile (TV-FOC-01).
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
            .focusable()
            .semantics(mergeDescendants = true) { selected = active },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TvProfileAvatar(
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

/**
 * Full-screen avatar choice for one profile. Focus opens on the current
 * avatar; choosing saves and closes, and Back dismisses without a change
 * (TV-NAV-02). The account photo leads when the account has one.
 */
@Composable
internal fun TvAvatarPicker(
    profile: Profile,
    accountPhotoAvailable: Boolean,
    onAvatar: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val keys = buildList {
        if (accountPhotoAvailable) add(ProfileAvatar.ACCOUNT_PHOTO_KEY)
        ProfileAvatar.entries.forEach { add(it.key) }
    }
    val currentFocus = remember { FocusRequester() }
    val currentIndex = keys.indexOf(profile.avatarKey).takeIf { it >= 0 }
        ?: keys.indexOf(ProfileAvatar.forKey(profile.avatarKey).key)
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { currentFocus.requestFocus() }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = TvLayoutTokens.screenHorizontalPadding, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically),
        ) {
            Text(
                text = stringResource(R.string.choose_avatar_for, profile.name),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            // Rows of six keep a straight D-pad path to every choice (TV-NAV-05).
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                keys.chunked(AVATAR_PICKER_COLUMNS).forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(TvLayoutTokens.itemSpacing)) {
                        row.forEachIndexed { columnIndex, key ->
                            val index = rowIndex * AVATAR_PICKER_COLUMNS + columnIndex
                            TvAvatarChoice(
                                avatarKey = key,
                                label = if (key == ProfileAvatar.ACCOUNT_PHOTO_KEY) {
                                    stringResource(R.string.avatar_account_photo)
                                } else {
                                    stringResource(ProfileAvatar.forKey(key).labelRes)
                                },
                                current = index == currentIndex,
                                modifier = if (index == currentIndex) Modifier.focusRequester(currentFocus) else Modifier,
                                onClick = { onAvatar(key) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val AVATAR_PICKER_COLUMNS = 6

@Composable
private fun TvAvatarChoice(
    avatarKey: String,
    label: String,
    current: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .width(SwitcherTileWidth)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
            .focusable()
            .semantics(mergeDescendants = true) { selected = current },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TvProfileAvatar(
            avatarKey = avatarKey,
            focused = focused,
            selected = current,
            modifier = Modifier
                .size(SwitcherAvatarSize)
                .graphicsLayer {
                    val scale = if (focused) TvMotionTokens.focusedArtworkScale else 1f
                    scaleX = scale
                    scaleY = scale
                },
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (focused || current) {
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
