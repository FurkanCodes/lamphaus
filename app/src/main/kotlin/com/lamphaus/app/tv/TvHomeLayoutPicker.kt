package com.lamphaus.app.tv

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.lamphaus.app.R
import com.lamphaus.app.ui.TvHomeLayout
import com.lamphaus.app.ui.rememberReducedMotion
import kotlinx.coroutines.delay

@StringRes
internal fun TvHomeLayout.labelRes(): Int = when (this) {
    TvHomeLayout.CLASSIC -> R.string.home_layout_classic
    TvHomeLayout.SPOTLIGHT -> R.string.home_layout_spotlight
    TvHomeLayout.SHOWCASE -> R.string.home_layout_showcase
    TvHomeLayout.MARQUEE -> R.string.home_layout_marquee
}

@StringRes
internal fun TvHomeLayout.summaryRes(): Int = when (this) {
    TvHomeLayout.CLASSIC -> R.string.home_layout_classic_summary
    TvHomeLayout.SPOTLIGHT -> R.string.home_layout_spotlight_summary
    TvHomeLayout.SHOWCASE -> R.string.home_layout_showcase_summary
    TvHomeLayout.MARQUEE -> R.string.home_layout_marquee_summary
}

@StringRes
internal fun TvHomeLayout.descriptionRes(): Int = when (this) {
    TvHomeLayout.CLASSIC -> R.string.home_layout_classic_description
    TvHomeLayout.SPOTLIGHT -> R.string.home_layout_spotlight_description
    TvHomeLayout.SHOWCASE -> R.string.home_layout_showcase_description
    TvHomeLayout.MARQUEE -> R.string.home_layout_marquee_description
}

/**
 * Settings → Appearance → Home layout (TV-CNT-07): the layouts listed beside
 * a live preview of the focused one. Focus starts on the current layout,
 * Select applies the focused one and closes, and Back closes unchanged.
 */
@Composable
internal fun TvHomeLayoutPicker(
    current: TvHomeLayout,
    onSelect: (TvHomeLayout) -> Unit,
    onDismiss: () -> Unit,
) {
    var focusedLayout by remember { mutableStateOf(current) }
    val currentFocus = remember { FocusRequester() }
    val reducedMotion = rememberReducedMotion()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        LaunchedEffect(Unit) { runCatching { currentFocus.requestFocus() } }
        Surface(
            modifier = Modifier.width(TvLayoutPickerTokens.dialogWidth),
            shape = TvShapeTokens.hero,
            colors = SurfaceDefaults.colors(containerColor = TvSurfaceTokens.elevated),
        ) {
            Column(Modifier.padding(horizontal = 32.dp, vertical = 28.dp)) {
                Text(
                    text = stringResource(R.string.home_layout),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.home_layout_picker_subtitle),
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.padding(top = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(TvLayoutPickerTokens.paneSpacing),
                ) {
                    Column(
                        modifier = Modifier.width(TvLayoutPickerTokens.listWidth).selectableGroup(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TvHomeLayout.entries.forEach { layout ->
                            TvHomeLayoutOption(
                                layout = layout,
                                selected = layout == current,
                                onClick = { onSelect(layout) },
                                modifier = Modifier
                                    .onFocusChanged { if (it.isFocused) focusedLayout = layout }
                                    .then(if (layout == current) Modifier.focusRequester(currentFocus) else Modifier),
                            )
                        }
                    }
                    Column(Modifier.width(TvLayoutPickerTokens.previewWidth)) {
                        val previewDescription = stringResource(
                            R.string.home_layout_preview_description,
                            stringResource(focusedLayout.labelRes()),
                        )
                        Box(
                            modifier = Modifier
                                .size(TvLayoutPickerTokens.previewWidth, TvLayoutPickerTokens.previewHeight)
                                .clip(TvShapeTokens.card)
                                .background(MaterialTheme.colorScheme.surface)
                                .semantics { contentDescription = previewDescription },
                        ) {
                            TvLayerlessCrossfade(
                                targetState = focusedLayout,
                                reducedMotion = reducedMotion,
                                label = "home layout preview",
                                modifier = Modifier.fillMaxSize(),
                            ) { layout ->
                                TvHomeLayoutPreview(
                                    layout = layout,
                                    animate = !reducedMotion,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        Text(
                            text = stringResource(focusedLayout.descriptionRes()),
                            modifier = Modifier.padding(top = 16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            minLines = 2,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvHomeLayoutOption(
    layout: TvHomeLayout,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvFocusableSurface(
        onClick = onClick,
        role = Role.RadioButton,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TvLayoutPickerTokens.optionMinHeight)
            .semantics { this.selected = selected },
    ) { focused ->
        val content = if (focused) TvFocusTokens.focusedContent else MaterialTheme.colorScheme.onBackground
        val supporting = if (focused) {
            TvFocusTokens.focusedContent.copy(alpha = 0.76f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(layout.labelRes()), style = MaterialTheme.typography.titleSmall, color = content)
                Text(stringResource(layout.summaryRes()), style = MaterialTheme.typography.bodySmall, color = supporting)
            }
            if (selected) {
                TvIcon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = if (focused) content else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * A layout's preview: plain shapes at a third of the reference canvas, with
 * focus walking through it the way the layout moves (TV-CNT-07). Each step
 * eases every shape from its last frame; only the draw phase reads the
 * clock, so the dialog never recomposes while it plays (QA-08). Without
 * [animate] the first frame stands still (TV-MOT-01).
 */
@Composable
internal fun TvHomeLayoutPreview(
    layout: TvHomeLayout,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    val frames = remember(layout) { homeLayoutPreviewFrames(layout) }
    var step by remember(layout) { mutableIntStateOf(0) }
    val progress = remember(layout) { Animatable(1f) }
    LaunchedEffect(layout, animate) {
        step = 0
        progress.snapTo(1f)
        if (!animate) return@LaunchedEffect
        while (true) {
            delay(TvLayoutPickerTokens.previewStepMillis)
            step = (step + 1) % frames.size
            progress.snapTo(0f)
            progress.animateTo(1f, tween(TvLayoutPickerTokens.previewMoveMillis, easing = FastOutSlowInEasing))
        }
    }
    val primary = MaterialTheme.colorScheme.primary
    val content = MaterialTheme.colorScheme.onBackground
    val surface = MaterialTheme.colorScheme.surface
    val avatar = TvSurfaceTokens.selectedFilter
    Canvas(modifier.clipToBounds()) {
        val scale = size.width / PreviewCanvasWidth
        val ring = 1.5f * scale
        drawRect(surface)
        val target = frames[step]
        val previous = frames[(step + frames.size - 1) % frames.size]
        val t = progress.value
        for (index in target.indices) {
            val block = previous[index].lerpTo(target[index], t)
            if (block.opacity <= 0.01f) continue
            val width = block.width * block.scale * scale
            val height = block.height * block.scale * scale
            val topLeft = Offset(
                (block.x + block.width * (1f - block.scale) / 2f) * scale,
                (block.y + block.height * (1f - block.scale) / 2f) * scale,
            )
            val blockSize = Size(width, height)
            when (block.ink) {
                PreviewInk.SCRIM -> {
                    drawRect(
                        Brush.horizontalGradient(
                            0f to surface,
                            0.24f to surface.copy(alpha = 0.9f),
                            0.68f to Color.Transparent,
                            startX = topLeft.x,
                            endX = topLeft.x + width,
                        ),
                        topLeft,
                        blockSize,
                    )
                    drawRect(
                        Brush.verticalGradient(
                            0.36f to Color.Transparent,
                            0.66f to surface,
                            startY = topLeft.y,
                            endY = topLeft.y + height,
                        ),
                        topLeft,
                        blockSize,
                    )
                }
                else -> {
                    val color = when (block.ink) {
                        PreviewInk.PRIMARY -> primary
                        PreviewInk.CONTENT -> content
                        PreviewInk.SURFACE -> surface
                        PreviewInk.AVATAR -> avatar
                        PreviewInk.SCRIM -> surface
                    }
                    drawRoundRect(
                        color = color.copy(alpha = (block.tone * block.opacity).coerceIn(0f, 1f)),
                        topLeft = topLeft,
                        size = blockSize,
                        cornerRadius = CornerRadius(block.corner * scale),
                    )
                }
            }
            if (block.focus > 0.01f) {
                drawRoundRect(
                    color = primary.copy(alpha = (block.focus * block.opacity).coerceIn(0f, 1f)),
                    topLeft = Offset(topLeft.x - ring / 2f, topLeft.y - ring / 2f),
                    size = Size(width + ring, height + ring),
                    cornerRadius = CornerRadius(block.corner * scale + ring / 2f),
                    style = Stroke(ring),
                )
            }
        }
    }
}

/* ---- Preview frames: pure geometry, unit tested ---- */

internal const val PreviewCanvasWidth = 320f
internal const val PreviewCanvasHeight = 180f

internal enum class PreviewInk { PRIMARY, CONTENT, SURFACE, AVATAR, SCRIM }

/** One shape of a preview frame, in units of a 320 × 180 canvas. */
@Immutable
internal data class PreviewBlock(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    /** Alpha of the ink. */
    val tone: Float,
    val ink: PreviewInk = PreviewInk.PRIMARY,
    /** The shape's own visibility: rows that dim or scroll out of view. */
    val opacity: Float = 1f,
    /** Strength of the focus ring. */
    val focus: Float = 0f,
    val scale: Float = 1f,
    val corner: Float = 2f,
) {
    fun lerpTo(next: PreviewBlock, fraction: Float): PreviewBlock = PreviewBlock(
        x = lerp(x, next.x, fraction),
        y = lerp(y, next.y, fraction),
        width = lerp(width, next.width, fraction),
        height = lerp(height, next.height, fraction),
        tone = lerp(tone, next.tone, fraction),
        ink = next.ink,
        opacity = lerp(opacity, next.opacity, fraction),
        focus = lerp(focus, next.focus, fraction),
        scale = lerp(scale, next.scale, fraction),
        corner = next.corner,
    )
}

/** Where focus rests in one step: the hero when [row] is negative, else a card. */
internal data class PreviewFocus(val row: Int, val column: Int = 0) {
    val inHero: Boolean get() = row < 0

    companion object {
        val Hero = PreviewFocus(-1)
    }
}

/**
 * The walk each preview loops through: along the first row, down (the next
 * row starts at its first card, or the card below in Classic), and back up
 * (a row restores its last card, TV-CNT-03).
 */
internal fun previewSteps(layout: TvHomeLayout): List<PreviewFocus> = when (layout) {
    TvHomeLayout.CLASSIC -> listOf(
        PreviewFocus.Hero,
        PreviewFocus(0, 0),
        PreviewFocus(0, 1),
        PreviewFocus(0, 2),
        PreviewFocus(1, 2),
        PreviewFocus(0, 2),
    )
    TvHomeLayout.SPOTLIGHT,
    TvHomeLayout.SHOWCASE,
    -> listOf(
        PreviewFocus(0, 0),
        PreviewFocus(0, 1),
        PreviewFocus(0, 2),
        PreviewFocus(1, 0),
        PreviewFocus(1, 1),
        PreviewFocus(0, 2),
    )
    TvHomeLayout.MARQUEE -> listOf(
        PreviewFocus.Hero,
        PreviewFocus(0, 0),
        PreviewFocus(0, 1),
        PreviewFocus(1, 0),
        PreviewFocus(0, 1),
    )
}

internal fun homeLayoutPreviewFrames(layout: TvHomeLayout): List<List<PreviewBlock>> {
    val steps = previewSteps(layout)
    return steps.indices.map { index ->
        // Each row remembers the card it last had focus on.
        val restored = IntArray(PreviewRows)
        for (k in 0..index) {
            val focus = steps[k]
            if (!focus.inHero) restored[focus.row] = focus.column
        }
        val focus = steps[index]
        buildList {
            when (layout) {
                TvHomeLayout.CLASSIC -> classicFrame(focus)
                TvHomeLayout.SPOTLIGHT -> widenedRows(
                    focus = focus,
                    restored = restored,
                    rowsTop = PreviewSpotlightTop - focus.row * PreviewSpotlightPitch,
                    pitch = PreviewSpotlightPitch,
                    details = true,
                    hideRowsAbove = false,
                )
                TvHomeLayout.SHOWCASE -> {
                    showcaseHeader(focus)
                    widenedRows(
                        focus = focus,
                        restored = restored,
                        rowsTop = PreviewShowcaseTop - focus.row * PreviewShowcasePitch,
                        pitch = PreviewShowcasePitch,
                        details = false,
                        hideRowsAbove = true,
                    )
                }
                TvHomeLayout.MARQUEE -> {
                    val scroll = if (focus.inHero) 0f else -(PreviewMarqueeScroll + focus.row * PreviewSpotlightPitch)
                    hero(top = scroll, tone = 0.2f, focused = focus.inHero, visible = if (focus.inHero) 1f else 0f)
                    widenedRows(
                        focus = focus,
                        restored = restored,
                        rowsTop = PreviewMarqueeRowsTop + scroll,
                        pitch = PreviewSpotlightPitch,
                        details = true,
                        hideRowsAbove = false,
                    )
                }
            }
            rail()
        }
    }
}

private const val PreviewRows = 3
private const val PreviewPageStart = 28f
private const val PreviewPoster = 51f
private const val PreviewWide = 137f
private const val PreviewGap = 6.67f
private const val PreviewCardHeight = 77f
private const val PreviewHeaderBlock = 10.67f
private const val PreviewSpotlightTop = 10.67f
private const val PreviewSpotlightPitch = 123.67f
private const val PreviewShowcaseTop = 72.67f
private const val PreviewShowcasePitch = 95.67f
private const val PreviewMarqueeRowsTop = 126.67f
private const val PreviewMarqueeScroll = 116f
private val PreviewTones = floatArrayOf(0.2f, 0.34f, 0.17f, 0.29f, 0.25f, 0.38f)

/** [visible] is 0 once the page has scrolled the hero above its own top edge. */
private fun MutableList<PreviewBlock>.hero(top: Float, tone: Float, focused: Boolean, visible: Float = 1f) {
    add(PreviewBlock(PreviewPageStart, 10.67f + top, 273f, 106.67f, tone, opacity = visible, focus = if (focused) 1f else 0f, corner = 4f))
    add(PreviewBlock(38f, 80f + top, 84f, 8f, 0.6f, opacity = visible))
    add(PreviewBlock(38f, 92f + top, 120f, 3f, 0.32f, opacity = visible))
    add(PreviewBlock(38f, 98f + top, 70f, 3f, 0.32f, opacity = visible))
    add(PreviewBlock(38f, 105f + top, 30f, 6f, 0.9f, PreviewInk.CONTENT, opacity = if (focused) visible else 0f))
}

private fun MutableList<PreviewBlock>.classicFrame(focus: PreviewFocus) {
    val scroll = if (focus.inHero) 0f else -(64f + focus.row * 117f)
    val heroTone = if (focus.inHero) 0.18f else PreviewTones[(focus.row * 5 + focus.column) % PreviewTones.size]
    hero(top = scroll, tone = heroTone, focused = focus.inHero)
    for (row in 0 until PreviewRows) {
        val top = 131f + row * 117f + scroll
        add(PreviewBlock(PreviewPageStart, top, 40f, 4f, 0.4f))
        for (column in 0 until 5) {
            val focused = row == focus.row && column == focus.column
            val x = PreviewPageStart + column * 58f
            val y = top + 13f
            add(
                PreviewBlock(
                    x, y, PreviewPoster, PreviewCardHeight,
                    tone = if (focused) 0.42f else 0.13f + ((row + column) % 3) * 0.03f,
                    focus = if (focused) 1f else 0f,
                    scale = if (focused) 1.03f else 1f,
                    corner = 1.5f,
                ),
            )
            add(PreviewBlock(x, y + 83f, 36f, 3f, 0.55f, opacity = if (focused) 1f else 0f))
        }
    }
}

private fun MutableList<PreviewBlock>.showcaseHeader(focus: PreviewFocus) {
    val key = (focus.row * 3 + focus.column) % PreviewTones.size
    val titleWidths = floatArrayOf(96f, 70f, 112f, 84f, 102f, 64f)
    add(PreviewBlock(0f, 0f, PreviewCanvasWidth, PreviewCanvasHeight, PreviewTones[key], corner = 0f))
    add(PreviewBlock(0f, 0f, PreviewCanvasWidth, PreviewCanvasHeight, 1f, PreviewInk.SCRIM, corner = 0f))
    add(PreviewBlock(PreviewPageStart, 16f, titleWidths[key], 10f, 0.72f))
    add(PreviewBlock(PreviewPageStart, 32f, 80f, 4f, 0.42f))
    add(PreviewBlock(PreviewPageStart, 40f, 124f, 3f, 0.28f))
    add(PreviewBlock(PreviewPageStart, 46f, 104f, 3f, 0.28f))
}

/** Spotlight-style rows: the focused row's card pins at the start and widens. */
private fun MutableList<PreviewBlock>.widenedRows(
    focus: PreviewFocus,
    restored: IntArray,
    rowsTop: Float,
    pitch: Float,
    details: Boolean,
    hideRowsAbove: Boolean,
) {
    for (row in 0 until PreviewRows) {
        val top = rowsTop + row * pitch
        val focusedRow = row == focus.row
        val opacity = when {
            focusedRow -> 1f
            hideRowsAbove && row < focus.row -> 0f
            else -> 0.5f
        }
        val pinned = restored[row]
        add(PreviewBlock(PreviewPageStart, top, 40f, 4f, 0.42f, opacity = opacity))
        for (column in 0 until 6) {
            val focused = focusedRow && column == focus.column
            val x = if (column <= pinned) {
                PreviewPageStart + (column - pinned) * (PreviewPoster + PreviewGap)
            } else {
                PreviewPageStart + (if (focusedRow) PreviewWide else PreviewPoster) + PreviewGap +
                    (column - pinned - 1) * (PreviewPoster + PreviewGap)
            }
            val y = top + PreviewHeaderBlock
            add(
                PreviewBlock(
                    x, y, if (focused) PreviewWide else PreviewPoster, PreviewCardHeight,
                    tone = if (focused) 0.44f else 0.14f + ((row + column) % 3) * 0.03f,
                    opacity = opacity,
                    focus = if (focused) 1f else 0f,
                    corner = 1.5f,
                ),
            )
            // Every widened card names its title with the logo (TV-CNT-03).
            add(PreviewBlock(x + 6f, y + 64f, 40f, 5f, 0.85f, PreviewInk.CONTENT, opacity = if (focused) 1f else 0f))
        }
        if (details) {
            val detailsTop = top + PreviewHeaderBlock + PreviewCardHeight + 5.33f
            val shown = if (focusedRow) 1f else 0f
            add(PreviewBlock(PreviewPageStart, detailsTop, 96f, 4f, 0.5f, opacity = shown))
            add(PreviewBlock(PreviewPageStart, detailsTop + 7f, 150f, 3f, 0.3f, opacity = shown))
            add(PreviewBlock(PreviewPageStart, detailsTop + 13f, 124f, 3f, 0.3f, opacity = shown))
        }
    }
}

/** The collapsed side rail, over a strip that hides cards scrolled beneath it. */
private fun MutableList<PreviewBlock>.rail() {
    add(PreviewBlock(0f, 0f, 26f, PreviewCanvasHeight, 0.92f, PreviewInk.SURFACE, corner = 0f))
    add(PreviewBlock(8.5f, 11f, 6f, 6f, 0.85f, corner = 1f))
    add(PreviewBlock(8.5f, 28f, 6f, 6f, 1f, PreviewInk.AVATAR))
    for (tab in 0 until 6) {
        add(PreviewBlock(9.5f, 45f + tab * 16f, 4f, 4f, if (tab == 1) 0.85f else 0.28f, PreviewInk.CONTENT))
    }
    add(PreviewBlock(9.5f, 158f, 4f, 4f, 0.28f, PreviewInk.CONTENT))
}
