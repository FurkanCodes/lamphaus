package com.lamphaus.app.tv

import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.createBitmap
import kotlin.math.roundToInt

/**
 * The hero's three legibility scrims (SHR-PROD-01) baked into one bitmap.
 *
 * QA-08: drawn as three stacked gradients they cost three blended passes over
 * half the screen on every frame; on a Mali-G31 TV that alone pushed the 90th
 * percentile vertical-scroll frame to about 110 ms. The scrims depend only on
 * theme colours and size, so they are rendered once (at half resolution; they
 * are smooth gradients) and drawn in a single pass. Order and stops match the
 * previous `.background` chain exactly.
 */
internal fun Modifier.tvHeroScrim(background: Color, primary: Color): Modifier = drawWithCache {
    val width = (size.width / 2f).roundToInt().coerceAtLeast(1)
    val height = (size.height / 2f).roundToInt().coerceAtLeast(1)
    val bitmap = createBitmap(width, height)
    val canvas = android.graphics.Canvas(bitmap)
    fun layer(stops: List<Pair<Float, Color>>, horizontal: Boolean) {
        val paint = Paint().apply {
            shader = LinearGradient(
                0f,
                0f,
                if (horizontal) width.toFloat() else 0f,
                if (horizontal) 0f else height.toFloat(),
                IntArray(stops.size) { stops[it].second.toArgb() },
                FloatArray(stops.size) { stops[it].first },
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
    layer(
        listOf(
            0f to background.copy(alpha = 0.96f),
            0.48f to background.copy(alpha = 0.58f),
            0.76f to Color.Transparent,
        ),
        horizontal = true,
    )
    layer(listOf(0f to Color.Transparent, 1f to background.copy(alpha = 0.42f)), horizontal = false)
    layer(listOf(0f to primary.copy(alpha = 0.10f), 0.42f to Color.Transparent), horizontal = true)
    val scrim = bitmap.asImageBitmap()
    val destination = IntSize(size.width.roundToInt(), size.height.roundToInt())
    onDrawBehind {
        drawImage(scrim, dstSize = destination, filterQuality = FilterQuality.Low)
    }
}
