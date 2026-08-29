package com.lamphaus.app.tv

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import com.lamphaus.app.ui.MediaArtwork
import com.lamphaus.app.ui.LocalArtworkResolver
import com.lamphaus.app.ui.fixtureArtworkResource
import com.lamphaus.core.model.MediaPreview

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
internal fun TvHeroArtwork(
    media: MediaPreview,
    userEnabled: Boolean,
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
    val motionEnabled = shouldAnimateKenBurns(userEnabled, reducedMotion, hasArtwork)

    LaunchedEffect(media.stableKey, motionEnabled) {
        progress.snapTo(0f)
        if (motionEnabled) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(TvMotionTokens.kenBurnsDurationMillis, easing = LinearEasing),
            )
        }
    }

    MediaArtwork(
        media = media,
        modifier = modifier.graphicsLayer {
            val progressValue = progress.value
            val scale = 1f + ((TvMotionTokens.kenBurnsMaxScale - 1f) * progressValue)
            scaleX = scale
            scaleY = scale
            translationX = size.width * path.horizontalSign *
                TvMotionTokens.kenBurnsHorizontalTranslationFraction * progressValue
            translationY = size.height * path.verticalSign *
                TvMotionTokens.kenBurnsVerticalTranslationFraction * progressValue
            transformOrigin = TransformOrigin.Center
        },
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        preferBackdrop = true,
    )
}
