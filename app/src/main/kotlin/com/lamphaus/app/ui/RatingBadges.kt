package com.lamphaus.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lamphaus.app.R
import com.lamphaus.core.model.RatingSourceScore
import java.util.Locale
import kotlin.math.roundToInt

// Rating brand marks shared by mobile and TV. Only the small chip visual is
// shared: focus, input, and focus treatment stay platform-owned (TV-FOC-02 /
// touch on mobile), matching the platform-boundary rule in AGENTS.md §16.
//
// Brand colors are asset colors of external services (like artwork accents),
// not product palette drift: chips are tiny, non-semantic surfaces and never
// reading surfaces (TV-CLR-01). Meaning is never color alone — every badge
// carries its source label/dots and an accessibility description.

private val ImdbYellow = Color(0xFFF5C518)
private val TmdbNavy = Color(0xFF0D253F)
private val TmdbBlue = Color(0xFF01B4E4)
private val TomatoRed = Color(0xFFFA320A)
private val LetterboxdInk = Color(0xFF14181C)
private val LetterboxdGreen = Color(0xFF00E054)
private val LetterboxdBlue = Color(0xFF40BCF4)
private val LetterboxdOrange = Color(0xFFFF8000)
private val NeutralChipInk = Color(0xFF2A2C31)
private val NeutralChipContent = Color(0xFFC7C6CA)

/** Badge display order: the sources users recognize first, then the rest. */
private val badgeOrder = listOf(
    "imdb",
    "tomatoes",
    "popcorn",
    "letterboxd",
    "tmdb",
    "metacritic",
    "trakt",
    "metacriticuser",
    "rogerebert",
    "myanimelist",
)

/** True only when the preview rating may be labeled IMDb (SHR-PROD-05). */
fun isImdbRating(ratingSource: String?): Boolean = ratingSource == "imdb"

/**
 * The catalog rating as an IMDb score — only when the addon actually exposed
 * an IMDb score. A generic provider rating must never wear the IMDb mark
 * (SHR-PROD-05).
 */
fun metadataImdbScore(
    rating: Double?,
    ratingSource: String?,
    displayName: String,
): RatingSourceScore? {
    if (!isImdbRating(ratingSource) || rating == null || rating !in 0.0..10.0) return null
    return RatingSourceScore(
        sourceId = "imdb",
        displayName = displayName,
        value = rating,
        scale = 10.0,
    )
}

/**
 * Metadata rating first (it wins the "imdb" slot over the MDBList aggregate),
 * then enrichment sources in badge order; unknown sources keep arrival order.
 */
fun orderedRatingScores(
    metadata: RatingSourceScore?,
    enrichment: List<RatingSourceScore>,
): List<RatingSourceScore> {
    val seen = mutableSetOf<String>()
    val merged = (listOfNotNull(metadata) + enrichment).filter { score -> seen.add(score.sourceId) }
    return merged.sortedBy { score ->
        badgeOrder.indexOf(score.sourceId).takeIf { it >= 0 } ?: badgeOrder.size
    }
}

/** Compact on-wire value: "7.8", "3.8", "93%". */
fun ratingValueText(score: RatingSourceScore): String = when (score.scale) {
    100.0 -> "${score.value.roundToInt()}%"
    else -> "%.1f".format(Locale.ROOT, score.value)
}

/** The brand chip only: colored mark with the source's short label or dots. */
@Composable
fun RatingBadgeChip(score: RatingSourceScore, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(18.dp)
            .background(chipBackground(score), RoundedCornerShape(3.dp))
            .then(
                if (score.sourceId == "popcorn") {
                    Modifier.border(1.dp, TomatoRed, RoundedCornerShape(3.dp))
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (score.sourceId) {
            "letterboxd" -> {
                listOf(LetterboxdGreen, LetterboxdBlue, LetterboxdOrange).forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(color, RoundedCornerShape(50)),
                    )
                }
            }
            else -> Text(
                text = chipLabel(score),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = chipContent(score),
                maxLines = 1,
            )
        }
    }
}

private fun chipBackground(score: RatingSourceScore): Color = when (score.sourceId) {
    "imdb" -> ImdbYellow
    "tmdb" -> TmdbNavy
    "tomatoes" -> TomatoRed
    "popcorn" -> Color.Transparent
    "letterboxd" -> LetterboxdInk
    else -> NeutralChipInk
}

private fun chipContent(score: RatingSourceScore): Color = when (score.sourceId) {
    "imdb" -> Color.Black
    "tmdb" -> TmdbBlue
    "tomatoes" -> Color.White
    "popcorn" -> TomatoRed
    "letterboxd" -> LetterboxdInk
    else -> NeutralChipContent
}

private fun chipLabel(score: RatingSourceScore): String = when (score.sourceId) {
    "imdb" -> "IMDb"
    "tmdb" -> "TMDB"
    "tomatoes", "popcorn" -> "RT"
    "metacritic" -> "MC"
    "trakt" -> "Trakt"
    else -> score.displayName
}

@Composable
private fun ratingBadgeDescription(score: RatingSourceScore): String = stringResource(
    R.string.rating_badge_description,
    score.displayName,
    ratingValueText(score),
)

/** Non-interactive badge: brand chip + value, one accessibility unit. */
@Composable
fun RatingBadge(
    score: RatingSourceScore,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.Unspecified,
) {
    val description = ratingBadgeDescription(score)
    Row(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = description
        },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RatingBadgeChip(score)
        Text(
            text = ratingValueText(score),
            style = MaterialTheme.typography.labelMedium,
            color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onBackground else valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
