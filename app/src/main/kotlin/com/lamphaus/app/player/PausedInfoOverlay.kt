package com.lamphaus.app.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lamphaus.app.R
import com.lamphaus.core.model.PlaybackRequest

/**
 * TV pause overlay (Nuvio-style): after a few idle seconds paused, the frame
 * dims and the title, episode, and synopsis appear in the lower start corner
 * inside the overscan-safe margins (TV-LAY-01). It is informational only —
 * any key brings the controls back.
 */
@Composable
internal fun PausedInfoOverlay(request: PlaybackRequest, modifier: Modifier = Modifier) {
    val episode = request.episode
    val episodeLine = listOfNotNull(
        episode?.season?.let { season ->
            episode.episode?.let { number -> stringResource(R.string.player_episode_label, season, number) }
        },
        episode?.title?.takeIf { it.isNotBlank() && it != request.title },
        request.preview?.releaseYear?.toString(),
    ).joinToString(" · ")
    val synopsis = episode?.overview?.takeIf(String::isNotBlank) ?: request.preview?.description
    val logo = request.preview?.logoUrl

    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 0.82f),
                    0.55f to Color.Black.copy(alpha = 0.45f),
                    1f to Color.Black.copy(alpha = 0.15f),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 58.dp, end = 58.dp, bottom = 56.dp)
                .widthIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.player_paused).uppercase(),
                color = PlayerPrimary,
                fontFamily = PlayerFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                letterSpacing = 1.5.sp,
            )
            if (!logo.isNullOrBlank()) {
                AsyncImage(
                    model = logo,
                    contentDescription = request.title,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    modifier = Modifier.widthIn(max = 320.dp).heightIn(max = 110.dp),
                )
            } else {
                Text(
                    text = request.title,
                    color = PlayerOnSurface,
                    fontFamily = PlayerFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 32.sp,
                    lineHeight = 38.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (episodeLine.isNotBlank()) {
                Text(
                    text = episodeLine,
                    color = PlayerOnSurface,
                    fontFamily = PlayerFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            synopsis?.let {
                Text(
                    text = it,
                    color = PlayerMutedInk,
                    fontFamily = PlayerFont,
                    fontSize = 16.sp,
                    lineHeight = 23.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** TV muted ink (TV-CLR-01). */
private val PlayerMutedInk = Color(0xFFC4C6CF)
