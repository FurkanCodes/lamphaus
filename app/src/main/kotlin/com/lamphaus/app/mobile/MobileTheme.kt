package com.lamphaus.app.mobile

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lamphaus.app.R

private val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private val MobileDark = darkColorScheme(
    background = MobileTokens.ink,
    onBackground = MobileTokens.textPrimary,
    surface = MobileTokens.ink,
    onSurface = MobileTokens.textPrimary,
    surfaceContainer = MobileTokens.surface,
    surfaceContainerHigh = MobileTokens.surfaceRaised,
    surfaceVariant = MobileTokens.surfaceRaised,
    primary = MobileTokens.accent,
    onPrimary = Color(0xFF003062), // TV-approved on-primary pairing
    primaryContainer = MobileTokens.accent.copy(alpha = 0.16f),
    onPrimaryContainer = MobileTokens.accent,
    secondaryContainer = MobileTokens.accent.copy(alpha = 0.16f),
    onSecondaryContainer = MobileTokens.accent,
    onSurfaceVariant = MobileTokens.textMuted,
    outlineVariant = MobileTokens.hairline,
)

private fun mobileTypography(): Typography {
    val base = Typography()
    fun withInter(style: TextStyle) = style.copy(fontFamily = Inter)
    return Typography(
        // Named ramp slots per design spec; the rest of the ramp keeps M3 metrics on Inter.
        displayLarge = base.displayLarge.copy(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 30.sp),
        titleLarge = base.titleLarge.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
        titleMedium = base.titleMedium.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp),
        labelMedium = base.labelMedium.copy(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp),
        displayMedium = withInter(base.displayMedium),
        displaySmall = withInter(base.displaySmall),
        headlineLarge = withInter(base.headlineLarge),
        headlineMedium = withInter(base.headlineMedium),
        headlineSmall = withInter(base.headlineSmall),
        titleSmall = withInter(base.titleSmall),
        bodyLarge = withInter(base.bodyLarge),
        bodySmall = withInter(base.bodySmall),
        labelLarge = withInter(base.labelLarge),
        labelSmall = withInter(base.labelSmall),
    )
}

@Composable
fun LamphausMobileTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MobileDark,
        typography = mobileTypography(),
        content = content,
    )
}
