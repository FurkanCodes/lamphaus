package com.lamphaus.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The outline stroke, in 24-unit icon space. Heroicons draws 1.5; a
 * television draws 2 so outlines stay legible from across the room
 * (TV-FND-01). Set once at process start, before the first icon is drawn.
 */
internal object HeroIconWeight {
    const val PHONE = 1.5f
    const val TELEVISION = 2f

    @Volatile
    var stroke: Float = PHONE
        private set

    fun useTelevision() {
        stroke = TELEVISION
    }
}

internal class HeroFill(val pathData: String, val evenOdd: Boolean)

// Icon and Image tint the whole vector, so the drawn colour only needs to be opaque.
private val Ink = SolidColor(Color.Black)

private fun heroBuilder(name: String, autoMirror: Boolean) = ImageVector.Builder(
    name = "Lamphaus.$name",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
    autoMirror = autoMirror,
)

internal fun heroOutline(name: String, autoMirror: Boolean, vararg paths: String): ImageVector =
    heroBuilder(name, autoMirror).apply {
        paths.forEach { path ->
            addPath(
                pathData = addPathNodes(path),
                stroke = Ink,
                strokeLineWidth = HeroIconWeight.stroke,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

internal fun heroSolid(name: String, vararg paths: HeroFill): ImageVector =
    heroBuilder(name, autoMirror = false).apply {
        paths.forEach { path ->
            addPath(
                pathData = addPathNodes(path.pathData),
                pathFillType = if (path.evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
                fill = Ink,
            )
        }
    }.build()
