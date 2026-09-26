package com.lamphaus.core.player

/**
 * Stereo fold-down for the Downmix = Stereo setting, using Android's own
 * constant-power levels so it sounds like the platform downmix: fronts at
 * unity, centre and surrounds at -3 dB, LFE at -6 dB. Media3 ships defaults
 * only up to 5.1, so 6.1 and 7.1 (TrueHD, DTS-HD) are covered here too.
 */
object StereoDownmix {
    /** Input layouts this mixer covers (Android/FFmpeg default channel order). */
    val supportedChannelCounts = 1..8

    /** Row-major `[input][left, right]` gains for [inputChannels]. */
    fun coefficients(inputChannels: Int): FloatArray = layout(inputChannels)
        .flatMap { channel -> listOf(channel.left, channel.right) }
        .toFloatArray()

    private data class Gain(val left: Float, val right: Float)

    private const val HALF_POWER = 0.7071f
    private val FRONT_LEFT = Gain(1f, 0f)
    private val FRONT_RIGHT = Gain(0f, 1f)
    private val CENTER = Gain(HALF_POWER, HALF_POWER)
    private val LFE = Gain(0.5f, 0.5f)
    private val LEFT_SURROUND = Gain(HALF_POWER, 0f)
    private val RIGHT_SURROUND = Gain(0f, HALF_POWER)
    private val BACK_CENTER = Gain(0.5f, 0.5f)

    private fun layout(channels: Int): List<Gain> = when (channels) {
        1 -> listOf(CENTER)
        2 -> listOf(FRONT_LEFT, FRONT_RIGHT)
        3 -> listOf(FRONT_LEFT, FRONT_RIGHT, CENTER)
        4 -> listOf(FRONT_LEFT, FRONT_RIGHT, LEFT_SURROUND, RIGHT_SURROUND)
        5 -> listOf(FRONT_LEFT, FRONT_RIGHT, CENTER, LEFT_SURROUND, RIGHT_SURROUND)
        6 -> listOf(FRONT_LEFT, FRONT_RIGHT, CENTER, LFE, LEFT_SURROUND, RIGHT_SURROUND)
        7 -> listOf(FRONT_LEFT, FRONT_RIGHT, CENTER, LFE, BACK_CENTER, LEFT_SURROUND, RIGHT_SURROUND)
        8 -> listOf(FRONT_LEFT, FRONT_RIGHT, CENTER, LFE, LEFT_SURROUND, RIGHT_SURROUND, LEFT_SURROUND, RIGHT_SURROUND)
        else -> throw IllegalArgumentException("No stereo downmix for $channels channels")
    }
}
