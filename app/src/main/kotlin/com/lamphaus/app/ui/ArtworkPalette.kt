package com.lamphaus.app.ui

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.scale
import com.google.android.material.color.DynamicColorsOptions
import com.google.android.material.color.MaterialColors
import com.lamphaus.core.model.TEXT_CONTRAST
import com.lamphaus.core.model.clampContrast

/**
 * One artwork's accent, derived from its dominant color with Material's
 * content-based scheme (MOB-CLR-01). [accent] is already clamped to text
 * contrast against the surface it was derived for (SHR-PROD-08), so it can
 * color text, controls, and progress directly. Used by the TV ambient.
 */
internal data class ArtworkPalette(
    val accent: Color,
    val accentContainer: Color,
)

private const val PALETTE_SAMPLE_SIZE = 96
private const val PALETTE_CACHE_CAPACITY = 48

private val paletteCache = LruCache<String, ArtworkPalette>(PALETTE_CACHE_CAPACITY)

/**
 * The palette for [bitmap], cached under [key]. Null when the artwork has no
 * usable seed color (for example, flat gray art); callers keep their brand
 * fallback (MOB-CLR-09).
 */
internal fun artworkPalette(key: String, bitmap: Bitmap, surface: Color): ArtworkPalette? {
    paletteCache.get(key)?.let { return it }
    return runCatching {
        val sample = bitmap.scale(PALETTE_SAMPLE_SIZE, PALETTE_SAMPLE_SIZE)
        val seed = DynamicColorsOptions.Builder()
            .setContentBasedSource(sample)
            .build()
            .contentBasedSeedColor
            ?: return@runCatching null
        val roles = MaterialColors.getColorRoles(seed, false)
        ArtworkPalette(
            accent = Color(clampContrast(roles.accent, surface.toArgb(), TEXT_CONTRAST)),
            accentContainer = Color(roles.accentContainer),
        )
    }.getOrNull()?.also { paletteCache.put(key, it) }
}

