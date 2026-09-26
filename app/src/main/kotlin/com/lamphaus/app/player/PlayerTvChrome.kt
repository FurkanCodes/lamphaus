package com.lamphaus.app.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lamphaus.core.model.PlaybackRequest

/**
 * TV title block: the title with one line of episode context beneath it.
 * Everything else in the TV chrome is icon-only.
 */
@Composable
internal fun PlayerTvTitle(request: PlaybackRequest, modifier: Modifier = Modifier) {
    val episodeLine = listOfNotNull(
        request.subtitle?.takeIf(String::isNotBlank),
        request.episode?.title?.takeIf { it.isNotBlank() && it != request.title },
    ).joinToString(" • ")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = request.title,
            color = PlayerOnSurface,
            fontFamily = PlayerFont,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (episodeLine.isNotBlank()) {
            Text(
                text = episodeLine,
                color = PlayerOnSurface.copy(alpha = 0.9f),
                fontFamily = PlayerFont,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** "12:34 / 45:00" at the end of the control row. */
@Composable
internal fun PlayerTvTime(positionMillis: Long, durationMillis: Long, modifier: Modifier = Modifier) {
    Text(
        text = if (durationMillis > 0) {
            "${positionMillis.asPlaybackTime()} / ${durationMillis.asPlaybackTime()}"
        } else {
            positionMillis.asPlaybackTime()
        },
        color = PlayerOnSurface.copy(alpha = 0.9f),
        fontFamily = PlayerFont,
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier,
    )
}
