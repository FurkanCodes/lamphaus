package com.lamphaus.core.player.dolbyvision

/**
 * Writes Dolby Vision RPUs field by field from the bitstream syntax (as
 * libdovi reads it), independently of [DolbyVisionRpu], so the converter's
 * output can be compared against an RPU written directly in its target form.
 */
internal object TestRpus {

    enum class Layer { FEL, MEL, NONE }

    /**
     * A UHD Blu-ray style RPU. [layer] FEL or MEL makes a profile 7 RPU;
     * NONE writes the profile 8.1 form. [identityMapping] writes libdovi's
     * empty 8.1 mapping in place of the source curves.
     */
    fun rpu(
        layer: Layer,
        identityMapping: Boolean = false,
        profile81Coefficients: Boolean = layer == Layer.NONE,
        usePrevious: Boolean = false,
        cmV40: Boolean = true,
    ): ByteArray {
        val w = RpuBitWriter()
        val residual = layer != Layer.NONE
        w.bits(8, 0x19)
        // rpu_data_header
        w.bits(6, 2) // rpu_type
        w.bits(11, 18) // rpu_format
        w.bits(4, 1) // vdr_rpu_profile
        w.bits(4, 0) // vdr_rpu_level
        w.bit(true) // vdr_seq_info_present_flag
        w.bit(false) // chroma_resampling_explicit_filter_flag
        w.bits(2, 0) // coefficient_data_type
        w.ue(23) // coefficient_log2_denom
        w.bits(2, 1) // vdr_rpu_normalized_idc
        w.bit(false) // bl_video_full_range_flag
        w.ue(2) // bl_bit_depth_minus8
        w.ue(2) // el_bit_depth_minus8
        w.ue(4) // vdr_bit_depth_minus8
        w.bit(false) // spatial_resampling_filter_flag
        w.bits(3, 0) // reserved_zero_3bits
        w.bit(residual) // el_spatial_resampling_filter_flag
        w.bit(!residual) // disable_residual_flag
        w.bit(true) // vdr_dm_metadata_present_flag
        w.bit(usePrevious) // use_prev_vdr_rpu_flag
        if (usePrevious) w.ue(0) // prev_vdr_rpu_id
        if (!usePrevious) {
            if (identityMapping) identityMapping(w) else sourceMapping(w, layer)
        }
        displayManagement(w, profile81Coefficients, cmV40)
        w.byteAlign() // rpu_alignment_zero_bit
        val body = w.toByteArray()
        val crc = Crc32Mpeg2.compute(body, 1, body.size)
        return body + byteArrayOf((crc ushr 24).toByte(), (crc ushr 16).toByte(), (crc ushr 8).toByte(), crc.toByte()) +
            byteArrayOf(0x80.toByte())
    }

    private fun sourceMapping(w: RpuBitWriter, layer: Layer) {
        w.ue(0) // vdr_rpu_id
        w.ue(0) // mapping_color_space
        w.ue(0) // mapping_chroma_format_idc
        // Luma: three pivots (two pieces); chroma: two pivots (one piece each).
        w.ue(1)
        intArrayOf(63, 512, 940).forEach { w.bits(10, it.toLong()) }
        repeat(2) {
            w.ue(0)
            intArrayOf(64, 960).forEach { w.bits(10, it.toLong()) }
        }
        if (layer != Layer.NONE) {
            w.bits(3, 0) // nlq_method_idc
            w.bits(10, 0) // nlq_pred_pivot_value
            w.bits(10, 1023)
        }
        w.ue(if (layer != Layer.NONE) 1 else 0) // num_x_partitions_minus1
        w.ue(0) // num_y_partitions_minus1
        // Luma pieces: order 2, then order 1.
        w.ue(0) // mapping_idc polynomial
        w.ue(1) // poly_order_minus1
        longArrayOf(-3, 1, 0).zip(longArrayOf(4_000_000, 7_654_321, 1_234)).forEach { (int, frac) ->
            w.se(int)
            w.bits(23, frac)
        }
        w.ue(0)
        w.ue(0)
        w.bit(false) // linear_interp_flag
        longArrayOf(2, 0).zip(longArrayOf(0x7FFFFF, 0)).forEach { (int, frac) ->
            w.se(int)
            w.bits(23, frac)
        }
        // Chroma pieces: MMR order 3 and order 1.
        listOf(2, 0).forEach { orderMinus1 ->
            w.ue(1) // mapping_idc MMR
            w.bits(2, orderMinus1.toLong())
            w.se(-1)
            w.bits(23, 3_000_000)
            repeat((orderMinus1 + 1) * 7) { k ->
                w.se((k % 5 - 2).toLong())
                w.bits(23, (k * 104_729L) and 0x7FFFFF)
            }
        }
        if (layer != Layer.NONE) {
            repeat(3) { component ->
                val full = layer == Layer.FEL
                w.bits(10, if (full) 512L + component else 0) // nlq_offset
                w.ue(if (full) 3 else 1) // vdr_in_max_int
                w.bits(23, if (full) 1_000_000L else 0) // vdr_in_max
                w.ue(0) // linear_deadzone_slope_int
                w.bits(23, if (full) 2_000_000L else 0) // linear_deadzone_slope
                w.ue(0) // linear_deadzone_threshold_int
                w.bits(23, if (full) 4_096L else 0) // linear_deadzone_threshold
            }
        }
    }

    /** libdovi `set_empty_p81_mapping`, with no residual and one partition. */
    private fun identityMapping(w: RpuBitWriter) {
        w.ue(0)
        w.ue(0)
        w.ue(0)
        repeat(3) {
            w.ue(0)
            w.bits(10, 0)
            w.bits(10, 1023)
        }
        w.ue(0)
        w.ue(0)
        repeat(3) {
            w.ue(0) // polynomial
            w.ue(0) // order 1
            w.bit(false)
            w.se(0)
            w.bits(23, 0)
            w.se(1)
            w.bits(23, 0)
        }
    }

    private val SOURCE_COEFFICIENTS = longArrayOf(
        8192, 0, 12900, 8192, -1534, -3836, 8192, 15201, 0,
        0, 268435456, 268435456,
        5845, 9702, 837, 2568, 12256, 1561, 0, 679, 15705,
    )
    private val PROFILE_81_COEFFICIENTS = longArrayOf(
        9574, 0, 13802, 9574, -1540, -5348, 9574, 17610, 0,
        16777216, 134217728, 134217728,
        7222, 8771, 390, 2654, 12430, 1300, 0, 422, 15962,
    )

    private fun displayManagement(w: RpuBitWriter, profile81: Boolean, cmV40: Boolean) {
        w.ue(0) // affected_dm_metadata_id
        w.ue(0) // current_dm_metadata_id
        w.ue(1) // scene_refresh_flag
        val coefficients = if (profile81) PROFILE_81_COEFFICIENTS else SOURCE_COEFFICIENTS
        coefficients.forEachIndexed { index, value -> w.bits(if (index in 9..11) 32 else 16, value) }
        w.bits(16, 65535) // signal_eotf
        w.bits(16, 0)
        w.bits(16, 0)
        w.bits(32, 0)
        w.bits(5, 12) // signal_bit_depth
        w.bits(2, if (profile81) 0 else 2) // signal_color_space
        w.bits(2, 0) // signal_chroma_format
        w.bits(2, 1) // signal_full_range_flag
        w.bits(12, 62) // source_min_pq
        w.bits(12, 3696) // source_max_pq
        w.bits(10, 42) // source_diagonal
        // CM v2.9: L1, L5, L6 and an unknown level, each written as raw bytes.
        extensionBlocks(w, listOf(1 to 5, 5 to 7, 6 to 8, 200 to 3))
        if (cmV40) extensionBlocks(w, listOf(3 to 2, 254 to 2))
    }

    private fun extensionBlocks(w: RpuBitWriter, blocks: List<Pair<Int, Int>>) {
        w.ue(blocks.size.toLong())
        w.byteAlign() // dm_alignment_zero_bit
        blocks.forEach { (level, length) ->
            w.ue(length.toLong())
            w.bits(8, level.toLong())
            // Payload bytes chosen to include emulation-prone zero runs.
            repeat(length) { w.bits(8, if (it % 3 == 0) 0 else (level + it).toLong() and 0xFF) }
        }
    }
}
