package com.lamphaus.app.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Gesture and rail math kept pure so it is unit-testable (SHR-ARC-15). A drag
 * across the full window covers [SEEK_SPAN_MILLIS] of the timeline.
 */
internal object PlayerGesturePolicy {
    const val SEEK_SPAN_MILLIS = 90_000L

    fun seekDeltaMillis(deltaPx: Float, widthPx: Float): Long =
        if (widthPx <= 0f) 0L else (deltaPx / widthPx * SEEK_SPAN_MILLIS).toLong()

    fun levelFor(start: Float, deltaPx: Float, heightPx: Float): Float =
        if (heightPx <= 0f) start else (start + deltaPx / heightPx).coerceIn(0f, 1f)
}

internal fun systemBrightnessFraction(context: Context): Float {
    val raw = runCatching {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    }.getOrDefault(128)
    return (raw / 255f).coerceIn(0.01f, 1f)
}

/** Window-scoped brightness; no permission and no durable setting (MOB-SET-01). */
internal fun applyWindowBrightness(activity: Activity?, fraction: Float) {
    val window = activity?.window ?: return
    window.attributes = window.attributes.apply {
        screenBrightness = fraction.coerceIn(0.01f, 1f)
    }
}

internal fun mediaVolumeFraction(context: Context): Float {
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return 1f
    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    return (audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max).coerceIn(0f, 1f)
}

/** Returns false when the platform refuses the change so the caller can fall back. */
internal fun setMediaVolumeFraction(context: Context, fraction: Float): Boolean {
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    return runCatching {
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (fraction * max).toInt().coerceIn(0, max), 0)
        true
    }.getOrDefault(false)
}

/** Vertical brightness/volume rail; inset from gesture edges by the caller (MOB-INP-01). */
@Composable
internal fun PlayerSideRail(
    icon: ImageVector,
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .weight(1f)
                .width(PlayerChromeTokens.ControlContainer)
                .heightIn(min = 96.dp)
                .semantics {
                    contentDescription = label
                    progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(0f, 1f), 0f..1f)
                    setProgress { target ->
                        onValueChange(target.coerceIn(0f, 1f))
                        true
                    }
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dy ->
                        onValueChange((value - dy / size.height).coerceIn(0f, 1f))
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.width(4.dp).fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp)).background(PlayerTrack),
            )
            Box(
                Modifier.width(4.dp).fillMaxHeight(value.coerceIn(0f, 1f))
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(2.dp)).background(PlayerPrimary),
            )
        }
        Icon(icon, null, Modifier.padding(top = 4.dp, bottom = 8.dp).width(20.dp), tint = PlayerOnSurface)
    }
}

/** Transient feedback for seek, brightness, and volume gestures. */
@Composable
internal fun PlayerHudBubble(icon: ImageVector?, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(PlayerSurface.copy(alpha = 0.94f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, null, Modifier.width(20.dp), tint = PlayerPrimary)
        Text(text, color = PlayerOnSurface, fontFamily = PlayerFont, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}

/**
 * Touch layer under the chrome: tap toggles the controls, double tap seeks,
 * horizontal drag scrubs, and vertical drag on the outer thirds adjusts
 * brightness and volume. The rails and buttons remain the visible,
 * screen-reader reachable alternative (MOB-A11Y-03, MOB-A11Y-06).
 */
@Composable
internal fun PlayerGestureSurface(
    enabled: Boolean,
    onTap: () -> Unit,
    onDoubleTap: (Boolean) -> Unit,
    onSeekDelta: (deltaPx: Float, widthPx: Float) -> Unit,
    onSeekEnd: () -> Unit,
    onBrightnessDelta: (deltaPx: Float, heightPx: Float) -> Unit,
    onVolumeDelta: (deltaPx: Float, heightPx: Float) -> Unit,
) {
    fun Modifier.taps(forward: Boolean): Modifier = pointerInput(enabled) {
        if (!enabled) return@pointerInput
        detectTapGestures(onTap = { onTap() }, onDoubleTap = { onDoubleTap(forward) })
    }

    Row(Modifier.fillMaxSize()) {
        Box(
            Modifier.weight(1f).fillMaxHeight().taps(forward = false)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectVerticalDragGestures { _, dy -> onBrightnessDelta(-dy, size.height.toFloat()) }
                },
        )
        Box(
            Modifier.weight(2f).fillMaxHeight().taps(forward = false)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = { onSeekEnd() },
                    ) { _, dx -> onSeekDelta(dx, size.width.toFloat()) }
                },
        )
        Box(
            Modifier.weight(1f).fillMaxHeight().taps(forward = true)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectVerticalDragGestures { _, dy -> onVolumeDelta(-dy, size.height.toFloat()) }
                },
        )
    }
}
