package com.lamphaus.app.tv

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.core.text.HtmlCompat
import com.lamphaus.app.R
import com.lamphaus.app.ui.MediaArtwork
import com.lamphaus.app.ui.metadataPresentation
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.Profile

internal enum class TvDestination(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val showLabel: Boolean = true,
) {
    HOME(R.string.home, Icons.Filled.Home),
    DISCOVER(R.string.discover, Icons.Filled.Explore),
    LIBRARY(R.string.library, Icons.Filled.VideoLibrary),
    SEARCH(R.string.search, Icons.Filled.Search, showLabel = false),
    SETTINGS(R.string.settings, Icons.Filled.Settings),
}

@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.tvContentFocusBoundary(
    topNavigationRequester: FocusRequester,
    leftNavigationRequester: FocusRequester = FocusRequester.Default,
): Modifier =
    focusProperties {
        exit = { direction ->
            when (direction) {
                FocusDirection.Up -> topNavigationRequester
                FocusDirection.Left -> leftNavigationRequester
                else -> FocusRequester.Default
            }
        }
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TvEditableTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onImeAction: () -> Unit = {},
    onNavigateDown: () -> Boolean = { false },
) {
    var editing by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var imeWasVisible by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val browseDescription = stringResource(R.string.press_select_to_edit)
    val editingDescription = stringResource(R.string.editing)
    val finishEditing: () -> Unit = {
        editing = false
        keyboardController?.hide()
    }
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(editing) {
        if (editing) {
            withFrameNanos { }
            keyboardController?.show()
        }
    }
    LaunchedEffect(imeVisible) {
        if (imeVisible) {
            imeWasVisible = true
        } else if (imeWasVisible && editing) {
            finishEditing()
        }
    }
    BackHandler(enabled = editing, onBack = finishEditing)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        readOnly = !editing,
        singleLine = true,
        textStyle = MaterialTheme.typography.titleSmall.copy(color = MaterialTheme.colorScheme.onSurface),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = KeyboardActions(onAny = {
            finishEditing()
            onImeAction()
        }),
        modifier = modifier
            .onFocusChanged {
                focused = it.isFocused
                if (!it.isFocused && editing) finishEditing()
            }
            .onPreviewKeyEvent { event ->
                if (!editing) {
                    when {
                        event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown ->
                            onNavigateDown()

                        event.key == Key.DirectionCenter || event.key == Key.Enter -> {
                            if (event.type == KeyEventType.KeyUp) {
                                editing = true
                            }
                            true
                        }

                        else -> false
                    }
                } else if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                    finishEditing()
                    true
                } else {
                    false
                }
            }
            .background(MaterialTheme.colorScheme.background, TvShapeTokens.card)
            .border(
                width = if (focused) TvFocusTokens.outlineWidth else 1.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.border,
                shape = TvShapeTokens.card,
            )
            .semantics {
                contentDescription = label
                stateDescription = if (editing) editingDescription else browseDescription
            }
            .padding(contentPadding),
        decorationBox = { input ->
            if (value.isBlank()) {
                Text(
                    placeholder,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            input()
        },
    )
}

@Composable
internal fun TvTopNavigation(
    selectedDestination: TvDestination,
    activeProfile: Profile?,
    focusDestination: TvDestination?,
    requesters: Map<TvDestination, FocusRequester>,
    contentDownRequester: FocusRequester,
    onFocusHandled: () -> Unit,
    onHasFocus: (Boolean) -> Unit,
    onDestination: (TvDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(focusDestination) {
        focusDestination?.let {
            requesters.getValue(it).requestFocus()
            onFocusHandled()
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TvLayoutTokens.topBarHeight)
            .onFocusChanged { onHasFocus(it.hasFocus) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvProfileNavigationItem(
            selected = selectedDestination == TvDestination.SETTINGS,
            profile = activeProfile,
            modifier = Modifier
                .focusRequester(requesters.getValue(TvDestination.SETTINGS))
                .focusProperties { down = contentDownRequester },
            onFocused = { onDestination(TvDestination.SETTINGS) },
            onClick = { onDestination(TvDestination.SETTINGS) },
        )
        Spacer(Modifier.width(20.dp))
        TvDestination.entries.filterNot { it == TvDestination.SETTINGS }.forEach { destination ->
            TvTopNavigationItem(
                destination = destination,
                selected = selectedDestination == destination,
                modifier = Modifier
                    .focusRequester(requesters.getValue(destination))
                    .focusProperties { down = contentDownRequester },
                onFocused = { onDestination(destination) },
                onClick = { onDestination(destination) },
            )
        }
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier.alpha(0.72f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_lamphaus_foreground),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = stringResource(R.string.app_name).uppercase(),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.6.sp,
                ),
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun TvProfileNavigationItem(
    selected: Boolean,
    profile: Profile?,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val description = stringResource(R.string.settings_and_profiles)
    Box(
        modifier = modifier
            .size(32.dp)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(role = Role.Button, onClick = onClick)
            .focusable()
            .semantics {
                this.selected = selected
                contentDescription = description
            },
        contentAlignment = Alignment.Center,
    ) {
        TvProfileAvatar(
            name = profile?.name.orEmpty(),
            avatarKey = profile?.avatarKey.orEmpty(),
            focused = focused,
            selected = selected,
            modifier = Modifier.fillMaxWidth().height(32.dp),
        )
    }
}

@Composable
private fun TvTopNavigationItem(
    destination: TvDestination,
    selected: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val label = stringResource(destination.labelRes)
    Box(
        modifier = Modifier.height(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = modifier
                .height(32.dp)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                }
                .background(
                    color = when {
                        focused -> TvFocusTokens.selectedNavigationContainer
                        selected -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.40f)
                        else -> Color.Transparent
                    },
                    shape = TvShapeTokens.button,
                )
                .clip(TvShapeTokens.button)
                .clickable(role = Role.Tab, onClick = onClick)
                .focusable()
                .semantics {
                    this.selected = selected
                    contentDescription = label
                }
                .padding(horizontal = if (destination.showLabel) 16.dp else 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (destination.showLabel) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = when {
                        focused -> TvFocusTokens.focusedContent
                        selected -> MaterialTheme.colorScheme.onBackground
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                )
            } else {
                TvIcon(
                    icon = destination.icon,
                    contentDescription = null,
                    tint = if (focused) TvFocusTokens.focusedContent else MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .width(24.dp)
                    .height(2.dp)
                    .background(TvFocusTokens.beam, CircleShape),
            )
        }
    }
}

@Composable
internal fun TvMediaCard(
    media: MediaPreview,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    showLabel: Boolean = true,
    revealLabelOnFocus: Boolean = false,
    compactLandscape: Boolean = false,
    watchProgress: Float? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val reducedMotion = rememberReducedMotion()
    val ambientAccent = LocalTvContentAccent.current ?: MaterialTheme.colorScheme.primary
    val ambientHalo = ambientAccent.copy(alpha = TvFocusTokens.halo.alpha)
    val ratingText = media.metadataPresentation().ratingText
    val cardDescription = ratingText?.let {
        stringResource(R.string.media_card_description_rating, media.name, it)
    } ?: media.name
    val labelAlpha by animateFloatAsState(
        targetValue = if (!revealLabelOnFocus || focused) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else tween(TvMotionTokens.focusDurationMillis),
        label = "card label",
    )
    val focusProgress by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else tween(TvMotionTokens.focusDurationMillis),
        label = "card focus",
    )
    val hasLocalPoster = media.id == "fixture:aurora" || media.id == "fixture:glass"
    val declaredLandscape = media.posterShape.equals("landscape", ignoreCase = true)
    val portrait = !declaredLandscape && (hasLocalPoster || !media.posterUrl.isNullOrBlank() || media.backgroundUrl.isNullOrBlank())
    val cardWidth = if (portrait || compactLandscape) {
        TvLayoutTokens.posterWidth
    } else {
        TvLayoutTokens.landscapeCardWidth
    }
    val cardHeight = when {
        portrait -> TvLayoutTokens.posterHeight
        compactLandscape -> 86.dp
        else -> TvLayoutTokens.landscapeCardHeight
    }
    Column(
        modifier = modifier
            .width(cardWidth)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(role = Role.Button, onClick = onClick)
            .focusable()
            .semantics { contentDescription = cardDescription },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(cardWidth)
                .height(cardHeight)
                .graphicsLayer {
                    val scale = 1f + ((TvMotionTokens.focusedArtworkScale - 1f) * focusProgress)
                    scaleX = scale
                    scaleY = scale
                    shadowElevation = 7.dp.toPx() * focusProgress
                    shape = TvShapeTokens.card
                    ambientShadowColor = ambientHalo
                    spotShadowColor = ambientHalo
                }
                .border(
                    width = TvFocusTokens.outlineWidth,
                    color = if (focused) ambientAccent else TvSurfaceTokens.subtleBorder,
                    shape = TvShapeTokens.card,
                )
                .padding(if (focused) TvFocusTokens.outlineWidth else 0.5.dp)
                .clip(TvShapeTokens.card),
        ) {
            MediaArtwork(
                media = media,
                modifier = Modifier.fillMaxWidth().height(cardHeight),
                contentScale = ContentScale.Crop,
            )
            ratingText?.let {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp)
                        .clip(TvShapeTokens.card)
                        .background(TvSurfaceTokens.ratingScrim)
                        .border(
                            width = 0.5.dp,
                            color = TvSurfaceTokens.ratingBorder,
                            shape = TvShapeTokens.card,
                        )
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "★",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = it,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    )
                }
            }
            watchProgress?.coerceIn(0f, 1f)?.takeIf { it > 0f }?.let { progress ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.Black.copy(alpha = 0.44f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(4.dp)
                            .background(TvFocusTokens.beam),
                    ) {
                        Box(
                            Modifier
                                .align(Alignment.CenterEnd)
                                .width(2.dp)
                                .height(4.dp)
                                .background(Color.White),
                        )
                    }
                }
            }
        }
        if (showLabel) {
            Text(
                text = media.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .padding(top = 4.dp)
                    .alpha(labelAlpha)
                    .graphicsLayer {
                        translationY = (1f - labelAlpha) * 6.dp.toPx()
                    },
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
internal fun TvProfileAvatar(
    name: String,
    avatarKey: String,
    focused: Boolean,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val container = when (Math.floorMod(avatarKey.hashCode(), 3)) {
        0 -> MaterialTheme.colorScheme.primaryContainer
        1 -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }
    Box(
        modifier = modifier
            .background(
                color = when {
                    focused -> TvFocusTokens.focusedContainer
                    selected -> container.copy(alpha = 0.72f)
                    else -> container
                },
                shape = TvShapeTokens.profile,
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) TvFocusTokens.beam else TvSurfaceTokens.subtleBorder,
                shape = TvShapeTokens.profile,
            )
            .clip(TvShapeTokens.profile),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().firstOrNull()?.uppercase() ?: "L",
            color = if (focused) TvFocusTokens.focusedContent else MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
internal fun TvEmptyMark(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(52.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), TvShapeTokens.profile)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f), TvShapeTokens.profile),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_lamphaus_monochrome),
            contentDescription = null,
            modifier = Modifier.size(30.dp),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
internal fun TvAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TvFocusableSurface(onClick = onClick, enabled = enabled, modifier = modifier) { focused ->
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvIcon(
                icon = icon,
                contentDescription = null,
                tint = if (focused) TvFocusTokens.focusedContent else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                color = if (focused) TvFocusTokens.focusedContent else MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

@Composable
internal fun TvFocusableSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = TvFocusTokens.defaultContainer,
    focusedContainerColor: Color = TvFocusTokens.focusedContainer,
    role: Role = Role.Button,
    content: @Composable (focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .background(
                color = when {
                    !enabled -> TvFocusTokens.disabledContainer
                    focused -> focusedContainerColor
                    else -> containerColor
                },
                shape = TvShapeTokens.button,
            )
            .border(
                width = if (focused) 1.dp else 0.dp,
                color = if (focused) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                shape = TvShapeTokens.button,
            )
            .clip(TvShapeTokens.button)
            .clickable(enabled = enabled, role = role, onClick = onClick)
            .focusable(enabled)
            .semantics { this.role = role },
    ) {
        content(focused)
    }
}

/**
 * Reading surface that expands to its full content while focused and collapses
 * back to [collapsedLines] when focus moves on. It is only focusable when the
 * collapsed text actually truncates, so short descriptions never introduce a
 * dead focus stop between interactive elements.
 *
 * Focus restore rule: reading surfaces are transient stops. When this surface
 * takes focus, [returnFocusProvider] supplies the button that had focus
 * immediately before; pressing down on the surface returns to that button.
 */
@Composable
internal fun TvExpandableText(
    text: String,
    collapsedLines: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    returnFocusProvider: () -> FocusRequester? = { null },
) {
    val reducedMotion = rememberReducedMotion()
    var focused by remember { mutableStateOf(false) }
    var overflows by remember { mutableStateOf(false) }
    var returnTarget by remember { mutableStateOf<FocusRequester?>(null) }
    val expansionSpec: FiniteAnimationSpec<IntSize> =
        if (reducedMotion) snap() else tween(TvMotionTokens.heroTransitionDurationMillis)

    Text(
        text = text,
        style = style,
        color = color,
        maxLines = if (focused) Int.MAX_VALUE else collapsedLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layoutResult ->
            // Only observe the collapsed layout; the expanded layout never
            // overflows and would clear the flag while the surface is focused.
            if (!focused) overflows = layoutResult.hasVisualOverflow
        },
        modifier = modifier
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                    val target = returnTarget
                    if (target != null) {
                        target.requestFocus()
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) returnTarget = returnFocusProvider()
            }
            .animateContentSize(animationSpec = expansionSpec)
            .focusable(enabled = overflows || focused),
    )
}

/**
 * Cast / directors section that mirrors [TvExpandableText]: collapsed it keeps
 * the compact name line, focused it grows into a wrapped chip list that shows
 * every name instead of an ellipsized summary.
 *
 * Shares the same focus restore rule — see [TvExpandableText].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TvExpandablePeopleSection(
    @StringRes labelRes: Int,
    people: List<String>,
    modifier: Modifier = Modifier,
    returnFocusProvider: () -> FocusRequester? = { null },
) {
    if (people.isEmpty()) return
    val names = remember(people) {
        people.map { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim() }
    }
    val reducedMotion = rememberReducedMotion()
    var focused by remember { mutableStateOf(false) }
    var overflows by remember { mutableStateOf(false) }
    var returnTarget by remember { mutableStateOf<FocusRequester?>(null) }
    // The collapsed summary shows at most six names in two lines; anything
    // beyond that must stay reachable through focus expansion even when the
    // summary itself happens to fit.
    val collapsedNameCount = 6
    val truncates = overflows || names.size > collapsedNameCount
    val expansionSpec: FiniteAnimationSpec<IntSize> =
        if (reducedMotion) snap() else tween(TvMotionTokens.heroTransitionDurationMillis)

    Column(
        modifier = modifier
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                    val target = returnTarget
                    if (target != null) {
                        target.requestFocus()
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) returnTarget = returnFocusProvider()
            }
            .animateContentSize(animationSpec = expansionSpec)
            .focusable(enabled = truncates || focused)
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(labelRes),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
        if (focused && truncates) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                names.forEach { name ->
                    Text(
                        text = name,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .background(TvSurfaceTokens.elevated, TvShapeTokens.button)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        } else {
            Text(
                text = names.take(6).joinToString("  •  "),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { layoutResult ->
                    if (!focused) overflows = layoutResult.hasVisualOverflow
                },
            )
        }
    }
}

@Composable
internal fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
}

@Composable
internal fun TvIcon(
    icon: ImageVector,
    contentDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = rememberVectorPainter(icon),
        contentDescription = contentDescription,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(20.dp),
    )
}
