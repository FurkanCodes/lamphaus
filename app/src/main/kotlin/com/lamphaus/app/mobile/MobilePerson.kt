package com.lamphaus.app.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lamphaus.app.R
import com.lamphaus.app.ui.PersonPageState
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.WatchProgress

/**
 * A cast or crew member's titles (MOB-SRCH-01): a child view with Up
 * (MOB-NAV-06), the person's photo and name, and their titles as a poster
 * grid. Loading, empty, and failure stay inside the grid area with a local
 * Retry (MOB-CMP-09); Back returns to the details page it came from.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MobilePersonScreen(
    page: PersonPageState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onMedia: (MediaPreview) -> Unit,
    progress: List<WatchProgress>,
    inLibrary: (MediaPreview) -> Boolean,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text(page.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = MobileTokens.spacingScreen, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(MobileTokens.surfaceRaised),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    page.name.split(' ').mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString(""),
                    style = MaterialTheme.typography.titleMedium,
                    color = MobileTokens.textMuted,
                )
                if (!page.profileUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = page.profileUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text(
                    page.name,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                page.filmography?.knownFor?.takeIf(String::isNotBlank)?.let { department ->
                    Text(
                        stringResource(R.string.person_known_for, department),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        val credits = page.filmography?.credits.orEmpty()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                page.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                page.failed -> PersonMessage(
                    text = stringResource(R.string.person_titles_failed),
                    action = stringResource(R.string.retry),
                    onAction = onRetry,
                )
                credits.isEmpty() -> PersonMessage(text = stringResource(R.string.person_titles_empty))
                else -> {
                    val completed = progress.filter { it.completed }.mapTo(HashSet()) { it.videoId }
                    MediaGrid(
                        media = credits,
                        onMedia = { media, _ -> onMedia(media) },
                        focusContainerKey = "person:${page.personId}",
                        restoreMediaKey = null,
                        onFocusRestored = {},
                        progressByVideo = progress.associateBy { it.videoId },
                        completedVideoIds = completed,
                        inLibrary = inLibrary,
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonMessage(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onAction) { Text(action) }
        }
    }
}
