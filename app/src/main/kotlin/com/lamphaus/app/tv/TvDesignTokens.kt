package com.lamphaus.app.tv

import androidx.compose.foundation.shape.RoundedCornerShape
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
    val landscapeCardWidth = 256.dp
    val landscapeCardHeight = 144.dp
    val heroHeight = 320.dp
    val settingsMenuWidth = 268.dp
    val settingsContentWidth = 452.dp
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
    val defaultContainer = Color.White.copy(alpha = 0.10f)
    val disabledContainer = Color.White.copy(alpha = 0.04f)
}

internal object TvSurfaceTokens {
    val elevated = Color(0xFF1E2023)
    val card = Color(0xFF292A2D)
    val selectedFilter = Color(0xFF354964)
    val subtleBorder = Color.White.copy(alpha = 0.10f)
    val ratingScrim = Color.Black.copy(alpha = 0.62f)
    val ratingBorder = Color.White.copy(alpha = 0.16f)
}

internal object TvMotionTokens {
    const val focusDurationMillis = 160
    const val heroTransitionDurationMillis = 220
    const val heroUpdateDelayMillis = 240L
    const val confirmationPulseDurationMillis = 110
    const val startupSweepDurationMillis = 480
    const val focusedArtworkScale = 1.02f
    const val kenBurnsDurationMillis = 24_000
    const val kenBurnsMaxScale = 1.08f
    const val kenBurnsHorizontalTranslationFraction = 0.020f
    const val kenBurnsVerticalTranslationFraction = 0.008f
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
