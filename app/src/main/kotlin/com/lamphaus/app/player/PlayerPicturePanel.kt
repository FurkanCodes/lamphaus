package com.lamphaus.app.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lamphaus.app.R
import com.lamphaus.core.player.PictureAdjustment
import kotlin.math.roundToInt

/**
 * The Picture panel (PLY-PIC-01): brightness, contrast, saturation, and hue
 * for this playback. Touch uses sliders (MOB-CMP-06); TV moves a centred bar
 * with Left/Right on the focused row, like the timeline.
 */
@Composable
internal fun ColumnScope.PlayerPictureControls(
    picture: PictureAdjustment,
    onPicture: (PictureAdjustment) -> Unit,
    firstFocus: FocusRequester,
) {
    Column(
        Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(if (LocalPlayerTelevision.current) 6.dp else 4.dp),
    ) {
        PlayerPictureRow(stringResource(R.string.player_picture_brightness), picture.brightness,
            Modifier.focusRequester(firstFocus)) { onPicture(picture.copy(brightness = it)) }
        PlayerPictureRow(stringResource(R.string.player_picture_contrast), picture.contrast) {
            onPicture(picture.copy(contrast = it))
        }
        PlayerPictureRow(stringResource(R.string.player_picture_saturation), picture.saturation) {
            onPicture(picture.copy(saturation = it))
        }
        PlayerPictureRow(stringResource(R.string.player_picture_hue), picture.hue) {
            onPicture(picture.copy(hue = it))
        }
    }
    if (!picture.isNeutral) {
        PlayerTextButton(stringResource(R.string.player_picture_reset), { onPicture(PictureAdjustment.NEUTRAL) })
    }
}

private fun Int.signedText(): String = if (this > 0) "+$this" else this.toString()

private fun Float.toPictureStep(): Int =
    ((this / PictureAdjustment.STEP).roundToInt() * PictureAdjustment.STEP)
        .coerceIn(PictureAdjustment.MIN, PictureAdjustment.MAX)

@Composable
private fun PlayerPictureRow(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    onValue: (Int) -> Unit,
) {
    val range = PictureAdjustment.MIN.toFloat()..PictureAdjustment.MAX.toFloat()
    if (!LocalPlayerTelevision.current) {
        Column(modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Text(value.signedText(), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Slider(
                value = value.toFloat(),
                onValueChange = { raw -> raw.toPictureStep().takeIf { it != value }?.let(onValue) },
                valueRange = range,
                steps = (PictureAdjustment.MAX - PictureAdjustment.MIN) / PictureAdjustment.STEP - 1,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .semantics { contentDescription = label },
            )
        }
        return
    }
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(4.dp)
    Column(
        modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(if (focused) PlayerFocused else Color.Transparent)
            .border(if (focused) 3.dp else 0.dp, if (focused) PlayerPrimary else Color.Transparent, shape)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val step = when (event.key) {
                    Key.DirectionLeft -> -PictureAdjustment.STEP
                    Key.DirectionRight -> PictureAdjustment.STEP
                    else -> return@onPreviewKeyEvent false
                }
                val next = (value + step).coerceIn(PictureAdjustment.MIN, PictureAdjustment.MAX)
                if (next != value) onValue(next)
                true
            }
            .semantics {
                contentDescription = label
                stateDescription = value.signedText()
                progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), range, steps = 39)
                setProgress { target -> onValue(target.toPictureStep()); true }
            }
            .focusable()
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val ink = if (focused) PlayerFocusedContent else PlayerOnSurface
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), color = ink, fontFamily = PlayerFont,
                fontWeight = FontWeight.Medium, fontSize = 16.sp)
            Text(value.signedText(), color = ink, fontFamily = PlayerFont, fontSize = 15.sp)
        }
        // A centred bar: the fill grows from zero toward the value, so "unchanged" reads at a glance.
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            val radius = CornerRadius(size.height / 2)
            val track = if (focused) PlayerFocusedContent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.22f)
            val fill = if (focused) PlayerFocusedContent else PlayerPrimary
            drawRoundRect(track, cornerRadius = radius)
            val centre = size.width / 2
            val end = centre + centre * value / PictureAdjustment.MAX.toFloat()
            val left = minOf(centre, end)
            drawRoundRect(fill, Offset(left, 0f), Size(kotlin.math.abs(end - centre).coerceAtLeast(size.height), size.height), radius)
        }
    }
}
