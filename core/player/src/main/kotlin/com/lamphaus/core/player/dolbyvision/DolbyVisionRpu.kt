package com.lamphaus.core.player.dolbyvision

/**
 * A Dolby Vision RPU (reference processing unit), the per-frame metadata a
 * Dolby Vision decoder reads from HEVC NAL units of type 62.
 *
 * Ported from libdovi (dovi_tool's `dolby_vision` crate, MIT; see the
 * player NOTICE). Only what profile 7 → 8.1 conversion rewrites is modelled
 * field by field: the header, the reshaping mapping, and the fixed colour
 * coefficients of the display-management data. The display-management
 * extension blocks (L1…L255) are length-prefixed and are carried over bit for
 * bit, so unknown or future levels survive untouched.
 */
internal class DolbyVisionRpu private constructor(
    private val source: ByteArray,
    val header: RpuHeader,
    val mapping: RpuMapping?,
    private val displayManagement: RpuDisplayManagement?,
    private val remaining: BitRun?,
) {
    val profile: Int get() = header.profile

    /**
     * libdovi conversion mode 2 ("to 8.1") for a profile 7 RPU: drops the
     * enhancement-layer residual, and for a full enhancement layer (FEL) also
     * resets the mapping to identity, because the base layer alone is already
     * the HDR10 picture. Returns false, changing nothing, for other profiles.
     */
    fun convertToProfile81(): Boolean {
        if (profile != 7) return false
        val fullEnhancementLayer = mapping?.isFullEnhancementLayer == true
        header.elSpatialResamplingFilter = false
        header.disableResidual = true
        mapping?.apply {
            nlqPredPivots = null
            nlq = null
            numXPartitionsMinus1 = 0
            numYPartitionsMinus1 = 0
            if (fullEnhancementLayer) setIdentity()
        }
        displayManagement?.setProfile81Coefficients()
        return true
    }

    /** The unescaped RPU payload: the 0x19 prefix through the CRC and the 0x80 end byte. */
    fun write(): ByteArray {
        val writer = RpuBitWriter()
        writer.bits(8, RPU_PREFIX.toLong())
        header.write(writer)
        if (!header.usePrevVdrRpu) mapping?.write(writer, header)
        if (header.vdrDmMetadataPresent) displayManagement?.write(writer, source)
        remaining?.copyTo(writer, source)
        writer.byteAlign()
        val body = writer.toByteArray()
        val crc = Crc32Mpeg2.compute(body, 1, body.size)
        return body.copyOf(body.size + 5).also { out ->
            out[body.size] = (crc ushr 24).toByte()
            out[body.size + 1] = (crc ushr 16).toByte()
            out[body.size + 2] = (crc ushr 8).toByte()
            out[body.size + 3] = crc.toByte()
            out[body.size + 4] = FINAL_BYTE.toByte()
        }
    }

    companion object {
        const val RPU_PREFIX = 0x19
        const val FINAL_BYTE = 0x80
        private const val CRC_AND_FINAL_BITS = 40

        // libdovi reads CM v4.0 metadata only with room for a level 254 block.
        private const val CM_V40_MIN_BITS = 56

        /** Parses an unescaped RPU payload that starts with the 0x19 prefix. */
        fun parse(payload: ByteArray, length: Int = payload.size): DolbyVisionRpu {
            var end = length
            while (end > 0 && payload[end - 1].toInt() == 0) end--
            if (end < 7) throw RpuFormatException("RPU too short")
            if (payload[0].toInt() and 0xFF != RPU_PREFIX) throw RpuFormatException("Not an RPU")
            if (payload[end - 1].toInt() and 0xFF != FINAL_BYTE) throw RpuFormatException("Invalid RPU end byte")
            val crcStart = end - 5
            val storedCrc = (payload[crcStart].toInt() and 0xFF shl 24) or
                (payload[crcStart + 1].toInt() and 0xFF shl 16) or
                (payload[crcStart + 2].toInt() and 0xFF shl 8) or
                (payload[crcStart + 3].toInt() and 0xFF)
            if (Crc32Mpeg2.compute(payload, 1, crcStart) != storedCrc) throw RpuFormatException("RPU CRC mismatch")

            val reader = RpuBitReader(payload, end)
            reader.bits(8)
            val header = RpuHeader.parse(reader)
            val mapping = if (header.usePrevVdrRpu) null else RpuMapping.parse(reader, header)
            val displayManagement = if (header.vdrDmMetadataPresent) {
                RpuDisplayManagement.parse(reader, header)
            } else {
                null
            }
            while (!reader.byteAligned) {
                if (reader.bit()) throw RpuFormatException("rpu_alignment_zero_bit != 0")
            }
            val remainingBits = reader.available - CRC_AND_FINAL_BITS
            if (remainingBits < 0) throw RpuFormatException("RPU overran its CRC")
            val remaining = if (remainingBits > 0) BitRun.read(reader, remainingBits) else null
            return DolbyVisionRpu(payload.copyOf(end), header, mapping, displayManagement, remaining)
        }

        internal fun hasCmV40(reader: RpuBitReader): Boolean = reader.available >= CM_V40_MIN_BITS
    }
}

/** Bits copied verbatim from the parsed payload. */
internal class BitRun(private val startBit: Int, private val bitCount: Int) {
    fun copyTo(writer: RpuBitWriter, source: ByteArray) {
        for (bit in startBit until startBit + bitCount) {
            writer.bit((source[bit ushr 3].toInt() ushr (7 - (bit and 7))) and 1 == 1)
        }
    }

    companion object {
        fun read(reader: RpuBitReader, bitCount: Int): BitRun {
            val run = BitRun(reader.position, bitCount)
            repeat(bitCount) { reader.bit() }
            return run
        }
    }
}

internal class RpuHeader(
    private val rpuType: Int,
    private val rpuFormat: Int,
    private val vdrRpuProfile: Int,
    private val vdrRpuLevel: Int,
    private val chromaResamplingExplicitFilter: Boolean,
    val coefficientDataType: Int,
    private val coefficientLog2Denom: Long,
    private val vdrRpuNormalizedIdc: Int,
    private val blVideoFullRange: Boolean,
    val blBitDepthMinus8: Long,
    /** el_bit_depth_minus8 with ext_mapping_idc packed above its low byte, as coded. */
    private val elBitDepthField: Long,
    private val vdrBitDepthMinus8: Long,
    private val spatialResamplingFilter: Boolean,
    val reservedZero3Bits: Int,
    var elSpatialResamplingFilter: Boolean,
    var disableResidual: Boolean,
    val vdrDmMetadataPresent: Boolean,
    val usePrevVdrRpu: Boolean,
    private val prevVdrRpuId: Long,
) {
    val elBitDepthMinus8: Long get() = elBitDepthField and 0xFF

    val coefficientBits: Int get() = if (coefficientDataType == 0) coefficientLog2Denom.toInt() else 32

    /** libdovi's profile guess: 5 is full range; 7 carries a residual at 12 bits. */
    val profile: Int
        get() = when {
            vdrRpuProfile == 0 && blVideoFullRange -> 5
            vdrRpuProfile == 1 && elSpatialResamplingFilter && !disableResidual ->
                if (vdrBitDepthMinus8 == 4L) 7 else 4
            vdrRpuProfile == 1 -> 8
            else -> 0
        }

    fun write(writer: RpuBitWriter) {
        writer.bits(6, rpuType.toLong())
        writer.bits(11, rpuFormat.toLong())
        writer.bits(4, vdrRpuProfile.toLong())
        writer.bits(4, vdrRpuLevel.toLong())
        // vdr_seq_info_present_flag: always present in a parsed RPU.
        writer.bit(true)
        writer.bit(chromaResamplingExplicitFilter)
        writer.bits(2, coefficientDataType.toLong())
        if (coefficientDataType == 0) writer.ue(coefficientLog2Denom)
        writer.bits(2, vdrRpuNormalizedIdc.toLong())
        writer.bit(blVideoFullRange)
        writer.ue(blBitDepthMinus8)
        writer.ue(elBitDepthField)
        writer.ue(vdrBitDepthMinus8)
        writer.bit(spatialResamplingFilter)
        writer.bits(3, reservedZero3Bits.toLong())
        writer.bit(elSpatialResamplingFilter)
        writer.bit(disableResidual)
        writer.bit(vdrDmMetadataPresent)
        writer.bit(usePrevVdrRpu)
        if (usePrevVdrRpu) writer.ue(prevVdrRpuId)
    }

    companion object {
        fun parse(reader: RpuBitReader): RpuHeader {
            val rpuType = reader.int(6)
            if (rpuType != 2) throw RpuFormatException("rpu_type $rpuType")
            val rpuFormat = reader.int(11)
            val vdrRpuProfile = reader.int(4)
            val vdrRpuLevel = reader.int(4)
            // libdovi requires the sequence info and the bit depths it carries
            // (bl/el 10-bit), so RPUs without them are not converted.
            if (!reader.bit()) throw RpuFormatException("No sequence info")
            val chromaResamplingExplicitFilter = reader.bit()
            val coefficientDataType = reader.int(2)
            if (coefficientDataType > 1) throw RpuFormatException("coefficient_data_type $coefficientDataType")
            val coefficientLog2Denom = if (coefficientDataType == 0) reader.ue() else 0L
            val vdrRpuNormalizedIdc = reader.int(2)
            val blVideoFullRange = reader.bit()
            if (rpuFormat and 0x700 != 0) throw RpuFormatException("rpu_format $rpuFormat")
            val blBitDepthMinus8 = reader.ue()
            val elBitDepthField = reader.ue()
            val vdrBitDepthMinus8 = reader.ue()
            val spatialResamplingFilter = reader.bit()
            val reservedZero3Bits = reader.int(3)
            val elSpatialResamplingFilter = reader.bit()
            val disableResidual = reader.bit()
            val vdrDmMetadataPresent = reader.bit()
            val usePrevVdrRpu = reader.bit()
            val prevVdrRpuId = if (usePrevVdrRpu) reader.ue() else 0L
            return RpuHeader(
                rpuType, rpuFormat, vdrRpuProfile, vdrRpuLevel, chromaResamplingExplicitFilter,
                coefficientDataType, coefficientLog2Denom, vdrRpuNormalizedIdc, blVideoFullRange,
                blBitDepthMinus8, elBitDepthField, vdrBitDepthMinus8, spatialResamplingFilter, reservedZero3Bits,
                elSpatialResamplingFilter, disableResidual, vdrDmMetadataPresent, usePrevVdrRpu, prevVdrRpuId,
            ).also { it.validate() }
        }
    }

    private fun validate() {
        if (vdrRpuLevel != 0) throw RpuFormatException("vdr_rpu_level $vdrRpuLevel")
        if (blBitDepthMinus8 != 2L || elBitDepthMinus8 != 2L) throw RpuFormatException("Not a 10-bit RPU")
        if (vdrBitDepthMinus8 > 6) throw RpuFormatException("vdr_bit_depth_minus8 $vdrBitDepthMinus8")
        if (coefficientLog2Denom > 23) throw RpuFormatException("coefficient_log2_denom $coefficientLog2Denom")
    }
}

/** One piece of a reshaping curve: a polynomial or a multivariate multiple regression (MMR). */
internal sealed interface CurvePiece {
    fun write(writer: RpuBitWriter, header: RpuHeader)

    class Polynomial(private val orderMinus1: Long, private val coefInt: LongArray, private val coef: LongArray) :
        CurvePiece {
        override fun write(writer: RpuBitWriter, header: RpuHeader) {
            writer.ue(MAPPING_POLYNOMIAL)
            writer.ue(orderMinus1)
            // linear_interp_flag: interpolated pieces are never parsed.
            if (orderMinus1 == 0L) writer.bit(false)
            for (j in coef.indices) {
                if (header.coefficientDataType == 0) writer.se(coefInt[j])
                writer.bits(header.coefficientBits, coef[j])
            }
        }

        companion object {
            /** y = x: the 8.1 identity piece. */
            val IDENTITY = Polynomial(orderMinus1 = 0, coefInt = longArrayOf(0, 1), coef = longArrayOf(0, 0))
        }
    }

    class Mmr(
        private val orderMinus1: Int,
        private val constantInt: Long,
        private val constant: Long,
        private val coefInt: Array<LongArray>,
        private val coef: Array<LongArray>,
    ) : CurvePiece {
        override fun write(writer: RpuBitWriter, header: RpuHeader) {
            writer.ue(MAPPING_MMR)
            writer.bits(2, orderMinus1.toLong())
            if (header.coefficientDataType == 0) writer.se(constantInt)
            writer.bits(header.coefficientBits, constant)
            for (j in coef.indices) {
                for (k in 0 until MMR_COEFFICIENTS) {
                    if (header.coefficientDataType == 0) writer.se(coefInt[j][k])
                    writer.bits(header.coefficientBits, coef[j][k])
                }
            }
        }
    }

    companion object {
        const val MAPPING_POLYNOMIAL = 0L
        const val MAPPING_MMR = 1L
        const val MMR_COEFFICIENTS = 7

        fun parse(reader: RpuBitReader, header: RpuHeader): CurvePiece {
            val bits = header.coefficientBits
            val integers = header.coefficientDataType == 0
            return when (val mappingIdc = reader.ue()) {
                MAPPING_POLYNOMIAL -> {
                    val orderMinus1 = reader.ue()
                    if (orderMinus1 > 1) throw RpuFormatException("poly_order_minus1 $orderMinus1")
                    if (orderMinus1 == 0L && reader.bit()) throw RpuFormatException("Linear interpolation")
                    val count = orderMinus1.toInt() + 2
                    val coefInt = LongArray(count)
                    val coef = LongArray(count)
                    for (j in 0 until count) {
                        if (integers) coefInt[j] = reader.se()
                        coef[j] = reader.bits(bits)
                    }
                    Polynomial(orderMinus1, coefInt, coef)
                }
                MAPPING_MMR -> {
                    val orderMinus1 = reader.int(2)
                    if (orderMinus1 > 2) throw RpuFormatException("mmr_order_minus1 $orderMinus1")
                    val constantInt = if (integers) reader.se() else 0L
                    val constant = reader.bits(bits)
                    val coefInt = Array(orderMinus1 + 1) { LongArray(MMR_COEFFICIENTS) }
                    val coef = Array(orderMinus1 + 1) { LongArray(MMR_COEFFICIENTS) }
                    for (j in 0..orderMinus1) {
                        for (k in 0 until MMR_COEFFICIENTS) {
                            if (integers) coefInt[j][k] = reader.se()
                            coef[j][k] = reader.bits(bits)
                        }
                    }
                    Mmr(orderMinus1, constantInt, constant, coefInt, coef)
                }
                else -> throw RpuFormatException("mapping_idc $mappingIdc")
            }
        }
    }
}

/** A reshaping curve: `pieces.size + 1` pivots in base-layer code values. */
internal class ReshapingCurve(val pivots: IntArray, val pieces: List<CurvePiece>)

/** Profile 7's non-linear quantisation of the enhancement-layer residual, per component. */
internal class NlqParams(
    val offset: Long,
    val vdrInMaxInt: Long,
    val vdrInMax: Long,
    val slopeInt: Long,
    val slope: Long,
    val thresholdInt: Long,
    val threshold: Long,
) {
    /** A minimal enhancement layer (MEL) carries no residual at all. */
    val isMinimal: Boolean
        get() = offset == 0L && vdrInMaxInt == 1L && vdrInMax == 0L &&
            slopeInt == 0L && slope == 0L && thresholdInt == 0L && threshold == 0L
}

internal class RpuMapping(
    private val vdrRpuId: Long,
    private val mappingColorSpace: Long,
    private val mappingChromaFormatIdc: Long,
    var curves: List<ReshapingCurve>,
    /** nlq_pred_pivot_value; present only while the RPU carries a residual. */
    var nlqPredPivots: IntArray?,
    var numXPartitionsMinus1: Long,
    var numYPartitionsMinus1: Long,
    var nlq: List<NlqParams>?,
) {
    val isFullEnhancementLayer: Boolean get() = nlq?.all { it.isMinimal } == false

    /** libdovi's empty 8.1 mapping: one identity piece over the full 10-bit range per component. */
    fun setIdentity() {
        curves = List(COMPONENTS) { ReshapingCurve(intArrayOf(0, 1023), listOf(CurvePiece.Polynomial.IDENTITY)) }
    }

    fun write(writer: RpuBitWriter, header: RpuHeader) {
        val blBitDepth = (header.blBitDepthMinus8 + 8).toInt()
        writer.ue(vdrRpuId)
        writer.ue(mappingColorSpace)
        writer.ue(mappingChromaFormatIdc)
        for (curve in curves) {
            writer.ue((curve.pivots.size - 2).toLong())
            for (pivot in curve.pivots) writer.bits(blBitDepth, pivot.toLong())
        }
        val pivots = nlqPredPivots
        if (!header.disableResidual && pivots != null) {
            writer.bits(3, NLQ_LINEAR_DEADZONE)
            for (pivot in pivots) writer.bits(blBitDepth, pivot.toLong())
        }
        writer.ue(numXPartitionsMinus1)
        writer.ue(numYPartitionsMinus1)
        for (curve in curves) curve.pieces.forEach { it.write(writer, header) }
        nlq?.forEach { params ->
            writer.bits((header.elBitDepthMinus8 + 8).toInt(), params.offset)
            if (header.coefficientDataType == 0) writer.ue(params.vdrInMaxInt)
            writer.bits(header.coefficientBits, params.vdrInMax)
            if (header.coefficientDataType == 0) writer.ue(params.slopeInt)
            writer.bits(header.coefficientBits, params.slope)
            if (header.coefficientDataType == 0) writer.ue(params.thresholdInt)
            writer.bits(header.coefficientBits, params.threshold)
        }
    }

    companion object {
        const val COMPONENTS = 3
        private const val NLQ_LINEAR_DEADZONE = 0L

        fun parse(reader: RpuBitReader, header: RpuHeader): RpuMapping {
            val blBitDepth = (header.blBitDepthMinus8 + 8).toInt()
            val vdrRpuId = reader.ue()
            val mappingColorSpace = reader.ue()
            val mappingChromaFormatIdc = reader.ue()
            val pivots = List(COMPONENTS) {
                val count = reader.ue() + 2
                if (count > 9) throw RpuFormatException("num_pivots $count")
                IntArray(count.toInt()) { reader.int(blBitDepth) }
            }
            val nlqPredPivots = if (!header.disableResidual) {
                val method = reader.bits(3)
                if (method != NLQ_LINEAR_DEADZONE) throw RpuFormatException("nlq_method_idc $method")
                IntArray(2) { reader.int(blBitDepth) }
            } else {
                null
            }
            val numX = reader.ue()
            val numY = reader.ue()
            val curves = pivots.map { curvePivots ->
                ReshapingCurve(curvePivots, List(curvePivots.size - 1) { CurvePiece.parse(reader, header) })
            }
            val integers = header.coefficientDataType == 0
            val bits = header.coefficientBits
            val nlq = nlqPredPivots?.let {
                List(COMPONENTS) {
                    val offset = reader.bits((header.elBitDepthMinus8 + 8).toInt())
                    val vdrInMaxInt = if (integers) reader.ue() else 0L
                    val vdrInMax = reader.bits(bits)
                    val slopeInt = if (integers) reader.ue() else 0L
                    val slope = reader.bits(bits)
                    val thresholdInt = if (integers) reader.ue() else 0L
                    val threshold = reader.bits(bits)
                    NlqParams(offset, vdrInMaxInt, vdrInMax, slopeInt, slope, thresholdInt, threshold)
                }
            }
            return RpuMapping(
                vdrRpuId, mappingColorSpace, mappingChromaFormatIdc, curves, nlqPredPivots, numX, numY, nlq,
            )
        }
    }
}

/**
 * vdr_dm_data_payload: three ids, the fixed signal description (absent when
 * compressed), then CM v2.9 and optional CM v4.0 extension blocks.
 */
internal class RpuDisplayManagement(
    private val ids: LongArray,
    private val fixed: LongArray?,
    private val cmV29: ExtensionBlocks,
    private val cmV40: ExtensionBlocks?,
) {
    /** libdovi `set_p81_coeffs`: BT.2020 YCbCr → RGB → LMS for a limited-range PQ base layer. */
    fun setProfile81Coefficients() {
        val values = fixed ?: return
        PROFILE_81_COEFFICIENTS.copyInto(values)
        values[SIGNAL_COLOR_SPACE] = 0
    }

    fun write(writer: RpuBitWriter, source: ByteArray) {
        ids.forEach(writer::ue)
        fixed?.forEachIndexed { index, value -> writer.bits(FIXED_FIELD_BITS[index], value) }
        cmV29.write(writer, source)
        cmV40?.write(writer, source)
    }

    companion object {
        /** Widths of the uncompressed fields, ycc_to_rgb_coef0 through source_diagonal. */
        private val FIXED_FIELD_BITS = IntArray(9) { 16 } + IntArray(3) { 32 } + IntArray(9) { 16 } +
            intArrayOf(16, 16, 16, 32, 5, 2, 2, 2, 12, 12, 10)
        private const val SIGNAL_COLOR_SPACE = 26

        private val PROFILE_81_COEFFICIENTS = longArrayOf(
            9574, 0, 13802, 9574, -1540, -5348, 9574, 17610, 0,
            16777216, 134217728, 134217728,
            7222, 8771, 390, 2654, 12430, 1300, 0, 422, 15962,
        )

        fun parse(reader: RpuBitReader, header: RpuHeader): RpuDisplayManagement {
            val ids = LongArray(3) { reader.ue() }
            val compressed = header.reservedZero3Bits == 1
            val fixed = if (compressed) null else LongArray(FIXED_FIELD_BITS.size) { reader.bits(FIXED_FIELD_BITS[it]) }
            val cmV29 = ExtensionBlocks.parse(reader)
            val cmV40 = if (DolbyVisionRpu.hasCmV40(reader)) ExtensionBlocks.parse(reader) else null
            return RpuDisplayManagement(ids, fixed, cmV29, cmV40)
        }
    }
}

/** num_ext_blocks, byte alignment, then each block as ue(length), u8 level, and `length` bytes. */
internal class ExtensionBlocks(private val blocks: List<Block>) {
    class Block(val lengthBytes: Long, val level: Int, val payload: BitRun)

    fun write(writer: RpuBitWriter, source: ByteArray) {
        writer.ue(blocks.size.toLong())
        writer.byteAlign()
        for (block in blocks) {
            writer.ue(block.lengthBytes)
            writer.bits(8, block.level.toLong())
            block.payload.copyTo(writer, source)
        }
    }

    companion object {
        private const val MAX_BLOCKS = 64

        fun parse(reader: RpuBitReader): ExtensionBlocks {
            val count = reader.ue()
            if (count > MAX_BLOCKS) throw RpuFormatException("num_ext_blocks $count")
            while (!reader.byteAligned) {
                if (reader.bit()) throw RpuFormatException("dm_alignment_zero_bit != 0")
            }
            val blocks = List(count.toInt()) {
                val lengthBytes = reader.ue()
                val level = reader.int(8)
                if (lengthBytes > reader.available / 8) throw RpuFormatException("Extension block overruns RPU")
                Block(lengthBytes, level, BitRun.read(reader, (lengthBytes * 8).toInt()))
            }
            return ExtensionBlocks(blocks)
        }
    }
}
