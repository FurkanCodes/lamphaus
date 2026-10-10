package com.lamphaus.app.tv

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity

/**
 * Settings → Appearance → Page transitions (TV-MOT-01): the page fades in
 * while rising into place each time [key] changes, and when it first
 * appears. Only the incoming page moves; the page it replaces is already
 * gone, so two pages never draw together.
 *
 * QA-08: alpha modulates each draw instead of rendering the page into an
 * offscreen buffer, and the animation is read only in the layer block, so an
 * entrance never recomposes or re-lays out the page. Without [enabled] the
 * page carries no layer at all and draws exactly as before.
 */
@Composable
internal fun Modifier.tvPageEntrance(key: Any?, enabled: Boolean): Modifier {
    if (!enabled) return this
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(progress) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(TvMotionTokens.pageEntranceMillis, easing = LinearOutSlowInEasing),
        )
    }
    val rise = with(LocalDensity.current) { TvMotionTokens.pageEntranceRise.toPx() }
    return graphicsLayer {
        val shown = progress.value
        alpha = shown
        translationY = rise * (1f - shown)
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }
}
