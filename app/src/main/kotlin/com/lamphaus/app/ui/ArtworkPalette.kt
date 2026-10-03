package com.lamphaus.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.scale
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import coil3.size.Size
import coil3.toBitmap
import com.google.android.material.color.DynamicColorsOptions
import com.google.android.material.color.MaterialColors
import com.lamphaus.core.model.TEXT_CONTRAST
import com.lamphaus.core.model.clampContrast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One artwork's accent, derived from its dominant color with Material's
 * content-based scheme (MOB-CLR-01). [accent] is already clamped to text
 * contrast against the surface it was derived for (SHR-PROD-08), so it can
 * color text, controls, and progress directly. Shared by the TV ambient and
 * the mobile details tint; only non-visual logic is shared (SHR-ARC-14).
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

/**
 * Decodes [data] small and off the main thread, then derives its palette.
 * Used where the screen does not already hold a decoded copy (mobile details).
 */
internal suspend fun loadArtworkPalette(context: Context, key: String, data: Any, surface: Color): ArtworkPalette? {
    paletteCache.get(key)?.let { return it }
    return withContext(Dispatchers.IO) {
        val result = SingletonImageLoader.get(context).execute(
            ImageRequest.Builder(context)
                .data(data)
                .size(Size(PALETTE_SAMPLE_SIZE * 2, PALETTE_SAMPLE_SIZE * 2))
                .allowHardware(false)
                .bitmapConfig(Bitmap.Config.ARGB_8888)
                .build(),
        )
        (result as? SuccessResult)?.let { artworkPalette(key, it.image.toBitmap(), surface) }
    }
}
