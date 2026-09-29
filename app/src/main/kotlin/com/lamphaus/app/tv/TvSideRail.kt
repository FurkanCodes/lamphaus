package com.lamphaus.app.tv

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.onFocusedBoundsChanged
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.lamphaus.app.R
import com.lamphaus.app.ui.LamphausWordmark
import com.lamphaus.app.ui.rememberReducedMotion
import com.lamphaus.core.model.Profile
import kotlin.math.roundToInt

/**
 * TV-NAV-01 side rail: the logo, the profile avatar, the centred tabs and
 * Settings in a slim column on the start safe margin. It widens over the page
 * while it has focus and folds back to icons when focus returns to the page.
 * Moving through it only moves focus; Select switches the page. Right, or
 * Select on the page already shown, hands focus back via [onReturnToContent].
 */
@Composable
internal fun TvSideRail(
    selectedDestination: TvDestination,
    activeProfile: Profile?,
    focusDestination: TvDestination?,
    requesters: Map<TvDestination, FocusRequester>,
    profileRequester: FocusRequester,
    onFocusHandled: () -> Unit,
    onHasFocus: (Boolean) -> Unit,
    onDestination: (TvDestination) -> Unit,
    onReturnToContent: () -> Unit,
    onProfileSwitcher: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(focusDestination) {
        focusDestination?.let {
            requesters.getValue(it).requestFocus()
            onFocusHandled()
        }
    }
    var expanded by remember { mutableStateOf(false) }
    val reducedMotion = rememberReducedMotion()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(expanded, reducedMotion) {
        val target = if (expanded) 1f else 0f
        when {
            reducedMotion -> progress.snapTo(target)
            // Opening settles on a soft spring; closing gets out of the way (TV-MOT-01).
            expanded -> progress.animateTo(target, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
            else -> progress.animateTo(
                target,
                tween(TvMotionTokens.railCloseDurationMillis, easing = FastOutLinearInEasing),
            )
        }
    }
    val openness = { progress.value }
    val background = MaterialTheme.colorScheme.background

    Box(modifier.fillMaxHeight()) {
        // Keeps the icons legible over cards scrolled beneath them.
        Box(
            Modifier
                .fillMaxHeight()
                .width(TvRailTokens.collapsedScrimWidth)
                .background(
                    Brush.horizontalGradient(
                        0f to background.copy(alpha = 0.94f),
                        0.78f to background.copy(alpha = 0.82f),
                        1f to Color.Transparent,
                    ),
                ),
        )
        // Dims the page while the rail is open; drawn only through alpha so the page never re-lays out.
        Box(
            Modifier
                .fillMaxHeight()
                .width(TvRailTokens.expandedScrimWidth)
                .graphicsLayer { alpha = openness() }
                .background(
                    Brush.horizontalGradient(
                        0f to background.copy(alpha = 0.97f),
                        0.45f to background.copy(alpha = 0.86f),
                        1f to Color.Transparent,
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .padding(
                    start = TvRailTokens.start,
                    top = TvLayoutTokens.screenTopPadding,
                    bottom = TvLayoutTokens.screenBottomPadding,
                )
                .fillMaxHeight()
                // Width follows the animation in the layout phase only: no recomposition per frame.
                .layout { measurable, constraints ->
                    val width = lerp(TvRailTokens.itemSize, TvRailTokens.expandedWidth, openness()).roundToPx()
                    val placeable = measurable.measure(
                        Constraints(minWidth = width, maxWidth = width, minHeight = constraints.minHeight, maxHeight = constraints.maxHeight),
                    )
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }
                .onFocusChanged {
                    expanded = it.hasFocus
                    onHasFocus(it.hasFocus)
                }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                        onReturnToContent()
                        true
                    } else {
                        false
                    }
                }
                // Up and Down stop at the ends; Left has nowhere to go (TV-NAV-05).
                .focusProperties {
                    onExit = {
                        when (requestedFocusDirection) {
                            FocusDirection.Up, FocusDirection.Down, FocusDirection.Left, FocusDirection.Right ->
                                cancelFocusChange()
                            else -> Unit
                        }
                    }
                }
                .focusGroup(),
        ) {
            TvRailLogo(openness)
            Spacer(Modifier.height(20.dp))
            TvRailProfileItem(
                profile = activeProfile,
                openness = openness,
                modifier = Modifier.focusRequester(profileRequester),
                onClick = onProfileSwitcher,
            )
            Spacer(Modifier.weight(1f))
            TvRailTabs(
                selectedDestination = selectedDestination,
                requesters = requesters,
                openness = openness,
                reducedMotion = reducedMotion,
                onSelect = { if (it == selectedDestination) onReturnToContent() else onDestination(it) },
            )
            Spacer(Modifier.weight(1f))
            TvRailItem(
                destination = TvDestination.SETTINGS,
                selected = selectedDestination == TvDestination.SETTINGS,
                showBeam = true,
                openness = openness,
                labelOrder = TvDestination.centerTabs.size,
                modifier = Modifier.focusRequester(requesters.getValue(TvDestination.SETTINGS)),
                onClick = {
                    if (selectedDestination == TvDestination.SETTINGS) {
                        onReturnToContent()
                    } else {
                        onDestination(TvDestination.SETTINGS)
                    }
                },
            )
        }
    }
}

@Composable
private fun TvRailLogo(openness: () -> Float) {
    Row(
        modifier = Modifier.height(TvRailTokens.itemSize),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(TvRailTokens.itemSize), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(R.drawable.ic_lamphaus_foreground),
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { alpha = 0.72f + 0.28f * openness() },
            )
        }
        LamphausWordmark(
            fontSize = MaterialTheme.typography.titleSmall.fontSize,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .railLabelReveal(openness, order = 0),
        )
    }
}

/** Select opens the profile switcher; focusing it never changes the page. */
@Composable
private fun TvRailProfileItem(
    profile: Profile?,
    openness: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val description = stringResource(R.string.switch_profile_current, profile?.name.orEmpty())
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TvRailTokens.itemSize)
            .onFocusChanged { focused = it.isFocused }
            .clip(TvShapeTokens.button)
            .background(if (focused) TvFocusTokens.selectedNavigationContainer else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .focusable()
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(TvRailTokens.itemSize), contentAlignment = Alignment.Center) {
            TvProfileAvatar(
                avatarKey = profile?.avatarKey.orEmpty(),
                focused = focused,
                modifier = Modifier.size(28.dp),
            )
        }
        TvRailLabel(
            text = profile?.name.orEmpty(),
            focused = focused,
            selected = false,
            openness = openness,
            order = 1,
        )
    }
}

/** The centred tabs, with the selected beam sliding to the page Select switched to. */
@Composable
private fun TvRailTabs(
    selectedDestination: TvDestination,
    requesters: Map<TvDestination, FocusRequester>,
    openness: () -> Float,
    reducedMotion: Boolean,
    onSelect: (TvDestination) -> Unit,
) {
    val tabs = TvDestination.centerTabs
    val selectedIndex = tabs.indexOf(selectedDestination)
    val beamOffset by animateDpAsState(
        targetValue = (TvRailTokens.itemSize + TvRailTokens.itemSpacing) * selectedIndex.coerceAtLeast(0) +
            (TvRailTokens.itemSize - TvRailTokens.beamHeight) / 2,
        animationSpec = if (reducedMotion) {
            tween(0)
        } else {
            spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium)
        },
        label = "railBeam",
    )
    Box {
        Column(verticalArrangement = Arrangement.spacedBy(TvRailTokens.itemSpacing)) {
            tabs.forEachIndexed { index, destination ->
                TvRailItem(
                    destination = destination,
                    selected = destination == selectedDestination,
                    showBeam = false,
                    openness = openness,
                    labelOrder = index + 2,
                    modifier = Modifier.focusRequester(requesters.getValue(destination)),
                    onClick = { onSelect(destination) },
                )
            }
        }
        if (selectedIndex >= 0) {
            TvRailBeam(Modifier.offset { IntOffset(0, beamOffset.roundToPx()) })
        }
    }
}

@Composable
private fun TvRailItem(
    destination: TvDestination,
    selected: Boolean,
    showBeam: Boolean,
    openness: () -> Float,
    labelOrder: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val label = stringResource(destination.labelRes)
    val selectedContainer = MaterialTheme.colorScheme.secondaryContainer
    Box {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(TvRailTokens.itemSize)
                .onFocusChanged { focused = it.isFocused }
                .clip(TvShapeTokens.button)
                .background(if (focused) TvFocusTokens.selectedNavigationContainer else Color.Transparent)
                // The selected page keeps a quiet container while the rail is open.
                .then(
                    if (selected && !focused) {
                        Modifier.drawBehind { drawRect(selectedContainer.copy(alpha = 0.40f * openness())) }
                    } else {
                        Modifier
                    },
                )
                .clickable(role = Role.Tab, onClick = onClick)
                .focusable()
                .semantics {
                    this.selected = selected
                    contentDescription = label
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(TvRailTokens.itemSize), contentAlignment = Alignment.Center) {
                TvIcon(
                    icon = destination.icon,
                    contentDescription = null,
                    tint = when {
                        focused -> TvFocusTokens.focusedContent
                        selected -> MaterialTheme.colorScheme.onBackground
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(TvRailTokens.iconSize),
                )
            }
            TvRailLabel(text = label, focused = focused, selected = selected, openness = openness, order = labelOrder)
        }
        if (selected && showBeam) {
            TvRailBeam(Modifier.align(Alignment.CenterStart))
        }
    }
}

@Composable
private fun TvRailLabel(
    text: String,
    focused: Boolean,
    selected: Boolean,
    openness: () -> Float,
    order: Int,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = when {
            focused -> TvFocusTokens.focusedContent
            selected -> MaterialTheme.colorScheme.onBackground
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = Modifier
            .padding(start = 4.dp, end = 12.dp)
            .wrapContentWidth(Alignment.Start, unbounded = true)
            .railLabelReveal(openness, order),
    )
}

/** Labels fade and slide in one after another as the rail opens, top to bottom. */
private fun Modifier.railLabelReveal(openness: () -> Float, order: Int): Modifier = graphicsLayer {
    val start = order * TvRailTokens.labelStagger
    val local = ((openness() - start) / (1f - start)).coerceIn(0f, 1f)
    alpha = local
    translationX = (1f - local) * -12.dp.toPx()
}

@Composable
private fun TvRailBeam(modifier: Modifier = Modifier) {
    Box(
        modifier
            .offset(x = -(TvRailTokens.beamWidth + 6.dp))
            .width(TvRailTokens.beamWidth)
            .height(TvRailTokens.beamHeight)
            .background(TvFocusTokens.beam, CircleShape),
    )
}

/**
 * Left that cannot move within the page (the start of a row, a grid's first
 * column, the Settings menu) calls [onOpen], so the rail is one press away at
 * any depth. Apply to the page container: it runs after the focused item's
 * own key handlers, so text fields keep Left for their cursor.
 */
internal fun Modifier.tvRailOpensOnLeft(focusManager: FocusManager, onOpen: () -> Unit): Modifier =
    onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown || event.key != Key.DirectionLeft) return@onKeyEvent false
        if (!focusManager.moveFocus(FocusDirection.Left)) onOpen()
        true
    }

/**
 * Remembers where focus sat on the page when the rail opened, so closing the
 * rail puts it back on that item (TV-NAV-02), however deep the page was
 * scrolled. The page reports its focused bounds. To return, a one-pixel
 * anchor appears just outside the page's start edge, level with the saved
 * item, takes focus and steps Right: spatial search then enters the saved row
 * or grid line (rows land on their own last card), and a grid steps on until
 * it reaches the saved column.
 */
@Stable
internal class TvContentFocusReturn {
    internal val anchor = FocusRequester()
    internal var anchorY by mutableStateOf<Int?>(null)
    private var container: LayoutCoordinates? = null
    private var focused: LayoutCoordinates? = null
    private var saved: Rect? = null

    /** Call while the page still has focus, just before focus leaves for the rail. */
    fun save() {
        saved = focusedBounds()
    }

    /** Forgets the saved position, for a page switch. */
    fun forget() {
        saved = null
    }

    /** Starts the walk back to the saved item; false when there is none. */
    fun restore(): Boolean {
        val target = saved ?: return false
        anchorY = target.center.y.roundToInt()
        return true
    }

    /** Steps Right through a grid line until focus reaches the saved column. */
    internal fun alignWithSaved(step: () -> Boolean) {
        val target = saved ?: return
        repeat(MAX_ALIGN_STEPS) {
            val current = focusedBounds() ?: return
            if (current.left >= target.left - ALIGN_TOLERANCE_PX || !step()) return
        }
    }

    private fun focusedBounds(): Rect? {
        val page = container?.takeIf { it.isAttached } ?: return null
        val item = focused?.takeIf { it.isAttached } ?: return null
        return page.localBoundingBoxOf(item, clipBounds = false)
    }

    internal fun onPagePlaced(coordinates: LayoutCoordinates) {
        container = coordinates
    }

    internal fun onFocusedBounds(coordinates: LayoutCoordinates?) {
        if (coordinates != null) focused = coordinates
    }

    private companion object {
        const val MAX_ALIGN_STEPS = 24
        const val ALIGN_TOLERANCE_PX = 2f
    }
}

@Composable
internal fun rememberTvContentFocusReturn(): TvContentFocusReturn = remember { TvContentFocusReturn() }

/** Tracks the page's focused item for [focusReturn]; apply to the page container. */
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.tvContentFocusMemory(focusReturn: TvContentFocusReturn): Modifier =
    onPlaced(focusReturn::onPagePlaced)
        .onFocusedBoundsChanged(focusReturn::onFocusedBounds)

/**
 * The anchor [TvContentFocusReturn.restore] walks back through; place it last
 * inside the page container. [onFailed] runs when nothing lies to its right.
 */
@Composable
internal fun TvContentFocusAnchor(focusReturn: TvContentFocusReturn, onFailed: () -> Unit) {
    val y = focusReturn.anchorY ?: return
    val focusManager = LocalFocusManager.current
    // Outside the page's start edge: spatial search only enters containers
    // that begin to the right of the focused item.
    Box(
        Modifier
            .offset { IntOffset(-(1.dp.roundToPx() + 1), y) }
            .size(1.dp)
            .focusRequester(focusReturn.anchor)
            .focusTarget(),
    )
    LaunchedEffect(y) {
        withFrameNanos { }
        val anchored = runCatching { focusReturn.anchor.requestFocus() }.getOrDefault(false)
        val returned = anchored && focusManager.moveFocus(FocusDirection.Right)
        if (returned) focusReturn.alignWithSaved { focusManager.moveFocus(FocusDirection.Right) }
        focusReturn.anchorY = null
        if (!returned) onFailed()
    }
}
