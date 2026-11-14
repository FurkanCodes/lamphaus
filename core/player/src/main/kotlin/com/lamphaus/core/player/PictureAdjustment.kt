package com.lamphaus.core.player

import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.effect.RgbMatrix
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The player's Picture panel (PLY-PIC-01): brightness, contrast, saturation,
 * and hue on mpv's -100..100 scale, so both engines read one value. It lasts
 * for one playback and is never saved.
 */
data class PictureAdjustment(
    val brightness: Int = 0,
    val contrast: Int = 0,
    val saturation: Int = 0,
    val hue: Int = 0,
) {
    val isNeutral: Boolean get() = this == NEUTRAL

    fun toBundle(): Bundle = Bundle().apply {
        putInt(KEY_BRIGHTNESS, brightness)
        putInt(KEY_CONTRAST, contrast)
        putInt(KEY_SATURATION, saturation)
        putInt(KEY_HUE, hue)
    }

    companion object {
        val NEUTRAL = PictureAdjustment()
        const val MIN = -100
        const val MAX = 100
        const val STEP = 5

        /** Session command that applies a [PictureAdjustment] to the playing engine. */
        const val ACTION_SET_PICTURE = "lamphaus.playback.SET_PICTURE"

        private const val KEY_BRIGHTNESS = "picture_brightness"
        private const val KEY_CONTRAST = "picture_contrast"
        private const val KEY_SATURATION = "picture_saturation"
        private const val KEY_HUE = "picture_hue"

        fun fromBundle(bundle: Bundle): PictureAdjustment = PictureAdjustment(
            brightness = bundle.getInt(KEY_BRIGHTNESS).coerceIn(MIN, MAX),
            contrast = bundle.getInt(KEY_CONTRAST).coerceIn(MIN, MAX),
            saturation = bundle.getInt(KEY_SATURATION).coerceIn(MIN, MAX),
            hue = bundle.getInt(KEY_HUE).coerceIn(MIN, MAX),
        )

        /**
         * Whether the playing video can be adjusted. libmpv always can: it
         * renders through its GPU output. ExoPlayer needs its effect pipeline,
         * which would drop HDR and Dolby Vision passthrough and cannot run
         * tunneled, so those stay untouched.
         */
        fun isAvailable(mpv: Boolean, tunneled: Boolean, hasVideo: Boolean, videoFormat: Format?): Boolean = when {
            !hasVideo -> false
            mpv -> true
            videoFormat == null || tunneled -> false
            videoFormat.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION -> false
            videoFormat.colorInfo?.colorTransfer == C.COLOR_TRANSFER_ST2084 -> false
            videoFormat.colorInfo?.colorTransfer == C.COLOR_TRANSFER_HLG -> false
            else -> true
        }
    }
}

/**
 * The column-major 4×4 RGB matrix for [adjustment]: hue turns around the grey
 * axis, saturation scales away from Rec. 709 luma, contrast pivots on mid
 * grey, and brightness lifts every channel.
 */
fun pictureMatrix(adjustment: PictureAdjustment): FloatArray {
    val angle = adjustment.hue / 100.0 * PI
    val c = cos(angle).toFloat()
    val s = sin(angle).toFloat()
    val third = (1f - c) / 3f
    val root = s / sqrt(3f)
    val hue = arrayOf(
        floatArrayOf(c + third, third - root, third + root),
        floatArrayOf(third + root, c + third, third - root),
        floatArrayOf(third - root, third + root, c + third),
    )
    val sat = 1f + adjustment.saturation / 100f
    val luma = floatArrayOf(0.2126f, 0.7152f, 0.0722f)
    val saturation = Array(3) { row -> FloatArray(3) { col -> (1f - sat) * luma[col] + if (row == col) sat else 0f } }
    val contrast = 1f + adjustment.contrast / 100f
    val offset = 0.5f * (1f - contrast) + adjustment.brightness / 100f
    val matrix = FloatArray(16)
    for (row in 0 until 3) {
        for (col in 0 until 3) {
            var sum = 0f
            for (k in 0 until 3) sum += saturation[row][k] * hue[k][col]
            matrix[col * 4 + row] = contrast * sum
        }
        matrix[12 + row] = offset
    }
    matrix[15] = 1f
    return matrix
}

/**
 * One ExoPlayer effect for the whole panel. Media3 asks for the matrix every
 * frame, so changing [adjustment] shows on the next frame without rebuilding
 * the effect pipeline.
 */
class PictureAdjustmentEffect : RgbMatrix {
    @Volatile
    var adjustment: PictureAdjustment = PictureAdjustment.NEUTRAL

    private var cachedFor: PictureAdjustment? = null
    private var cached: FloatArray = pictureMatrix(PictureAdjustment.NEUTRAL)

    override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray {
        val current = adjustment
        if (current != cachedFor) {
            cached = pictureMatrix(current)
            cachedFor = current
        }
        return cached
    }
}
