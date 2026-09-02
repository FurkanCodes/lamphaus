package com.lamphaus.app.mobile

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lamphaus.app.R
import com.lamphaus.app.ui.ContentMenuAction
import com.lamphaus.app.ui.ContentMenuOrigin
import com.lamphaus.app.ui.ContentMenuState
import com.lamphaus.app.ui.ContentMenuTarget
import com.lamphaus.app.ui.MediaArtwork
import com.lamphaus.app.ui.menuActions
import com.lamphaus.core.model.MediaType

/**
 * Mobile rendering of the shared content menu model (SHR-ARC-14): a Material
 * 3 bottom sheet with artwork, title, and ListItem action rows
 * (MOB-CMP-03/08). Long-press on cards is never the only route; the same
 * actions stay reachable from visible More buttons. Resolution failures keep
 * the geometry stable and offer an inline Retry (MOB-CMP-09).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MobileContentMenuSheet(
    menu: ContentMenuState,
    inLibrary: Boolean,
    onDismiss: () -> Unit,
    onAction: (ContentMenuAction) -> Unit,
) {
    val target = menu.target ?: return
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .size(width = 64.dp, height = 96.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    MediaArtwork(target.media, Modifier.fillMaxSize())
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = target.media.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    target.progress?.episodeLabel?.let { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MobileTokens.hairline)
            if (menu.resolving) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.content_menu_resolving)) },
                    trailingContent = {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
            if (menu.resolutionError) {
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.content_menu_resolution_failed),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    trailingContent = {
                        TextButton(onClick = { onAction(ContentMenuAction.StartFromBeginning) }) {
                            Text(stringResource(R.string.retry))
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
            val actions = target.menuActions()
            actions.forEachIndexed { index, action ->
                // Pending resolution disables duplicate activation; the rows
                // keep their geometry so the sheet never reflows (MOB-CMP-09).
                ListItem(
                    headlineContent = { Text(action.menuLabel(target, inLibrary, context)) },
                    leadingContent = { Icon(action.menuIcon(inLibrary), contentDescription = null) },
                    modifier = Modifier
                        .clickable(enabled = !menu.resolving) { onAction(action) }
                        .heightIn(min = 48.dp)
                        .semantics(mergeDescendants = true) {},
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                if (index != actions.lastIndex) {
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MobileTokens.hairline)
                }
            }
        }
    }
}

/**
 * Shared so cards can expose the same labels as custom accessibility
 * actions. Takes a Context instead of being composable: semantics custom
 * actions are built outside composable lambdas.
 */
internal fun ContentMenuAction.menuLabel(
    target: ContentMenuTarget,
    inLibrary: Boolean,
    context: Context,
): String = when (this) {
    ContentMenuAction.ViewDetails -> context.getString(
        if (target.episode != null ||
            target.origin == ContentMenuOrigin.CONTINUE_WATCHING && target.media.type == MediaType.SERIES
        ) {
            R.string.content_menu_view_series_details
        } else {
            R.string.content_menu_view_details
        },
    )
    ContentMenuAction.ToggleLibrary -> context.getString(
        if (inLibrary) R.string.content_menu_remove_library else R.string.content_menu_add_library,
    )
    ContentMenuAction.MarkWatched -> context.getString(
        if (target.episode != null) {
            R.string.content_menu_mark_episode_watched
        } else {
            R.string.content_menu_mark_watched
        },
    )
    ContentMenuAction.MarkUnwatched -> context.getString(R.string.content_menu_mark_unwatched)
    ContentMenuAction.RemoveFromContinueWatching ->
        context.getString(R.string.content_menu_remove_continue)
    ContentMenuAction.StartFromBeginning ->
        context.getString(R.string.content_menu_start_beginning)
}

internal fun ContentMenuAction.menuIcon(inLibrary: Boolean): ImageVector = when (this) {
    ContentMenuAction.ViewDetails -> Icons.Outlined.Info
    ContentMenuAction.ToggleLibrary ->
        if (inLibrary) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder
    ContentMenuAction.MarkWatched -> Icons.Outlined.Check
    ContentMenuAction.MarkUnwatched -> Icons.Outlined.Close
    ContentMenuAction.RemoveFromContinueWatching -> Icons.Outlined.Delete
    ContentMenuAction.StartFromBeginning -> Icons.Filled.Replay
}
