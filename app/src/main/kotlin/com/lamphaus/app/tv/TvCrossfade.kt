package com.lamphaus.app.tv

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

/**
 * The approved hero/ambient crossfade (TV-MOT-01) without offscreen buffers.
 *
 * `fadeIn`/`fadeOut` render each side into an offscreen layer the size of
 * the content, which for full-screen artwork dominates GPU time on TV
 * hardware (QA-08). Here alpha modulates each draw directly, which is
 * pixel-identical for single-image content. The optional drift offsets are
 * fractions of the content width, matching the previous slide transitions.
 */
@Composable
internal fun <T> TvLayerlessCrossfade(
    targetState: T,
    reducedMotion: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    enterOffsetFraction: Float = 0f,
    exitOffsetFraction: Float = 0f,
    content: @Composable (T) -> Unit,
) {
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
        label = label,
    ) { state ->
        val visibility by transition.animateFloat(
            transitionSpec = {
                if (reducedMotion) snap() else tween(TvMotionTokens.heroTransitionDurationMillis)
            },
            label = "$label visibility",
        ) { if (it == EnterExitState.Visible) 1f else 0f }
        val exiting = transition.targetState == EnterExitState.PostExit
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = visibility
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                    val offset = if (exiting) exitOffsetFraction else enterOffsetFraction
                    translationX = size.width * offset * (1f - visibility)
                },
        ) {
            content(state)
        }
    }
}
