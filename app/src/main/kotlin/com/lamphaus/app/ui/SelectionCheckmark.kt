package com.lamphaus.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun SelectionCheckmark(
    selected: Boolean,
    selectedContainerColor: Color,
    selectedContentColor: Color,
    modifier: Modifier = Modifier,
) {
    // Reduced motion swaps the reveal for an instant state change (MOB-MOT-03).
    val reducedMotion = rememberReducedMotion()
    AnimatedVisibility(
        visible = selected,
        modifier = modifier,
        enter = if (reducedMotion) {
            fadeIn(tween(0)) + scaleIn(tween(0), initialScale = 1f)
        } else {
            fadeIn(tween(140)) + scaleIn(tween(180), initialScale = 0.72f)
        },
        exit = if (reducedMotion) {
            fadeOut(tween(0)) + scaleOut(tween(0), targetScale = 1f)
        } else {
            fadeOut(tween(90)) + scaleOut(tween(90), targetScale = 0.72f)
        },
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(selectedContainerColor)
                .border(
                    width = 1.dp,
                    color = selectedContentColor.copy(alpha = 0.24f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = selectedContentColor,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
