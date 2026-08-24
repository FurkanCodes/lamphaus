package com.lamphaus.app.tv

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import com.lamphaus.app.R

private val LamphausTvColors = darkColorScheme(
    primary = Color(0xFFA8C8FF),
    onPrimary = Color(0xFF003062),
    primaryContainer = Color(0xFF00468A),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFBDC7DC),
    onSecondary = Color(0xFF273141),
    secondaryContainer = Color(0xFF3E4758),
    onSecondaryContainer = Color(0xFFD9E3F8),
    tertiary = Color(0xFFDCBCE1),
    onTertiary = Color(0xFF3E2845),
    tertiaryContainer = Color(0xFF563E5C),
    onTertiaryContainer = Color(0xFFF9D8FE),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A1C1E),
    onBackground = Color(0xFFE3E2E6),
    surface = Color(0xFF121316),
    onSurface = Color(0xFFC7C6CA),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC4C6CF),
    border = Color(0xFF8E9099),
    borderVariant = Color(0xFF45474E),
)

private val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semi_bold, FontWeight.SemiBold),
)

private fun tvTextStyle(
    fontWeight: FontWeight,
    fontSize: Int,
    lineHeight: Int,
    letterSpacing: Float = 0f,
) = TextStyle(
    fontFamily = Inter,
    fontWeight = fontWeight,
    fontSize = fontSize.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
    textMotion = TextMotion.Animated,
)

private val LamphausTvTypography = Typography(
    displayLarge = tvTextStyle(FontWeight.SemiBold, 57, 64),
    displayMedium = tvTextStyle(FontWeight.Normal, 45, 52),
    displaySmall = tvTextStyle(FontWeight.Medium, 36, 44),
    headlineLarge = tvTextStyle(FontWeight.Normal, 32, 40),
    headlineMedium = tvTextStyle(FontWeight.Normal, 28, 36),
    headlineSmall = tvTextStyle(FontWeight.Normal, 24, 32),
    titleLarge = tvTextStyle(FontWeight.Normal, 22, 28),
    titleMedium = tvTextStyle(FontWeight.Medium, 16, 24, 0.15f),
    titleSmall = tvTextStyle(FontWeight.Medium, 14, 20, 0.1f),
    labelLarge = tvTextStyle(FontWeight.Medium, 14, 20, 0.1f),
    labelMedium = tvTextStyle(FontWeight.Medium, 12, 16, 0.25f),
    labelSmall = tvTextStyle(FontWeight.Medium, 11, 16, 0.1f),
    bodyLarge = tvTextStyle(FontWeight.Normal, 16, 24, 0.5f),
    bodyMedium = tvTextStyle(FontWeight.Normal, 14, 20, 0.25f),
    bodySmall = tvTextStyle(FontWeight.Normal, 12, 16, 0.2f),
)

@Composable
fun LamphausTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LamphausTvColors,
        typography = LamphausTvTypography,
        content = content,
    )
}
