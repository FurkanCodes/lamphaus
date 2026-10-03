package com.lamphaus.app.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lamphaus.app.R
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.MonthlyRecap
import java.time.format.TextStyle

/** The recap month's name in the viewer's language ("September"). */
@Composable
private fun monthName(recap: MonthlyRecap): String =
    recap.month.month.getDisplayName(TextStyle.FULL_STANDALONE, LocalLocale.current.platformLocale)

/** "14 hours · 9 episodes · 2 movies": plain numbers, nothing to beat (SHR-PROD-03). */
@Composable
private fun recapSummary(recap: MonthlyRecap): String = listOfNotNull(
    pluralStringResource(R.plurals.recap_hours, recap.hours.coerceAtLeast(1), recap.hours.coerceAtLeast(1)),
    recap.episodesFinished.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.recap_episodes, it, it) },
    recap.moviesFinished.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.recap_movies, it, it) },
).joinToString(" · ")

/**
 * Last month's private recap at the top of Library (SHR-PROD-17): a calm
 * card that opens the breakdown, with a close action whose snackbar offers
 * Undo (MOB-CMP-05).
 */
@Composable
internal fun RecapCard(recap: MonthlyRecap, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val openLabel = stringResource(R.string.recap_open)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(MobileTokens.radiusSection),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MobileTokens.spacingScreen, vertical = 8.dp)
            .clip(RoundedCornerShape(MobileTokens.radiusSection))
            .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onOpen),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.recap_title, monthName(recap)),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    recapSummary(recap),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.recap_hide))
            }
        }
    }
}

/**
 * The month's breakdown in a modal sheet with a drag handle (MOB-CMP-03):
 * time watched, what was finished, the title watched most, and the month's
 * posters, each opening its details.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecapSheet(recap: MonthlyRecap, onDismiss: () -> Unit, onMedia: (MediaPreview) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.recap_sheet_title, monthName(recap)),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp).semantics { heading() },
            )
            Text(
                recapSummary(recap),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            recap.topTitle?.let { top ->
                Text(
                    stringResource(R.string.recap_top_title, top.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            if (recap.titles.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(recap.titles, key = MediaPreview::stableKey) { media ->
                        Column(
                            Modifier
                                .width(96.dp)
                                .clip(RoundedCornerShape(MobileTokens.radiusCard))
                                .clickable(role = Role.Button) { onMedia(media) },
                        ) {
                            AsyncImage(
                                model = media.posterUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(MobileTokens.radiusCard)),
                            )
                            Text(
                                media.name,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
