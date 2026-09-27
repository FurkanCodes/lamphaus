package com.lamphaus.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.lamphaus.app.R

/**
 * Lamphaus profile avatars: drawn artwork keyed by [com.lamphaus.core.model.Profile.avatarKey].
 * Drawn rather than bundled so each stays sharp at any size and needs no
 * per-density assets. Colors live here, in one table, rather than in callers.
 */
enum class ProfileAvatar(
    val key: String,
    @StringRes val labelRes: Int,
    internal val top: Color,
    internal val bottom: Color,
    internal val ink: Color,
    internal val accent: Color,
    internal val motif: DrawScope.(ProfileAvatar) -> Unit,
) {
    MOON("moon", R.string.avatar_moon, Color(0xFF1B2B4B), Color(0xFF3A5A8C), Color(0xFFE8EEF8), Color(0xFFA8C8FF), { drawMoon(it) }),
    SPROUT("sprout", R.string.avatar_sprout, Color(0xFF1C3A2D), Color(0xFF3F7A5A), Color(0xFFDDF3E4), Color(0xFF9FE0B4), { drawSprout(it) }),
    COMET("comet", R.string.avatar_comet, Color(0xFF261D40), Color(0xFF5B4A8F), Color(0xFFF1E9FF), Color(0xFFC7B6FF), { drawComet(it) }),
    ORBIT("orbit", R.string.avatar_orbit, Color(0xFF12303A), Color(0xFF2F6E7D), Color(0xFFDDF5F8), Color(0xFF8FD6E3), { drawOrbit(it) }),
    AURORA("aurora", R.string.avatar_aurora, Color(0xFF0F2530), Color(0xFF1F5C5A), Color(0xFF8FE6C9), Color(0xFFA8C0FF), { drawAurora(it) }),
    SUMMIT("summit", R.string.avatar_summit, Color(0xFF29283C), Color(0xFF6A5C7E), Color(0xFFEDE7F6), Color(0xFFFFC9A8), { drawSummit(it) }),
    LANTERN("lantern", R.string.avatar_lantern, Color(0xFF2B2216), Color(0xFF7A5A2E), Color(0xFFFFE3B0), Color(0xFFFFC56B), { drawLantern(it) }),
    TIDE("tide", R.string.avatar_tide, Color(0xFF0E2842), Color(0xFF25618F), Color(0xFFD8ECFF), Color(0xFF8CC4F5), { drawTide(it) }),
    PRISM("prism", R.string.avatar_prism, Color(0xFF26213A), Color(0xFF4F4470), Color(0xFFF5F0FF), Color(0xFFFFB4C8), { drawPrism(it) }),
    EMBER("ember", R.string.avatar_ember, Color(0xFF361B18), Color(0xFF8A3B2C), Color(0xFFFFD2B8), Color(0xFFFFA36B), { drawEmber(it) }),
    ;

    companion object {
        /** Shows the signed-in account's own photo, when the account has one. */
        const val ACCOUNT_PHOTO_KEY = "account"

        /** Unknown or legacy keys still map to a stable design instead of a letter. */
        fun forKey(key: String): ProfileAvatar =
            entries.firstOrNull { it.key == key } ?: entries[Math.floorMod(key.hashCode(), entries.size)]
    }
}

/**
 * The signed-in account's photo (for example the Google account picture).
 * Only [ProfileAvatar.ACCOUNT_PHOTO_KEY] profiles read it.
 */
val LocalAccountPhotoUrl = staticCompositionLocalOf<String?> { null }

/**
 * Draws a profile's avatar filling its bounds. Callers clip it to their own
 * shape and draw their own focus treatment.
 */
@Composable
fun ProfileAvatarArt(avatarKey: String, modifier: Modifier = Modifier) {
    val photoUrl = LocalAccountPhotoUrl.current
    if (avatarKey == ProfileAvatar.ACCOUNT_PHOTO_KEY && photoUrl != null) {
        // A failed photo load falls back to drawn art rather than an empty tile.
        var failed by remember(photoUrl) { mutableStateOf(false) }
        Box(modifier) {
            if (failed) {
                ProfileAvatarCanvas(ProfileAvatar.MOON, Modifier.fillMaxSize())
            } else {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onError = { failed = true },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    } else {
        ProfileAvatarCanvas(ProfileAvatar.forKey(avatarKey), modifier)
    }
}

@Composable
private fun ProfileAvatarCanvas(avatar: ProfileAvatar, modifier: Modifier) {
    Canvas(modifier) {
        drawRect(Brush.linearGradient(listOf(avatar.top, avatar.bottom), start = Offset.Zero, end = Offset(size.width, size.height)))
        avatar.motif(this, avatar)
    }
}

// Motifs are drawn on a unit square scaled to the avatar, so every design keeps
// its proportions at 32dp in the navigation and 72dp in the switcher.

private fun DrawScope.u(x: Float, y: Float) = Offset(size.width * x, size.height * y)

private val DrawScope.unit get() = minOf(size.width, size.height)

private fun DrawScope.drawMoon(a: ProfileAvatar) {
    val center = u(0.5f, 0.5f)
    // A true crescent: the bite is subtracted, so the gradient shows through.
    val disc = Path().apply { addOval(Rect(center, unit * 0.26f)) }
    val bite = Path().apply { addOval(Rect(u(0.62f, 0.4f), unit * 0.23f)) }
    drawPath(Path().apply { op(disc, bite, PathOperation.Difference) }, a.ink)
    drawCircle(a.accent, radius = unit * 0.035f, center = u(0.74f, 0.7f))
    drawCircle(a.accent.copy(alpha = 0.7f), radius = unit * 0.022f, center = u(0.26f, 0.24f))
}

private fun DrawScope.drawSprout(a: ProfileAvatar) {
    val stroke = unit * 0.06f
    drawLine(a.ink, u(0.5f, 0.82f), u(0.5f, 0.46f), strokeWidth = stroke, cap = StrokeCap.Round)
    val left = Path().apply {
        moveTo(u(0.5f, 0.52f).x, u(0.5f, 0.52f).y)
        cubicTo(u(0.22f, 0.5f).x, u(0.22f, 0.5f).y, u(0.2f, 0.28f).x, u(0.2f, 0.28f).y, u(0.24f, 0.24f).x, u(0.24f, 0.24f).y)
        cubicTo(u(0.44f, 0.26f).x, u(0.44f, 0.26f).y, u(0.52f, 0.38f).x, u(0.52f, 0.38f).y, u(0.5f, 0.52f).x, u(0.5f, 0.52f).y)
        close()
    }
    val right = Path().apply {
        moveTo(u(0.5f, 0.46f).x, u(0.5f, 0.46f).y)
        cubicTo(u(0.76f, 0.44f).x, u(0.76f, 0.44f).y, u(0.8f, 0.22f).x, u(0.8f, 0.22f).y, u(0.76f, 0.18f).x, u(0.76f, 0.18f).y)
        cubicTo(u(0.56f, 0.2f).x, u(0.56f, 0.2f).y, u(0.48f, 0.32f).x, u(0.48f, 0.32f).y, u(0.5f, 0.46f).x, u(0.5f, 0.46f).y)
        close()
    }
    drawPath(left, a.accent)
    drawPath(right, a.ink)
}

private fun DrawScope.drawComet(a: ProfileAvatar) {
    val head = u(0.66f, 0.34f)
    listOf(0.0f to 0.9f, 0.09f to 0.55f, -0.09f to 0.55f).forEach { (offset, alpha) ->
        drawLine(
            brush = Brush.linearGradient(listOf(a.accent.copy(alpha = 0f), a.accent.copy(alpha = alpha)), u(0.18f + offset, 0.82f - offset), head),
            start = u(0.18f + offset, 0.82f - offset),
            end = head,
            strokeWidth = unit * 0.06f,
            cap = StrokeCap.Round,
        )
    }
    drawCircle(a.ink, radius = unit * 0.13f, center = head)
    drawCircle(a.ink.copy(alpha = 0.6f), radius = unit * 0.025f, center = u(0.28f, 0.26f))
}

private fun DrawScope.drawOrbit(a: ProfileAvatar) {
    val center = u(0.5f, 0.52f)
    drawCircle(a.ink, radius = unit * 0.2f, center = center)
    rotate(-22f, center) {
        drawOval(
            color = a.accent,
            topLeft = Offset(center.x - unit * 0.38f, center.y - unit * 0.1f),
            size = Size(unit * 0.76f, unit * 0.2f),
            style = Stroke(width = unit * 0.05f),
        )
    }
    // Re-draw the planet's upper half so the ring passes behind it.
    drawArc(a.ink, startAngle = 180f + 18f, sweepAngle = 144f, useCenter = true,
        topLeft = Offset(center.x - unit * 0.2f, center.y - unit * 0.2f), size = Size(unit * 0.4f, unit * 0.4f))
    drawCircle(a.accent, radius = unit * 0.03f, center = u(0.2f, 0.22f))
}

private fun DrawScope.drawAurora(a: ProfileAvatar) {
    listOf(Triple(0.34f, a.ink, 0.95f), Triple(0.5f, a.accent, 0.8f), Triple(0.66f, a.ink, 0.55f)).forEach { (y, color, alpha) ->
        val path = Path().apply {
            moveTo(0f, size.height * (y + 0.06f))
            cubicTo(size.width * 0.3f, size.height * (y - 0.12f), size.width * 0.6f, size.height * (y + 0.16f), size.width, size.height * (y - 0.04f))
        }
        drawPath(path, color.copy(alpha = alpha), style = Stroke(width = unit * 0.07f, cap = StrokeCap.Round))
    }
    drawCircle(a.ink, radius = unit * 0.025f, center = u(0.78f, 0.18f))
}

private fun DrawScope.drawSummit(a: ProfileAvatar) {
    drawCircle(a.accent, radius = unit * 0.12f, center = u(0.68f, 0.32f))
    val back = Path().apply {
        moveTo(u(0.36f, 0.86f).x, u(0.36f, 0.86f).y)
        lineTo(u(0.66f, 0.46f).x, u(0.66f, 0.46f).y)
        lineTo(u(0.96f, 0.86f).x, u(0.96f, 0.86f).y)
        close()
    }
    val front = Path().apply {
        moveTo(u(0.04f, 0.86f).x, u(0.04f, 0.86f).y)
        lineTo(u(0.38f, 0.36f).x, u(0.38f, 0.36f).y)
        lineTo(u(0.72f, 0.86f).x, u(0.72f, 0.86f).y)
        close()
    }
    drawPath(back, a.ink.copy(alpha = 0.55f))
    drawPath(front, a.ink)
    val snow = Path().apply {
        moveTo(u(0.38f, 0.36f).x, u(0.38f, 0.36f).y)
        lineTo(u(0.3f, 0.48f).x, u(0.3f, 0.48f).y)
        lineTo(u(0.46f, 0.48f).x, u(0.46f, 0.48f).y)
        close()
    }
    drawPath(snow, a.bottom.copy(alpha = 0.5f))
}

private fun DrawScope.drawLantern(a: ProfileAvatar) {
    drawCircle(a.accent.copy(alpha = 0.28f), radius = unit * 0.3f, center = u(0.5f, 0.56f))
    drawLine(a.ink, u(0.5f, 0.14f), u(0.5f, 0.26f), strokeWidth = unit * 0.04f, cap = StrokeCap.Round)
    drawRoundRect(a.ink, topLeft = u(0.36f, 0.26f), size = Size(unit * 0.28f, unit * 0.06f), cornerRadius = CornerRadius(unit * 0.02f))
    drawRoundRect(a.accent, topLeft = u(0.37f, 0.33f), size = Size(unit * 0.26f, unit * 0.34f), cornerRadius = CornerRadius(unit * 0.08f))
    drawRoundRect(a.ink, topLeft = u(0.36f, 0.68f), size = Size(unit * 0.28f, unit * 0.06f), cornerRadius = CornerRadius(unit * 0.02f))
    drawCircle(a.ink, radius = unit * 0.05f, center = u(0.5f, 0.5f))
}

private fun DrawScope.drawTide(a: ProfileAvatar) {
    listOf(0.4f to 1f, 0.56f to 0.75f, 0.72f to 0.5f).forEach { (y, alpha) ->
        val path = Path().apply {
            moveTo(size.width * 0.08f, size.height * y)
            var x = 0.08f
            while (x < 0.92f) {
                quadraticTo(size.width * (x + 0.105f), size.height * (y - 0.1f), size.width * (x + 0.21f), size.height * y)
                x += 0.21f
            }
        }
        drawPath(path, (if (y == 0.4f) a.ink else a.accent).copy(alpha = alpha), style = Stroke(width = unit * 0.06f, cap = StrokeCap.Round))
    }
    drawCircle(a.ink, radius = unit * 0.06f, center = u(0.74f, 0.2f))
}

private fun DrawScope.drawPrism(a: ProfileAvatar) {
    listOf(Color(0xFFFFB4C8), Color(0xFFFFE3A0), Color(0xFFA8E6C0), Color(0xFFA8C8FF)).forEachIndexed { index, color ->
        drawLine(color, u(0.56f, 0.5f), u(0.94f, 0.4f + index * 0.08f), strokeWidth = unit * 0.045f, cap = StrokeCap.Round)
    }
    drawLine(a.ink.copy(alpha = 0.7f), u(0.06f, 0.6f), u(0.4f, 0.5f), strokeWidth = unit * 0.04f, cap = StrokeCap.Round)
    val prism = Path().apply {
        moveTo(u(0.5f, 0.22f).x, u(0.5f, 0.22f).y)
        lineTo(u(0.74f, 0.74f).x, u(0.74f, 0.74f).y)
        lineTo(u(0.26f, 0.74f).x, u(0.26f, 0.74f).y)
        close()
    }
    drawPath(prism, a.ink.copy(alpha = 0.92f))
    drawPath(prism, a.bottom, style = Stroke(width = unit * 0.02f))
}

private fun DrawScope.drawEmber(a: ProfileAvatar) {
    val outer = Path().apply {
        moveTo(u(0.5f, 0.14f).x, u(0.5f, 0.14f).y)
        cubicTo(u(0.78f, 0.4f).x, u(0.78f, 0.4f).y, u(0.8f, 0.62f).x, u(0.8f, 0.62f).y, u(0.66f, 0.78f).x, u(0.66f, 0.78f).y)
        cubicTo(u(0.56f, 0.88f).x, u(0.56f, 0.88f).y, u(0.44f, 0.88f).x, u(0.44f, 0.88f).y, u(0.34f, 0.78f).x, u(0.34f, 0.78f).y)
        cubicTo(u(0.2f, 0.62f).x, u(0.2f, 0.62f).y, u(0.26f, 0.44f).x, u(0.26f, 0.44f).y, u(0.38f, 0.34f).x, u(0.38f, 0.34f).y)
        cubicTo(u(0.4f, 0.44f).x, u(0.4f, 0.44f).y, u(0.46f, 0.46f).x, u(0.46f, 0.46f).y, u(0.5f, 0.14f).x, u(0.5f, 0.14f).y)
        close()
    }
    val inner = Path().apply {
        moveTo(u(0.5f, 0.46f).x, u(0.5f, 0.46f).y)
        cubicTo(u(0.64f, 0.58f).x, u(0.64f, 0.58f).y, u(0.62f, 0.8f).x, u(0.62f, 0.8f).y, u(0.5f, 0.8f).x, u(0.5f, 0.8f).y)
        cubicTo(u(0.38f, 0.8f).x, u(0.38f, 0.8f).y, u(0.38f, 0.62f).x, u(0.38f, 0.62f).y, u(0.5f, 0.46f).x, u(0.5f, 0.46f).y)
        close()
    }
    drawPath(outer, a.accent)
    drawPath(inner, a.ink)
}
