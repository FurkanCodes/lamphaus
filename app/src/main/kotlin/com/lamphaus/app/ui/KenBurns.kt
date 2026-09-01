package com.lamphaus.app.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.lamphaus.core.model.MediaPreview

internal object KenBurnsDefaults {
    const val durationMillis = 24_000
    const val maxScale = 1.08f
    const val horizontalTranslationFraction = 0.020f
    const val verticalTranslationFraction = 0.008f
}

internal data class KenBurnsPath(
    val horizontalSign: Float,
    val verticalSign: Float,
)

internal fun kenBurnsPathFor(stableKey: String): KenBurnsPath = when (Math.floorMod(stableKey.hashCode(), 4)) {
    0 -> KenBurnsPath(+1f, +1f)
    1 -> KenBurnsPath(+1f, -1f)
    2 -> KenBurnsPath(-1f, +1f)
    else -> KenBurnsPath(-1f, -1f)
}

internal fun shouldAnimateKenBurns(
    userEnabled: Boolean,
    reducedMotion: Boolean,
    hasArtwork: Boolean,
): Boolean = userEnabled && !reducedMotion && hasArtwork

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

/**
 * Artwork with the shared slow Ken Burns drift. Motion is deterministic per
 * title (path derived from the stable key) and honours the user's Appearance
 * toggle plus the system's reduced-motion (animator duration scale) setting.
 */
@Composable
internal fun KenBurnsArtwork(
    media: MediaPreview,
    enabled: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val resolver = LocalArtworkResolver.current
    val resolvedMedia = resolver.resolve(media).media
    val path = remember(media.stableKey) { kenBurnsPathFor(media.stableKey) }
    val progress = remember(media.stableKey) { Animatable(0f) }
    val hasArtwork = fixtureArtworkResource(media) != null ||
        !resolvedMedia.backgroundUrl.isNullOrBlank() ||
        !resolvedMedia.posterUrl.isNullOrBlank()
    val motionEnabled = shouldAnimateKenBurns(enabled, reducedMotion, hasArtwork)

    LaunchedEffect(media.stableKey, motionEnabled) {
        progress.snapTo(0f)
        if (motionEnabled) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(KenBurnsDefaults.durationMillis, easing = LinearEasing),
            )
        }
    }

    MediaArtwork(
        media = media,
        modifier = modifier.graphicsLayer {
            val progressValue = progress.value
            val scale = 1f + ((KenBurnsDefaults.maxScale - 1f) * progressValue)
            scaleX = scale
            scaleY = scale
            translationX = size.width * path.horizontalSign *
                KenBurnsDefaults.horizontalTranslationFraction * progressValue
            translationY = size.height * path.verticalSign *
                KenBurnsDefaults.verticalTranslationFraction * progressValue
            transformOrigin = TransformOrigin.Center
        },
        contentScale = ContentScale.Crop,
        preferBackdrop = true,
    )
}
