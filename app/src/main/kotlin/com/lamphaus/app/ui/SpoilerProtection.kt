package com.lamphaus.app.ui

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lamphaus.core.model.SpoilerProtectionSettings

internal enum class SpoilerContent {
    EPISODE_ARTWORK,
    EPISODE_SYNOPSIS,
}

internal fun SpoilerProtectionSettings.shouldBlur(
    content: SpoilerContent,
    watched: Boolean,
): Boolean {
    if (!enabled || watched) return false
    return when (content) {
        SpoilerContent.EPISODE_ARTWORK -> blurEpisodeArtwork
        SpoilerContent.EPISODE_SYNOPSIS -> blurEpisodeSynopsis
    }
}

@Composable
internal fun SpoilerBlurLayer(
    hidden: Boolean,
    veilColor: Color,
    semanticLabel: String,
    modifier: Modifier = Modifier,
    veilContent: @Composable BoxScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val contentAlpha by animateFloatAsState(
        targetValue = if (!hidden || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 1f else 0f,
        animationSpec = tween(180),
        label = "spoiler-content-alpha",
    )
    val veilAlpha by animateFloatAsState(
        targetValue = if (hidden) 1f else 0f,
        animationSpec = tween(180),
        label = "spoiler-veil-alpha",
    )
    val contentModifier = Modifier
        .fillMaxSize()
        .clip(RoundedCornerShape(0.dp))
        .then(if (hidden && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(18.dp) else Modifier)
        .alpha(contentAlpha)
        .then(if (hidden) Modifier.clearAndSetSemantics {} else Modifier)

    Box(modifier = modifier) {
        Box(modifier = contentModifier, content = content)
        if (hidden || veilAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(veilAlpha)
                    .background(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) veilColor.copy(alpha = veilColor.alpha * 0.72f)
                        else veilColor,
                    )
                    .semantics { contentDescription = semanticLabel },
                content = veilContent,
            )
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Box(
                        Modifier
                            .width(68.dp)
                            .height(10.dp)
                            .background(veilColor.copy(alpha = 0.32f), RoundedCornerShape(5.dp)),
                    )
                    Box(
                        Modifier
                            .padding(top = 10.dp)
                            .width(124.dp)
                            .height(10.dp)
                            .background(veilColor.copy(alpha = 0.26f), RoundedCornerShape(5.dp)),
                    )
                }
            }
        }
    }
}
