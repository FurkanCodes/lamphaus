package com.lamphaus.app.mobile

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import coil3.compose.AsyncImage
import com.lamphaus.app.R
import com.lamphaus.app.ui.releaseCountdownText
import com.lamphaus.app.ui.ReleaseKind
import com.lamphaus.app.ui.episodeAirDateText
import com.lamphaus.app.ui.ContentMenuOrigin
import com.lamphaus.app.ui.ContentMenuTarget
import com.lamphaus.app.ui.LocalArtworkResolver
import com.lamphaus.app.ui.MediaArtwork
import com.lamphaus.app.ui.RatingBadge
import com.lamphaus.app.ui.SpoilerBlurLayer
import com.lamphaus.app.ui.SpoilerContent
import com.lamphaus.app.ui.metadataImdbScore
import com.lamphaus.app.ui.metadataPresentation
import com.lamphaus.app.ui.openRatingPage
import com.lamphaus.app.ui.orderedRatingScores
import com.lamphaus.app.ui.ratingDetailsUrl
import com.lamphaus.app.ui.rememberReducedMotion
import com.lamphaus.app.ui.shouldBlur
import com.lamphaus.core.model.DetailEnrichment
import com.lamphaus.app.ui.SeasonTimeLeft
import com.lamphaus.app.ui.recapEpisodeTitle
import com.lamphaus.app.ui.seasonTimeLeft
import com.lamphaus.app.ui.seasonTimeLeftText
import com.lamphaus.app.ui.seriesRecap
import com.lamphaus.app.ui.nextUpEpisode
import com.lamphaus.app.ui.numberParts
import com.lamphaus.core.model.Episode
import com.lamphaus.core.model.MediaDetail
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.PersonCredit
import com.lamphaus.core.model.SpoilerProtectionSettings
import com.lamphaus.core.model.TrailerSource
import com.lamphaus.core.model.WatchProgress
import kotlin.math.roundToInt

private val Emphasized = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val ContentMaxWidth = 840.dp
private const val ENTER_MILLIS = 900

/**
 * Mobile details, Nuvio-style: a full-bleed backdrop that drifts in and
 * parallaxes away, a large title logo over a deep scrim, ratings from every
 * connected source, one primary Play/Resume with the time left, labelled
 * secondary actions, then episodes with stills, cast with photos, and more
 * like this. The top bar turns solid with the title once the hero scrolls
 * away. Sections arrive with a short staggered rise; reduced motion shows
 * them at once.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MobileDetailScreen(
    detail: MediaDetail?,
    enrichment: DetailEnrichment?,
    inLibrary: Boolean,
    watchedEpisodeIds: Set<String>,
    spoilerProtection: SpoilerProtectionSettings,
    resumeProgress: WatchProgress?,
    onBack: () -> Unit,
    onPlay: (Episode?) -> Unit,
    onLibrary: () -> Unit,
    onEditArtwork: () -> Unit,
    onOpenMedia: (MediaPreview) -> Unit,
    progress: List<WatchProgress>,
    onOpenMenu: (ContentMenuTarget) -> Unit,
    recapEnabled: Boolean = true,
    seasonTimeLeftEnabled: Boolean = true,
    trailersEnabled: Boolean = false,
    resolveTrailer: suspend (media: MediaPreview, maxHeight: Int, refresh: Boolean) -> TrailerSource? = { _, _, _ -> null },
    /** Opens a cast member's titles; null when person pages are unavailable (MOB-SRCH-01). */
    onOpenPerson: ((PersonCredit) -> Unit)? = null,
    /** Shows the one-time new-episode invitation under the actions (SHR-PROD-16). */
    newEpisodePrompt: Boolean = false,
    onEnableNewEpisodes: () -> Unit = {},
    onDismissNewEpisodes: () -> Unit = {},
    /** The tapped card's key; the hero is the other end of its container transform (MOB-MOT-01). */
    sharedKey: String? = null,
) {
    if (detail == null) return
    var trailerOpen by rememberSaveable(detail.preview.stableKey) { mutableStateOf(false) }
    if (trailerOpen) {
        MobileTrailerDialog(
            media = detail.preview,
            resolve = resolveTrailer,
            onDismiss = { trailerOpen = false },
        )
    }
    val artworkResolver = LocalArtworkResolver.current
    val preview = remember(detail.preview, artworkResolver) { artworkResolver.resolve(detail.preview).media }
    val reducedMotion = rememberReducedMotion()
    val enter = remember(detail.preview.stableKey) { Animatable(if (reducedMotion) ENTER_MILLIS.toFloat() else 0f) }
    LaunchedEffect(detail.preview.stableKey) {
        if (!reducedMotion) enter.animateTo(ENTER_MILLIS.toFloat(), tween(ENTER_MILLIS, easing = LinearEasing))
    }
    val menuTarget = ContentMenuTarget(
        media = detail.preview,
        progress = progress.firstOrNull { it.videoId == detail.preview.id },
        origin = ContentMenuOrigin.DETAIL,
    )
    val seasons = remember(detail) { detail.episodes.mapNotNull { it.season }.distinct().sorted() }
    val resumeEpisode = detail.episodes.firstOrNull { it.id == resumeProgress?.videoId }
    // A series always plays an episode (SHR-PROD-02): the one being resumed,
    // else the next up, as on TV. Without one, sources were asked for the
    // whole show and progress, Up next, and seek previews had no episode.
    val nextUp = remember(detail.episodes, progress, watchedEpisodeIds) {
        nextUpEpisode(detail.episodes, progress, watchedEpisodeIds)
    }
    val playEpisode = resumeEpisode ?: nextUp?.episode
    val recap = remember(detail.episodes, progress, watchedEpisodeIds, recapEnabled) {
        if (recapEnabled) seriesRecap(detail.episodes, progress, watchedEpisodeIds) else null
    }
    // The viewer's pick, if any; the default follows the episodes as they load
    // (the detail can arrive before its episode list).
    var pickedSeason by rememberSaveable(detail.preview.stableKey) { mutableStateOf<Int?>(null) }
    val selectedSeason = pickedSeason?.takeIf(seasons::contains)
        ?: resumeEpisode?.season?.takeIf(seasons::contains)
        ?: seasons.firstOrNull { it > 0 }
        ?: seasons.firstOrNull()
    val visibleEpisodes = remember(detail, selectedSeason) {
        detail.episodes
            .filter { selectedSeason == null || it.season == selectedSeason }
            .sortedWith(compareBy<Episode>({ it.season ?: 0 }, { it.episode ?: Int.MAX_VALUE }))
    }
    val ratings = orderedRatingScores(
        metadata = metadataImdbScore(preview.rating, preview.ratingSource, stringResource(R.string.source_imdb)),
        enrichment = enrichment?.ratings.orEmpty(),
    )
    val cast: List<PersonCredit> = enrichment?.cast?.takeIf { it.isNotEmpty() }
        ?: detail.cast.map { PersonCredit(name = plainName(it)) }
    val similar = enrichment?.similar.orEmpty()
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize().background(MobileTokens.ink)) {
        val heroHeight = (maxHeight * 0.66f).coerceIn(440.dp, 680.dp)
        val heroHeightPx = with(density) { heroHeight.toPx() }
        // One rise per section, in order, 90 ms apart.
        fun section(index: Int): Modifier = Modifier.graphicsLayer {
            val start = 180f + index * 90f
            val p = Emphasized.transform(((enter.value - start) / 520f).coerceIn(0f, 1f))
            alpha = p
            translationY = (1f - p) * 28.dp.toPx()
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            item(key = "hero", contentType = "hero") {
                DetailHero(
                    preview = preview,
                    height = heroHeight,
                    scrollOffset = {
                        if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else heroHeightPx
                    },
                    enter = { enter.value },
                    sharedKey = sharedKey,
                )
            }
            constrained("headline", section(0)) {
                DetailHeadline(detail, preview, ratings)
            }
            constrained("actions", section(1)) {
                DetailActions(
                    inLibrary = inLibrary,
                    resumeProgress = resumeProgress,
                    playEpisode = playEpisode,
                    onPlay = onPlay,
                    onLibrary = onLibrary,
                    onEditArtwork = onEditArtwork,
                    onOpenMenu = { onOpenMenu(menuTarget) },
                    onTrailer = { trailerOpen = true }.takeIf {
                        trailersEnabled && detail.preview.trailerYtIds.isNotEmpty()
                    },
                )
            }
            if (newEpisodePrompt) {
                constrained("new-episodes", section(1)) {
                    NewEpisodePromptCard(onTurnOn = onEnableNewEpisodes, onNotNow = onDismissNewEpisodes)
                }
            }
            recap?.let { episode ->
                constrained("recap", section(2)) {
                    DetailRecap(episode)
                }
            }
            constrained("synopsis", section(2)) {
                DetailSynopsis(detail)
            }
            if (detail.episodes.isNotEmpty()) {
                constrained("episodes-header", section(3)) {
                    val timeLeft = remember(detail, selectedSeason, progress, watchedEpisodeIds, seasonTimeLeftEnabled) {
                        if (seasonTimeLeftEnabled) {
                            seasonTimeLeft(detail.episodes, selectedSeason, progress, watchedEpisodeIds, detail.runtimeMinutes)
                        } else {
                            null
                        }
                    }
                    EpisodesHeader(seasons, selectedSeason, timeLeft, onSeason = { pickedSeason = it })
                }
                visibleEpisodes.forEach { episode ->
                    constrained("episode:${episode.id}", section(4), contentType = "episode") {
                        EpisodeCard(
                            media = detail.preview,
                            episode = episode,
                            watched = episode.id in watchedEpisodeIds,
                            spoilerProtection = spoilerProtection,
                            progress = progress.firstOrNull { it.videoId == episode.id },
                            onPlay = onPlay,
                            onOpenMenu = onOpenMenu,
                        )
                    }
                }
            }
            if (cast.isNotEmpty()) {
                item(key = "cast", contentType = "cast") {
                    Box(section(5)) { CastRow(cast, onOpenPerson) }
                }
            }
            if (similar.isNotEmpty()) {
                item(key = "similar", contentType = "similar") {
                    Box(section(6)) { SimilarRow(similar, onOpenMedia) }
                }
            }
            enrichment?.facts
                ?.takeIf { it.status != null || it.originalLanguage != null || (it.budgetUsd ?: 0) > 0 || (it.revenueUsd ?: 0) > 0 }
                ?.let { facts ->
                    constrained("facts", section(7)) { FactsGrid(facts) }
                }
        }

        DetailTopBar(
            title = preview.name,
            progress = {
                if (listState.firstVisibleItemIndex > 0) {
                    1f
                } else {
                    (listState.firstVisibleItemScrollOffset / (heroHeightPx * 0.72f)).coerceIn(0f, 1f)
                }
            },
            onBack = onBack,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FactsGrid(facts: com.lamphaus.core.model.MediaFacts) {
    val entries = listOfNotNull(
        facts.status?.let { stringResource(R.string.detail_status) to it },
        facts.originalLanguage?.let { stringResource(R.string.detail_language) to it.uppercase() },
        facts.budgetUsd?.takeIf { it > 0 }?.let { stringResource(R.string.detail_budget) to usd(it) },
        facts.revenueUsd?.takeIf { it > 0 }?.let { stringResource(R.string.detail_revenue) to usd(it) },
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.padding(top = 28.dp),
    ) {
        entries.forEach { (label, value) ->
            Column {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MobileTokens.textMuted)
                Text(value, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** A lazy item whose content keeps screen margins and a readable maximum width. */
private fun LazyListScope.constrained(
    key: String,
    modifier: Modifier,
    contentType: String = key,
    content: @Composable () -> Unit,
) {
    item(key = key, contentType = contentType) {
        Box(
            modifier
                .fillMaxWidth()
                .wrapContentWidth()
                .widthIn(max = ContentMaxWidth)
                .padding(horizontal = MobileTokens.spacingScreen),
        ) { content() }
    }
}

@Composable
private fun DetailHero(
    preview: MediaPreview,
    height: Dp,
    scrollOffset: () -> Float,
    enter: () -> Float,
    sharedKey: String? = null,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .mediaSharedBounds(sharedKey)
            .graphicsLayer { clip = true },
    ) {
        MediaArtwork(
            preview,
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Parallax: the backdrop moves at half the scroll speed and
                    // dims as it leaves; on open it settles from a slow push-in.
                    val offset = scrollOffset()
                    translationY = offset * 0.5f
                    alpha = 1f - (offset / size.height).coerceIn(0f, 1f) * 0.6f
                    val settle = Emphasized.transform((enter() / ENTER_MILLIS).coerceIn(0f, 1f))
                    val scale = 1.12f - 0.12f * settle
                    scaleX = scale
                    scaleY = scale
                },
            preferBackdrop = true,
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to MobileTokens.ink.copy(alpha = 0.55f),
                    0.2f to Color.Transparent,
                    0.5f to Color.Transparent,
                    0.82f to MobileTokens.ink.copy(alpha = 0.78f),
                    1f to MobileTokens.ink,
                ),
            ),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .padding(bottom = 8.dp)
                .graphicsLayer {
                    val p = Emphasized.transform(((enter() - 120f) / 600f).coerceIn(0f, 1f))
                    alpha = p
                    val scale = 0.94f + 0.06f * p
                    scaleX = scale
                    scaleY = scale
                },
            contentAlignment = Alignment.BottomCenter,
        ) {
            if (!preview.logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = preview.logoUrl,
                    contentDescription = preview.name,
                    modifier = Modifier.fillMaxWidth(0.78f).heightIn(max = 120.dp),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.BottomCenter,
                )
            } else {
                Text(
                    preview.name,
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
            }
        }
    }
}

@Composable
private fun DetailTopBar(title: String, progress: () -> Float, onBack: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {}
            .background(Color.Transparent),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = progress() }
                .background(MobileTokens.ink),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.padding(4.dp).size(48.dp),
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .graphicsLayer { alpha = 1f - progress() * 0.7f }
                        .background(Color.Black.copy(alpha = 0.42f), CircleShape),
                )
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = Color.White,
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp)
                    .graphicsLayer {
                        val p = ((progress() - 0.6f) / 0.4f).coerceIn(0f, 1f)
                        alpha = p
                        translationY = (1f - p) * 8.dp.toPx()
                    },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailHeadline(
    detail: MediaDetail,
    preview: MediaPreview,
    ratings: List<com.lamphaus.core.model.RatingSourceScore>,
) {
    val presentation = detail.metadataPresentation(maxGenres = 3)
    Column(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            val parts = buildList {
                (releaseCountdownText(presentation.upcomingReleaseMillis, ReleaseKind.TITLE) ?: presentation.year?.toString())
                    ?.let(::add)
                presentation.runtimeMinutes?.let { add(runtimeText(it)) }
            }
            parts.forEachIndexed { index, part ->
                if (index > 0) MetaDot()
                Text(part, style = MaterialTheme.typography.labelLarge, color = MobileTokens.textMuted)
            }
            presentation.contentRating?.let {
                if (parts.isNotEmpty()) MetaDot()
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MobileTokens.textPrimary,
                    modifier = Modifier
                        .border(1.dp, MobileTokens.textMuted.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
        if (presentation.genres.isNotEmpty()) {
            Text(
                presentation.genres.joinToString("  ·  "),
                style = MaterialTheme.typography.labelLarge,
                color = MobileTokens.textPrimary.copy(alpha = 0.86f),
                textAlign = TextAlign.Center,
            )
        }
        if (ratings.isNotEmpty()) {
            val context = LocalContext.current
            // Each badge opens the title's page on its source in the browser
            // (MOB-NAV-11); the 48dp targets carry their own spacing.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                ratings.forEach { score ->
                    val url = ratingDetailsUrl(score, detail.preview)
                    RatingBadge(
                        score,
                        valueColor = MobileTokens.textPrimary,
                        onClick = url?.let { { openRatingPage(context, it) } },
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun MetaDot() {
    Box(Modifier.size(3.dp).background(MobileTokens.textMuted, CircleShape))
}

@Composable
private fun DetailActions(
    inLibrary: Boolean,
    resumeProgress: WatchProgress?,
    playEpisode: Episode?,
    onPlay: (Episode?) -> Unit,
    onLibrary: () -> Unit,
    onEditArtwork: () -> Unit,
    onOpenMenu: () -> Unit,
    onTrailer: (() -> Unit)?,
) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = { onPlay(playEpisode) },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MobileTokens.textPrimary, contentColor = Color.Black),
        ) {
            Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = when {
                    resumeProgress == null -> {
                        val number = playEpisode?.numberParts()
                        if (number?.season != null && number.episode != null) {
                            stringResource(
                                R.string.play_episode_format,
                                stringResource(R.string.episode_format, number.season, number.episode),
                            )
                        } else {
                            stringResource(R.string.play)
                        }
                    }
                    resumeProgress.episodeLabel != null ->
                        stringResource(R.string.resume_episode_format, resumeProgress.episodeLabel ?: "")
                    else -> stringResource(R.string.resume)
                },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        resumeProgress?.takeIf { it.durationMillis > 0 }?.let { progress ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.16f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress.fraction)
                            .height(3.dp)
                            .background(MobileTokens.accent),
                    )
                }
                val leftMinutes = ((progress.durationMillis - progress.positionMillis).coerceAtLeast(0) / 60_000).toInt()
                Text(
                    stringResource(R.string.detail_time_left, runtimeText(leftMinutes.coerceAtLeast(1))),
                    style = MaterialTheme.typography.labelMedium,
                    color = MobileTokens.textMuted,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            LabeledAction(
                icon = if (inLibrary) Icons.Outlined.Check else Icons.Outlined.Add,
                label = stringResource(if (inLibrary) R.string.in_library else R.string.my_list),
                highlighted = inLibrary,
                onClick = onLibrary,
            )
            onTrailer?.let { LabeledAction(Icons.Outlined.Movie, stringResource(R.string.trailer), onClick = it) }
            LabeledAction(Icons.Outlined.Edit, stringResource(R.string.detail_artwork), onClick = onEditArtwork)
            LabeledAction(Icons.Outlined.MoreHoriz, stringResource(R.string.content_menu_more), onClick = onOpenMenu)
        }
    }
}

@Composable
private fun LabeledAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    Column(
        Modifier
            .widthIn(min = 88.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = if (highlighted) MobileTokens.accent else MobileTokens.textPrimary, modifier = Modifier.size(26.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MobileTokens.textMuted, maxLines = 1)
    }
}

/** "Previously": the last finished episode, for a viewer returning to a series (SHR-PROD-13). */
@Composable
private fun DetailRecap(episode: Episode) {
    Column(
        Modifier.fillMaxWidth().padding(top = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(R.string.recap_heading),
            style = MaterialTheme.typography.labelLarge,
            color = MobileTokens.accent,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            recapEpisodeTitle(episode),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MobileTokens.textPrimary,
        )
        Text(
            episode.overview.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MobileTokens.textMuted,
        )
    }
}

@Composable
private fun DetailSynopsis(detail: MediaDetail) {
    val overview = detail.preview.description?.takeIf(String::isNotBlank)
    var expanded by rememberSaveable(detail.preview.stableKey) { mutableStateOf(false) }
    var overflows by remember(detail.preview.stableKey) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(top = 20.dp).animateContentSize(tween(220)),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        overview?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.08f),
                color = MobileTokens.textPrimary.copy(alpha = 0.9f),
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { layout -> if (!expanded) overflows = layout.hasVisualOverflow },
                modifier = Modifier.clickable(enabled = overflows || expanded) { expanded = !expanded },
            )
            if (overflows || expanded) {
                Text(
                    stringResource(if (expanded) R.string.detail_less else R.string.detail_more),
                    style = MaterialTheme.typography.labelLarge,
                    color = MobileTokens.accent,
                    modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 4.dp),
                )
            }
        }
        if (detail.directors.isNotEmpty()) {
            CreditLine(stringResource(R.string.directors), detail.directors.joinToString(", ") { plainName(it) })
        }
    }
}

@Composable
private fun CreditLine(label: String, value: String) {
    Text(
        text = androidx.compose.ui.text.buildAnnotatedString {
            pushStyle(androidx.compose.ui.text.SpanStyle(color = MobileTokens.textMuted))
            append("$label  ")
            pop()
            append(value)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MobileTokens.textPrimary.copy(alpha = 0.86f),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun EpisodesHeader(seasons: List<Int>, selected: Int?, timeLeft: SeasonTimeLeft?, onSeason: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.episodes),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.semantics { heading() },
            )
            // SHR-PROD-14: what is left of the season on view.
            timeLeft?.let {
                Text(
                    seasonTimeLeftText(it),
                    style = MaterialTheme.typography.labelMedium,
                    color = MobileTokens.textMuted,
                )
            }
        }
        if (seasons.size > 1 && selected != null) {
            var open by remember { mutableStateOf(false) }
            Box {
                TextButton(
                    onClick = { open = true },
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MobileTokens.surfaceRaised),
                ) {
                    Text(
                        seasonLabel(selected),
                        style = MaterialTheme.typography.labelLarge,
                        color = MobileTokens.textPrimary,
                    )
                    Icon(Icons.Outlined.ArrowDropDown, null, tint = MobileTokens.textPrimary)
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    seasons.forEach { season ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    seasonLabel(season),
                                    fontWeight = if (season == selected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            },
                            onClick = {
                                onSeason(season)
                                open = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun seasonLabel(season: Int): String =
    if (season == 0) stringResource(R.string.detail_specials) else stringResource(R.string.season_format, season)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EpisodeCard(
    media: MediaPreview,
    episode: Episode,
    watched: Boolean,
    spoilerProtection: SpoilerProtectionSettings,
    progress: WatchProgress?,
    onPlay: (Episode?) -> Unit,
    onOpenMenu: (ContentMenuTarget) -> Unit,
) {
    val artworkHidden = spoilerProtection.shouldBlur(SpoilerContent.EPISODE_ARTWORK, watched)
    val synopsisHidden = spoilerProtection.shouldBlur(SpoilerContent.EPISODE_SYNOPSIS, watched)
    val haptics = LocalHapticFeedback.current
    // Unaired: "Airs in 3 days"; aired: the date (Nuvio's air-date badge).
    val airDate = episodeAirDateText(episode.releasedAtEpochMillis)
    val watchedDescription = if (watched) stringResource(R.string.watched) else ""
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                role = Role.Button,
                onClick = { onPlay(episode) },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onOpenMenu(ContentMenuTarget(media = media, episode = episode, progress = progress, origin = ContentMenuOrigin.EPISODE))
                },
            )
            .semantics { stateDescription = watchedDescription }
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(148.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MobileTokens.surfaceRaised),
            ) {
                if (!episode.thumbnailUrl.isNullOrBlank()) {
                    SpoilerBlurLayer(
                        hidden = artworkHidden,
                        veilColor = MobileTokens.surface,
                        semanticLabel = stringResource(R.string.spoiler_hidden),
                        modifier = Modifier.fillMaxSize(),
                        veilContent = {
                            Icon(
                                Icons.Outlined.Visibility,
                                null,
                                tint = MobileTokens.textMuted,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        },
                        content = {
                            AsyncImage(
                                model = episode.thumbnailUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        },
                    )
                }
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f)),
                    ),
                )
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(34.dp)
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                progress?.fraction?.takeIf { it > 0f && !watched }?.let { fraction ->
                    Box(
                        Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.25f)),
                    ) {
                        Box(Modifier.fillMaxWidth(fraction).height(3.dp).background(MobileTokens.accent))
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    listOfNotNull(episode.episode?.let { "$it." }, episode.title).joinToString(" "),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (watched) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = MobileTokens.accent, modifier = Modifier.size(14.dp))
                    }
                    airDate?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MobileTokens.textMuted) }
                }
            }
        }
        // A hidden synopsis leaves no placeholder line under every card.
        episode.overview?.takeIf { !synopsisHidden && it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MobileTokens.textMuted,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        modifier = modifier
            .padding(horizontal = MobileTokens.spacingScreen)
            .semantics { heading() },
    )
}

@Composable
private fun CastRow(cast: List<PersonCredit>, onOpenPerson: ((PersonCredit) -> Unit)?) {
    Column(Modifier.fillMaxWidth().padding(top = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle(stringResource(R.string.cast))
        LazyRow(
            contentPadding = PaddingValues(horizontal = MobileTokens.spacingScreen),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(cast.take(20), key = { it.personId ?: it.name }) { person ->
                // Only TMDB-resolved people have titles to show; provider-only
                // names stay plain text rather than leading nowhere.
                val open = onOpenPerson?.takeIf { person.personId != null }
                val openLabel = stringResource(R.string.person_open_titles)
                Column(
                    Modifier
                        .width(84.dp)
                        .then(
                            if (open != null) {
                                Modifier
                                    .clip(RoundedCornerShape(MobileTokens.radiusCard))
                                    .clickable(onClickLabel = openLabel, role = Role.Button) { open(person) }
                            } else {
                                Modifier
                            },
                        )
                        .semantics(mergeDescendants = true) {},
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(MobileTokens.surfaceRaised),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            person.name.split(' ').mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString(""),
                            style = MaterialTheme.typography.titleMedium,
                            color = MobileTokens.textMuted,
                        )
                        if (!person.profileUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = person.profileUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        person.name,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    person.role?.takeIf(String::isNotBlank)?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MobileTokens.textMuted,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SimilarRow(similar: List<MediaPreview>, onOpenMedia: (MediaPreview) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle(stringResource(R.string.similar_title))
        LazyRow(
            contentPadding = PaddingValues(horizontal = MobileTokens.spacingScreen),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(similar, key = { it.stableKey }) { media ->
                Column(
                    Modifier
                        .width(116.dp)
                        .clip(RoundedCornerShape(MobileTokens.radiusCard))
                        .clickable(role = Role.Button) { onOpenMedia(media) },
                ) {
                    MediaArtwork(
                        media,
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(MobileTokens.radiusCard)),
                    )
                    Text(
                        media.name,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun runtimeText(minutes: Int): String = when {
    minutes >= 60 && minutes % 60 == 0 -> stringResource(R.string.detail_hours, minutes / 60)
    minutes >= 60 -> stringResource(R.string.detail_hours_minutes, minutes / 60, minutes % 60)
    else -> stringResource(R.string.minutes_format, minutes)
}

private fun usd(value: Long): String = when {
    value >= 1_000_000_000 -> "$" + "%.1fB".format(value / 1e9)
    value >= 1_000_000 -> "$" + "${(value / 1e6).roundToInt()}M"
    else -> "$" + "%,d".format(value)
}

private fun plainName(value: String): String =
    HtmlCompat.fromHtml(value, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()

