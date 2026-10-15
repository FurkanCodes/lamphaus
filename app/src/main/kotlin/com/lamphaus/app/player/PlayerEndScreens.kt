package com.lamphaus.app.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lamphaus.app.R
import com.lamphaus.app.ui.ReleaseKind
import com.lamphaus.app.ui.episodeAirDateText
import com.lamphaus.app.ui.releaseCountdownText
import com.lamphaus.core.model.Episode
import com.lamphaus.core.model.PlaybackRequest
import com.lamphaus.core.model.hasAired

/**
 * What the end of playback shows (PLY-AUTO-01). [secondsLeft] counts down to
 * closing the player when Settings → Close the player when no one answers is
 * on; null means the screen waits for an answer.
 */
internal sealed interface PlayerEndPrompt {
    val secondsLeft: Int?

    /** Ask before next episode: the next episode, Yes or No. */
    data class UpNext(override val secondsLeft: Int?) : PlayerEndPrompt

    /** After several automatic starts in a row: Continue or Stop. */
    data class StillWatching(override val secondsLeft: Int?) : PlayerEndPrompt

    /** A movie, or the last episode there is (or the next one has not aired): Watch again or Close. */
    data class Finished(override val secondsLeft: Int?) : PlayerEndPrompt
}

/** One answer on an end screen; the first one is the primary action and takes focus. */
internal data class PlayerEndAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/**
 * Nuvio's "Up next" at an episode's end when Ask before next episode is on:
 * the next episode, full screen, and Yes or No. Yes starts it on the source
 * already found and the screen says so until it plays; No or Back closes the
 * player. Spoiler protection veils the still and leaves the synopsis out.
 */
@Composable
internal fun UpNextPrompt(
    episode: Episode,
    secondsLeft: Int?,
    blurArtwork: Boolean,
    hideSynopsis: Boolean,
    isTelevision: Boolean,
    onYes: () -> Unit,
    onNo: () -> Unit,
    seriesTitle: String? = null,
    seriesLogoUrl: String? = null,
    progress: NextEpisodeProgress = NextEpisodeProgress.Idle,
    failureMessage: String? = null,
    onCancel: () -> Unit = {},
) {
    NextEpisodeEndScreen(
        label = stringResource(R.string.up_next_title),
        episode = episode,
        seriesTitle = seriesTitle,
        seriesLogoUrl = seriesLogoUrl,
        synopsis = episode.overview?.takeUnless { hideSynopsis || it.isBlank() },
        question = stringResource(R.string.up_next_question),
        secondsLeft = secondsLeft,
        blurArtwork = blurArtwork,
        isTelevision = isTelevision,
        accept = PlayerEndAction(stringResource(R.string.up_next_yes), Icons.Rounded.PlayArrow, onYes),
        decline = PlayerEndAction(stringResource(R.string.up_next_no), Icons.Rounded.Close, onNo),
        progress = progress,
        failureMessage = failureMessage,
        onCancel = onCancel,
    )
}

/**
 * Nuvio's "Still watching?": after several episodes started on their own,
 * the next waits for the viewer. Continue starts it, as Yes does on Up next;
 * Stop or Back closes the player.
 */
@Composable
internal fun StillWatchingPrompt(
    secondsLeft: Int?,
    isTelevision: Boolean,
    onContinue: () -> Unit,
    onStop: () -> Unit,
    nextEpisode: Episode? = null,
    blurArtwork: Boolean = false,
    seriesTitle: String? = null,
    seriesLogoUrl: String? = null,
    progress: NextEpisodeProgress = NextEpisodeProgress.Idle,
    failureMessage: String? = null,
    onCancel: () -> Unit = {},
) {
    val continueAction = PlayerEndAction(stringResource(R.string.still_watching_continue), Icons.Rounded.PlayArrow, onContinue)
    val stopAction = PlayerEndAction(stringResource(R.string.still_watching_stop), Icons.Rounded.Close, onStop)
    if (nextEpisode != null) {
        NextEpisodeEndScreen(
            label = stringResource(R.string.still_watching_title),
            episode = nextEpisode,
            seriesTitle = seriesTitle,
            seriesLogoUrl = seriesLogoUrl,
            synopsis = null,
            question = stringResource(R.string.still_watching_question),
            secondsLeft = secondsLeft,
            blurArtwork = blurArtwork,
            isTelevision = isTelevision,
            accept = continueAction,
            decline = stopAction,
            progress = progress,
            failureMessage = failureMessage,
            onCancel = onCancel,
        )
        return
    }
    PlayerEndPanel(
        label = stringResource(R.string.still_watching_title),
        title = stringResource(R.string.still_watching_title),
        detail = null,
        synopsis = null,
        question = stringResource(R.string.still_watching_question),
        secondsLeft = secondsLeft,
        isTelevision = isTelevision,
        actions = listOf(continueAction, stopAction),
        onBack = onStop,
    )
}

/**
 * An episode's end with the next one to start (PLY-AUTO-01): the screen goes
 * black and the next episode takes all of it, its still beside the series
 * logo or title, the episode's code and title, air date, and synopsis, over
 * the question and its answers. Once the answer that starts it is given the
 * screen stays, saying the source is being found and which one plays, with
 * Cancel back to the question (TV-NAV-04); a failure stays here, and the
 * answers try again. It scrolls rather than clips at 200% font (MOB-TYP-03).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NextEpisodeEndScreen(
    label: String,
    episode: Episode,
    seriesTitle: String?,
    seriesLogoUrl: String?,
    synopsis: String?,
    question: String,
    secondsLeft: Int?,
    blurArtwork: Boolean,
    isTelevision: Boolean,
    accept: PlayerEndAction,
    decline: PlayerEndAction,
    progress: NextEpisodeProgress,
    failureMessage: String?,
    onCancel: () -> Unit,
) {
    val starting = progress != NextEpisodeProgress.Idle
    val primaryFocus = remember { FocusRequester() }
    // The first answer takes focus, and again when Cancel replaces the answers or they return.
    LaunchedEffect(starting) { runCatching { primaryFocus.requestFocus() } }
    BackHandler(onBack = if (starting) onCancel else decline.onClick)
    val actions = if (starting) {
        listOf(PlayerEndAction(stringResource(R.string.cancel), Icons.Rounded.Close, onCancel))
    } else {
        listOf(accept, decline)
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // The video underneath takes no taps while the screen asks.
            .pointerInput(Unit) { detectTapGestures { } }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .semantics { paneTitle = label },
    ) {
        val stacked = !isTelevision && maxWidth < 600.dp
        val margin = if (isTelevision) 58.dp else 24.dp
        val still: @Composable (Modifier) -> Unit = { modifier ->
            NextEpisodeThumbnail(episode, blurArtwork, modifier.aspectRatio(16f / 9f))
        }
        val details: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                NextEpisodeEndDetails(label, episode, seriesTitle, seriesLogoUrl, synopsis, isTelevision)
                NextEpisodeEndStatus(progress, failureMessage, question, secondsLeft)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    actions.forEachIndexed { index, action ->
                        PlayerEndButton(
                            action = action,
                            primary = index == 0,
                            isTelevision = isTelevision,
                            modifier = if (index == 0) Modifier.focusRequester(primaryFocus) else Modifier,
                        )
                    }
                }
            }
        }
        if (stacked) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = margin, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                still(Modifier.fillMaxWidth())
                details(Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = margin, vertical = if (isTelevision) 32.dp else 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (isTelevision) 40.dp else 32.dp),
            ) {
                details(Modifier.weight(1f).verticalScroll(rememberScrollState()))
                still(Modifier.weight(1f))
            }
        }
    }
}

/** Label, the series logo or title, the episode's code and title, air date, and synopsis. */
@Composable
private fun NextEpisodeEndDetails(
    label: String,
    episode: Episode,
    seriesTitle: String?,
    seriesLogoUrl: String?,
    synopsis: String?,
    isTelevision: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            color = PlayerPrimary,
            fontFamily = PlayerFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            letterSpacing = 1.5.sp,
            modifier = Modifier.semantics { heading() },
        )
        var logoFailed by remember(seriesLogoUrl) { mutableStateOf(false) }
        if (!seriesLogoUrl.isNullOrBlank() && !logoFailed) {
            AsyncImage(
                model = seriesLogoUrl,
                contentDescription = seriesTitle,
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
                onError = { logoFailed = true },
                modifier = Modifier.widthIn(max = 320.dp).heightIn(max = 88.dp),
            )
        } else if (!seriesTitle.isNullOrBlank()) {
            Text(
                text = seriesTitle,
                style = if (isTelevision) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
                color = PlayerOnSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = episodeDisplayLabel(episode),
            style = if (isTelevision) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            color = PlayerOnSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        episodeAirDateText(episode.releasedAtEpochMillis)?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = PlayerOnSurfaceMuted)
        }
        synopsis?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge,
                color = PlayerOnSurfaceMuted,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The question and its countdown, or where starting the episode stands. */
@Composable
private fun NextEpisodeEndStatus(
    progress: NextEpisodeProgress,
    failureMessage: String?,
    question: String,
    secondsLeft: Int?,
) {
    val status = when (progress) {
        NextEpisodeProgress.Searching -> stringResource(R.string.next_episode_finding_source)
        is NextEpisodeProgress.Starting -> if (progress.secondsLeft > 0) {
            stringResource(R.string.next_episode_playing_via, progress.sourceName, progress.secondsLeft)
        } else {
            stringResource(R.string.next_episode_starting_via, progress.sourceName)
        }
        NextEpisodeProgress.Idle -> null
    }
    if (status != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = PlayerPrimary, strokeWidth = 2.dp)
            Text(text = status, style = MaterialTheme.typography.titleMedium, color = PlayerOnSurface)
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        failureMessage?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = PlayerOnSurface,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Text(text = question, style = MaterialTheme.typography.titleMedium, color = PlayerOnSurface)
        secondsLeft?.let {
            Text(
                text = pluralStringResource(R.plurals.up_next_closes_in, it, it),
                style = MaterialTheme.typography.bodyMedium,
                color = PlayerOnSurfaceMuted,
            )
        }
    }
}

/**
 * The end of a movie or of the last episode there is: it stays until the
 * viewer answers, instead of leaving them on a frozen last frame. A next
 * episode that has not aired yet says when it arrives (SHR-PROD-09).
 */
@Composable
internal fun FinishedPrompt(
    request: PlaybackRequest,
    secondsLeft: Int?,
    isTelevision: Boolean,
    onWatchAgain: () -> Unit,
    onClose: () -> Unit,
) {
    val episode = request.episode
    val next = request.nextEpisode
    val nextAired = remember(next) { next?.hasAired() == true }
    val unairedNext = next?.takeUnless { nextAired }
    val label = stringResource(
        when {
            // A movie, or an episode whose aired next one the viewer chose not to start.
            episode == null || (next != null && unairedNext == null) -> R.string.player_finished_movie
            unairedNext != null -> R.string.player_finished_next_soon
            else -> R.string.player_finished_series
        },
    )
    val question = when {
        episode == null || (next != null && unairedNext == null) -> null
        unairedNext != null -> listOfNotNull(
            episodeDisplayLabel(unairedNext),
            releaseCountdownText(unairedNext.releasedAtEpochMillis, ReleaseKind.EPISODE),
        ).joinToString(" · ")
        else -> stringResource(R.string.player_finished_series_body)
    }
    val logo = request.preview?.logoUrl
    val backdrop = request.preview?.backgroundUrl ?: request.artworkUrl
    PlayerEndPanel(
        label = label,
        title = request.title,
        detail = episode?.let { episodeDisplayLabel(it) },
        synopsis = null,
        question = question,
        secondsLeft = secondsLeft,
        isTelevision = isTelevision,
        actions = listOf(
            PlayerEndAction(stringResource(R.string.player_finished_watch_again), Icons.Rounded.Replay, onWatchAgain),
            PlayerEndAction(stringResource(R.string.player_finished_close), Icons.Rounded.Close, onClose),
        ),
        onBack = onClose,
        titleLogoUrl = logo,
        artwork = if (!backdrop.isNullOrBlank()) {
            { modifier ->
                AsyncImage(
                    model = backdrop,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = modifier.clip(RoundedCornerShape(if (isTelevision) 4.dp else 12.dp)),
                )
            }
        } else {
            null
        },
    )
}

/**
 * The shared end-of-playback layout, in the language of the player's side
 * panels: the last frame stays behind a start-edge scrim, and one column at
 * the safe margins holds a small label, the artwork, the title, one line of
 * detail, the question, and the answers (TV-LAY-01, PLY-IMM-03). It scrolls
 * rather than clips at 200% font (MOB-TYP-03); Back is the second answer.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerEndPanel(
    label: String,
    title: String,
    detail: String?,
    synopsis: String?,
    question: String?,
    secondsLeft: Int?,
    isTelevision: Boolean,
    actions: List<PlayerEndAction>,
    onBack: () -> Unit,
    titleLogoUrl: String? = null,
    artwork: (@Composable (Modifier) -> Unit)? = null,
) {
    val primaryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { primaryFocus.requestFocus() } }
    BackHandler(onBack = onBack)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 0.94f),
                    0.6f to Color.Black.copy(alpha = 0.72f),
                    1f to Color.Black.copy(alpha = 0.45f),
                ),
            )
            // The video underneath takes no taps while the screen asks.
            .pointerInput(Unit) { detectTapGestures { } }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .semantics { paneTitle = label },
    ) {
        val narrow = !isTelevision && maxWidth < 600.dp
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = if (isTelevision) 58.dp else 24.dp, vertical = if (isTelevision) 32.dp else 24.dp)
                .widthIn(max = if (isTelevision) 640.dp else 560.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            artwork?.invoke(
                (if (narrow) Modifier.fillMaxWidth() else Modifier.width(if (isTelevision) 360.dp else 320.dp))
                    .aspectRatio(16f / 9f),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = label,
                    color = PlayerPrimary,
                    fontFamily = PlayerFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.semantics { heading() },
                )
                if (!titleLogoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = titleLogoUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.CenterStart,
                        modifier = Modifier.widthIn(max = 320.dp).heightIn(max = 96.dp),
                    )
                } else {
                    Text(
                        text = title,
                        style = if (isTelevision) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
                        color = PlayerOnSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                detail?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleMedium,
                        color = PlayerOnSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                synopsis?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = PlayerOnSurfaceMuted,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (question != null || secondsLeft != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    question?.let {
                        Text(text = it, style = MaterialTheme.typography.titleMedium, color = PlayerOnSurface)
                    }
                    secondsLeft?.let {
                        Text(
                            text = pluralStringResource(R.plurals.up_next_closes_in, it, it),
                            style = MaterialTheme.typography.bodyMedium,
                            color = PlayerOnSurfaceMuted,
                        )
                    }
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                actions.forEachIndexed { index, action ->
                    PlayerEndButton(
                        action = action,
                        primary = index == 0,
                        isTelevision = isTelevision,
                        modifier = if (index == 0) Modifier.focusRequester(primaryFocus) else Modifier,
                    )
                }
            }
        }
    }
}

/** TV: the player's 4dp action with the approved focus fill (TV-FOC-02); touch: Material buttons, 48dp tall. */
@Composable
private fun PlayerEndButton(
    action: PlayerEndAction,
    primary: Boolean,
    isTelevision: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!isTelevision) {
        val content: @Composable () -> Unit = {
            Icon(action.icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Text(action.label, modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
        }
        if (primary) {
            Button(onClick = action.onClick, modifier = modifier.heightIn(min = 48.dp)) { content() }
        } else {
            OutlinedButton(onClick = action.onClick, modifier = modifier.heightIn(min = 48.dp)) { content() }
        }
        return
    }
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(4.dp)
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(
                when {
                    focused -> PlayerFocused
                    primary -> PlayerPrimary.copy(alpha = 0.22f)
                    else -> PlayerControlContainer
                },
            )
            .border(if (focused) 3.dp else 0.dp, if (focused) PlayerPrimary else Color.Transparent, shape)
            .clickable(role = Role.Button, onClick = action.onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val ink = if (focused) PlayerFocusedContent else PlayerOnSurface
        Icon(action.icon, contentDescription = null, tint = ink, modifier = Modifier.size(22.dp))
        Text(
            text = action.label,
            color = ink,
            fontFamily = PlayerFont,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
        )
    }
}
