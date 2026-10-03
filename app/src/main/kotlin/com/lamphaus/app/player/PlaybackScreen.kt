package com.lamphaus.app.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.ui.focus.focusRestorer
import com.lamphaus.app.tv.LamphausTvTheme
import com.lamphaus.app.tv.TvFilterChip
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.lamphaus.app.R
import com.lamphaus.app.ui.SpoilerContent
import com.lamphaus.app.ui.rememberReducedMotion
import com.lamphaus.app.ui.shouldBlur
import com.lamphaus.core.model.PlaybackRequest
import com.lamphaus.core.model.SubtitleCue
import com.lamphaus.core.model.SubtitleEdgeStyle
import com.lamphaus.core.model.SubtitleFontFamily
import com.lamphaus.core.model.SubtitleStyle
import com.lamphaus.core.model.SubtitleStylePolicy
import com.lamphaus.core.model.stepAudioDelay
import com.lamphaus.core.model.stepSubtitleDelay
import com.lamphaus.core.model.clampSubtitleDelayMillis
import com.lamphaus.core.model.PlaybackSegment
import com.lamphaus.core.model.PlaybackSegmentType
import com.lamphaus.core.model.PlaybackSettings
import com.lamphaus.core.model.NextEpisodePolicy
import com.lamphaus.core.model.SkipSegmentPolicy
import com.lamphaus.core.model.DisplayModeCandidate
import com.lamphaus.core.model.MediaType
import com.lamphaus.core.model.SpoilerProtectionSettings
import com.lamphaus.core.model.hasAired
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

internal val PlayerBackground = Color(0xFF121316)
internal val PlayerOnSurface = Color(0xFFE3E2E6)
internal val PlayerOnSurfaceMuted = Color(0xFFC4C6CF)
internal val PlayerSurface = Color(0xFF292A2D)
internal val PlayerFocused = Color(0xFFE3E2E6)
internal val PlayerFocusedContent = Color(0xFF2F3033)
internal val PlayerPrimary = Color(0xFFA8C8FF)
internal val PlayerOnPrimary = Color(0xFF003062)
// Tonal control container (TV-TOK-02 default 10% white) keeps ghost buttons legible
// over video without adding glass or saturated chrome (SHR-PROD-01/03).
internal val PlayerControlContainer = Color.White.copy(alpha = 0.10f)
internal val PlayerTrack = Color.White.copy(alpha = 0.28f)
internal val PlayerBuffered = Color.White.copy(alpha = 0.45f)
internal val PlayerFont = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semi_bold, FontWeight.SemiBold),
)

internal enum class PlayerPanel { AUDIO, SUBTITLES, SPEED, DISPLAY, MORE, INFO, SOURCES, EPISODES }

internal enum class PlaybackStartupPhase { LOADING, READY, FAILED }

private enum class PlayerEditor { TIMING, STYLE }

internal data class PlayerSnapshot(
    val playing: Boolean = false,
    val buffering: Boolean = true,
    val positionMillis: Long = 0,
    val bufferedPositionMillis: Long = 0,
    val durationMillis: Long = 0,
    val tracks: Tracks = Tracks.EMPTY,
    val speed: Float = 1f,
    val errorMessage: String? = null,
)

private data class TrackOption(
    val id: String,
    val title: String,
    val supportingText: String?,
    val selected: Boolean,
    val supported: Boolean,
    val languageKey: String,
    val group: TrackGroup,
    val trackIndex: Int,
    /** Codec, layout, and role badges (SRT, 5.1, Forced, SDH, Default). */
    val badges: List<String> = emptyList(),
    /** Where the track comes from: the stream itself, or a subtitle add-on. */
    val origin: TrackOrigin = TrackOrigin.BuiltIn,
    val format: androidx.media3.common.Format? = null,
)

/** A subtitle's source, shown as a chip and used as a filter (Nuvio's Built-in / add-on tabs). */
private sealed interface TrackOrigin {
    val key: String

    data object BuiltIn : TrackOrigin {
        override val key = "built-in"
    }

    /** [name] is the add-on's display name; null when the add-on did not give one. */
    data class Addon(val name: String?) : TrackOrigin {
        override val key = "addon:${name.orEmpty()}"
    }
}

private data class SubtitleLanguageRailItem(
    val key: String,
    val label: String,
    val trackCount: Int,
)

@Composable
internal fun PlaybackScreen(
    request: PlaybackRequest,
    player: Player?,
    isTelevision: Boolean,
    settings: PlaybackSettings,
    segments: List<PlaybackSegment>,
    nextEpisodeProgress: NextEpisodeProgress,
    nextEpisodeMessage: String?,
    onExit: () -> Unit,
    onOpenExternally: () -> Unit,
    onNextEpisode: () -> Unit,
    onDismissNextEpisodeMessage: () -> Unit,
    spoilerProtection: SpoilerProtectionSettings,
    nextEpisodeDismissed: Boolean,
    onDismissNextEpisodeCard: () -> Unit,
    onPlayerViewLayout: (android.view.View) -> Unit,
    onEnterPictureInPicture: () -> Unit,
    pictureInPictureAvailable: Boolean,
    inPictureInPicture: Boolean,
    subtitleStyle: SubtitleStyle,
    subtitleDelayMillis: Long,
    audioDelayMillis: Long,
    streamInfo: String?,
    streamStats: () -> com.lamphaus.core.player.PlaybackStats? = { null },
    onSubtitleDelay: (Long) -> Unit,
    onAudioDelay: (Long) -> Unit,
    onSubtitleStyle: (SubtitleStyle) -> Unit,
    onLoadSidecarCues: ((List<SubtitleCue>) -> Unit) -> Unit,
    onApplySyncByLine: (Long, Long) -> Unit,
    onControlsVisibilityChanged: (Boolean) -> Unit = {},
    onSubtitleLiftChanged: (Float) -> Unit = {},
    startupPhase: PlaybackStartupPhase = PlaybackStartupPhase.READY,
    matchedDisplayMode: DisplayModeCandidate? = null,
    sources: PlayerSourcesState = PlayerSourcesState(),
    onLoadSources: () -> Unit = {},
    onSelectSource: (PlayerSourceOption) -> Unit = {},
    /** The end-of-playback screen showing now, if any (PLY-AUTO-01). */
    endPrompt: PlayerEndPrompt? = null,
    onStillWatchingContinue: () -> Unit = {},
    onStillWatchingStop: () -> Unit = {},
    onUpNextYes: () -> Unit = {},
    onUpNextNo: () -> Unit = {},
    /** Finished screen: play this title again from the start. */
    onWatchAgain: () -> Unit = {},
    episodeSwitch: EpisodeSwitchState? = null,
    onSelectEpisode: (com.lamphaus.core.model.Episode) -> Unit = {},
    /** Audio follows the profile defaults (the panel's Automatic row); null reads the player's overrides. */
    audioFollowsDefaults: Boolean? = null,
    /** The viewer picked a track (null format: subtitles off). */
    onTrackChosen: ((trackType: Int, format: androidx.media3.common.Format?) -> Unit)? = null,
    /** The viewer chose Automatic: hand the track type back to the defaults. */
    onTrackDefaults: ((trackType: Int) -> Unit)? = null,
) {
    val nextEpisodeLoading = nextEpisodeProgress != NextEpisodeProgress.Idle
    var snapshot by remember(player) { mutableStateOf(player?.snapshot() ?: PlayerSnapshot()) }
    var controlsVisible by remember { mutableStateOf(true) }
    var resizeMode by rememberSaveable { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var panel by remember { mutableStateOf<PlayerPanel?>(null) }
    var locked by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val hostActivity = context as? Activity
    var brightnessFraction by remember { mutableFloatStateOf(systemBrightnessFraction(context)) }
    var volumeFraction by remember { mutableFloatStateOf(mediaVolumeFraction(context)) }
    var hudText by remember { mutableStateOf<String?>(null) }
    var hudIcon by remember { mutableStateOf<ImageVector?>(null) }
    var seekTarget by remember { mutableStateOf<Long?>(null) }
    var liveSubtitleCues by remember(player) {
        mutableStateOf(player?.currentCues?.cues.orEmpty())
    }

    fun showHud(icon: ImageVector, text: String) {
        hudIcon = icon
        hudText = text
    }

    // Remote seeks accumulate into one target while keys keep arriving, then
    // seek once: tap-tap-tap is a single +30 s jump, and a held key speeds up
    // (Nuvio's scrub rates) without a rebuffer per repeat.
    var keySeekTarget by remember { mutableStateOf<Long?>(null) }
    fun keySeek(forward: Boolean, repeatCount: Int) {
        val activePlayer = player ?: return
        val step = remoteSeekStepMillis(repeatCount)
        val base = keySeekTarget ?: activePlayer.currentPosition
        val duration = snapshot.durationMillis.takeIf { it > 0 } ?: Long.MAX_VALUE
        val next = (base + if (forward) step else -step).coerceIn(0L, duration)
        keySeekTarget = next
        showHud(
            if (forward) Icons.Rounded.FastForward else Icons.Rounded.FastRewind,
            next.asPlaybackTime(),
        )
    }
    LaunchedEffect(keySeekTarget) {
        val target = keySeekTarget ?: return@LaunchedEffect
        delay(REMOTE_SEEK_COMMIT_DELAY_MILLIS)
        player?.seekTo(target)
        keySeekTarget = null
    }

    LaunchedEffect(hudText) {
        if (hudText != null) {
            delay(900)
            hudText = null
        }
    }
    var editor by remember { mutableStateOf<PlayerEditor?>(null) }
    var panelParent by remember { mutableStateOf<PlayerPanel?>(null) }
    var returnFocusPanel by remember { mutableStateOf<PlayerPanel?>(null) }
    var controlsFocusVersion by remember { mutableLongStateOf(0L) }
    var interactionVersion by remember { mutableLongStateOf(0L) }
    val rootFocus = remember { FocusRequester() }
    val reducedMotion = rememberReducedMotion()
    val startupLoading = startupPhase == PlaybackStartupPhase.LOADING
    // Media3 reports BUFFERING for every seek and every momentary stall. Only
    // buffering that persists is shown; a blip keeps the last frame on screen.
    val stalled = snapshot.buffering && snapshot.errorMessage == null
    var rebufferIndicatorVisible by remember { mutableStateOf(false) }
    var sustainedRebuffer by remember { mutableStateOf(false) }
    LaunchedEffect(stalled) {
        rebufferIndicatorVisible = false
        sustainedRebuffer = false
        if (!stalled) return@LaunchedEffect
        delay(PlayerChromeTokens.RebufferIndicatorDelayMillis)
        rebufferIndicatorVisible = true
        delay(PlayerChromeTokens.RebufferSurfaceDelayMillis - PlayerChromeTokens.RebufferIndicatorDelayMillis)
        sustainedRebuffer = true
    }
    // The engine re-prepares transient network errors on its own, so an error
    // is only surfaced once it outlives that retry (SHR-PROD-04).
    var shownError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(snapshot.errorMessage) {
        val message = snapshot.errorMessage
        if (message != null && shownError == null) delay(PlayerChromeTokens.ErrorRevealDelayMillis)
        shownError = message
    }
    // TV startup and sustained rebuffering during clean viewing share the stable,
    // artwork-led surface. While the viewer operates the chrome, the frame and
    // chrome stay put and the compact indicator reports progress instead.
    val rebufferSurfaceVisible = isTelevision && sustainedRebuffer && !controlsVisible && panel == null
    val loadingSurfaceVisible = startupLoading || rebufferSurfaceVisible
    // TV: a few idle seconds paused trade the chrome for the title and
    // synopsis over the dimmed frame (Nuvio-style). Any key brings it back.
    var pauseOverlayVisible by remember { mutableStateOf(false) }
    LaunchedEffect(isTelevision, snapshot.playing, stalled, panel, interactionVersion, shownError, startupLoading, inPictureInPicture) {
        pauseOverlayVisible = false
        if (!isTelevision || snapshot.playing || stalled || panel != null || shownError != null ||
            startupLoading || inPictureInPicture
        ) {
            return@LaunchedEffect
        }
        delay(PAUSE_OVERLAY_DELAY_MILLIS)
        controlsVisible = false
        pauseOverlayVisible = true
    }
    val windowWidthDp = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width.toDp()
    }
    val wideLayout = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE ||
        windowWidthDp >= 600.dp
    val landscapeOrientation = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val skippableSegments = segments.filter { segment ->
        when (segment.type) {
            PlaybackSegmentType.INTRO, PlaybackSegmentType.RECAP -> settings.skipIntroEnabled
            PlaybackSegmentType.ENDING -> settings.skipEndingEnabled
            PlaybackSegmentType.POST_CREDITS -> true
        }
    }
    val activeSegment = SkipSegmentPolicy.activeSegment(skippableSegments, snapshot.positionMillis, snapshot.durationMillis)
    val skipTargetsPostCredits = activeSegment != null &&
        SkipSegmentPolicy.postCreditsSceneAfter(activeSegment, segments, snapshot.durationMillis) != null
    var dismissedSegment by remember(request.videoId) { mutableStateOf<PlaybackSegment?>(null) }
    // A skip button or next-episode card holding TV focus receives Select and
    // left/right itself instead of the hidden-chrome seek shortcuts.
    var cueFocused by remember { mutableStateOf(false) }
    val nextEpisode = request.nextEpisode
    val timingReady = NextEpisodePolicy.shouldShowCard(
        positionMillis = snapshot.positionMillis,
        durationMillis = snapshot.durationMillis,
        segments = segments,
        thresholdMode = settings.nextEpisodeThresholdMode,
        thresholdPercent = settings.nextEpisodeThresholdPercent,
        thresholdMinutesBeforeEnd = settings.nextEpisodeThresholdMinutesBeforeEnd,
    )
    val nextEpisodeReady = settings.nextEpisodeEnabled &&
        nextEpisode != null &&
        nextEpisode.hasAired() &&
        timingReady
    val nextEpisodeCardVisible = nextEpisodeReady && !nextEpisodeDismissed
    val endPromptShown = endPrompt != null && !inPictureInPicture
    // The end screen replaces the chrome; Watch again brings the chrome back with playback.
    LaunchedEffect(endPromptShown) { if (endPromptShown) controlsVisible = false }
    val nextEpisodeSkipInCard = nextEpisodeCardVisible && !wideLayout && !isTelevision &&
        activeSegment?.type == PlaybackSegmentType.ENDING

    fun revealControls() {
        controlsVisible = true
        interactionVersion++
    }

    fun closePanel() {
        if (panelParent != null) {
            panel = panelParent
            panelParent = null
            return
        }
        panel = null
        editor = null
        controlsFocusVersion++
        revealControls()
    }

    fun closeEditor() {
        editor = null
        revealControls()
    }

    DisposableEffect(player) {
        if (player == null) return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                snapshot = player.snapshot()
            }

            override fun onPlayerError(error: PlaybackException) {
                snapshot = player.snapshot().copy(errorMessage = error.safeMessage())
            }

            override fun onCues(cueGroup: CueGroup) {
                liveSubtitleCues = cueGroup.cues
            }
        }
        player.addListener(listener)
        snapshot = player.snapshot()
        onDispose { player.removeListener(listener) }
    }

    // Position also drives skip/next-episode prompts while chrome is hidden (QA-06).
    LaunchedEffect(player, inPictureInPicture) {
        if (player == null || inPictureInPicture) return@LaunchedEffect
        while (isActive) {
            snapshot = player.snapshot()
            delay(500)
        }
    }

    LaunchedEffect(controlsVisible, panel, snapshot.playing, interactionVersion) {
        if (controlsVisible && panel == null && snapshot.playing && snapshot.errorMessage == null) {
            delay(PlayerChromeTokens.AutoHideMillis)
            controlsVisible = false
        }
    }

    LaunchedEffect(controlsVisible, panel) {
        if (!controlsVisible && panel == null) rootFocus.requestFocus()
    }

    LaunchedEffect(loadingSurfaceVisible, controlsVisible, inPictureInPicture, panel) {
        onControlsVisibilityChanged(!loadingSurfaceVisible && !inPictureInPicture && (controlsVisible || panel != null))
    }

    // One lift value drives both engines: Media3 through the subtitle view below,
    // MPV through the session command (PLY-IMM-03, PLY-IMM-04).
    LaunchedEffect(loadingSurfaceVisible, controlsVisible, panel, inPictureInPicture) {
        onSubtitleLiftChanged(
            if (!loadingSurfaceVisible && !inPictureInPicture && (controlsVisible || panel != null)) {
                PlayerChromeTokens.SubtitleLiftFraction
            } else {
                0f
            },
        )
    }

    LaunchedEffect(inPictureInPicture) {
        if (inPictureInPicture) {
            controlsVisible = false
            panel = null
            editor = null
        }
    }

    BackHandler {
        when {
            startupLoading -> onExit()
            pauseOverlayVisible -> revealControls()
            // Back unwinds editor -> submenu -> controls -> exit (plan §5),
            // restoring the originating focus at each layer. Lock is one layer.
            locked -> locked = false
            editor != null -> closeEditor()
            panel != null -> closePanel()
            nextEpisodeCardVisible -> onDismissNextEpisodeCard()
            shownError != null -> onExit()
            controlsVisible -> controlsVisible = false
            else -> onExit()
        }
    }

    PlaybackTheme(isTelevision) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || player == null || startupLoading) return@onPreviewKeyEvent false
                // "Still watching?" and "Up next" own the remote: their buttons take Select and the D-pad.
                if (endPromptShown) return@onPreviewKeyEvent false
                // Any key is interaction: it restarts the auto-hide and pause-overlay timers.
                interactionVersion++
                if (cueFocused && !controlsVisible && event.key in CUE_KEYS) return@onPreviewKeyEvent false
                if (pauseOverlayVisible && event.key !in PLAY_PAUSE_KEYS) {
                    // The first key only dismisses the overlay; it never seeks or moves focus.
                    revealControls()
                    return@onPreviewKeyEvent true
                }
                when (event.key) {
                    Key.MediaPlayPause -> {
                        if (snapshot.playing) player.pause() else player.play()
                        revealControls()
                        true
                    }
                    Key.MediaPlay -> {
                        player.play()
                        revealControls()
                        true
                    }
                    Key.MediaPause -> {
                        player.pause()
                        revealControls()
                        true
                    }
                    Key.MediaRewind -> {
                        keySeek(forward = false, event.nativeKeyEvent.repeatCount)
                        revealControls()
                        true
                    }
                    Key.MediaFastForward -> {
                        keySeek(forward = true, event.nativeKeyEvent.repeatCount)
                        revealControls()
                        true
                    }
                    Key.DirectionCenter, Key.Enter -> if (!controlsVisible) {
                        revealControls()
                        true
                    } else false
                    // A seek that started with the chrome hidden keeps the
                    // D-pad until it commits, so holding never wanders focus.
                    Key.DirectionLeft -> if (!controlsVisible || keySeekTarget != null) {
                        keySeek(forward = false, event.nativeKeyEvent.repeatCount)
                        revealControls()
                        true
                    } else false
                    Key.DirectionRight -> if (!controlsVisible || keySeekTarget != null) {
                        keySeek(forward = true, event.nativeKeyEvent.repeatCount)
                        revealControls()
                        true
                    } else false
                    Key.DirectionUp, Key.DirectionDown -> if (!controlsVisible) {
                        // Keep every D-pad direction live: hidden controls are
                        // revealed instead of the key press landing nowhere.
                        revealControls()
                        true
                    } else false
                    else -> false
                }
            }
            .pointerInput(player, locked) {
                detectTapGestures(onTap = {
                    if (!locked) {
                        controlsVisible = !controlsVisible
                        panel = null
                        interactionVersion++
                    }
                })
            },
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    check(videoSurfaceView is SurfaceView) {
                        "Playback must use SurfaceView so Media3 can preserve HDR and frame timing"
                    }
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    setKeepContentOnPlayerReset(true)
                    subtitleView?.setUserDefaultTextSize()
                    addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ -> onPlayerViewLayout(view) }
                }
            },
            update = { view ->
                view.resizeMode = resizeMode
                view.player = player
                view.keepScreenOn = player?.isPlaying == true
                view.subtitleView?.visibility = if (isTelevision && panel == PlayerPanel.SUBTITLES) {
                    View.INVISIBLE
                } else {
                    View.VISIBLE
                }
                // Keep captions above the control gradient while chrome is visible
                // (common streaming pattern), at rest position during clean viewing.
                val liftedStyle = if (controlsVisible || panel != null) {
                    subtitleStyle.copy(
                        verticalPositionFraction = (subtitleStyle.verticalPositionFraction -
                            PlayerChromeTokens.SubtitleLiftFraction).coerceIn(0f, 1f),
                    )
                } else {
                    subtitleStyle
                }
                SubtitleStyleApplier.apply(view.subtitleView!!, liftedStyle)
                // Explicitly positioned cues (WebVTT automatic lines, ASS) ignore
                // bottomPaddingFraction, so the whole caption layer shifts with the
                // chrome instead (PLY-IMM-03).
                view.subtitleView?.translationY = if (controlsVisible || panel != null) {
                    -view.height * PlayerChromeTokens.SubtitleLiftFraction
                } else {
                    0f
                }
                onPlayerViewLayout(view)
            },
            onRelease = { view -> view.player = null },
            modifier = Modifier.fillMaxSize(),
        )

        if (!inPictureInPicture && !isTelevision) {
            PlayerGestureSurface(
                enabled = !loadingSurfaceVisible && !locked && shownError == null,
                onTap = {
                    controlsVisible = !controlsVisible
                    panel = null
                    interactionVersion++
                },
                onDoubleTap = { forward ->
                    val duration = snapshot.durationMillis
                    if (duration > 0) {
                        val target = (snapshot.positionMillis + if (forward) 10_000L else -10_000L)
                            .coerceIn(0L, duration)
                        player?.seekTo(target)
                        showHud(
                            if (forward) Icons.Rounded.Forward10 else Icons.Rounded.Replay10,
                            "${if (forward) "+" else "\u2212"}10 s",
                        )
                    }
                    revealControls()
                },
                onSeekDelta = { deltaPx, widthPx ->
                    val duration = snapshot.durationMillis
                    if (duration > 0) {
                        val next = ((seekTarget ?: snapshot.positionMillis) +
                            PlayerGesturePolicy.seekDeltaMillis(deltaPx, widthPx)).coerceIn(0L, duration)
                        seekTarget = next
                        showHud(Icons.Rounded.FastForward, next.asPlaybackTime())
                    }
                },
                onSeekEnd = {
                    seekTarget?.let { player?.seekTo(it) }
                    seekTarget = null
                    interactionVersion++
                },
                onBrightnessDelta = { deltaPx, heightPx ->
                    brightnessFraction = PlayerGesturePolicy.levelFor(brightnessFraction, deltaPx, heightPx)
                    applyWindowBrightness(hostActivity, brightnessFraction)
                    showHud(Icons.Rounded.BrightnessMedium, "${(brightnessFraction * 100).toInt()}%")
                },
                onVolumeDelta = { deltaPx, heightPx ->
                    volumeFraction = PlayerGesturePolicy.levelFor(volumeFraction, deltaPx, heightPx)
                    if (!setMediaVolumeFraction(context, volumeFraction)) player?.volume = volumeFraction
                    showHud(Icons.AutoMirrored.Rounded.VolumeUp, "${(volumeFraction * 100).toInt()}%")
                },
            )
        }

        if (!loadingSurfaceVisible && !inPictureInPicture && rebufferIndicatorVisible) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(if (isTelevision) 52.dp else 42.dp),
                    color = PlayerPrimary,
                    strokeWidth = 3.dp,
                )
                Text(
                    text = stringResource(R.string.player_loading),
                    color = PlayerOnSurface,
                    fontFamily = PlayerFont,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (!loadingSurfaceVisible && !inPictureInPicture && isTelevision && panel == PlayerPanel.SUBTITLES) {
            // Same dark scrim as the other panels; the live preview stays on top.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.45f),
                        0.22f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1f to PlayerBackground.copy(alpha = 0.92f),
                    ),
                ),
            )
            PlayerLiveSubtitleOverlay(liveSubtitleCues, subtitleStyle)
        } else if (!loadingSurfaceVisible && !inPictureInPicture && (controlsVisible || panel != null || shownError != null)) {
            Box(
                Modifier.fillMaxSize().background(
                    if (isTelevision && panel == null) {
                        // Top and bottom scrims so the chrome reads over bright scenes (PLY-IMM-03).
                        // Panels keep the original darker scrim below.
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.70f),
                            0.16f to Color.Transparent,
                            0.42f to Color.Transparent,
                            0.62f to Color.Black.copy(alpha = 0.60f),
                            1f to Color.Black.copy(alpha = 0.90f),
                        )
                    } else {
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.45f),
                            0.22f to Color.Transparent,
                            0.55f to Color.Transparent,
                            1f to PlayerBackground.copy(alpha = 0.92f),
                        )
                    },
                ),
            )
        }

        if (!loadingSurfaceVisible && !inPictureInPicture && controlsVisible && panel == null && !locked && shownError == null) {
            PlayerControls(
                request = request,
                snapshot = snapshot,
                isTelevision = isTelevision,
                onInteraction = ::revealControls,
                onTogglePlay = { if (snapshot.playing) player?.pause() else player?.play() },
                onReplay = { player?.seekTo(0); player?.play() },
                onSeekBack = { player?.seekBack() },
                onSeekForward = { player?.seekForward() },
                onSeekTo = { player?.seekTo(it) },
                onEnterPictureInPicture = onEnterPictureInPicture,
                pictureInPictureAvailable = pictureInPictureAvailable,
                canPlayNext = settings.nextEpisodeEnabled && nextEpisode != null && nextEpisode.hasAired(),
                nextEpisodeLoading = nextEpisodeLoading,
                onNextEpisode = onNextEpisode,
                focusPanel = returnFocusPanel,
                focusRequestVersion = controlsFocusVersion,
                onPanel = {
                    returnFocusPanel = it
                    panel = it
                    panelParent = null
                    editor = null
                    revealControls()
                },
                segments = segments,
                onExit = onExit,
                onLock = {
                    locked = true
                    controlsVisible = false
                    panel = null
                },
                canSwitchSource = request.preview != null,
                onToggleOrientation = {
                    hostActivity?.requestedOrientation = if (landscapeOrientation) {
                        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    }
                },
            )
        }

        if (!loadingSurfaceVisible && !inPictureInPicture && locked) {
            // Locked mode only blocks video-surface touches; Back still unwinds and
            // the chip is a visible, screen-reader reachable unlock (MOB-A11Y-03).
            PlayerActionButton(
                Icons.Rounded.LockOpen,
                stringResource(R.string.player_unlock),
                Modifier.align(Alignment.CenterStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
                    .padding(start = 16.dp),
                active = true,
            ) {
                locked = false
                revealControls()
            }
        }

        val visibleSegment = if (nextEpisodeSkipInCard) null else activeSegment
        val skipSegment: () -> Unit = {
            activeSegment?.let { segment ->
                SkipSegmentPolicy.skipTarget(segment, segments, snapshot.durationMillis)?.let { player?.seekTo(it) }
                dismissedSegment = segment
            }
        }
        val cueBottomPadding = if (controlsVisible) 190.dp else if (isTelevision) 32.dp else 28.dp
        val cueHorizontalPadding = if (isTelevision) 58.dp else 16.dp
        val cuesAllowed = !loadingSurfaceVisible && !inPictureInPicture && panel == null && shownError == null
        if (cuesAllowed && nextEpisodeMessage != null && !nextEpisodeCardVisible) {
            PlaybackMessage(
                message = nextEpisodeMessage,
                onDismiss = onDismissNextEpisodeMessage,
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(horizontal = cueHorizontalPadding)
                    .padding(bottom = cueBottomPadding),
            )
        }
        if (cuesAllowed && !locked) {
            PlayerSkipButton(
                segment = visibleSegment,
                targetsPostCredits = skipTargetsPostCredits,
                movie = request.episode == null,
                dismissed = visibleSegment != null && visibleSegment == dismissedSegment,
                controlsVisible = controlsVisible,
                isTelevision = isTelevision,
                reducedMotion = reducedMotion,
                onSkip = skipSegment,
                onFocusChanged = { cueFocused = it },
                modifier = Modifier.align(Alignment.BottomStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                    .padding(start = cueHorizontalPadding, bottom = cueBottomPadding),
            )
        }
        val nextEpisodeFocus = remember { FocusRequester() }
        AnimatedVisibility(
            visible = cuesAllowed && !locked && nextEpisodeCardVisible,
            enter = when {
                reducedMotion -> EnterTransition.None
                wideLayout || isTelevision -> slideInHorizontally(tween(260)) { it / 2 } + fadeIn(tween(220))
                else -> slideInVertically(tween(220)) { it } + fadeIn(tween(220))
            },
            exit = when {
                reducedMotion -> ExitTransition.None
                wideLayout || isTelevision -> slideOutHorizontally(tween(200)) { it / 2 } + fadeOut(tween(160))
                else -> slideOutVertically(tween(160)) { it } + fadeOut(tween(160))
            },
            modifier = when {
                wideLayout || isTelevision -> Modifier.align(Alignment.BottomEnd)
                else -> Modifier.align(Alignment.BottomCenter)
            }.windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                ),
            ).padding(horizontal = cueHorizontalPadding),
        ) {
            DisposableEffect(Unit) { onDispose { cueFocused = false } }
            nextEpisode?.let { current ->
                // Nuvio: the card takes TV focus when it appears over clean viewing.
                LaunchedEffect(current.id, controlsVisible, endPromptShown) {
                    if (isTelevision && !controlsVisible && !endPromptShown) runCatching { nextEpisodeFocus.requestFocus() }
                }
                NextEpisodeCard(
                    episode = current,
                    progress = nextEpisodeProgress,
                    failureMessage = nextEpisodeMessage,
                    blurArtwork = spoilerProtection.shouldBlur(
                        SpoilerContent.EPISODE_ARTWORK,
                        watched = false,
                    ),
                    isTelevision = isTelevision,
                    wide = wideLayout,
                    showSkipCredits = nextEpisodeSkipInCard,
                    onPlayNext = onNextEpisode,
                    onSkipCredits = skipSegment,
                    onDismiss = onDismissNextEpisodeCard,
                    focusRequester = nextEpisodeFocus,
                    onFocusChanged = { cueFocused = it },
                    modifier = Modifier.padding(bottom = cueBottomPadding),
                )
            }
        }

        // Nuvio's start-of-playback info: content details at the top start, the
        // matched display mode at the top end, each once per item over clean viewing.
        val infoAllowed = !loadingSurfaceVisible && !inPictureInPicture && !controlsVisible && panel == null &&
            shownError == null && !pauseOverlayVisible && !locked
        val contentRows = contentInfoRows(request)
        var contentInfoDone by remember(request.videoId) { mutableStateOf(false) }
        var contentInfoStarted by remember(request.videoId) { mutableStateOf(false) }
        if (infoAllowed && snapshot.playing && !contentInfoDone && contentRows.isNotEmpty()) contentInfoStarted = true
        if (contentInfoStarted && !contentInfoDone) {
            if (infoAllowed) {
                PlayerInfoLines(
                    rows = contentRows,
                    alignEnd = false,
                    reducedMotion = reducedMotion,
                    onFinished = { contentInfoDone = true },
                    modifier = Modifier.align(Alignment.TopStart)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Start))
                        .padding(start = cueHorizontalPadding, top = if (isTelevision) 32.dp else 16.dp),
                )
            } else {
                // The chrome or a panel interrupting the block ends it, as in Nuvio.
                SideEffect { contentInfoDone = true }
            }
        }
        var displayInfoDone by remember(matchedDisplayMode) { mutableStateOf(false) }
        if (matchedDisplayMode != null && !displayInfoDone) {
            if (infoAllowed) {
                PlayerInfoLines(
                    rows = displayModeRows(matchedDisplayMode),
                    alignEnd = true,
                    reducedMotion = reducedMotion,
                    onFinished = { displayInfoDone = true },
                    modifier = Modifier.align(Alignment.TopEnd)
                        .padding(end = cueHorizontalPadding, top = if (isTelevision) 32.dp else 16.dp),
                )
            } else if (controlsVisible || panel != null) {
                SideEffect { displayInfoDone = true }
            }
        }
        if (isTelevision && !loadingSurfaceVisible && !inPictureInPicture && controlsVisible && panel == null &&
            shownError == null
        ) {
            PlayerClock(
                positionMillis = snapshot.positionMillis,
                durationMillis = snapshot.durationMillis,
                speed = snapshot.speed,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 58.dp, top = 32.dp),
            )
        }

        if (!loadingSurfaceVisible && !isTelevision && !inPictureInPicture && !locked && wideLayout && (controlsVisible || panel != null)) {
            PlayerSideRail(
                icon = Icons.Rounded.BrightnessMedium,
                label = stringResource(R.string.player_brightness),
                value = brightnessFraction,
                onValueChange = { value ->
                    brightnessFraction = value
                    applyWindowBrightness(hostActivity, value)
                },
                modifier = Modifier.align(Alignment.CenterStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical))
                    .padding(start = 24.dp)
                    .fillMaxHeight(0.4f)
                    .heightIn(max = 320.dp),
            )
            PlayerSideRail(
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                label = stringResource(R.string.player_volume),
                value = volumeFraction,
                onValueChange = { value ->
                    volumeFraction = value
                    if (!setMediaVolumeFraction(context, value)) player?.volume = value
                },
                modifier = Modifier.align(Alignment.CenterEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End + WindowInsetsSides.Vertical))
                    .padding(end = 24.dp)
                    .fillMaxHeight(0.4f)
                    .heightIn(max = 320.dp),
            )
        }
        if (!loadingSurfaceVisible) hudText?.let { text ->
            PlayerHudBubble(hudIcon, text, Modifier.align(Alignment.Center))
        }
        // Remote seeks (TV) and drag-to-scrub (mobile) gather into one target
        // before committing: its frame shows above the bubble meanwhile
        // (PLY-SEEK-01). Drawn only when a preview exists.
        val bubbleSeekTarget = keySeekTarget ?: seekTarget
        if (!loadingSurfaceVisible && bubbleSeekTarget != null) {
            val previewWidth = if (isTelevision) PlayerChromeTokens.TvSeekPreviewWidth else PlayerChromeTokens.SeekPreviewWidth
            SeekPreviewThumbnail(
                positionMillis = bubbleSeekTarget,
                durationMillis = snapshot.durationMillis,
                width = previewWidth,
                modifier = Modifier.align(Alignment.Center)
                    .offset(y = -(previewWidth * 9f / 32f + 36.dp)),
            )
        }

        shownError?.takeUnless { inPictureInPicture }?.let { message ->
            PlayerErrorPanel(
                message = message,
                onRetry = {
                    player?.run {
                        // A live stream that fell behind its window resumes at the live edge.
                        if (playerError?.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                            seekToDefaultPosition()
                        }
                        prepare()
                        play()
                    }
                },
                onOpenExternally = onOpenExternally,
            )
        }

        panel?.takeUnless { inPictureInPicture || loadingSurfaceVisible }?.let { activePanel ->
            if (activePanel == PlayerPanel.EPISODES) {
                PlayerEpisodesPanel(
                    episodes = request.episodeQueue,
                    currentVideoId = request.videoId,
                    switch = episodeSwitch,
                    isTelevision = isTelevision,
                    onSelect = onSelectEpisode,
                    onClose = ::closePanel,
                )
            } else if (activePanel == PlayerPanel.SOURCES) {
                LaunchedEffect(Unit) { onLoadSources() }
                PlayerSourcesPanel(
                    state = sources,
                    currentUri = request.source.uri,
                    isTelevision = isTelevision,
                    onSelect = { option ->
                        onSelectSource(option)
                        closePanel()
                    },
                    onClose = ::closePanel,
                )
            } else if (activePanel == PlayerPanel.INFO) {
                PlayerStreamInfoPanel(
                    streamInfo = streamInfo,
                    streamStats = streamStats,
                    isTelevision = isTelevision,
                    onClose = ::closePanel,
                )
            } else if (editor == PlayerEditor.TIMING) {
                PlayerTimingEditor(
                    isTelevision = isTelevision,
                    currentPositionMillis = snapshot.positionMillis,
                    subtitleDelayMillis = subtitleDelayMillis,
                    onSubtitleDelay = onSubtitleDelay,
                    onLoadSidecarCues = onLoadSidecarCues,
                    onApplySyncByLine = { capturedPosition, cueStart ->
                        onApplySyncByLine(capturedPosition, cueStart)
                        closeEditor()
                    },
                    onClose = ::closeEditor,
                )
            } else if (editor == PlayerEditor.STYLE) {
                PlayerStyleEditor(
                    style = subtitleStyle,
                    isTelevision = isTelevision,
                    onStyleChange = onSubtitleStyle,
                    onClose = ::closeEditor,
                )
            } else {
                PlayerSettingsPanel(
                    panel = activePanel,
                    player = player,
                    snapshot = snapshot,
                    isTelevision = isTelevision,
                    trackActions = remember(player, onTrackChosen, onTrackDefaults) {
                        PlayerTrackActions(player, onTrackChosen, onTrackDefaults)
                    },
                    audioAutomatic = audioFollowsDefaults ?: (player?.hasOverride(C.TRACK_TYPE_AUDIO) == false),
                    addonSubtitles = remember(request.source.subtitles) {
                        request.source.subtitles.associate { it.id to it.providerName }
                    },
                    audioDelayMillis = audioDelayMillis,
                    onAudioDelay = onAudioDelay,
                    onOpenEditor = { opened ->
                        editor = opened
                        revealControls()
                    },
                    onClose = ::closePanel,
                    subtitleDelayMillis = subtitleDelayMillis,
                    onSubtitleDelay = onSubtitleDelay,
                    subtitleStyle = subtitleStyle,
                    onSubtitleStyle = onSubtitleStyle,
                    resizeMode = resizeMode,
                    onResizeMode = { resizeMode = it },
                    onPanel = { panelParent = panel; panel = it },
                    onOpenExternally = onOpenExternally,
                    showEpisodes = request.episodeQueue.size > 1,
                )
            }
        }

        AnimatedVisibility(
            visible = pauseOverlayVisible && !endPromptShown,
            enter = fadeIn(tween(if (reducedMotion) 0 else 220)),
            exit = fadeOut(tween(if (reducedMotion) 0 else 160)),
        ) {
            PausedInfoOverlay(request)
        }

        // The loading surface dissolves into the first frames instead of cutting away.
        AnimatedVisibility(
            visible = loadingSurfaceVisible,
            enter = if (reducedMotion) EnterTransition.None else fadeIn(tween(220)),
            exit = if (reducedMotion) ExitTransition.None else fadeOut(tween(420)),
        ) {
            PlaybackLoadingSurface(
                request = request,
                isTelevision = isTelevision,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Topmost: nothing else may cover or take input from the prompt.
        if (endPromptShown) {
            when (endPrompt) {
                is PlayerEndPrompt.StillWatching -> StillWatchingPrompt(
                    secondsLeft = endPrompt.secondsLeft,
                    isTelevision = isTelevision,
                    onContinue = onStillWatchingContinue,
                    onStop = onStillWatchingStop,
                    nextEpisode = nextEpisode,
                    blurArtwork = spoilerProtection.shouldBlur(SpoilerContent.EPISODE_ARTWORK, watched = false),
                )
                is PlayerEndPrompt.UpNext -> if (nextEpisode != null) {
                    UpNextPrompt(
                        episode = nextEpisode,
                        secondsLeft = endPrompt.secondsLeft,
                        blurArtwork = spoilerProtection.shouldBlur(SpoilerContent.EPISODE_ARTWORK, watched = false),
                        hideSynopsis = spoilerProtection.shouldBlur(SpoilerContent.EPISODE_SYNOPSIS, watched = false),
                        isTelevision = isTelevision,
                        onYes = onUpNextYes,
                        onNo = onUpNextNo,
                    )
                }
                is PlayerEndPrompt.Finished -> FinishedPrompt(
                    request = request,
                    secondsLeft = endPrompt.secondsLeft,
                    isTelevision = isTelevision,
                    onWatchAgain = onWatchAgain,
                    onClose = onExit,
                )
                null -> Unit
            }
        }
    }
    }
}

@Composable
private fun PlaybackMessage(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(PlayerBackground.copy(alpha = 0.96f))
            .clickable(onClick = onDismiss)
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(message, color = PlayerOnSurface, fontFamily = PlayerFont, fontSize = 14.sp)
        Text(stringResource(R.string.player_dismiss), color = PlayerPrimary, fontFamily = PlayerFont, fontWeight = FontWeight.Medium)
    }
}

/** Rating, type and year, and genres from the catalog snapshot. */
@Composable
private fun contentInfoRows(request: PlaybackRequest): List<PlayerInfoRow> {
    val preview = request.preview ?: return emptyList()
    val typeLabel = stringResource(if (preview.type == MediaType.SERIES) R.string.player_info_series else R.string.player_info_movie)
    val ratingLabel = stringResource(R.string.player_info_rating)
    val genreLabel = stringResource(R.string.player_info_genre)
    return remember(preview, typeLabel, ratingLabel, genreLabel) {
        listOfNotNull(
            preview.contentRating?.takeIf(String::isNotBlank)?.let { PlayerInfoRow(ratingLabel, it.trim()) },
            preview.releaseYear?.let { PlayerInfoRow(typeLabel, it.toString()) },
            preview.genres.filter(String::isNotBlank).take(2).takeIf { it.isNotEmpty() }
                ?.let { PlayerInfoRow(genreLabel, it.joinToString(", ")) },
        )
    }
}

@Composable
internal fun PlayerTitle(request: PlaybackRequest, isTelevision: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = request.title,
            color = PlayerOnSurface,
            fontFamily = PlayerFont,
            fontWeight = FontWeight.Medium,
            style = if (isTelevision) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        request.subtitle?.takeIf(String::isNotBlank)?.let {
            Text(
                text = it,
                color = PlayerOnSurfaceMuted,
                fontFamily = PlayerFont,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}


@Composable
internal fun PlayerActionButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    visualSize: androidx.compose.ui.unit.Dp = 26.dp,
    active: Boolean = false,
    primary: Boolean = false,
    containerSize: androidx.compose.ui.unit.Dp = 48.dp,
    onClick: () -> Unit,
) {
    if (!LocalPlayerTelevision.current) {
        FilledIconButton(onClick = onClick, modifier = modifier.size(containerSize),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (primary) PlayerFocused else Color.Black.copy(alpha = .48f),
                contentColor = if (primary) PlayerFocusedContent else if (active) PlayerPrimary else PlayerOnSurface,
            )) { Icon(icon, label, Modifier.size(visualSize)) }
        return
    }
    var focused by remember { mutableStateOf(false) }
    // Circular and borderless; focus fills the circle with the approved pale
    // container and dark content (TV-TOK-02 playback exception).
    Box(
        modifier = modifier
            .size(containerSize)
            .onFocusChanged { focused = it.isFocused }
            .clip(CircleShape)
            .background(if (focused) PlayerFocused else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = label
                selected = active
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = when {
                focused -> PlayerFocusedContent
                active -> PlayerPrimary
                else -> PlayerOnSurface
            },
            modifier = Modifier.size(visualSize),
        )
        // Active state is a shape as well as a color (MOB-A11Y-08).
        if (active) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (focused) PlayerFocusedContent else PlayerPrimary),
            )
        }
    }
}
@Composable
internal fun PlayerSettingButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    if (!LocalPlayerTelevision.current) {
        // Icon-only chrome: the label survives as the accessible name (MOB-A11Y-01),
        // and the active state changes fill and tint, not color alone (MOB-A11Y-08).
        FilledIconButton(
            onClick = onClick,
            modifier = modifier
                .size(PlayerChromeTokens.ControlContainer)
                .semantics {
                    role = Role.Button
                    contentDescription = label
                    selected = active
                },
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (active) PlayerPrimary.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.48f),
                contentColor = if (active) PlayerPrimary else PlayerOnSurface,
            ),
        ) {
            Icon(icon, null, Modifier.size(PlayerChromeTokens.ControlGlyph))
        }
        return
    }
    // Icon-only at rest; the focused label appears beside the row (PLY-CHR-05).
    PlayerActionButton(
        icon = icon,
        label = label,
        modifier = modifier,
        visualSize = PlayerChromeTokens.TvControlGlyph,
        active = active,
        containerSize = PlayerChromeTokens.TvControlContainer,
        onClick = onClick,
    )
}

@kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerProgress(
    positionMillis: Long,
    bufferedPositionMillis: Long,
    durationMillis: Long,
    modifier: Modifier,
    onSeekTo: (Long) -> Unit,
    onInteraction: () -> Unit,
    segments: List<PlaybackSegment> = emptyList(),
) {
    val seekDescription = stringResource(R.string.player_seek)
    if (!LocalPlayerTelevision.current) {
        var scrubPosition by remember { mutableStateOf<Float?>(null) }
        BoxWithConstraints(modifier) {
            val trackWidth = maxWidth
            Slider(
                value = scrubPosition ?: positionMillis.toFloat().coerceIn(0f, durationMillis.coerceAtLeast(1L).toFloat()),
                onValueChange = { scrubPosition = it; onInteraction() },
                onValueChangeFinished = { scrubPosition?.let { onSeekTo(it.toLong()) }; scrubPosition = null },
                valueRange = 0f..durationMillis.coerceAtLeast(1L).toFloat(),
                enabled = durationMillis > 0,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = seekDescription },
                thumb = { Box(Modifier.size(12.dp).background(PlayerPrimary, CircleShape)) },
                track = { state ->
                    Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                        val radius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                        val played = state.value / durationMillis.coerceAtLeast(1L)
                        val buffered = bufferedPositionMillis.toFloat() / durationMillis.coerceAtLeast(1L)
                        drawRoundRect(PlayerTrack, cornerRadius = radius)
                        drawRoundRect(PlayerBuffered, size = size.copy(width = size.width * buffered.coerceIn(0f, 1f)), cornerRadius = radius)
                        drawRoundRect(PlayerPrimary, size = size.copy(width = size.width * played.coerceIn(0f, 1f)), cornerRadius = radius)
                    }
                },
            )
            // Target time while scrubbing, so the thumb never needs to be guessed;
            // with seek previews the frame there sits above it, following the
            // thumb but never past the bar's ends (PLY-SEEK-01).
            scrubPosition?.let { target ->
                val previewWidth = PlayerChromeTokens.SeekPreviewWidth
                val fraction = (target / durationMillis.coerceAtLeast(1L)).coerceIn(0f, 1f)
                val centre = trackWidth * fraction
                val start = (centre - previewWidth / 2).coerceIn(0.dp, (trackWidth - previewWidth).coerceAtLeast(0.dp))
                Column(
                    Modifier.align(Alignment.TopStart)
                        .offset(x = start)
                        .width(previewWidth)
                        // Bottom edge sits just above the bar, whatever the preview's height.
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(
                                constraints.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity),
                            )
                            layout(placeable.width, 0) { placeable.place(0, -placeable.height + 2.dp.roundToPx()) }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SeekPreviewThumbnail(
                        positionMillis = target.toLong(),
                        durationMillis = durationMillis,
                        width = previewWidth,
                    )
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(PlayerSurface.copy(alpha = 0.94f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = target.toLong().asPlaybackTime(),
                            color = PlayerOnSurface,
                            fontFamily = PlayerFont,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
        return
    }
    var focused by remember { mutableStateOf(false) }
    val played = if (durationMillis > 0) positionMillis.toFloat() / durationMillis else 0f
    val buffered = if (durationMillis > 0) bufferedPositionMillis.toFloat() / durationMillis else 0f
    Canvas(
        modifier
            .height(24.dp)
            .onFocusChanged { focused = it.isFocused }
            .semantics {
                contentDescription = seekDescription
                progressBarRangeInfo = ProgressBarRangeInfo(played.coerceIn(0f, 1f), 0f..1f)
                setProgress { fraction ->
                    if (durationMillis <= 0) false else {
                        onSeekTo((durationMillis * fraction.coerceIn(0f, 1f)).toLong())
                        onInteraction()
                        true
                    }
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || durationMillis <= 0L) return@onPreviewKeyEvent false
                val target = when (event.key) {
                    Key.DirectionLeft -> positionMillis - 10_000L
                    Key.DirectionRight -> positionMillis + 10_000L
                    else -> return@onPreviewKeyEvent false
                }
                onSeekTo(target.coerceIn(0L, durationMillis))
                onInteraction()
                true
            }
            .focusable()
            .pointerInput(durationMillis) {
                detectTapGestures { offset ->
                    if (durationMillis > 0 && size.width > 0) {
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeekTo((durationMillis * fraction).toLong())
                        onInteraction()
                    }
                }
            },
    ) {
        val centerY = size.height / 2
        // A thick rounded bar that grows while focused; no knob.
        val barHeight = if (focused) 12.dp.toPx() else 8.dp.toPx()
        val radius = androidx.compose.ui.geometry.CornerRadius(barHeight / 2)
        val origin = androidx.compose.ui.geometry.Offset(0f, centerY - barHeight / 2)
        drawRoundRect(
            Color.White.copy(alpha = if (focused) 0.45f else 0.30f),
            origin, androidx.compose.ui.geometry.Size(size.width, barHeight), radius,
        )
        drawRoundRect(PlayerPrimary.copy(alpha = 0.35f), origin, androidx.compose.ui.geometry.Size(size.width * buffered.coerceIn(0f, 1f), barHeight), radius)
        drawRoundRect(PlayerPrimary, origin, androidx.compose.ui.geometry.Size(size.width * played.coerceIn(0f, 1f), barHeight), radius)
        if (durationMillis > 0) {
            segments.forEach { segment ->
                val end = segment.endMillis ?: durationMillis
                if (end <= 0L) return@forEach
                listOf(segment.startMillis, end).forEach { edge ->
                    val x = size.width * (edge.toFloat() / durationMillis).coerceIn(0f, 1f)
                    drawRect(
                        color = PlayerBackground,
                        topLeft = androidx.compose.ui.geometry.Offset(x - 1.dp.toPx() / 2, centerY - barHeight / 2),
                        size = androidx.compose.ui.geometry.Size(1.dp.toPx().coerceAtLeast(2f), barHeight),
                    )
                }
            }
        }
    }
}

@Composable
internal fun PlayerTime(milliseconds: Long) {
    Text(
        text = milliseconds.asPlaybackTime(),
        color = PlayerOnSurface,
        fontFamily = PlayerFont,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    )
}

@Composable
internal fun PlayerRemaining(positionMillis: Long, durationMillis: Long) {
    val remaining = (durationMillis - positionMillis).coerceAtLeast(0L)
    Text(
        text = "−${remaining.asPlaybackTime()}",
        color = PlayerOnSurfaceMuted,
        fontFamily = PlayerFont,
        fontSize = 14.sp,
    )
}

@Composable
private fun PlayerSettingsPanel(
    panel: PlayerPanel,
    player: Player?,
    snapshot: PlayerSnapshot,
    isTelevision: Boolean,
    trackActions: PlayerTrackActions,
    audioAutomatic: Boolean,
    addonSubtitles: Map<String, String?>,
    audioDelayMillis: Long,
    onAudioDelay: (Long) -> Unit,
    onOpenEditor: (PlayerEditor) -> Unit,
    onClose: () -> Unit,
    subtitleDelayMillis: Long,
    onSubtitleDelay: (Long) -> Unit,
    subtitleStyle: SubtitleStyle,
    onSubtitleStyle: (SubtitleStyle) -> Unit,
    resizeMode: Int,
    onResizeMode: (Int) -> Unit,
    onPanel: (PlayerPanel) -> Unit,
    onOpenExternally: () -> Unit,
    showEpisodes: Boolean = false,
) {
    val firstFocus = remember(panel) { FocusRequester() }
    val title = stringResource(when (panel) {
        PlayerPanel.AUDIO -> R.string.player_audio
        PlayerPanel.SUBTITLES -> R.string.player_subtitles
        PlayerPanel.SPEED -> R.string.player_speed
        PlayerPanel.DISPLAY -> R.string.player_display
        PlayerPanel.MORE -> R.string.player_more
        PlayerPanel.INFO -> R.string.player_info
        PlayerPanel.SOURCES -> R.string.player_sources
        PlayerPanel.EPISODES -> R.string.player_episodes
    })
    val options = when (panel) {
        PlayerPanel.AUDIO -> snapshot.tracks.options(C.TRACK_TYPE_AUDIO)
        PlayerPanel.SUBTITLES -> snapshot.tracks.options(C.TRACK_TYPE_TEXT, addonSubtitles)
        else -> emptyList()
    }
    val audioUsesAutoSelection = audioAutomatic
    LaunchedEffect(panel) { if (isTelevision) runCatching { firstFocus.requestFocus() } }
    PlayerOverlayLayout(
        // "Subtitles · 14": how many tracks this source offers, at a glance.
        if ((panel == PlayerPanel.SUBTITLES || panel == PlayerPanel.AUDIO) && options.isNotEmpty()) {
            "$title · ${options.size}"
        } else {
            title
        },
        isTelevision,
        onClose,
        tvWidth = if (panel == PlayerPanel.SUBTITLES) 844.dp else 724.dp,
        tvScrim = true,
    ) {
        when (panel) {
            PlayerPanel.AUDIO -> {
                if (isTelevision) {
                    Row(Modifier.fillMaxWidth().heightIn(max = 360.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        PlayerTrackList(options, C.TRACK_TYPE_AUDIO, trackActions, true,
                            audioUsesAutoSelection, firstFocus, modifier = Modifier.weight(1f))
                        PlayerAudioTimingControls(audioDelayMillis, onAudioDelay,
                            Modifier.width(268.dp).verticalScroll(rememberScrollState()))
                    }
                } else {
                    PlayerTrackList(options, C.TRACK_TYPE_AUDIO, trackActions, true,
                        audioUsesAutoSelection, firstFocus, compactRows = true,
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                        footer = { PlayerAudioTimingControls(audioDelayMillis, onAudioDelay) })
                }
            }
            PlayerPanel.SUBTITLES -> {
                if (isTelevision) {
                    TvSubtitleRailPanel(
                        options = options,
                        actions = trackActions,
                        firstFocus = firstFocus,
                        subtitleStyle = subtitleStyle,
                        subtitleDelayMillis = subtitleDelayMillis,
                        onSubtitleDelay = onSubtitleDelay,
                        onSubtitleStyle = onSubtitleStyle,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                } else {
                    PlayerTrackList(options, C.TRACK_TYPE_TEXT, trackActions, false, false, firstFocus,
                        compactRows = true, showOriginFilters = true,
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                        footer = {
                            MobileSubtitleToolbar(subtitleStyle, subtitleDelayMillis,
                                { onOpenEditor(PlayerEditor.TIMING) }, { onOpenEditor(PlayerEditor.STYLE) })
                        })
                }
            }
            PlayerPanel.SPEED -> {
                val selectedIndex = PLAYBACK_SPEEDS.indexOf(snapshot.speed).coerceAtLeast(0)
                LazyColumn(Modifier.heightIn(max = 360.dp), state = rememberLazyListState(selectedIndex)) {
                    itemsIndexed(PLAYBACK_SPEEDS, key = { _, speed -> speed }) { index, speed ->
                        PlayerChoiceRow(
                            title = if (speed == 1f) stringResource(R.string.player_speed_normal) else "${speed}×",
                            supportingText = null, selected = snapshot.speed == speed,
                            modifier = if (index == selectedIndex) Modifier.focusRequester(firstFocus) else Modifier,
                        ) { player?.setPlaybackSpeed(speed) }
                    }
                }
            }
            PlayerPanel.DISPLAY -> {
                val modes = listOf(AspectRatioFrameLayout.RESIZE_MODE_FIT,
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM, AspectRatioFrameLayout.RESIZE_MODE_FILL)
                val labels = listOf(R.string.player_fit, R.string.player_zoom, R.string.player_fill)
                val details = listOf(R.string.player_fit_detail, R.string.player_zoom_detail, R.string.player_fill_detail)
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    itemsIndexed(modes) { index, mode ->
                        PlayerChoiceRow(stringResource(labels[index]), stringResource(details[index]), resizeMode == mode,
                            modifier = if (resizeMode == mode) Modifier.focusRequester(firstFocus) else Modifier,
                        ) { onResizeMode(mode) }
                    }
                }
            }
            PlayerPanel.MORE -> LazyColumn(Modifier.heightIn(max = 360.dp)) {
                if (showEpisodes) {
                    item {
                        PlayerChoiceRow(stringResource(R.string.player_episodes), null, false,
                            Modifier.focusRequester(firstFocus), actionRole = Role.Button) { onPanel(PlayerPanel.EPISODES) }
                    }
                }
                item {
                    PlayerChoiceRow(stringResource(R.string.player_speed), "${snapshot.speed}×", false,
                        if (showEpisodes) Modifier else Modifier.focusRequester(firstFocus), actionRole = Role.Button) { onPanel(PlayerPanel.SPEED) }
                }
                item { PlayerChoiceRow(stringResource(R.string.player_display), null, false, actionRole = Role.Button) { onPanel(PlayerPanel.DISPLAY) } }
                item { PlayerChoiceRow(stringResource(R.string.player_info), null, false, actionRole = Role.Button) { onPanel(PlayerPanel.INFO) } }
                item { PlayerChoiceRow(stringResource(R.string.player_external), null, false, actionRole = Role.Button, onClick = onOpenExternally) }
            }
            PlayerPanel.INFO, PlayerPanel.SOURCES, PlayerPanel.EPISODES -> Unit
        }
    }
}

@Composable
private fun MobileSubtitleToolbar(
    subtitleStyle: SubtitleStyle,
    subtitleDelayMillis: Long,
    onTiming: () -> Unit,
    onStyle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(PlayerSurface.copy(alpha = 0.56f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PlayerTextButton(
            label = stringResource(R.string.player_sync_toolbar, formatSignedDelay(subtitleDelayMillis)),
            onClick = onTiming,
            modifier = Modifier.weight(1f),
        )
        PlayerTextButton(
            label = stringResource(R.string.player_style_toolbar, subtitleStyle.sizePercent),
            onClick = onStyle,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * TV caption selection follows a progressive rail model: choose a language,
 * choose the concrete stream track, then tune the rendered result. This keeps
 * the entire flow visible without turning a remote interaction into a stack of
 * dialogs (TV-NAV-05, TV-FOC-01, PLY-IMM-04).
 */
@Composable
private fun TvSubtitleRailPanel(
    options: List<TrackOption>,
    actions: PlayerTrackActions,
    firstFocus: FocusRequester,
    subtitleStyle: SubtitleStyle,
    subtitleDelayMillis: Long,
    onSubtitleDelay: (Long) -> Unit,
    onSubtitleStyle: (SubtitleStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val displayLocale = configuration.locales[0]
    val unknownLabel = stringResource(R.string.player_subtitle_language_unknown)
    val activeLanguageKey = options.firstOrNull(TrackOption::selected)?.languageKey
        ?: SUBTITLE_LANGUAGE_OFF
    val groupedOptions = options.groupBy(TrackOption::languageKey)
    // The order is fixed while the panel is open: choosing a track only moves
    // the check mark, so the row the viewer pressed stays where it was, in
    // view and focused (TV-FOC-01). It is decided again only when the source
    // reports a different set of tracks.
    val languageOrder = remember(groupedOptions.keys) {
        groupedOptions.keys.sortedWith(
            compareBy<String> { it != activeLanguageKey }
                .thenBy { subtitleLanguageDisplayName(it, displayLocale, unknownLabel).lowercase(displayLocale) },
        )
    }
    val languageItems = buildList {
        add(
            SubtitleLanguageRailItem(
                key = SUBTITLE_LANGUAGE_OFF,
                label = stringResource(R.string.player_subtitle_off),
                trackCount = 0,
            ),
        )
        languageOrder.forEach { languageKey ->
            groupedOptions[languageKey]?.let { tracks ->
                add(
                    SubtitleLanguageRailItem(
                        key = languageKey,
                        label = subtitleLanguageDisplayName(languageKey, displayLocale, unknownLabel),
                        trackCount = tracks.size,
                    ),
                )
            }
        }
    }
    var browsedLanguageKey by remember { mutableStateOf(activeLanguageKey) }
    LaunchedEffect(activeLanguageKey, languageItems.map(SubtitleLanguageRailItem::key)) {
        if (languageItems.none { it.key == browsedLanguageKey }) {
            browsedLanguageKey = activeLanguageKey
        }
    }
    val browsedOptions = rememberStableTrackOrder(groupedOptions[browsedLanguageKey].orEmpty())
    var originFilter by remember(browsedLanguageKey) { mutableStateOf<String?>(null) }
    val origins = browsedOptions.originCounts()
    val visibleOptions = browsedOptions.filter { originFilter == null || it.origin.key == originFilter }
    val activeLanguageIndex = languageItems.indexOfFirst { it.key == activeLanguageKey }.coerceAtLeast(0)
    val initialLanguageIndex = remember { activeLanguageIndex }

    Row(
        modifier = modifier.fillMaxHeight(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlayerRailColumn(
            title = stringResource(R.string.player_subtitle_languages),
            modifier = Modifier.weight(0.20f),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                state = rememberLazyListState(initialLanguageIndex),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(languageItems, key = { _, item -> item.key }) { index, item ->
                    val isOff = item.key == SUBTITLE_LANGUAGE_OFF
                    PlayerChoiceRow(
                        title = item.label,
                        supportingText = when {
                            isOff -> stringResource(R.string.player_subtitle_no_captions)
                            item.key == activeLanguageKey -> pluralStringResource(
                                R.plurals.player_subtitle_track_count_active,
                                item.trackCount,
                                item.trackCount,
                            )
                            else -> pluralStringResource(
                                R.plurals.player_subtitle_track_count,
                                item.trackCount,
                                item.trackCount,
                            )
                        },
                        selected = item.key == activeLanguageKey,
                        modifier = if (index == initialLanguageIndex) Modifier.focusRequester(firstFocus) else Modifier,
                    ) {
                        browsedLanguageKey = item.key
                        if (isOff) actions.turnOff(C.TRACK_TYPE_TEXT)
                    }
                }
            }
        }

        PlayerRailColumn(
            title = stringResource(R.string.player_subtitle_tracks),
            modifier = Modifier.weight(0.40f),
        ) {
            when {
                browsedLanguageKey == SUBTITLE_LANGUAGE_OFF -> PlayerRailEmptyState(
                    title = stringResource(R.string.player_subtitle_off),
                    body = stringResource(R.string.player_subtitle_off_description),
                )
                browsedOptions.isEmpty() -> PlayerRailEmptyState(
                    title = stringResource(R.string.player_subtitle_no_tracks_title),
                    body = stringResource(R.string.player_subtitle_no_tracks_body),
                )
                else -> {
                    if (origins.size > 1) {
                        // The details screen's chips, in its theme (TV-FOC-02).
                        LamphausTvTheme {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item("all") {
                                    TvFilterChip(
                                        label = stringResource(R.string.player_subtitle_origin_all),
                                        count = browsedOptions.size,
                                        selected = originFilter == null,
                                        onClick = { originFilter = null },
                                    )
                                }
                                items(origins, key = { it.first.key }) { (origin, count) ->
                                    TvFilterChip(
                                        label = trackOriginLabel(origin),
                                        count = count,
                                        selected = originFilter == origin.key,
                                        onClick = { originFilter = origin.key },
                                    )
                                }
                            }
                        }
                    }
                    val selectedIndex = visibleOptions.indexOfFirst(TrackOption::selected).coerceAtLeast(0)
                    val selectedFocus = remember { FocusRequester() }
                    val trackListState = remember(browsedLanguageKey, originFilter) { LazyListState(selectedIndex) }
                    LazyColumn(
                        state = trackListState,
                        // Entering the list lands on the chosen track, else where the viewer last was.
                        modifier = Modifier.fillMaxWidth().weight(1f).focusRestorer(selectedFocus),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        itemsIndexed(visibleOptions, key = { _, item -> item.id }) { index, option ->
                            PlayerChoiceRow(
                                title = option.title,
                                supportingText = null,
                                badges = listOf(trackOriginLabel(option.origin)) + option.badges,
                                selected = option.selected,
                                enabled = option.supported,
                                modifier = if (index == selectedIndex) Modifier.focusRequester(selectedFocus) else Modifier,
                            ) {
                                actions.choose(C.TRACK_TYPE_TEXT, option)
                            }
                        }
                    }
                }
            }
        }

        PlayerRailColumn(
            title = stringResource(R.string.player_caption_lab),
            modifier = Modifier.weight(0.40f),
        ) {
            TvSubtitleAppearancePane(
                style = subtitleStyle,
                subtitleDelayMillis = subtitleDelayMillis,
                onSubtitleDelay = onSubtitleDelay,
                onStyleChange = onSubtitleStyle,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Keeps [options] in the order they had when this list first saw them, the
 * selected track first. Choosing another track updates the rows in place
 * instead of moving the chosen one out from under focus.
 */
@Composable
private fun rememberStableTrackOrder(options: List<TrackOption>, selectedFirst: Boolean = true): List<TrackOption> {
    val order = remember(options.map(TrackOption::id).toSet()) {
        (if (selectedFirst) options.sortedByDescending(TrackOption::selected) else options).map(TrackOption::id)
    }
    val byId = options.associateBy(TrackOption::id)
    return order.mapNotNull(byId::get)
}

/** Each source of subtitles in [this] list with its track count, built-in first. */
private fun List<TrackOption>.originCounts(): List<Pair<TrackOrigin, Int>> =
    groupBy(TrackOption::origin)
        .map { (origin, tracks) -> origin to tracks.size }
        .sortedBy { (origin, _) -> if (origin == TrackOrigin.BuiltIn) 0 else 1 }

@Composable
private fun trackOriginLabel(origin: TrackOrigin): String = when (origin) {
    TrackOrigin.BuiltIn -> stringResource(R.string.player_subtitle_origin_built_in)
    is TrackOrigin.Addon -> origin.name ?: stringResource(R.string.player_subtitle_origin_addon)
}

/** The panels' track actions: the player change, then the activity hears the viewer chose (plan §3). */
private class PlayerTrackActions(
    private val player: Player?,
    private val onChosen: ((Int, androidx.media3.common.Format?) -> Unit)?,
    private val onDefaults: ((Int) -> Unit)?,
) {
    fun choose(trackType: Int, option: TrackOption) {
        onChosen?.invoke(trackType, option.format)
        player?.selectTrack(trackType, option)
    }

    fun turnOff(trackType: Int) {
        onChosen?.invoke(trackType, null)
        player?.clearTrackOverride(trackType, disabled = true)
    }

    fun automatic(trackType: Int) {
        if (onDefaults != null) onDefaults.invoke(trackType) else player?.clearTrackOverride(trackType, disabled = false)
    }
}

@Composable
private fun PlayerRailColumn(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            color = PlayerOnSurfaceMuted,
            fontFamily = PlayerFont,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        content()
    }
}

/**
 * The TV appearance pane edits the persisted subtitle style in place. It
 * stays in the three-pane workspace so there is no modal or Save step
 * (PLY-IMM-04, TV-NAV-05, TV-FOC-01).
 */
@Composable
private fun TvSubtitleAppearancePane(
    style: SubtitleStyle,
    subtitleDelayMillis: Long,
    onSubtitleDelay: (Long) -> Unit,
    onStyleChange: (SubtitleStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fontLabel = when (style.fontFamily) {
        SubtitleFontFamily.SYSTEM -> stringResource(R.string.player_font_system)
        SubtitleFontFamily.SANS_SERIF -> stringResource(R.string.player_font_sans_serif)
        SubtitleFontFamily.SERIF -> stringResource(R.string.player_font_serif)
        SubtitleFontFamily.MONOSPACE -> stringResource(R.string.player_font_monospace)
    }
    val edgeLabel = when (style.edgeStyle) {
        SubtitleEdgeStyle.NONE -> stringResource(R.string.player_edge_none)
        SubtitleEdgeStyle.DROP_SHADOW -> stringResource(R.string.player_edge_shadow)
        SubtitleEdgeStyle.RAISED -> stringResource(R.string.player_edge_raised)
        SubtitleEdgeStyle.DEPRESSED -> stringResource(R.string.player_edge_depressed)
        SubtitleEdgeStyle.OUTLINE -> stringResource(R.string.player_edge_outline)
    }
    LazyColumn(
        modifier = modifier.testTag("tv-subtitle-appearance"),
        contentPadding = PaddingValues(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item("preview") { PlayerSubtitlePreview(style) }
        item("font") {
            PlayerChoiceRow(
                title = stringResource(R.string.player_font),
                supportingText = fontLabel,
                selected = false,
                actionRole = Role.Button,
            ) {
                val entries = SubtitleFontFamily.entries
                onStyleChange(
                    style.copy(
                        fontFamily = entries[(entries.indexOf(style.fontFamily) + 1) % entries.size],
                        preserveEmbeddedStyles = false,
                    ),
                )
            }
        }
        item("size") {
            PlayerDelayRow(
                label = stringResource(R.string.player_text_size),
                valueText = "${style.sizePercent}%",
                onStep = { steps ->
                    onStyleChange(
                        style.copy(
                            sizePercent = SubtitleStylePolicy.clampSizePercent(style.sizePercent + steps * 10),
                            preserveEmbeddedStyles = false,
                        ),
                    )
                },
                onReset = { onStyleChange(style.copy(sizePercent = 100)) },
            )
        }
        item("text-color") {
            SubtitleColorRow(
                label = stringResource(R.string.player_text_color),
                selected = style.textColor,
                colors = SUBTITLE_TEXT_COLORS,
            ) { onStyleChange(style.copy(textColor = it, preserveEmbeddedStyles = false)) }
        }
        item("background-color") {
            SubtitleColorRow(
                label = stringResource(R.string.player_background_color),
                selected = style.backgroundColor,
                colors = SUBTITLE_BACKGROUND_COLORS,
            ) { onStyleChange(style.copy(backgroundColor = it, preserveEmbeddedStyles = false)) }
        }
        item("background-opacity") {
            PlayerDelayRow(
                label = stringResource(R.string.player_background_opacity),
                valueText = "${(SubtitleStylePolicy.clampOpacity(style.backgroundOpacity) * 100).toInt()}%",
                onStep = { steps ->
                    onStyleChange(
                        style.copy(
                            backgroundOpacity = SubtitleStylePolicy.clampOpacity(style.backgroundOpacity + steps * 0.1f),
                            preserveEmbeddedStyles = false,
                        ),
                    )
                },
                onReset = { onStyleChange(style.copy(backgroundOpacity = 0f)) },
            )
        }
        item("edge") {
            PlayerChoiceRow(
                title = stringResource(R.string.player_edge),
                supportingText = edgeLabel,
                selected = style.edgeStyle != SubtitleEdgeStyle.NONE,
                actionRole = Role.Button,
            ) {
                val entries = SubtitleEdgeStyle.entries
                val next = entries[(entries.indexOf(style.edgeStyle) + 1) % entries.size]
                onStyleChange(
                    style.copy(
                        edgeStyle = next,
                        outlineEnabled = next == SubtitleEdgeStyle.OUTLINE,
                        preserveEmbeddedStyles = false,
                    ),
                )
            }
        }
        item("position") {
            PlayerDelayRow(
                label = stringResource(R.string.player_position),
                valueText = "${(style.verticalPositionFraction * 100).toInt()}%",
                onStep = { steps ->
                    onStyleChange(
                        style.copy(
                            verticalPositionFraction = SubtitleStylePolicy.clampPositionFraction(
                                style.verticalPositionFraction + steps * 0.05f,
                            ),
                        ),
                    )
                },
                onReset = { onStyleChange(style.copy(verticalPositionFraction = 0.92f)) },
            )
        }
        item("embedded") {
            PlayerChoiceRow(
                title = stringResource(R.string.player_preserve_styles),
                supportingText = stringResource(R.string.player_preserve_styles_detail),
                selected = style.preserveEmbeddedStyles,
                actionRole = Role.Checkbox,
            ) {
                onStyleChange(style.copy(preserveEmbeddedStyles = !style.preserveEmbeddedStyles))
            }
        }
        item("sync") {
            PlayerDelayRow(
                label = stringResource(R.string.player_sync),
                valueText = formatSignedDelay(subtitleDelayMillis),
                onStep = { steps -> onSubtitleDelay(stepSubtitleDelay(subtitleDelayMillis, steps)) },
                onReset = { onSubtitleDelay(0L) },
            )
        }
        item("sync-help") {
            Text(
                text = stringResource(
                    when {
                        subtitleDelayMillis > 0L -> R.string.player_subtitle_later
                        subtitleDelayMillis < 0L -> R.string.player_subtitle_earlier
                        else -> R.string.player_subtitle_in_sync
                    },
                ),
                color = PlayerOnSurfaceMuted,
                fontFamily = PlayerFont,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        }
        item("reset") {
            PlayerChoiceRow(
                title = stringResource(R.string.player_reset_appearance),
                supportingText = stringResource(R.string.player_reset_appearance_detail),
                selected = false,
                actionRole = Role.Button,
            ) { onStyleChange(SubtitleStyle()) }
        }
    }
}

/** Re-renders the active cue above the workspace scrim with the playback renderer's exact style. */
@Composable
private fun PlayerLiveSubtitleOverlay(cues: List<Cue>, style: SubtitleStyle) {
    if (cues.isEmpty()) return
    AndroidView(
        factory = { context -> androidx.media3.ui.SubtitleView(context) },
        update = { view ->
            SubtitleStyleApplier.apply(view, style)
            view.setCues(cues)
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun PlayerRailEmptyState(
    title: String,
    body: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(PlayerSurface.copy(alpha = 0.44f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(4.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            color = PlayerOnSurface,
            fontFamily = PlayerFont,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
        )
        Text(
            text = body,
            color = PlayerOnSurfaceMuted,
            fontFamily = PlayerFont,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun PlayerTrackList(
    options: List<TrackOption>,
    trackType: Int,
    actions: PlayerTrackActions,
    includeAutomatic: Boolean,
    automaticSelected: Boolean,
    firstFocus: FocusRequester,
    modifier: Modifier = Modifier,
    compactRows: Boolean = false,
    showOriginFilters: Boolean = false,
    footer: (@Composable () -> Unit)? = null,
) {
    // Fixed order while open, so choosing a track never moves it out of view (TV-FOC-01, MOB-A11Y-05).
    val ordered = rememberStableTrackOrder(options, selectedFirst = false)
    var originFilter by remember { mutableStateOf<String?>(null) }
    val origins = if (showOriginFilters) ordered.originCounts() else emptyList()
    val visible = ordered.filter { originFilter == null || it.origin.key == originFilter }
    val showOrigin = showOriginFilters && origins.size > 1
    // The list opens on the chosen row; the first rows are Automatic or Off.
    val initialIndex = remember {
        if (includeAutomatic && automaticSelected) 0 else (visible.indexOfFirst(TrackOption::selected) + 1).coerceAtLeast(0)
    }
    val listState = remember(originFilter) { LazyListState(if (originFilter == null) initialIndex else 0) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showOrigin) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item("all") {
                    FilterChip(
                        selected = originFilter == null,
                        onClick = { originFilter = null },
                        label = { Text("${stringResource(R.string.player_subtitle_origin_all)}  ${ordered.size}") },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
                items(origins, key = { it.first.key }) { (origin, count) ->
                    FilterChip(
                        selected = originFilter == origin.key,
                        onClick = { originFilter = origin.key },
                        label = { Text("${trackOriginLabel(origin)}  $count") },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            state = listState,
            contentPadding = PaddingValues(vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (includeAutomatic) {
                item("automatic") {
                    PlayerChoiceRow(
                        title = stringResource(R.string.player_automatic),
                        supportingText = options.firstOrNull(TrackOption::selected)?.let { selected ->
                            listOfNotNull(selected.title, selected.supportingText).joinToString(" · ")
                        } ?: stringResource(R.string.player_best_track),
                        selected = automaticSelected,
                        compact = compactRows,
                        modifier = if (initialIndex == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    ) {
                        actions.automatic(trackType)
                    }
                }
            } else {
                item("off") {
                    PlayerChoiceRow(
                        title = stringResource(R.string.player_subtitle_off),
                        supportingText = stringResource(R.string.player_subtitle_no_captions),
                        selected = options.none(TrackOption::selected),
                        compact = compactRows,
                        modifier = if (initialIndex == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    ) {
                        actions.turnOff(trackType)
                    }
                }
            }
            if (options.isEmpty()) {
                item("empty") {
                    Text(
                        text = if (trackType == C.TRACK_TYPE_AUDIO) {
                            stringResource(R.string.player_no_audio)
                        } else {
                            stringResource(R.string.player_no_subtitles)
                        },
                        color = PlayerOnSurfaceMuted,
                        fontFamily = PlayerFont,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    )
                }
            }
            itemsIndexed(visible, key = { _, item -> item.id }) { index, option ->
                PlayerChoiceRow(
                    title = option.title,
                    supportingText = null,
                    badges = if (trackType == C.TRACK_TYPE_TEXT) {
                        listOf(trackOriginLabel(option.origin)) + option.badges
                    } else {
                        option.badges
                    },
                    selected = option.selected && (!includeAutomatic || !automaticSelected),
                    enabled = option.supported,
                    compact = compactRows,
                    modifier = if (originFilter == null && index + 1 == initialIndex) {
                        Modifier.focusRequester(firstFocus)
                    } else {
                        Modifier
                    },
                ) {
                    actions.choose(trackType, option)
                }
            }
            if (footer != null) item("tools") { footer() }
        }
    }
}

@Composable
private fun PlayerAudioTimingControls(
    delayMillis: Long,
    onDelay: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(PlayerSurface.copy(alpha = 0.56f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.player_audio_timing),
            color = PlayerOnSurface,
            fontFamily = PlayerFont,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
        )
        Text(
            text = stringResource(R.string.player_audio_route),
            color = PlayerOnSurfaceMuted,
            fontFamily = PlayerFont,
            fontSize = 12.sp,
        )
        PlayerStepper(
            value = if (delayMillis == 0L) "0 ms" else "%+d ms".format(delayMillis),
            decreaseLabel = stringResource(R.string.player_audio_earlier),
            increaseLabel = stringResource(R.string.player_audio_later),
            onDecrease = { onDelay(stepAudioDelay(delayMillis, -1)) },
            onReset = { onDelay(0L) },
            onIncrease = { onDelay(stepAudioDelay(delayMillis, 1)) },
        )
        Text(
            text = stringResource(R.string.player_audio_delay_help),
            color = PlayerOnSurfaceMuted,
            fontFamily = PlayerFont,
            fontSize = 12.sp,
        )
    }
}

@Composable
internal fun PlayerChoiceRow(
    title: String,
    supportingText: String?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
    actionRole: Role = Role.RadioButton,
    /** Short facts under the title as chips (Built-in, SRT, Forced); read as part of the row. */
    badges: List<String> = emptyList(),
    onClick: () -> Unit,
) {
    val interactionModifier = when (actionRole) {
        Role.Button -> Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        Role.Checkbox -> Modifier.toggleable(value = selected, enabled = enabled, role = Role.Checkbox) { onClick() }
        else -> Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
    }
    if (!LocalPlayerTelevision.current) {
        ListItem(
            headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
            supportingContent = if (supportingText == null && badges.isEmpty()) {
                null
            } else {
                {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        supportingText?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        if (badges.isNotEmpty()) {
                            PlayerBadgeRow(
                                badges,
                                content = MaterialTheme.colorScheme.onSurfaceVariant,
                                outline = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
            },
            trailingContent = {
                when (actionRole) {
                    Role.Button -> Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
                    Role.Checkbox -> Checkbox(selected, onCheckedChange = null, enabled = enabled)
                    else -> RadioButton(selected, onClick = null, enabled = enabled)
                }
            },
            colors = ListItemDefaults.colors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
                else Color.Transparent),
            modifier = modifier.then(interactionModifier),
        )
        return
    }
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(4.dp))
            .background(
                when {
                    focused -> PlayerFocused
                    selected -> PlayerPrimary.copy(alpha = 0.14f)
                    else -> Color.Transparent
                },
            )
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) PlayerPrimary else Color.Transparent,
                shape = RoundedCornerShape(4.dp),
            )
            .then(interactionModifier)
            .padding(
                horizontal = if (compact) 16.dp else 22.dp,
                vertical = if (compact) 9.dp else 14.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                color = when {
                    !enabled -> PlayerOnSurfaceMuted.copy(alpha = 0.45f)
                    focused -> PlayerFocusedContent
                    else -> PlayerOnSurface
                },
                fontFamily = PlayerFont,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            supportingText?.let {
                Text(
                    text = it,
                    color = if (focused) PlayerFocusedContent.copy(alpha = 0.74f) else PlayerOnSurfaceMuted,
                    fontFamily = PlayerFont,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (badges.isNotEmpty()) {
                PlayerBadgeRow(
                    badges,
                    content = if (focused) PlayerFocusedContent else PlayerOnSurfaceMuted,
                    outline = if (focused) PlayerFocusedContent.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.22f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = if (focused) PlayerFocusedContent else PlayerPrimary,
            )
        }
    }
}

/** Outlined chips for a track's facts; they wrap rather than clip at large font scales (MOB-TYP-03). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerBadgeRow(
    badges: List<String>,
    content: Color,
    outline: Color,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        badges.forEach { badge ->
            Text(
                text = badge,
                color = content,
                fontFamily = PlayerFont,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier
                    .border(1.dp, outline, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun PlayerStreamInfoPanel(
    streamInfo: String?,
    streamStats: () -> com.lamphaus.core.player.PlaybackStats?,
    isTelevision: Boolean,
    onClose: () -> Unit,
) {
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (isTelevision) closeFocus.requestFocus() }
    // Live engine numbers, refreshed each second while the panel is open.
    var stats by remember { mutableStateOf(streamStats()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            stats = streamStats()
            delay(1_000)
        }
    }
    val rows = buildList {
        stats?.let { current ->
            current.videoFormat?.let { format ->
                add(stringResource(R.string.player_stats_video) to StreamStatsFormat.video(
                    width = format.width,
                    height = format.height,
                    fps = format.frameRate.takeIf { it > 0f } ?: current.measuredFrameRate,
                    codec = format.sampleMimeType?.let { friendlyCodecName(it) },
                    hdr = format.playbackHdrType(),
                    bitrate = format.bitrate,
                ))
            }
            current.videoDecoder?.let {
                add(stringResource(R.string.player_stats_video_decoder) to StreamStatsFormat.decoder(it))
            }
            current.audioFormat?.let { format ->
                add(stringResource(R.string.player_stats_audio) to StreamStatsFormat.audio(
                    codec = format.sampleMimeType?.let { friendlyCodecName(it, atmos = "atmos" in format.label.orEmpty().lowercase()) },
                    channels = channelLayout(format.channelCount),
                    sampleRate = format.sampleRate,
                    bitrate = format.bitrate,
                ))
            }
            val decoder = current.audioDecoder
            val outputFormat = StreamStatsFormat.output(
                current.audioPassthrough, current.audioOutputEncoding, current.audioOutputChannels,
            )
            val output = when {
                current.audioPassthrough == true ->
                    stringResource(R.string.player_stats_passthrough, outputFormat.orEmpty())
                decoder != null ->
                    stringResource(R.string.player_stats_decoded, StreamStatsFormat.decoder(decoder), outputFormat.orEmpty())
                current.audioPassthrough == false ->
                    stringResource(R.string.player_stats_decoded_on_device, outputFormat.orEmpty())
                else -> null
            }
            output?.let { add(stringResource(R.string.player_stats_output) to it) }
            add(stringResource(R.string.player_stats_buffer) to
                stringResource(R.string.player_stats_buffer_value, current.bufferedMillis / 1_000f))
            add(stringResource(R.string.player_stats_dropped) to current.droppedFrames.toString())
        }
        streamInfo?.let { add(stringResource(R.string.player_stats_display) to it) }
    }
    PlayerOverlayLayout(stringResource(R.string.player_info), isTelevision, onClose, tvWidth = 560.dp) {
        if (rows.isEmpty()) {
            Text(stringResource(R.string.player_info_empty),
                style = MaterialTheme.typography.bodyLarge, color = PlayerOnSurfaceMuted)
        }
        // Rows scroll on their own so Done always stays on screen (TV-LAY-01).
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            rows.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = PlayerOnSurfaceMuted,
                        modifier = Modifier.width(128.dp))
                    Text(value, style = MaterialTheme.typography.bodyMedium, color = PlayerOnSurface,
                        modifier = Modifier.weight(1f))
                }
            }
        }
        PlayerTextButton(stringResource(R.string.player_done), onClose, Modifier.focusRequester(closeFocus))
    }
}

@Composable
private fun PlayerTimingEditor(
    isTelevision: Boolean,
    currentPositionMillis: Long,
    subtitleDelayMillis: Long,
    onSubtitleDelay: (Long) -> Unit,
    onLoadSidecarCues: ((List<SubtitleCue>) -> Unit) -> Unit,
    onApplySyncByLine: (Long, Long) -> Unit,
    onClose: () -> Unit,
) {
    var cues by remember { mutableStateOf<List<SubtitleCue>>(emptyList()) }
    var capturedPositionMillis by remember { mutableStateOf<Long?>(null) }
    var loadingCues by remember { mutableStateOf(false) }
    val initialFocus = remember { FocusRequester() }
    val visibleCues = remember(cues, capturedPositionMillis) {
        capturedPositionMillis?.let { nearbySubtitleCues(cues, it) }.orEmpty()
    }
    LaunchedEffect(Unit) { if (isTelevision) initialFocus.requestFocus() }
    PlayerOverlayLayout(stringResource(R.string.player_timing), isTelevision, onClose, tvWidth = 560.dp) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item("delay") {
                PlayerDelayRow(stringResource(R.string.player_manual_delay), formatSignedDelay(subtitleDelayMillis),
                    { onSubtitleDelay(stepSubtitleDelay(subtitleDelayMillis, it)) }, { onSubtitleDelay(0) })
            }
            item("help") {
                Text(stringResource(R.string.player_subtitle_delay_help), color = PlayerOnSurfaceMuted,
                    style = MaterialTheme.typography.bodyMedium)
            }
            item("sync") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.player_sync_help), color = PlayerOnSurfaceMuted,
                        style = MaterialTheme.typography.bodyMedium)
                    PlayerTextButton(stringResource(if (capturedPositionMillis == null) R.string.player_sync_now
                        else R.string.player_capture_again), onClick = {
                        capturedPositionMillis = currentPositionMillis
                        loadingCues = true
                        onLoadSidecarCues { cues = it; loadingCues = false }
                    }, modifier = Modifier.focusRequester(initialFocus))
                }
            }
            if (capturedPositionMillis != null) {
                item("captured") {
                    Text(stringResource(R.string.player_sync_capture, capturedPositionMillis!!.asPlaybackTime()),
                        color = PlayerOnSurface, style = MaterialTheme.typography.bodyMedium)
                }
                if (loadingCues) item("loading") { CircularProgressIndicator(Modifier.size(28.dp), color = PlayerPrimary) }
                else if (cues.isEmpty()) item("empty") {
                    Text(stringResource(R.string.player_sync_no_sidecar), color = PlayerOnSurfaceMuted,
                        style = MaterialTheme.typography.bodyMedium)
                }
                else itemsIndexed(visibleCues, key = { index, cue -> "$index:${cue.startMillis}" }) { _, cue ->
                    PlayerChoiceRow(cue.text.replace("\n", " "), cue.startMillis.asPlaybackTime(), false) {
                        onApplySyncByLine(capturedPositionMillis!!, cue.startMillis)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerStepper(
    value: String,
    decreaseLabel: String,
    increaseLabel: String,
    onDecrease: () -> Unit,
    onReset: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PlayerTextButton(
            label = "−",
            onClick = onDecrease,
            modifier = Modifier.semantics { contentDescription = decreaseLabel },
        )
        PlayerTextButton(label = value, onClick = onReset)
        PlayerTextButton(
            label = "+",
            onClick = onIncrease,
            modifier = Modifier.semantics { contentDescription = increaseLabel },
        )
    }
}

@Composable
private fun PlayerDelayRow(
    label: String,
    valueText: String,
    onStep: (Int) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val decreaseDescription = stringResource(R.string.player_decrease_value, label)
    val resetDescription = stringResource(R.string.player_reset_value, label)
    val increaseDescription = stringResource(R.string.player_increase_value, label)
    if (!LocalPlayerTelevision.current) {
        Row(modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PlayerDelayLabel(label, valueText, Modifier.weight(1f))
            FilledTonalIconButton(onClick = { onStep(-1) }) {
                Icon(Icons.Rounded.Remove, stringResource(R.string.player_decrease_value, label))
            }
            FilledTonalIconButton(onClick = onReset) {
                Icon(Icons.Rounded.Replay, stringResource(R.string.player_reset_value, label))
            }
            FilledTonalIconButton(onClick = { onStep(1) }) {
                Icon(Icons.Rounded.Add, stringResource(R.string.player_increase_value, label))
            }
        }
        return
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 4.dp),
    ) {
        val compact = maxWidth < 480.dp
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayerDelayLabel(label, valueText)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlayerTextButton(
                        label = "−",
                        onClick = { onStep(-1) },
                        modifier = Modifier.semantics { contentDescription = decreaseDescription },
                    )
                    PlayerTextButton(
                        label = stringResource(R.string.player_reset),
                        onClick = onReset,
                        modifier = Modifier.semantics { contentDescription = resetDescription },
                    )
                    PlayerTextButton(
                        label = "+",
                        onClick = { onStep(1) },
                        modifier = Modifier.semantics { contentDescription = increaseDescription },
                    )
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlayerDelayLabel(label, valueText, Modifier.weight(1f))
                PlayerTextButton(
                    label = "−",
                    onClick = { onStep(-1) },
                    modifier = Modifier.semantics { contentDescription = decreaseDescription },
                )
                PlayerTextButton(
                    label = stringResource(R.string.player_reset),
                    onClick = onReset,
                    modifier = Modifier.semantics { contentDescription = resetDescription },
                )
                PlayerTextButton(
                    label = "+",
                    onClick = { onStep(1) },
                    modifier = Modifier.semantics { contentDescription = increaseDescription },
                )
            }
        }
    }
}

@Composable
private fun PlayerDelayLabel(label: String, valueText: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(text = label, color = PlayerOnSurface, fontFamily = PlayerFont, fontSize = 15.sp)
        Text(text = valueText, color = PlayerOnSurfaceMuted, fontFamily = PlayerFont, fontSize = 13.sp)
    }
}

@Composable
private fun PlayerStyleEditor(
    style: SubtitleStyle,
    isTelevision: Boolean,
    onStyleChange: (SubtitleStyle) -> Unit,
    onClose: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }
    PlayerOverlayLayout(stringResource(R.string.player_appearance), isTelevision, onClose, tvWidth = 560.dp) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item("preview") { PlayerSubtitlePreview(style) }
            item("reset") {
                PlayerTextButton(stringResource(R.string.player_reset_all),
                    { onStyleChange(SubtitleStyle()) }, Modifier.focusRequester(firstFocus))
            }
            item("presets") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerTextButton(
                    label = stringResource(R.string.player_preset_default),
                    onClick = { onStyleChange(SubtitleStyle()) },
                    modifier = Modifier.weight(1f),
                )
                PlayerTextButton(
                    label = stringResource(R.string.player_preset_cinema),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onStyleChange(
                            SubtitleStyle(
                                sizePercent = 110,
                                verticalPositionFraction = style.verticalPositionFraction,
                                bold = false,
                                textColor = 0xFFFFFFFFL,
                                textOpacity = 1f,
                                backgroundColor = 0xFF000000L,
                                backgroundOpacity = 0.65f,
                                outlineEnabled = true,
                                outlineColor = 0xFF000000L,
                                outlineWidthDp = 2f,
                                preserveEmbeddedStyles = false,
                            ),
                        )
                    },
                )
                PlayerTextButton(
                    label = stringResource(R.string.player_preset_accessible),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onStyleChange(
                            SubtitleStyle(
                                sizePercent = 130,
                                verticalPositionFraction = style.verticalPositionFraction,
                                bold = true,
                                textColor = 0xFFFFFFFFL,
                                textOpacity = 1f,
                                backgroundColor = 0xFF000000L,
                                backgroundOpacity = 0.75f,
                                outlineEnabled = true,
                                outlineColor = 0xFF000000L,
                                outlineWidthDp = 2f,
                                preserveEmbeddedStyles = false,
                            ),
                        )
                    },
                )
            }
            }
                item("size") {
                    PlayerDelayRow(
                        label = stringResource(R.string.player_text_size),
                        valueText = "${style.sizePercent}%",
                        onStep = { steps ->
                            onStyleChange(style.copy(sizePercent = SubtitleStylePolicy.clampSizePercent(style.sizePercent + steps * 10)))
                        },
                        onReset = { onStyleChange(style.copy(sizePercent = 100)) },
                    )
                }
                item("position") {
                    PlayerDelayRow(
                        label = stringResource(R.string.player_position),
                        valueText = "${(style.verticalPositionFraction * 100).toInt()}%",
                        onStep = { steps ->
                            onStyleChange(
                                style.copy(
                                    verticalPositionFraction = SubtitleStylePolicy.clampPositionFraction(
                                        style.verticalPositionFraction + steps * 0.05f,
                                    ),
                                ),
                            )
                        },
                        onReset = { onStyleChange(style.copy(verticalPositionFraction = 0.92f)) },
                    )
                }
                item("text-opacity") {
                    PlayerDelayRow(
                        label = stringResource(R.string.player_text_opacity),
                        valueText = "${(SubtitleStylePolicy.clampOpacity(style.textOpacity) * 100).toInt()}%",
                        onStep = { steps ->
                            onStyleChange(
                                style.copy(textOpacity = SubtitleStylePolicy.clampOpacity(style.textOpacity + steps * 0.1f)),
                            )
                        },
                        onReset = { onStyleChange(style.copy(textOpacity = 1f)) },
                    )
                }
                item("bold") {
                    PlayerChoiceRow(
                        title = stringResource(R.string.player_bold),
                        supportingText = null,
                        selected = style.bold,
                        actionRole = Role.Checkbox,
                    ) {
                        onStyleChange(style.copy(bold = !style.bold))
                    }
                }
                item("text-color") {
                    SubtitleColorRow(
                        label = stringResource(R.string.player_text_color),
                        selected = style.textColor,
                        colors = SUBTITLE_TEXT_COLORS,
                    ) { onStyleChange(style.copy(textColor = it)) }
                }
                item("background-opacity") {
                    PlayerDelayRow(
                        label = stringResource(R.string.player_caption_background),
                        valueText = "${(SubtitleStylePolicy.clampOpacity(style.backgroundOpacity) * 100).toInt()}%",
                        onStep = { steps ->
                            onStyleChange(
                                style.copy(
                                    backgroundColor = 0xFF000000L,
                                    backgroundOpacity = SubtitleStylePolicy.clampOpacity(
                                        style.backgroundOpacity + steps * 0.1f,
                                    ),
                                ),
                            )
                        },
                        onReset = { onStyleChange(style.copy(backgroundOpacity = 0f)) },
                    )
                }
                item("outline") {
                    PlayerChoiceRow(
                        title = stringResource(R.string.player_outline),
                        supportingText = if (style.outlineEnabled) "Enabled · ${style.outlineWidthDp} dp" else "Disabled",
                        selected = style.outlineEnabled,
                        actionRole = Role.Checkbox,
                    ) {
                        onStyleChange(style.copy(outlineEnabled = !style.outlineEnabled))
                    }
                }
                if (style.outlineEnabled) {
                    item("outline-width") {
                        PlayerDelayRow(
                            label = stringResource(R.string.player_outline_width),
                            valueText = "${style.outlineWidthDp} dp",
                            onStep = { steps ->
                                onStyleChange(
                                    style.copy(
                                        outlineWidthDp = SubtitleStylePolicy.clampOutlineWidthDp(
                                            style.outlineWidthDp + steps * 0.5f,
                                        ),
                                    ),
                                )
                            },
                            onReset = { onStyleChange(style.copy(outlineWidthDp = 1.5f)) },
                        )
                    }
                    item("outline-color") {
                        SubtitleColorRow(
                            label = stringResource(R.string.player_outline_color),
                            selected = style.outlineColor,
                            colors = SUBTITLE_OUTLINE_COLORS,
                        ) { onStyleChange(style.copy(outlineColor = it)) }
                    }
                }
                item("preserve") {
                    PlayerChoiceRow(
                        title = stringResource(R.string.player_preserve_styles),
                        supportingText = stringResource(R.string.player_preserve_styles_detail),
                        selected = style.preserveEmbeddedStyles,
                        actionRole = Role.Checkbox,
                    ) {
                        onStyleChange(style.copy(preserveEmbeddedStyles = !style.preserveEmbeddedStyles))
                    }
                }
        }
    }
}

@Composable
private fun PlayerSubtitlePreview(style: SubtitleStyle) {
    val sample = stringResource(R.string.player_caption_preview)
    AndroidView(
        factory = { context -> androidx.media3.ui.SubtitleView(context) },
        update = { view ->
            SubtitleStyleApplier.apply(view, style)
            view.setCues(listOf(androidx.media3.common.text.Cue.Builder().setText(sample).build()))
        },
        modifier = Modifier.fillMaxWidth().height(160.dp).background(Color.Black)
            .semantics { contentDescription = sample },
    )
}

@Composable
private fun SubtitleColorRow(
    label: String,
    selected: Long,
    colors: List<Long>,
    onSelected: (Long) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            color = PlayerOnSurface,
            fontFamily = PlayerFont,
            fontSize = 15.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
        colors.forEach { value ->
            val colorName = stringResource(when (value) {
                0xFFFFFFFFL -> R.string.player_color_white
                0xFFFFD54FL -> R.string.player_color_yellow
                0xFF80DEEDL -> R.string.player_color_cyan
                0xFFA5D6A7L -> R.string.player_color_green
                0xFFEF9A9AL -> R.string.player_color_red
                0xFF000000L -> R.string.player_color_black
                else -> R.string.player_color_charcoal
            })
            var focused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .onFocusChanged { focused = it.isFocused }
                    .clip(RoundedCornerShape(4.dp))
                    .border(
                        width = if (focused || value == selected) 3.dp else 1.dp,
                        color = if (focused) PlayerPrimary else if (value == selected) PlayerOnSurface else PlayerOnSurfaceMuted.copy(alpha = 0.42f),
                        shape = RoundedCornerShape(4.dp),
                    )
                    .selectable(selected = value == selected, role = Role.RadioButton) { onSelected(value) }
                    .semantics { contentDescription = "$label: $colorName" }
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(value)),
                )
            }
        }
        }
    }
}

@Composable
private fun PlayerErrorPanel(
    message: String,
    onRetry: () -> Unit,
    onOpenExternally: () -> Unit,
) {
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { retryFocus.requestFocus() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = 560.dp).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = message,
                color = PlayerOnSurface,
                fontFamily = PlayerFont,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlayerTextButton("Retry", onRetry, Modifier.focusRequester(retryFocus))
                PlayerTextButton("Open in another player", onClick = onOpenExternally)
            }
        }
    }
}

@Composable
internal fun PlayerTextButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!LocalPlayerTelevision.current) {
        TextButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
        return
    }
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(4.dp))
            .background(if (focused) PlayerFocused else PlayerControlContainer)
            .border(if (focused) 3.dp else 0.dp, PlayerPrimary, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (focused) PlayerFocusedContent else PlayerOnSurface,
            fontFamily = PlayerFont,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
        )
    }
}

private fun Player.snapshot(): PlayerSnapshot {
    val tracks = currentTracks
    val capabilityError = when {
        tracks.containsType(C.TRACK_TYPE_VIDEO) && !tracks.isTypeSupported(C.TRACK_TYPE_VIDEO) ->
            "This device cannot decode this video's format or resolution. Try another source or player."
        tracks.containsType(C.TRACK_TYPE_AUDIO) && !tracks.isTypeSupported(C.TRACK_TYPE_AUDIO) ->
            "This device cannot decode this source's audio. Try another source or player."
        else -> null
    }
    return PlayerSnapshot(
        playing = isPlaying,
        buffering = playbackState == Player.STATE_BUFFERING,
        positionMillis = currentPosition.coerceAtLeast(0),
        bufferedPositionMillis = bufferedPosition.coerceAtLeast(0),
        durationMillis = duration.takeUnless { it == C.TIME_UNSET }?.coerceAtLeast(0) ?: 0,
        tracks = tracks,
        speed = playbackParameters.speed,
        errorMessage = playerError?.safeMessage() ?: capabilityError,
    )
}

private fun PlaybackException.safeMessage(): String = when (errorCode) {
    in 2000..2999 -> "The stream could not be loaded. Check the connection or try another source."
    in 3000..3999 -> "This source uses a container the player could not read."
    in 4000..4999 -> "This device cannot decode this video format. Try another source or player."
    in 5000..5999 -> "This TV cannot play the source's audio format. Try another source or player."
    else -> "Playback stopped unexpectedly. Try again or choose another source."
}

/**
 * The panel rows for [trackType]. [addonSubtitles] maps add-on subtitle ids
 * (carried as the track's format id) to their add-on's name, which marks the
 * track's origin and is dropped from its title since the chip shows it.
 */
private fun Tracks.options(
    trackType: Int,
    addonSubtitles: Map<String, String?> = emptyMap(),
): List<TrackOption> = groups
    .filter { it.type == trackType }
    .flatMapIndexed { groupIndex, group ->
        (0 until group.length).map { trackIndex ->
            val format = group.getTrackFormat(trackIndex)
            val addon = com.lamphaus.core.model.sourceTrackId(format.id)?.takeIf(addonSubtitles::containsKey)
            val addonName = addon?.let(addonSubtitles::get)?.takeIf(String::isNotBlank)
            val label = addonName?.let { name -> format.label?.removePrefix(name)?.removePrefix(" · ") } ?: format.label
            TrackOption(
                id = "$trackType:$groupIndex:$trackIndex:${format.id.orEmpty()}",
                title = trackTitle(format.language, label, trackIndex),
                supportingText = format.trackDetails(trackType),
                selected = group.isTrackSelected(trackIndex),
                supported = group.isTrackSupported(trackIndex, true),
                languageKey = normalizedSubtitleLanguageKey(format.language),
                group = group.mediaTrackGroup,
                trackIndex = trackIndex,
                badges = format.trackBadges(trackType),
                origin = if (addon != null) TrackOrigin.Addon(addonName) else TrackOrigin.BuiltIn,
                format = format,
            )
        }
    }

private fun Player.selectTrack(trackType: Int, option: TrackOption) {
    trackSelectionParameters = trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(trackType, false)
        .setOverrideForType(TrackSelectionOverride(option.group, option.trackIndex))
        .build()
}

private fun Player.clearTrackOverride(trackType: Int, disabled: Boolean) {
    trackSelectionParameters = trackSelectionParameters.buildUpon()
        .clearOverridesOfType(trackType)
        .setTrackTypeDisabled(trackType, disabled)
        .build()
}

private fun Player.hasOverride(trackType: Int): Boolean =
    trackSelectionParameters.overrides.values.any { it.type == trackType }

internal fun Long.asPlaybackTime(): String {
    val totalSeconds = coerceAtLeast(0) / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

private val PLAYBACK_SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
private val SUBTITLE_TEXT_COLORS = listOf(
    0xFFFFFFFFL,
    0xFFFFD54FL,
    0xFF80DEEDL,
    0xFFA5D6A7L,
    0xFFEF9A9AL,
    0xFF000000L,
)
private val SUBTITLE_BACKGROUND_COLORS = listOf(
    0xFF000000L,
    0xFF263238L,
    0xFFFFFFFFL,
)
private val SUBTITLE_OUTLINE_COLORS = listOf(
    0xFF000000L,
    0xFFFFFFFFL,
    0xFF263238L,
)
