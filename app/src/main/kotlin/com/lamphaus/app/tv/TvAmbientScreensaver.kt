package com.lamphaus.app.tv

import android.text.format.DateFormat
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import coil3.compose.AsyncImage
import com.lamphaus.app.ui.KenBurnsDefaults
import com.lamphaus.app.ui.LamphausWordmark
import com.lamphaus.app.ui.kenBurnsPathFor
import com.lamphaus.core.model.LibraryEntry
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.WatchProgress
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.util.Date

/** One screensaver frame: a title's backdrop with its logo or name. */
internal data class AmbientSlide(
    val key: String,
    val imageUrl: String,
    val logoUrl: String?,
    val title: String,
)

/** Slides at most; enough variety for an evening without a large image cache. */
private const val AMBIENT_MAX_SLIDES = 30

/**
 * The profile's own titles that have a backdrop: Continue Watching first,
 * then Library, newest first, each once (TV-AMB-01). Only artwork and names
 * are used: progress, profile, and episode details never appear in a
 * shared room (SHR-PROD-06).
 */
internal fun ambientSlides(
    progress: List<WatchProgress>,
    library: List<LibraryEntry>,
    fallback: List<MediaPreview> = emptyList(),
): List<AmbientSlide> {
    val own: Sequence<MediaPreview> =
        progress.asSequence().sortedByDescending(WatchProgress::updatedAtEpochMillis).mapNotNull { it.preview } +
            library.asSequence().sortedByDescending(LibraryEntry::updatedAtEpochMillis).map { it.preview }
    // A new profile with nothing of its own borrows Home's loaded titles.
    val previews = if (own.any { !it.backgroundUrl.isNullOrBlank() }) own else fallback.asSequence()
    return previews
        .filter { !it.backgroundUrl.isNullOrBlank() }
        .distinctBy(MediaPreview::stableKey)
        .take(AMBIENT_MAX_SLIDES)
        .map { AmbientSlide(it.stableKey, it.backgroundUrl!!, it.logoUrl?.takeIf(String::isNotBlank), it.name) }
        .toList()
}

/** How long each slide shows; with animations removed it holds longer and does not drift. */
private const val SLIDE_MILLIS = 20_000L
private const val STILL_SLIDE_MILLIS = 60_000L

/**
 * The ambient screensaver (TV-AMB-01): the profile's backdrops in slow Ken
 * Burns drift with a 20 s crossfade, the title's logo bottom-start inside
 * the safe margins (TV-LAY-01), and a small clock with the wordmark. With
 * animations removed the frame is still and changes every minute
 * (TV-MOT-01). Shared by the system screensaver and the in-app idle
 * ambient; it holds no focus and handles no keys.
 */
@Composable
internal fun TvAmbientScreensaver(
    slides: List<AmbientSlide>,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    var index by remember(slides) { mutableIntStateOf(0) }
    LaunchedEffect(slides, reducedMotion) {
        if (slides.size < 2) return@LaunchedEffect
        while (true) {
            delay(if (reducedMotion) STILL_SLIDE_MILLIS else SLIDE_MILLIS)
            index = (index + 1) % slides.size
        }
    }
    Box(modifier.fillMaxSize().background(Color.Black)) {
        val slide = slides.getOrNull(index)
        Crossfade(
            targetState = slide,
            animationSpec = tween(if (reducedMotion) 0 else TvMotionTokens.ambientSlideCrossfadeMillis),
            label = "ambient slide",
        ) { current ->
            if (current != null) AmbientFrame(current, reducedMotion)
        }
        AmbientClock(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = TvLayoutTokens.screenTopPadding, end = TvLayoutTokens.screenHorizontalPadding),
        )
    }
}

@Composable
private fun AmbientFrame(slide: AmbientSlide, reducedMotion: Boolean) {
    val path = remember(slide.key) { kenBurnsPathFor(slide.key) }
    val progress = remember(slide.key) { Animatable(0f) }
    LaunchedEffect(slide.key, reducedMotion) {
        if (!reducedMotion) progress.animateTo(1f, tween(SLIDE_MILLIS.toInt() + 4_000, easing = LinearEasing))
    }
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = slide.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val p = progress.value
                val scale = 1f + (KenBurnsDefaults.maxScale - 1f) * p
                scaleX = scale
                scaleY = scale
                translationX = size.width * path.horizontalSign * KenBurnsDefaults.horizontalTranslationFraction * p
                translationY = size.height * path.verticalSign * KenBurnsDefaults.verticalTranslationFraction * p
            },
        )
        // Legibility: a quiet bottom-start scrim behind the logo (PLY-IMM-03 spirit).
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.72f)),
            ),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = TvLayoutTokens.screenHorizontalPadding, bottom = 48.dp)
                .fillMaxWidth(0.4f),
        ) {
            if (slide.logoUrl != null) {
                AsyncImage(
                    model = slide.logoUrl,
                    contentDescription = slide.title,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.BottomStart,
                    modifier = Modifier.widthIn(max = 320.dp).heightIn(max = 110.dp),
                )
            } else {
                BasicText(
                    slide.title,
                    style = TextStyle(color = AmbientInk, fontSize = 32.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                )
            }
        }
    }
}

/** The time, updated each minute, beside the wordmark. */
@Composable
private fun AmbientClock(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val format = remember(context) { DateFormat.getTimeFormat(context) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000 - now % 60_000)
            now = System.currentTimeMillis()
        }
    }
    Column(modifier, horizontalAlignment = Alignment.End) {
        BasicText(
            format.format(Date(now)),
            style = TextStyle(color = AmbientInk, fontSize = 28.sp, fontWeight = FontWeight.Light),
        )
        LamphausWordmark(fontSize = 11.sp, color = AmbientInk.copy(alpha = 0.7f))
    }
}

/** TV primary ink (TV-CLR-01) at full opacity over the scrim. */
private val AmbientInk = Color(0xFFE3E2E6)

/**
 * The in-app idle ambient (TV-AMB-01): with the device-local setting on, the
 * chosen number of minutes without the remote on any Lamphaus screen covers
 * it with the screensaver. It waits while something it cannot see keys for
 * is open: a dialog or menu in its own window, or the on-screen keyboard.
 * Playback runs in its own activity, so it never covers a playing title.
 * Any key ends it and is swallowed, so focus stays exactly where it was; it
 * never enters the back stack (TV-NAV-06).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TvIdleAmbient(
    state: com.lamphaus.app.ui.AppUiState,
    host: TvAmbientHost,
    reducedMotion: Boolean,
) {
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val keyboardOpen = WindowInsets.isImeVisible
    val signedIn = state.account is com.lamphaus.core.data.cloud.AccountState.SignedIn
    val enabled = state.engagement.tvIdleAmbient && signedIn
    val idleMillis = state.engagement.tvIdleAmbientMinutes * 60_000L
    LaunchedEffect(resumed) { if (resumed) host.markActive() }
    // Coming back from a dialog or the keyboard counts as activity.
    LaunchedEffect(windowFocused, keyboardOpen) { host.markActive() }
    // Keys are read in a snapshot flow, not in composition, so D-pad presses
    // never recompose anything here (QA-08); off by default, it costs nothing.
    LaunchedEffect(enabled, resumed, windowFocused, keyboardOpen, idleMillis) {
        if (!enabled || !resumed || !windowFocused || keyboardOpen) {
            host.showing = false
            return@LaunchedEffect
        }
        snapshotFlow { host.lastInputAtMillis }.collectLatest { lastInput ->
            val idleFor = android.os.SystemClock.elapsedRealtime() - lastInput
            delay((idleMillis - idleFor).coerceAtLeast(0))
            host.showing = true
        }
    }
    if (host.showing) {
        val slides = remember(state.progress, state.library, state.sections) {
            ambientSlides(state.progress, state.library, com.lamphaus.app.ui.firstDistinctMedia(state.sections, limit = 30))
        }
        TvAmbientScreensaver(slides, reducedMotion)
    }
}

/**
 * In-app idle ambient state (TV-AMB-01). The activity records every key; a
 * key that ends the ambient is swallowed so it never also acts on the page
 * beneath.
 */
@Stable
internal class TvAmbientHost {
    var showing by mutableStateOf(false)
    var lastInputAtMillis by mutableLongStateOf(android.os.SystemClock.elapsedRealtime())
        private set
    private var swallowing = false

    /** Counts returning to the page (from playback, for example) as activity. */
    fun markActive() {
        lastInputAtMillis = android.os.SystemClock.elapsedRealtime()
    }

    /** Returns true when the key was consumed by dismissing the ambient. */
    fun onKey(down: Boolean): Boolean {
        lastInputAtMillis = android.os.SystemClock.elapsedRealtime()
        if (showing && down) {
            swallowing = true
            return true
        }
        if (swallowing) {
            if (!down) {
                swallowing = false
                showing = false
            }
            return true
        }
        return false
    }
}

internal val LocalTvAmbientHost = staticCompositionLocalOf { TvAmbientHost() }
