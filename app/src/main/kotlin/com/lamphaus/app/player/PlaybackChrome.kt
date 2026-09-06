package com.lamphaus.app.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lamphaus.app.R
import com.lamphaus.app.mobile.LamphausMobileTheme
import com.lamphaus.core.model.PlaybackRequest
import com.lamphaus.core.model.PlaybackSegment
import androidx.media3.common.C

internal val LocalPlayerTelevision = staticCompositionLocalOf { false }

@Composable
internal fun PlaybackTheme(isTelevision: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPlayerTelevision provides isTelevision) {
        // Playback is an intentionally dark immersive surface (PLY-IMM-01/03).
        if (isTelevision) {
            val base = Typography()
            MaterialTheme(
                colorScheme = darkColorScheme(background = PlayerBackground, surface = PlayerBackground,
                    onSurface = PlayerOnSurface, onBackground = PlayerOnSurface,
                    primary = PlayerPrimary, onPrimary = PlayerOnPrimary, onSurfaceVariant = PlayerOnSurfaceMuted),
                typography = Typography(
                    headlineMedium = base.headlineMedium.copy(fontFamily = PlayerFont),
                    titleLarge = base.titleLarge.copy(fontFamily = PlayerFont),
                    titleMedium = base.titleMedium.copy(fontFamily = PlayerFont),
                    bodyLarge = base.bodyLarge.copy(fontFamily = PlayerFont),
                    bodyMedium = base.bodyMedium.copy(fontFamily = PlayerFont),
                    labelLarge = base.labelLarge.copy(fontFamily = PlayerFont),
                ),
                content = content,
            )
        } else LamphausMobileTheme(content)
    }
}

/** Nuvio's lower title → timeline → actions hierarchy, with platform-specific input. */
@Composable
internal fun PlayerControls(
    request: PlaybackRequest,
    snapshot: PlayerSnapshot,
    isTelevision: Boolean,
    onInteraction: () -> Unit,
    onTogglePlay: () -> Unit,
    onReplay: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onEnterPictureInPicture: () -> Unit,
    pictureInPictureAvailable: Boolean,
    canPlayNext: Boolean,
    nextEpisodeLoading: Boolean,
    onNextEpisode: () -> Unit,
    focusPanel: PlayerPanel?,
    focusRequestVersion: Long,
    onPanel: (PlayerPanel) -> Unit,
    segments: List<PlaybackSegment> = emptyList(),
    onExit: () -> Unit = {},
) {
    val playFocus = remember { FocusRequester() }
    val audioFocus = remember { FocusRequester() }
    val subtitlesFocus = remember { FocusRequester() }
    val moreFocus = remember { FocusRequester() }
    val ended = snapshot.durationMillis > 0 && snapshot.positionMillis >= snapshot.durationMillis
    val subtitlesActive = snapshot.tracks.groups.any { it.type == C.TRACK_TYPE_TEXT && it.isSelected }
    val playLabel = stringResource(when {
        ended -> R.string.player_replay
        snapshot.playing -> R.string.player_pause
        else -> R.string.player_play
    })
    val playIcon = when {
        ended -> Icons.Rounded.Replay
        snapshot.playing -> Icons.Rounded.Pause
        else -> Icons.Rounded.PlayArrow
    }
    val toggle = { if (ended) onReplay() else onTogglePlay(); onInteraction() }
    LaunchedEffect(focusRequestVersion) {
        if (isTelevision || focusRequestVersion > 0) {
            when (focusPanel) {
                PlayerPanel.AUDIO -> audioFocus
                PlayerPanel.SUBTITLES -> subtitlesFocus
                null -> playFocus
                else -> moreFocus
            }.requestFocus()
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().onPreviewKeyEvent { onInteraction(); false }) {
        if (isTelevision) {
            // TV-LAY-01: scale to the available window while retaining overscan-safe margins.
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .padding(horizontal = 58.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlayerTitle(request, true)
                PlayerProgress(
                    snapshot.positionMillis, snapshot.bufferedPositionMillis, snapshot.durationMillis,
                    Modifier.fillMaxWidth(), onSeekTo, onInteraction, segments,
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlayerActionButton(playIcon, playLabel, Modifier.focusRequester(playFocus),
                        visualSize = 32.dp, containerSize = 56.dp, primary = true, onClick = toggle)
                    PlayerActionButton(Icons.Rounded.Replay10, stringResource(R.string.player_rewind)) {
                        onSeekBack(); onInteraction()
                    }
                    PlayerActionButton(Icons.Rounded.Forward10, stringResource(R.string.player_forward)) {
                        onSeekForward(); onInteraction()
                    }
                    if (canPlayNext) PlayerActionButton(Icons.Rounded.SkipNext,
                        stringResource(R.string.player_next_episode)) {
                        if (!nextEpisodeLoading) onNextEpisode()
                        onInteraction()
                    }
                    PlayerSettingButton(Icons.Rounded.ClosedCaption, stringResource(R.string.player_subtitles),
                        subtitlesActive, Modifier.focusRequester(subtitlesFocus)) { onPanel(PlayerPanel.SUBTITLES) }
                    PlayerSettingButton(Icons.AutoMirrored.Rounded.VolumeUp, stringResource(R.string.player_audio),
                        false, Modifier.focusRequester(audioFocus)) { onPanel(PlayerPanel.AUDIO) }
                    PlayerActionButton(Icons.Rounded.MoreHoriz, stringResource(R.string.player_more),
                        Modifier.focusRequester(moreFocus)) { onPanel(PlayerPanel.MORE) }
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        PlayerTime(snapshot.positionMillis)
                        PlayerRemaining(snapshot.positionMillis, snapshot.durationMillis)
                    }
                }
            }
        } else {
            val compactHeight = maxHeight < 480.dp
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlayerActionButton(Icons.Rounded.Close, stringResource(R.string.player_close), onClick = onExit)
                PlayerTitle(request, false, Modifier.weight(1f))
                if (pictureInPictureAvailable) PlayerActionButton(Icons.Rounded.PictureInPictureAlt,
                    stringResource(R.string.player_pip), onClick = onEnterPictureInPicture)
                PlayerActionButton(Icons.Rounded.MoreHoriz, stringResource(R.string.player_more),
                    Modifier.focusRequester(moreFocus)) { onPanel(PlayerPanel.MORE) }
            }
            Row(
                Modifier.align(Alignment.Center).padding(bottom = if (compactHeight) 16.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (compactHeight) 24.dp else 32.dp),
            ) {
                PlayerActionButton(Icons.Rounded.Replay10, stringResource(R.string.player_rewind),
                    containerSize = 56.dp, visualSize = 32.dp) { onSeekBack(); onInteraction() }
                PlayerActionButton(playIcon, playLabel, Modifier.focusRequester(playFocus),
                    containerSize = if (compactHeight) 64.dp else 80.dp, visualSize = 40.dp,
                    primary = true, onClick = toggle)
                PlayerActionButton(Icons.Rounded.Forward10, stringResource(R.string.player_forward),
                    containerSize = 56.dp, visualSize = 32.dp) { onSeekForward(); onInteraction() }
            }
            Column(
                Modifier.align(Alignment.BottomCenter).widthIn(max = 840.dp).fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                PlayerProgress(snapshot.positionMillis, snapshot.bufferedPositionMillis, snapshot.durationMillis,
                    Modifier.fillMaxWidth(), onSeekTo, onInteraction, segments)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    PlayerTime(snapshot.positionMillis)
                    PlayerRemaining(snapshot.positionMillis, snapshot.durationMillis)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlayerSettingButton(Icons.AutoMirrored.Rounded.VolumeUp, stringResource(R.string.player_audio),
                        false, Modifier.weight(1f).focusRequester(audioFocus)) { onPanel(PlayerPanel.AUDIO) }
                    PlayerSettingButton(Icons.Rounded.ClosedCaption, stringResource(R.string.player_subtitles),
                        subtitlesActive, Modifier.weight(1f).focusRequester(subtitlesFocus)) { onPanel(PlayerPanel.SUBTITLES) }
                    if (canPlayNext) PlayerSettingButton(Icons.Rounded.SkipNext,
                        stringResource(R.string.player_next_episode), false, Modifier.weight(1f)) {
                        if (!nextEpisodeLoading) onNextEpisode()
                        onInteraction()
                    }
                }
            }
        }
    }
}

/** TV overlays live on the video. Touch pickers use a sheet, or a side pane in wide windows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerOverlayLayout(
    title: String,
    isTelevision: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    tvWidth: Dp = 844.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val availableHeight = maxHeight
        if (isTelevision) {
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(
                Color.Black.copy(alpha = .94f), Color.Black.copy(alpha = .72f), Color.Black.copy(alpha = .45f),
            ))))
            Column(
                modifier.align(Alignment.BottomStart).padding(horizontal = 58.dp, vertical = 32.dp)
                    .widthIn(max = tvWidth).fillMaxWidth().heightIn(max = maxHeight - 64.dp)
                    .semantics { paneTitle = title },
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(title, style = MaterialTheme.typography.headlineMedium, color = PlayerOnSurface,
                    modifier = Modifier.semantics { heading() })
                content()
            }
        } else if (maxWidth >= 600.dp) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .4f))
                .pointerInput(onClose) { detectTapGestures { onClose() } })
            Surface(
                modifier.align(Alignment.CenterEnd).windowInsetsPadding(WindowInsets.safeDrawing)
                    .width(420.dp).fillMaxHeight().semantics { paneTitle = title },
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    PlayerPanelHeading(title, onClose)
                    content()
                }
            }
        } else {
            ModalBottomSheet(
                onDismissRequest = onClose,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                Column(modifier.fillMaxWidth().heightIn(max = availableHeight * .85f)
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    PlayerPanelHeading(title, onClose)
                    content()
                }
            }
        }
    }
}

@Composable
private fun PlayerPanelHeading(title: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        IconButton(onClick = onClose) {
            Icon(Icons.Rounded.Close, stringResource(R.string.player_done))
        }
    }
}
