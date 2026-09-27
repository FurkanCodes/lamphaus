package com.lamphaus.app.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lamphaus.app.R
import com.lamphaus.core.model.PlaybackSegment
import com.lamphaus.core.model.PlaybackSegmentType
import com.lamphaus.core.model.SkipSegmentPolicy

/**
 * Nuvio's skip button: one labelled action for the active segment. With the
 * chrome hidden a bar drains for ten seconds and the button hides itself; the
 * chrome brings it back without restarting the countdown. On TV it takes focus
 * while the chrome is hidden so Select skips (TV-FOC-01/02); on mobile it is a
 * 48dp touch target (MOB-A11Y-04).
 */
@Composable
internal fun PlayerSkipButton(
    segment: PlaybackSegment?,
    targetsPostCredits: Boolean,
    movie: Boolean,
    dismissed: Boolean,
    controlsVisible: Boolean,
    isTelevision: Boolean,
    reducedMotion: Boolean,
    onSkip: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var lastSegment by remember { mutableStateOf(segment) }
    if (segment != null) lastSegment = segment
    var autoHidden by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }

    LaunchedEffect(segment?.startMillis, segment?.type) {
        autoHidden = false
        progress.snapTo(0f)
    }
    val shouldShow = segment != null && (!dismissed || controlsVisible)
    LaunchedEffect(shouldShow, autoHidden, controlsVisible) {
        if (shouldShow && !autoHidden && !controlsVisible) {
            val remaining = ((1f - progress.value) * SkipSegmentPolicy.AUTO_HIDE_MILLIS).toInt().coerceAtLeast(1)
            progress.animateTo(1f, tween(remaining, easing = LinearEasing))
            autoHidden = true
        }
    }
    val visible = SkipSegmentPolicy.isButtonVisible(
        hasActiveSegment = segment != null,
        dismissed = dismissed,
        controlsVisible = controlsVisible,
        autoHidden = autoHidden,
    )
    LaunchedEffect(visible, controlsVisible) {
        if (isTelevision && visible && !controlsVisible) runCatching { focusRequester.requestFocus() }
    }

    AnimatedVisibility(
        visible = visible,
        enter = if (reducedMotion) EnterTransition.None else fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.8f),
        exit = if (reducedMotion) ExitTransition.None else fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.8f),
        modifier = modifier,
    ) {
        // A removed node does not always report losing focus.
        DisposableEffect(Unit) { onDispose { onFocusChanged(false) } }
        val shape = RoundedCornerShape(if (isTelevision) 4.dp else 8.dp)
        val content = if (focused) PlayerFocusedContent else PlayerOnSurface
        val showCountdown = !(controlsVisible || autoHidden || dismissed)
        Column(
            Modifier
                .width(IntrinsicSize.Max)
                .focusRequester(focusRequester)
                .onFocusChanged {
                    focused = it.isFocused
                    onFocusChanged(it.isFocused)
                }
                .clip(shape)
                .background(if (focused) PlayerFocused else Color(0xFF1E1E1E).copy(alpha = 0.85f))
                .then(if (focused && isTelevision) Modifier.border(3.dp, PlayerPrimary, shape) else Modifier)
                .clickable(role = Role.Button, onClick = onSkip),
        ) {
            Row(
                Modifier.heightIn(min = 48.dp).padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
                Text(
                    text = skipLabel(lastSegment?.type, targetsPostCredits, movie),
                    color = content,
                    fontFamily = PlayerFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = if (isTelevision) 15.sp else 14.sp,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Box(
                Modifier.fillMaxWidth().height(3.dp)
                    .background(Color.White.copy(alpha = if (showCountdown) 0.15f else 0f)),
            ) {
                Box(
                    Modifier.fillMaxWidth(progress.value).height(3.dp)
                        .background(PlayerPrimary.copy(alpha = if (showCountdown) 0.85f else 0f)),
                )
            }
        }
    }
}

@Composable
private fun skipLabel(type: PlaybackSegmentType?, targetsPostCredits: Boolean, movie: Boolean): String = stringResource(
    when (type) {
        PlaybackSegmentType.RECAP -> R.string.skip_recap
        PlaybackSegmentType.ENDING -> when {
            targetsPostCredits -> R.string.skip_to_post_credits
            movie -> R.string.skip_movie_credits
            else -> R.string.skip_ending
        }
        else -> R.string.skip_intro
    },
)
