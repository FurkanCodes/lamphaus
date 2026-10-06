package com.lamphaus.core.player

/**
 * Downmix to a smaller speaker layout (PLY-AUD-01), with Android's own
 * constant-power levels so it sounds like the platform downmix: matching
 * speakers at unity, a missing centre or surround folded in at -3 dB, LFE at
 * -6 dB when the layout has none. Media3 ships defaults only up to 5.1, so
 * 6.1 and 7.1 (TrueHD, DTS-HD) are covered here too.
 *
 * Without [keepVolume] the matrix is scaled so no output channel can sum
 * past full scale: nothing clips, but the mix gets quieter (Nuvio's
 * "Maintain original audio on downmix" off).
 */
object Downmix {
    /** Output layouts offered: stereo, quad, 5.1 (Android channel order). */
    val targetChannelCounts = listOf(2, 4, 6)

    /** Input layouts this mixer covers (Android/FFmpeg default channel order). */
    val supportedInputChannelCounts = 1..8

    /** Row-major `[input][output]` gains folding [inputChannels] into [outputChannels]. */
    fun coefficients(inputChannels: Int, outputChannels: Int, keepVolume: Boolean = true): FloatArray {
        val inputs = inputLayout(inputChannels)
        val outputs = outputLayout(outputChannels)
        val matrix = Array(inputs.size) { FloatArray(outputs.size) }
        inputs.forEachIndexed { row, speaker ->
            gains(speaker, outputs).forEach { (target, gain) -> matrix[row][outputs.indexOf(target)] += gain }
        }
        if (!keepVolume) {
            val loudest = outputs.indices.maxOf { column -> matrix.sumOf { it[column].toDouble() } }.toFloat()
            if (loudest > 1f) matrix.forEach { row -> row.indices.forEach { row[it] /= loudest } }
        }
        return matrix.flatMap { it.asList() }.toFloatArray()
    }

    private enum class Speaker { FL, FR, FC, LFE, BL, BR, BC, SL, SR }

    private const val HALF_POWER = 0.7071f
    private const val HALF = 0.5f

    /** Where one input speaker goes in [outputs]. */
    private fun gains(speaker: Speaker, outputs: List<Speaker>): List<Pair<Speaker, Float>> {
        if (speaker in outputs) return listOf(speaker to 1f)
        val rear = Speaker.BL in outputs
        return when (speaker) {
            Speaker.FC -> listOf(Speaker.FL to HALF_POWER, Speaker.FR to HALF_POWER)
            Speaker.LFE -> listOf(Speaker.FL to HALF, Speaker.FR to HALF)
            Speaker.BL -> listOf(Speaker.FL to HALF_POWER)
            Speaker.BR -> listOf(Speaker.FR to HALF_POWER)
            Speaker.SL -> listOf((if (rear) Speaker.BL else Speaker.FL) to HALF_POWER)
            Speaker.SR -> listOf((if (rear) Speaker.BR else Speaker.FR) to HALF_POWER)
            Speaker.BC -> if (rear) {
                listOf(Speaker.BL to HALF_POWER, Speaker.BR to HALF_POWER)
            } else {
                listOf(Speaker.FL to HALF, Speaker.FR to HALF)
            }
            Speaker.FL, Speaker.FR -> emptyList()
        }
    }

    private fun inputLayout(channels: Int): List<Speaker> = when (channels) {
        1 -> listOf(Speaker.FC)
        2 -> listOf(Speaker.FL, Speaker.FR)
        3 -> listOf(Speaker.FL, Speaker.FR, Speaker.FC)
        4 -> listOf(Speaker.FL, Speaker.FR, Speaker.BL, Speaker.BR)
        5 -> listOf(Speaker.FL, Speaker.FR, Speaker.FC, Speaker.BL, Speaker.BR)
        6 -> listOf(Speaker.FL, Speaker.FR, Speaker.FC, Speaker.LFE, Speaker.BL, Speaker.BR)
        7 -> listOf(Speaker.FL, Speaker.FR, Speaker.FC, Speaker.LFE, Speaker.BC, Speaker.SL, Speaker.SR)
        8 -> listOf(Speaker.FL, Speaker.FR, Speaker.FC, Speaker.LFE, Speaker.BL, Speaker.BR, Speaker.SL, Speaker.SR)
        else -> throw IllegalArgumentException("No downmix for $channels channels")
    }

    private fun outputLayout(channels: Int): List<Speaker> = when (channels) {
        2 -> listOf(Speaker.FL, Speaker.FR)
        4 -> listOf(Speaker.FL, Speaker.FR, Speaker.BL, Speaker.BR)
        6 -> listOf(Speaker.FL, Speaker.FR, Speaker.FC, Speaker.LFE, Speaker.BL, Speaker.BR)
        else -> throw IllegalArgumentException("No $channels-channel downmix layout")
    }
}
