package com.lamphaus.app.player

import android.graphics.Typeface
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.lamphaus.core.model.SubtitleEdgeStyle
import com.lamphaus.core.model.SubtitleStyle
import com.lamphaus.core.model.SubtitleStylePolicy

/**
 * Applies the profile [SubtitleStyle] to the Media3 subtitle view (plan §4).
 * Embedded ASS/SSA authoring is preserved by default; the profile style only
 * overrides when [SubtitleStyle.preserveEmbeddedStyles] is off. The same
 * style reaches the MPV engine through [com.lamphaus.core.player.mpv.MpvPlayer.applySubtitleStyle].
 */
object SubtitleStyleApplier {

    private const val BASE_TEXT_SIZE_SP = 18f

    fun apply(view: SubtitleView, style: SubtitleStyle) {
        val sizePercent = SubtitleStylePolicy.clampSizePercent(style.sizePercent)
        view.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, BASE_TEXT_SIZE_SP * sizePercent / 100f)
        view.setBottomPaddingFraction(1f - SubtitleStylePolicy.clampPositionFraction(style.verticalPositionFraction))
        view.setApplyEmbeddedStyles(style.preserveEmbeddedStyles)
        view.setApplyEmbeddedFontSizes(style.preserveEmbeddedStyles)
        if (style.preserveEmbeddedStyles) {
            view.setUserDefaultStyle()
            return
        }
        val foreground = withAlpha(style.textColor, SubtitleStylePolicy.clampOpacity(style.textOpacity))
        val background = withAlpha(style.backgroundColor, SubtitleStylePolicy.clampOpacity(style.backgroundOpacity))
        val edgeColor = withAlpha(style.outlineColor, 1f)
        view.setStyle(
            CaptionStyleCompat(
                foreground,
                background,
                /* windowColor = */ background,
                edgeType(style.edgeStyle, style.outlineEnabled),
                edgeColor,
                if (style.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT,
            ),
        )
    }

    private fun edgeType(edgeStyle: SubtitleEdgeStyle, outlineEnabled: Boolean): Int = when {
        !outlineEnabled && edgeStyle == SubtitleEdgeStyle.NONE -> CaptionStyleCompat.EDGE_TYPE_NONE
        edgeStyle == SubtitleEdgeStyle.DROP_SHADOW -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
        edgeStyle == SubtitleEdgeStyle.RAISED -> CaptionStyleCompat.EDGE_TYPE_RAISED
        edgeStyle == SubtitleEdgeStyle.DEPRESSED -> CaptionStyleCompat.EDGE_TYPE_DEPRESSED
        outlineEnabled -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
        else -> CaptionStyleCompat.EDGE_TYPE_NONE
    }

    /** Multiplies the stored alpha by the editor opacity; ARGB in, ARGB out. */
    private fun withAlpha(argb: Long, opacity: Float): Int {
        val alpha = ((argb ushr 24).toInt() * opacity).toInt().coerceIn(0, 255)
        return (alpha shl 24) or (argb and 0x00FFFFFF).toInt()
    }
}
