package com.lamphaus.app.mobile

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CancellationException

/** The shared-element scope around browsing and details; null when motion is reduced. */
@OptIn(ExperimentalSharedTransitionApi::class)
internal val LocalMediaSharedScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The browse-or-details content scope a shared element animates with. */
internal val LocalMediaAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Marks a poster (or the details hero) as one end of the poster-to-details
 * container transform (MOB-MOT-01). [key] is the tapped card's own focus key,
 * so a title shown in two rows morphs from the card that was actually
 * tapped. Without a key, or with motion reduced, nothing changes.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.mediaSharedBounds(key: String?): Modifier {
    if (key == null) return this
    val shared = LocalMediaSharedScope.current ?: return this
    val animated = LocalMediaAnimatedScope.current ?: return this
    return with(shared) {
        this@mediaSharedBounds.sharedBounds(
            sharedContentState = rememberSharedContentState("media:$key"),
            animatedVisibilityScope = animated,
            enter = fadeIn(tween(TRANSFORM_MILLIS)),
            exit = fadeOut(tween(TRANSFORM_MILLIS)),
            clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(MobileTokens.radiusCard)),
        )
    }
}

private const val TRANSFORM_MILLIS = 240

/**
 * Browsing and a details page as one seekable transition (MOB-MOT-01,
 * MOB-NAV-08/09): opening a title morphs its poster into the details hero;
 * the system Back gesture scrubs the page back toward the poster, shrinking
 * it no smaller than 90%, and either commits ([onBack]) or restores the page
 * on cancel. A page that goes back somewhere else ([predictiveBack] false,
 * such as a title opened from a person page) uses plain Back. With
 * animations removed, changes are instant and no element is shared
 * (MOB-MOT-03).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun MediaDetailTransition(
    detailKey: String?,
    reducedMotion: Boolean,
    predictiveBack: Boolean,
    onBack: () -> Unit,
    content: @Composable (key: String?) -> Unit,
) {
    val transitionState = remember { SeekableTransitionState(detailKey) }
    LaunchedEffect(detailKey, reducedMotion) {
        if (reducedMotion) transitionState.snapTo(detailKey) else transitionState.animateTo(detailKey)
    }
    val seekBack = detailKey != null && predictiveBack && !reducedMotion
    PredictiveBackHandler(enabled = seekBack) { events ->
        val from = detailKey
        try {
            events.collect { event -> transitionState.seekTo(event.progress, targetState = null) }
            onBack()
        } catch (cancelled: CancellationException) {
            // The gesture was abandoned: the page settles back unchanged.
            transitionState.animateTo(from)
            throw cancelled
        }
    }
    BackHandler(enabled = detailKey != null && !seekBack, onBack = onBack)

    SharedTransitionLayout {
        CompositionLocalProvider(LocalMediaSharedScope provides this.takeUnless { reducedMotion }) {
            rememberTransition(transitionState, label = "media detail").AnimatedContent(
                transitionSpec = {
                    if (targetState == null) {
                        // Back: the page recedes toward its poster (≥ 90%).
                        fadeIn(tween(TRANSFORM_MILLIS, easing = LinearOutSlowInEasing)) togetherWith
                            (scaleOut(targetScale = 0.9f, animationSpec = tween(TRANSFORM_MILLIS)) +
                                fadeOut(tween(TRANSFORM_MILLIS, easing = FastOutLinearInEasing)))
                    } else {
                        fadeIn(tween(TRANSFORM_MILLIS, delayMillis = 60, easing = LinearOutSlowInEasing)) togetherWith
                            fadeOut(tween(150, easing = FastOutLinearInEasing))
                    }
                },
            ) { key ->
                CompositionLocalProvider(LocalMediaAnimatedScope provides this.takeUnless { reducedMotion }) {
                    content(key)
                }
            }
        }
    }
}
