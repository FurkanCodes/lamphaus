package com.lamphaus.app.mobile

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.lamphaus.core.data.preferences.ThemePreference

private val LamphausLight = lightColorScheme(
    primary = Color(0xFF40588D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE2FF),
    onPrimaryContainer = Color(0xFF122653),
    secondary = Color(0xFF006878),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFA8EDFA),
    onSecondaryContainer = Color(0xFF00363F),
    background = Color.White,
    onBackground = Color(0xFF191B22),
    surface = Color(0xFFF6F7FA),
    onSurface = Color(0xFF191B22),
    surfaceVariant = Color(0xFFE2E3EA),
    onSurfaceVariant = Color(0xFF454852),
    outline = Color(0xFF757781),
)

private val LamphausDark = darkColorScheme(
    primary = Color(0xFFAFC2FF),
    onPrimary = Color(0xFF10234F),
    primaryContainer = Color(0xFF293F72),
    onPrimaryContainer = Color(0xFFDCE2FF),
    secondary = Color(0xFF68D4E8),
    onSecondary = Color(0xFF00363F),
    secondaryContainer = Color(0xFF004F5C),
    onSecondaryContainer = Color(0xFFA8EDFA),
    background = Color(0xFF090A0D),
    onBackground = Color(0xFFF2F3FA),
    surface = Color(0xFF15171E),
    onSurface = Color(0xFFF2F3FA),
    surfaceVariant = Color(0xFF30323A),
    onSurfaceVariant = Color(0xFFC6C8D2),
    outline = Color(0xFF90929D),
)

@Composable
fun LamphausMobileTheme(
    preference: ThemePreference,
    dynamicColor: Boolean,
    content: @Composable () -> Unit,
) {
    val dark = when (preference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> LamphausDark
        else -> LamphausLight
    }
    MaterialTheme(colorScheme = colors, content = content)
}

