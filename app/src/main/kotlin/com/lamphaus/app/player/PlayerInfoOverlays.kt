package com.lamphaus.app.player

import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lamphaus.app.R
import com.lamphaus.core.model.DisplayModeCandidate
import kotlinx.coroutines.delay
import java.util.Date
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** One "Label · value" line in a start-of-playback info block. */
internal data class PlayerInfoRow(val label: String, val value: String)

private val InfoRowHeight = 18.dp
private val InfoRowGap = 4.dp
private const val INFO_HOLD_MILLIS = 5_000L

/**
 * Nuvio's start-of-playback info block: an accent line grows, the rows fade in
 * one by one, hold for five seconds, then leave in reverse. [alignEnd] mirrors
 * it for the top-end corner (display mode). Reduced motion keeps the hold but
 * makes every transition instant (TV-MOT-01, MOB-MOT-03).
 */
@Composable
internal fun PlayerInfoLines(
    rows: List<PlayerInfoRow>,
    alignEnd: Boolean,
    reducedMotion: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (rows.isEmpty()) return
    val count = rows.size
    val totalLineHeight = InfoRowHeight * count + InfoRowGap * (count - 1)
    val containerAlpha = remember(rows) { Animatable(0f) }
    val lineFraction = remember(rows) { Animatable(0f) }
    val rowAlphas = remember(rows) { List(count) { Animatable(0f) } }

    LaunchedEffect(rows, reducedMotion) {
        fun ms(value: Int) = if (reducedMotion) 0 else value
        containerAlpha.animateTo(1f, tween(ms(300)))
        lineFraction.animateTo(1f, tween(ms(400), easing = FastOutSlowInEasing))
        rowAlphas.forEach { alpha ->
            if (!reducedMotion) delay(80)
            alpha.animateTo(1f, tween(ms(200)))
        }
        delay(INFO_HOLD_MILLIS)
        rowAlphas.asReversed().forEach { alpha ->
            if (!reducedMotion) delay(60)
            alpha.animateTo(0f, tween(ms(150)))
        }
        if (!reducedMotion) delay(100)
        lineFraction.animateTo(0f, tween(ms(300), easing = FastOutSlowInEasing))
        if (!reducedMotion) delay(200)
        containerAlpha.animateTo(0f, tween(ms(200)))
        onFinished()
    }

    if (containerAlpha.value <= 0f) return
    val summary = rows.joinToString { "${it.label} ${it.value}" }
    Row(
        modifier = modifier
            .alpha(containerAlpha.value)
            .clearAndSetSemantics { contentDescription = summary },
        verticalAlignment = Alignment.Top,
    ) {
        val line = @Composable {
            Box(
                Modifier
                    .width(3.dp)
                    .height(totalLineHeight * lineFraction.value)
                    .clip(RoundedCornerShape(2.dp))
                    .background(PlayerPrimary),
            )
        }
        if (!alignEnd) line()
        Column(
            modifier = Modifier.padding(start = if (alignEnd) 0.dp else 10.dp, end = if (alignEnd) 10.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(InfoRowGap),
            horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
        ) {
            rows.forEachIndexed { index, row ->
                Row(
                    Modifier.height(InfoRowHeight).alpha(rowAlphas[index].value),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(row.label, color = PlayerOnSurface, fontFamily = PlayerFont, fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold)
                    Text(" · ", color = PlayerOnSurface.copy(alpha = 0.5f), fontFamily = PlayerFont, fontSize = 11.sp)
                    Text(row.value, color = PlayerOnSurfaceMuted, fontFamily = PlayerFont, fontSize = 11.sp)
                }
            }
        }
        if (alignEnd) line()
    }
}

/** Display-mode rows, as Nuvio shows them after frame-rate matching settles. */
@Composable
internal fun displayModeRows(mode: DisplayModeCandidate): List<PlayerInfoRow> {
    val refresh = stringResource(R.string.player_info_refresh)
    val resolution = stringResource(R.string.player_info_resolution)
    // Stable across recompositions so the animation is not restarted by position ticks.
    return remember(mode, refresh, resolution) {
        listOf(
            PlayerInfoRow(refresh, formatRefreshRate(mode.refreshRateHz)),
            PlayerInfoRow(resolution, "${mode.width}x${mode.height}"),
        )
    }
}

internal fun formatRefreshRate(rate: Float): String {
    val rounded = (rate * 1000f).roundToInt() / 1000f
    val whole = rounded.roundToInt()
    val display = if (abs(rounded - whole) < 0.01f) whole.toString() else String.format(java.util.Locale.ROOT, "%.3f", rounded)
    return "$display Hz"
}

/** When playback finishes at the current speed, from [nowMillis]. */
internal fun playbackEndsAtMillis(nowMillis: Long, positionMillis: Long, durationMillis: Long, speed: Float): Long? {
    if (durationMillis <= 0L) return null
    val effectiveSpeed = speed.takeIf { it > 0f } ?: 1f
    val remaining = (durationMillis - positionMillis).coerceAtLeast(0L)
    return nowMillis + ceil(remaining / effectiveSpeed.toDouble()).toLong()
}

/** Ticks on each wall-clock second so the clock and "Ends at" stay current. */
@Composable
internal fun rememberWallClockMillis(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay((1_000L - now % 1_000L).coerceAtLeast(250L))
        }
    }
    return now
}

/** Nuvio's top-end clock: the time now and when this title will end. */
@Composable
internal fun PlayerClock(
    positionMillis: Long,
    durationMillis: Long,
    speed: Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val formatter = remember(context) { DateFormat.getTimeFormat(context) }
    val now = rememberWallClockMillis()
    val endsAt = playbackEndsAtMillis(now, positionMillis, durationMillis, speed)
    Column(modifier, horizontalAlignment = Alignment.End) {
        Text(
            text = formatter.format(Date(now)),
            color = PlayerOnSurface,
            fontFamily = PlayerFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
        if (endsAt != null) {
            Text(
                text = stringResource(R.string.player_ends_at, formatter.format(Date(endsAt))),
                color = PlayerOnSurfaceMuted,
                fontFamily = PlayerFont,
                fontSize = 12.sp,
            )
        }
    }
}

/** "Ends at 22:41" for the mobile timeline row. */
@Composable
internal fun PlayerEndsAt(positionMillis: Long, durationMillis: Long, speed: Float, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val formatter = remember(context) { DateFormat.getTimeFormat(context) }
    val endsAt = playbackEndsAtMillis(rememberWallClockMillis(), positionMillis, durationMillis, speed) ?: return
    Text(
        text = stringResource(R.string.player_ends_at, formatter.format(Date(endsAt))),
        color = PlayerOnSurfaceMuted,
        fontFamily = PlayerFont,
        fontSize = 12.sp,
        modifier = modifier,
    )
}
