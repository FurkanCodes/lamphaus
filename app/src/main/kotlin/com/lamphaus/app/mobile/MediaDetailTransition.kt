package com.lamphaus.app.mobile

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.CancellationException

private const val BACK_MILLIS = 240

/**
 * Browsing and a details page as one seekable transition (MOB-MOT-01,
 * MOB-NAV-08/09). Opening a title swaps to the page at once so its own
 * entrance (the backdrop settling, sections rising) carries the motion with
 * no blank frame between the two. The system Back gesture scrubs the page
 * back toward browsing, receding no smaller than 90%, and either commits
 * ([onBack]) or settles back unchanged on cancel. A page that goes back
 * somewhere else ([predictiveBack] false, such as a title opened from a
 * person page) uses plain Back. With animations removed, changes are
 * instant (MOB-MOT-03).
 */
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

    rememberTransition(transitionState, label = "media detail").AnimatedContent(
        transitionSpec = {
            if (targetState == null) {
                // Back: the page recedes toward browsing (≥ 90%).
                fadeIn(tween(BACK_MILLIS, easing = LinearOutSlowInEasing)) togetherWith
                    (scaleOut(targetScale = 0.9f, animationSpec = tween(BACK_MILLIS)) +
                        fadeOut(tween(BACK_MILLIS, easing = FastOutLinearInEasing)))
            } else {
                EnterTransition.None togetherWith ExitTransition.None
            }
        },
    ) { key ->
        content(key)
    }
}
