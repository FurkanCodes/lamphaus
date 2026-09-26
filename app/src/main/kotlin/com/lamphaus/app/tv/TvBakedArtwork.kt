package com.lamphaus.app.tv

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import kotlin.math.roundToInt

/**
 * One linear scrim, in the same terms as `Brush.horizontalGradient` /
 * `Brush.verticalGradient` colour stops.
 */
internal class ScrimLayer(
    val stops: Array<Pair<Float, Color>>,
    val horizontal: Boolean,
)

/**
 * Draws [layer] over the whole canvas with the same platform gradient the
 * Compose brushes use, so baked scrims match the live ones.
 */
internal fun Canvas.drawScrim(layer: ScrimLayer, width: Int, height: Int) {
    val paint = Paint().apply {
        shader = LinearGradient(
            0f,
            0f,
            if (layer.horizontal) width.toFloat() else 0f,
            if (layer.horizontal) 0f else height.toFloat(),
            IntArray(layer.stops.size) { layer.stops[it].second.toArgb() },
            FloatArray(layer.stops.size) { layer.stops[it].first },
            Shader.TileMode.CLAMP,
        )
    }
    drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
}

/**
 * QA-08: bakes a full-screen artwork stack (background, artwork
 * centre-cropped at [imageAlpha], then [scrims] in order) into one opaque
 * bitmap, off the main thread. On TV GPUs such as the Mali-G31 each
 * full-screen blended layer costs milliseconds per frame, so one opaque pass
 * replaces three or four blended ones.
 */
internal fun bakeArtwork(
    artwork: Bitmap?,
    width: Int,
    height: Int,
    background: Color,
    imageAlpha: Float,
    scrims: List<ScrimLayer>,
    placeholder: Drawable? = null,
): Bitmap {
    val out = createBitmap(width, height)
    val canvas = Canvas(out)
    canvas.drawColor(background.toArgb())
    if (artwork != null && artwork.width > 0 && artwork.height > 0) {
        val scale = maxOf(width / artwork.width.toFloat(), height / artwork.height.toFloat())
        val drawnWidth = artwork.width * scale
        val drawnHeight = artwork.height * scale
        val left = (width - drawnWidth) / 2f
        val top = (height - drawnHeight) / 2f
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = (imageAlpha * 255).roundToInt() }
        canvas.drawBitmap(artwork, null, RectF(left, top, left + drawnWidth, top + drawnHeight), paint)
    } else if (placeholder != null) {
        // MediaArtwork's missing-art mark: fitted into a centred box 28% of
        // each dimension, so a square mark is 28% of the shorter side.
        val side = (minOf(width, height) * 0.28f).roundToInt()
        val left = (width - side) / 2
        val top = (height - side) / 2
        placeholder.setBounds(left, top, left + side, top + side)
        placeholder.draw(canvas)
    }
    scrims.forEach { canvas.drawScrim(it, width, height) }
    // Fully opaque: lets the GPU skip blending when it is drawn.
    out.setHasAlpha(false)
    return out
}
