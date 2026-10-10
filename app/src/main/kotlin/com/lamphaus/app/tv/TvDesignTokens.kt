package com.lamphaus.app.tv

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Lamphaus TV component tokens, measured against the 960 x 540 reference canvas.
 * Components consume these semantic values instead of scattering visual constants.
 */
internal object TvLayoutTokens {
    val screenHorizontalPadding = 58.dp
    val screenTopPadding = 32.dp
    val screenBottomPadding = 28.dp
    val topBarHeight = 32.dp
    val contentTopPadding = 104.dp
    val rowSpacing = 40.dp
    val itemSpacing = 20.dp
    val sectionTitleSpacing = 16.dp
    val bottomListPadding = 108.dp
    val posterWidth = 153.dp
    val posterHeight = 231.dp
    /** Spotlight layout: the focused poster widens to 16:9 at poster height (TV-CNT-03). */
    val spotlightExpandedWidth = 411.dp
    val landscapeCardWidth = 256.dp
    val landscapeCardHeight = 144.dp
    val heroHeight = 320.dp
    val settingsMenuWidth = 268.dp
    val settingsContentWidth = 452.dp
}

/**
 * Showcase Home (TV-CNT-05), measured from the top of the screen so the
 * focused row sits in the same place under either navigation placement.
 */
internal object TvShowcaseTokens {
    val detailsTopWithRail = 44.dp
    val detailsTopWithTopBar = 80.dp
    val rowsTopWithRail = 218.dp
    val rowsTopWithTopBar = 236.dp
    val detailsWidth = 460.dp
    val logoWidth = 300.dp
    val logoHeight = 64.dp
    val rowSpacing = 24.dp
}

/** The Home layout picker (TV-CNT-07): a list beside a live 16:9 preview. */
internal object TvLayoutPickerTokens {
    val dialogWidth = 768.dp
    val listWidth = 248.dp
    val optionMinHeight = 64.dp
    val previewWidth = 424.dp
    val previewHeight = 239.dp
    val paneSpacing = 32.dp
    /** Each preview step: focus rests, then moves to its next target. */
    const val previewStepMillis = 1_100L
    const val previewMoveMillis = 280
}

/**
 * Side-rail navigation (TV-NAV-01, TV-LAY-01). The rail sits [start] from the
 * screen edge, inside the usual safe margin, so pages give up only
 * [contentStartOffset] of width.
 */
internal object TvRailTokens {
    val start = 24.dp
    val itemSize = 40.dp
    val itemSpacing = 8.dp
    val iconSize = 20.dp
    val expandedWidth = 208.dp
    /**
     * Added to a page's own `58dp` start padding so it begins one `20dp` rail
     * gap after the collapsed rail: 24 + 40 + 20 = 84dp from the edge.
     */
    val contentStartOffset = 26.dp
    /** Pages start at the screen's top safe margin; no navigation sits above them. */
    val contentTopPadding = 32.dp
    val beamWidth = 2.dp
    val beamHeight = 24.dp
    /** Keeps the collapsed icons legible over cards scrolled under them. */
    val collapsedScrimWidth = 84.dp
    val expandedScrimWidth = 520.dp
    /** Share of the opening each successive label waits before it fades in. */
    const val labelStagger = 0.05f
}

internal object TvShapeTokens {
    val card = RoundedCornerShape(4.dp)
    val button = RoundedCornerShape(4.dp)
    val hero = RoundedCornerShape(12.dp)
    val profile = RoundedCornerShape(
        topStart = 8.dp,
        topEnd = 2.dp,
        bottomEnd = 8.dp,
        bottomStart = 2.dp,
    )
}

internal object TvFocusTokens {
    val outlineWidth = 3.dp
    val beam = Color(0xFFA8C8FF)
    val focusedCardOutline = beam
    val halo = beam.copy(alpha = 0.28f)
    val focusedContainer = Color(0xFFE3E2E6)
    val focusedContent = Color(0xFF2F3033)
    val selectedNavigationContainer = Color(0xFFC7C6CA)
    val defaultContainer: Color
        @Composable @ReadOnlyComposable get() = LocalTvSurfaces.current.actionContainer
    val disabledContainer: Color
        @Composable @ReadOnlyComposable get() = LocalTvSurfaces.current.disabledContainer
}

internal object TvSurfaceTokens {
    val elevated: Color
        @Composable @ReadOnlyComposable get() = LocalTvSurfaces.current.elevated
    val card: Color
        @Composable @ReadOnlyComposable get() = LocalTvSurfaces.current.card
    val selectedFilter = Color(0xFF354964)
    val subtleBorder = Color.White.copy(alpha = 0.10f)
}

/**
 * Panel and action colours that follow Settings → Appearance → Black
 * background (TV-CLR-01). With black, panels match the page and resting
 * actions carry a hairline outline instead of a grey fill, so rows and
 * buttons stay recognisable before they take focus.
 */
@Immutable
internal data class TvSurfaces(
    val elevated: Color,
    val card: Color,
    val actionContainer: Color,
    val disabledContainer: Color,
    val actionOutline: Color,
) {
    companion object {
        val Dark = TvSurfaces(
            elevated = Color(0xFF1E2023),
            card = Color(0xFF292A2D),
            actionContainer = Color.White.copy(alpha = 0.10f),
            disabledContainer = Color.White.copy(alpha = 0.04f),
            actionOutline = Color.Transparent,
        )
        val Black = TvSurfaces(
            elevated = Color.Black,
            card = Color.Black,
            actionContainer = Color.Black,
            disabledContainer = Color.Black,
            actionOutline = Color.White.copy(alpha = 0.16f),
        )
    }
}

internal val LocalTvSurfaces = staticCompositionLocalOf { TvSurfaces.Dark }

internal object TvMotionTokens {
    const val focusDurationMillis = 160
    const val heroTransitionDurationMillis = 220
    const val heroUpdateDelayMillis = 240L
    const val confirmationPulseDurationMillis = 110
    /**
     * QA-08: the Settings content pane follows menu focus only after it rests
     * this long, so passing through sections does not build every pane.
     */
    const val settingsPaneSettleMillis = 160L
    const val focusedArtworkScale = 1.02f
    /** The side rail opens on a soft spring and closes on a quicker ease (TV-MOT-01). */
    const val railCloseDurationMillis = 180
    /** The ambient screensaver's slow slide crossfade (TV-AMB-01). */
    const val ambientSlideCrossfadeMillis = 1_200
}

internal object TvAmbientTokens {
    val imageAlpha = 0.30f
    val horizontalScrimLeftAlpha = 0.97f
    val horizontalScrimMiddleAlpha = 0.72f
    val horizontalScrimRightAlpha = 0.42f
    val verticalScrimTopAlpha = 0.35f
    val verticalScrimMiddleAlpha = 0f
    val verticalScrimBottomAlpha = 0.94f
}
