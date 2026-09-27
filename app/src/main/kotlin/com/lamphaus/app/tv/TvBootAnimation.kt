package com.lamphaus.app.tv

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.lamphaus.app.R
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

/**
 * TV boot sequence (TV-MOT-01): the Lamphaus mark is drawn as a blueprint,
 * fills, and its lamp switches on; the light cone blooms, the wordmark lights
 * up letter by letter, and the light then opens a circular window into Home.
 *
 * It never adds waiting: the intro plays while Home loads underneath, the
 * reveal starts as soon as Home is usable (or [TvBootTokens.maxHoldMillis]
 * passes), any key skips ahead, and remove-animations omits it entirely.
 */
internal object TvBootTokens {
    const val introMillis = 1_900
    const val exitMillis = 620
    const val maxHoldMillis = 3_500L

    /**
     * Home's first composition blocks the main thread for a few hundred
     * milliseconds on TV boxes; the reveal waits for this many consecutive
     * on-time frames (at most [settleCapMillis]) so it is never skipped.
     */
    const val settleFrames = 8
    const val settleFrameBudgetMillis = 34L
    const val settleCapMillis = 1_200L

    val background = Color(0xFF090A0D)
    val house = Color(0xFF4058D8)
    val light = Color(0xFF68D4E8)
    val blueprint = Color(0xFFA8C8FF)
    val markSize = 152.dp

    // Intro timeline, in milliseconds from the first frame.
    const val traceStart = 0; const val traceEnd = 520
    const val fillStart = 380; const val fillEnd = 720
    const val lampStart = 560; const val lampEnd = 780
    const val switchOn = 780
    const val coneStart = 820; const val coneEnd = 1_080
    const val bloomStart = 820; const val bloomEnd = 1_400
    const val slideStart = 1_000; const val slideEnd = 1_450
    const val lettersStart = 1_100; const val letterStagger = 45; const val letterDuration = 300
    const val sweepStart = 1_350; const val sweepEnd = 1_900
}

/**
 * Plays on every launch of the TV host. The played flag is saved state, so a
 * configuration change, process-death restore, or returning from playback
 * (the activity survives) never replays it, while a fresh launch always does.
 */
@Stable
internal class TvBootState(enabled: Boolean, private val onFinished: () -> Unit = {}) {
    var active by mutableStateOf(enabled)
        private set
    internal var skipRequests by mutableIntStateOf(0)
        private set

    /** Any key fast-forwards: to the held logo if Home is not ready, else straight to the reveal. */
    fun skip() {
        skipRequests++
    }

    internal fun finish() {
        active = false
        onFinished()
    }
}

@Composable
internal fun rememberTvBootState(enabled: Boolean): TvBootState {
    var played by rememberSaveable { mutableStateOf(false) }
    return remember { TvBootState(enabled && !played) { played = true } }
}

private val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

private fun progress(t: Float, start: Int, end: Int, easing: Easing = LinearEasing): Float =
    easing.transform(((t - start) / (end - start)).coerceIn(0f, 1f))

/** A quick three-step flicker as the lamp catches, then steady. */
private fun flicker(t: Float): Float {
    val local = t - TvBootTokens.switchOn
    return when {
        local < 0 -> 0f
        local < 50 -> local / 50f
        local < 100 -> 0.35f
        local < 150 -> 1f
        local < 190 -> 0.6f
        else -> 1f
    }
}

private suspend fun awaitSmoothFrames() {
    val start = withFrameMillis { it }
    var last = start
    var onTime = 0
    while (onTime < TvBootTokens.settleFrames && last - start < TvBootTokens.settleCapMillis) {
        val now = withFrameMillis { it }
        onTime = if (now - last <= TvBootTokens.settleFrameBudgetMillis) onTime + 1 else 0
        last = now
    }
}

@Composable
internal fun TvBootOverlay(state: TvBootState, contentReady: Boolean, modifier: Modifier = Modifier) {
    if (!state.active) return
    val ready by rememberUpdatedState(contentReady)
    val timeline = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    var breathing by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        timeline.animateTo(TvBootTokens.introMillis.toFloat(), tween(TvBootTokens.introMillis, easing = LinearEasing))
        // Hold on the lit mark, breathing gently, until Home is usable.
        val holdStart = withFrameMillis { it }
        while (!ready) {
            val now = withFrameMillis { it }
            val elapsed = now - holdStart
            if (elapsed >= TvBootTokens.maxHoldMillis) break
            breathing = sin(elapsed / 1_400.0 * 2 * PI).toFloat()
        }
        breathing = 0f
        awaitSmoothFrames()
        exit.animateTo(1f, tween(TvBootTokens.exitMillis, easing = FastOutSlowInEasing))
        state.finish()
    }
    LaunchedEffect(state.skipRequests) {
        if (state.skipRequests == 0) return@LaunchedEffect
        if (timeline.value < TvBootTokens.introMillis) {
            timeline.snapTo(TvBootTokens.introMillis.toFloat())
        } else if (ready) {
            exit.snapTo(1f)
        }
    }

    TvBootScene(t = timeline.value, e = exit.value, breathing = breathing, modifier = modifier)
}

/**
 * The lit lockup as a still frame, for the loading mark when the boot
 * sequence is not playing (remove animations, a global-search launch).
 */
@Composable
internal fun TvBootStill(modifier: Modifier = Modifier) {
    TvBootScene(t = TvBootTokens.introMillis.toFloat(), e = 0f, breathing = 0f, modifier = modifier)
}

@Composable
private fun TvBootScene(t: Float, e: Float, breathing: Float, modifier: Modifier = Modifier) {
    var rowWidth by remember { mutableIntStateOf(0) }
    var lightCenter by remember { mutableStateOf(Offset.Unspecified) }
    val appName = stringResource(R.string.app_name)
    val letters = remember(appName) { appName.uppercase().toList() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clearAndSetSemantics { contentDescription = appName }
            .drawWithContent {
                // No offscreen layer: the reveal is a feathered hole in the
                // background itself, so every frame stays a single cheap pass (QA-08).
                drawBackground(e, lightCenter)
                drawBloom(t, breathing, lightCenter, fade = 1f - e)
                drawContent()
                if (e > 0f) drawRevealRim(e, lightCenter)
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .onSizeChanged { rowWidth = it.width }
                .graphicsLayer {
                    // The lockup is gone before the opening light crosses Home.
                    alpha = 1f - (e / 0.3f).coerceAtMost(1f)
                    val scale = 1f + 0.06f * e
                    scaleX = scale
                    scaleY = scale
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Canvas(
                Modifier
                    .size(TvBootTokens.markSize)
                    .graphicsLayer {
                        // Starts centred on screen, then slides to make room for the wordmark.
                        val startOffset = (rowWidth - TvBootTokens.markSize.toPx()) / 2f
                        translationX = startOffset * (1f - progress(t, TvBootTokens.slideStart, TvBootTokens.slideEnd, EmphasizedDecelerate))
                    }
                    .onGloballyPositioned { coordinates ->
                        val bounds: Rect = coordinates.boundsInRoot()
                        // The light pool sits under the lamp cone (viewport 54,84 of 108).
                        lightCenter = Offset(
                            bounds.left + bounds.width * 54f / 108f,
                            bounds.top + bounds.height * 72f / 108f,
                        )
                    },
            ) {
                drawMark(t)
            }
            Row(
                modifier = Modifier
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawSweep(t, size)
                    },
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                letters.forEachIndexed { index, letter ->
                    val start = TvBootTokens.lettersStart + index * TvBootTokens.letterStagger
                    val p = progress(t, start, start + TvBootTokens.letterDuration, EmphasizedDecelerate)
                    Text(
                        text = letter.toString(),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 4.sp,
                        ),
                        color = Color(0xFFE3E2E6),
                        modifier = Modifier.graphicsLayer {
                            alpha = p
                            translationY = (1f - p) * 14.dp.toPx()
                        },
                    )
                }
            }
        }
    }
}

private object MarkPaths {
    val house: Path = PathParser().parsePathString("M18,48 L54,18 L90,48 L90,88 L18,88 Z").toPath()
    val lamp: Path = PathParser()
        .parsePathString("M50,31 L58,31 L58,48 L67,48 C67,48 73,50 76,58 L32,58 C35,50 41,48 50,48 Z").toPath()
    val cone: Path = PathParser().parsePathString("M43,58 L65,58 L76,84 L32,84 Z").toPath()
}

private fun DrawScope.drawMark(t: Float) {
    val scale = size.width / 108f
    withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
        val fill = progress(t, TvBootTokens.fillStart, TvBootTokens.fillEnd, FastOutSlowInEasing)
        val trace = progress(t, TvBootTokens.traceStart, TvBootTokens.traceEnd, FastOutSlowInEasing)
        // House body fills in behind the blueprint line.
        if (fill > 0f) drawPath(MarkPaths.house, TvBootTokens.house.copy(alpha = fill))
        if (trace > 0f && fill < 1f) {
            val measure = PathMeasure().apply { setPath(MarkPaths.house, false) }
            val traced = Path()
            val length = measure.length * trace
            measure.getSegment(0f, length, traced, true)
            drawPath(
                traced,
                TvBootTokens.blueprint.copy(alpha = 1f - fill),
                style = Stroke(width = 1.6f),
            )
            if (trace < 1f) {
                // A bright pen head leads the line.
                val head = measure.getPosition(length)
                drawCircle(TvBootTokens.blueprint.copy(alpha = 0.35f), radius = 4f, center = head)
                drawCircle(Color.White, radius = 1.4f, center = head)
            }
        }
        val lamp = progress(t, TvBootTokens.lampStart, TvBootTokens.lampEnd, EmphasizedDecelerate)
        if (lamp > 0f) {
            val lampScale = 0.85f + 0.15f * lamp
            withTransform({ scale(lampScale, lampScale, pivot = Offset(54f, 48f)) }) {
                drawPath(MarkPaths.lamp, TvBootTokens.background.copy(alpha = lamp))
            }
        }
        val cone = progress(t, TvBootTokens.coneStart, TvBootTokens.coneEnd, EmphasizedDecelerate)
        val lit = flicker(t)
        if (lit > 0f) {
            // The cone pours down from the shade.
            clipRect(left = 0f, top = 58f, right = 108f, bottom = 58f + 26f * cone.coerceAtLeast(0.08f)) {
                drawPath(MarkPaths.cone, TvBootTokens.light.copy(alpha = lit))
            }
        }
    }
}

private fun DrawScope.drawBloom(t: Float, breathing: Float, center: Offset, fade: Float) {
    if (center == Offset.Unspecified) return
    val bloom = progress(t, TvBootTokens.bloomStart, TvBootTokens.bloomEnd, EmphasizedDecelerate) * flicker(t) * fade
    if (bloom <= 0f) return
    val breath = 1f + 0.08f * breathing
    val wide = size.minDimension * 0.9f * (0.4f + 0.6f * bloom) * breath
    drawCircle(
        brush = Brush.radialGradient(
            0f to TvBootTokens.house.copy(alpha = 0.16f * bloom),
            1f to Color.Transparent,
            center = center,
            radius = wide,
        ),
        radius = wide,
        center = center,
    )
    val near = size.minDimension * 0.34f * (0.3f + 0.7f * bloom) * breath
    drawCircle(
        brush = Brush.radialGradient(
            0f to TvBootTokens.light.copy(alpha = 0.20f * bloom),
            1f to Color.Transparent,
            center = center,
            radius = near,
        ),
        radius = near,
        center = center,
    )
}

/** A soft band of light crossing the wordmark, painted only on the glyphs. */
private fun DrawScope.drawSweep(t: Float, area: Size) {
    val p = progress(t, TvBootTokens.sweepStart, TvBootTokens.sweepEnd, FastOutSlowInEasing)
    if (p <= 0f || p >= 1f) return
    val band = area.width * 0.28f
    val x = -band + (area.width + 2 * band) * p
    drawRect(
        brush = Brush.horizontalGradient(
            0f to Color.Transparent,
            0.5f to TvBootTokens.light,
            1f to Color.Transparent,
            startX = x - band / 2,
            endX = x + band / 2,
        ),
        size = area,
        blendMode = BlendMode.SrcAtop,
    )
}

private fun DrawScope.revealOrigin(center: Offset): Offset =
    if (center == Offset.Unspecified) this.center else center

private fun DrawScope.revealRadius(e: Float, origin: Offset): Float {
    val farthest = listOf(
        Offset.Zero, Offset(size.width, 0f), Offset(0f, size.height), Offset(size.width, size.height),
    ).maxOf { hypot(it.x - origin.x, it.y - origin.y) }
    return farthest * 1.15f * e
}

/**
 * The boot field; during the exit the lamp's light opens a feathered circular
 * window through it into Home.
 */
private fun DrawScope.drawBackground(e: Float, center: Offset) {
    if (e <= 0f) {
        drawRect(TvBootTokens.background)
        return
    }
    val origin = revealOrigin(center)
    val outer = revealRadius(e, origin)
    val feather = outer * 0.16f
    val hole = Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(Rect(Offset.Zero, size))
        addOval(Rect(origin, outer))
    }
    drawPath(hole, TvBootTokens.background)
    if (outer > 1f) {
        drawCircle(
            brush = Brush.radialGradient(
                ((outer - feather) / outer).coerceIn(0f, 1f) to Color.Transparent,
                1f to TvBootTokens.background,
                center = origin,
                radius = outer,
            ),
            radius = outer,
            center = origin,
        )
    }
}

/** A thin bright rim leads the opening light. */
private fun DrawScope.drawRevealRim(e: Float, center: Offset) {
    val origin = revealOrigin(center)
    val outer = revealRadius(e, origin)
    drawCircle(
        color = TvBootTokens.light.copy(alpha = 0.5f * (1f - e)),
        radius = outer * 0.9f,
        center = origin,
        style = Stroke(width = 2.dp.toPx()),
    )
}
