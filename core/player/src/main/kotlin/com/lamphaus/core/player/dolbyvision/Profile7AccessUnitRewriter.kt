package com.lamphaus.core.player.dolbyvision

/**
 * Rewrites a Dolby Vision access unit the way Nuvio's libdovi pipeline does.
 * Converting (profile 7 → 8.1 signalling) drops the enhancement layer and
 * converts every RPU, with libdovi mode 1 ([Mode.TO_MEL], Nuvio's Auto) or
 * mode 2 ([Mode.TO_81], its "Convert to DV8.1", falling back to mode 1).
 * Stripping ([Mode.STRIP]) drops RPUs and the enhancement layer, leaving the
 * HDR10 base layer for the HEVC decoder. Base-layer NAL units always pass
 * through byte for byte.
 *
 * Works on Annex-B data, as Media3's MP4 and Matroska extractors hand video
 * samples to their track outputs. One instance per track; not thread safe.
 */
internal class Profile7AccessUnitRewriter {

    enum class Mode {
        /** Dolby Vision untouched (only HDR10+ may be stripped). */
        KEEP,
        TO_MEL,
        TO_81,

        /** Mode 2 keeping every mapping as authored (Nuvio's "Preserve DV mapping"). */
        TO_81_PRESERVING_MAPPING,

        /** Profile 5 RPUs rewritten as 8.1 (Nuvio's "Convert DV5 to DV8.1" with Convert). */
        PROFILE5_TO_81,
        STRIP,
    }

    var output = ByteArray(INITIAL_CAPACITY)
        private set

    /**
     * RPUs that could not be converted; they travel on as they came, their
     * NAL header moved to layer 0, as Nuvio forwards them.
     */
    var unconvertedRpus = 0L
        private set

    private var scratch = ByteArray(RPU_SCRATCH_CAPACITY)

    /** HDR10+ SEI messages removed so far. */
    var strippedHdr10PlusMessages = 0L
        private set

    /**
     * Rewrites `data[from, to)` into [output] and returns the rewritten
     * length. With [stripHdr10Plus], HDR10+ dynamic metadata (ITU-T T.35
     * SEI messages) is removed from prefix and suffix SEI NAL units, after
     * any Dolby Vision conversion.
     */
    fun rewrite(data: ByteArray, from: Int, to: Int, mode: Mode = Mode.TO_81, stripHdr10Plus: Boolean = false): Int {
        ensureCapacity(to - from)
        var length = 0
        var startCode = findStartCode(data, from, to)
        if (startCode < 0) {
            data.copyInto(output, 0, from, to)
            return to - from
        }
        while (startCode >= 0) {
            val nalStart = startCode + 3
            val next = findStartCode(data, nalStart, to)
            var nalEnd = if (next < 0) to else next
            // trailing_zero_8bits and the next 4-byte start code's zero_byte
            // are not part of the NAL unit, which never ends in 0x00.
            while (nalEnd > nalStart && data[nalEnd - 1].toInt() == 0) nalEnd--
            length = writeNal(data, nalStart, nalEnd, length, mode, stripHdr10Plus)
            startCode = next
        }
        return length
    }

    private fun writeNal(data: ByteArray, start: Int, end: Int, position: Int, mode: Mode, stripHdr10Plus: Boolean): Int {
        if (end - start < 2) return position
        val type = nalType(data, start)
        val layerId = ((data[start].toInt() and 0x01) shl 5) or ((data[start + 1].toInt() and 0xF8) ushr 3)
        return when {
            stripHdr10Plus && (type == NAL_TYPE_PREFIX_SEI || type == NAL_TYPE_SUFFIX_SEI) ->
                writeSeiWithoutHdr10Plus(data, start, end, position)
            mode == Mode.KEEP -> appendNal(data, start, end, position)
            type == NAL_TYPE_RPU && mode == Mode.STRIP -> position
            type == NAL_TYPE_RPU -> {
                val rpu = convertRpu(data, start, end, mode)
                if (rpu == null) {
                    unconvertedRpus++
                    appendLayerZero(data, start, end, position)
                } else {
                    appendNal(rpu, 0, rpu.size, position)
                }
            }
            // The enhancement layer: wrapped in type 63, or carried as layer 1 (profile 7 only).
            mode != Mode.PROFILE5_TO_81 && (type == NAL_TYPE_ENHANCEMENT_LAYER || layerId > 0) -> position
            else -> appendNal(data, start, end, position)
        }
    }

    private fun appendNal(source: ByteArray, start: Int, end: Int, position: Int): Int {
        ensureCapacity(position + START_CODE.size + end - start)
        START_CODE.copyInto(output, position)
        source.copyInto(output, position + START_CODE.size, start, end)
        return position + START_CODE.size + end - start
    }

    /** Appends a NAL unit with its nuh_layer_id cleared, temporal id kept. */
    private fun appendLayerZero(source: ByteArray, start: Int, end: Int, position: Int): Int {
        val next = appendNal(source, start, end, position)
        val header = position + START_CODE.size
        output[header] = (output[header].toInt() and 0xFE).toByte()
        output[header + 1] = (output[header + 1].toInt() and 0x07).toByte()
        return next
    }

    /**
     * The converted RPU NAL unit (header included, escaped), the original one
     * when it is already profile 8, or null when it cannot be converted.
     * Mode 2 falls back to mode 1 for an RPU it cannot rewrite.
     */
    private fun convertRpu(data: ByteArray, start: Int, end: Int, mode: Mode): ByteArray? {
        if (scratch.size < end - start) scratch = ByteArray(end - start)
        val payloadLength = unescape(data, start + 2, end, scratch)
        fun converted(convert: DolbyVisionRpu.() -> Boolean): ByteArray? = try {
            val rpu = DolbyVisionRpu.parse(scratch, payloadLength)
            when {
                rpu.convert() -> escapeRpu(rpu.write())
                rpu.profile == 8 -> RPU_NAL_HEADER + data.copyOfRange(start + 2, end)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
        return when (mode) {
            Mode.TO_81 -> converted { convertToProfile81() } ?: converted { convertToMel() }
            Mode.TO_81_PRESERVING_MAPPING -> converted { convertToProfile81(preserveMapping = true) } ?: converted { convertToMel() }
            Mode.TO_MEL -> converted { convertToMel() }
            Mode.PROFILE5_TO_81 -> converted { convertProfile5To81() }
            Mode.KEEP, Mode.STRIP -> null
        }
    }

    /**
     * Copies an SEI NAL unit without its HDR10+ messages: the NAL unit as it
     * was when it carries none, nothing when HDR10+ was all it carried.
     */
    private fun writeSeiWithoutHdr10Plus(data: ByteArray, start: Int, end: Int, position: Int): Int {
        if (scratch.size < end - start) scratch = ByteArray(end - start)
        val rbspLength = unescape(data, start + 2, end, scratch)
        val kept = Hdr10PlusSei.withoutHdr10Plus(scratch, rbspLength) ?: return appendNal(data, start, end, position)
        strippedHdr10PlusMessages += kept.removed
        if (kept.payload == null) return position
        val nal = escapeNal(data[start], data[start + 1], kept.payload)
        return appendNal(nal, 0, nal.size, position)
    }

    private fun ensureCapacity(capacity: Int) {
        if (output.size >= capacity) return
        output = output.copyOf(maxOf(capacity, output.size * 2))
    }

    companion object {
        const val NAL_TYPE_RPU = 62
        const val NAL_TYPE_ENHANCEMENT_LAYER = 63
        const val NAL_TYPE_PREFIX_SEI = 39
        const val NAL_TYPE_SUFFIX_SEI = 40
        private const val INITIAL_CAPACITY = 1 shl 20
        private const val RPU_SCRATCH_CAPACITY = 1024
        private val START_CODE = byteArrayOf(0, 0, 0, 1)

        /** nal_unit_type 62, nuh_layer_id 0, nuh_temporal_id_plus1 1. */
        private val RPU_NAL_HEADER = byteArrayOf(0x7C, 0x01)

        /** True when the Annex-B data carries a Dolby Vision RPU in-band. */
        fun containsRpu(data: ByteArray, from: Int, to: Int): Boolean {
            var startCode = findStartCode(data, from, to)
            while (startCode >= 0) {
                val nalStart = startCode + 3
                if (nalStart < to && nalType(data, nalStart) == NAL_TYPE_RPU) return true
                startCode = findStartCode(data, nalStart, to)
            }
            return false
        }

        private fun nalType(data: ByteArray, start: Int): Int = (data[start].toInt() ushr 1) and 0x3F

        /**
         * Index of the next `00 00 01` in `data[from, to)`, or -1. Emulation
         * prevention guarantees the pattern never occurs inside a NAL unit.
         */
        fun findStartCode(data: ByteArray, from: Int, to: Int): Int {
            var i = from
            while (i + 2 < to) {
                val third = data[i + 2].toInt()
                when {
                    third > 1 || third < 0 -> i += 3
                    third == 1 -> {
                        if (data[i].toInt() == 0 && data[i + 1].toInt() == 0) return i
                        i += 3
                    }
                    else -> i++
                }
            }
            return -1
        }

        /** Removes emulation-prevention bytes from `data[from, to)` into [out]. */
        fun unescape(data: ByteArray, from: Int, to: Int, out: ByteArray): Int {
            var zeros = 0
            var length = 0
            for (i in from until to) {
                val value = data[i].toInt()
                if (zeros >= 2 && value == 3) {
                    zeros = 0
                    continue
                }
                out[length++] = data[i]
                zeros = if (value == 0) zeros + 1 else 0
            }
            return length
        }

        /** The RPU NAL header followed by the payload with emulation prevention added. */
        fun escapeRpu(payload: ByteArray): ByteArray = escapeNal(RPU_NAL_HEADER[0], RPU_NAL_HEADER[1], payload)

        /** A NAL unit with header [first] [second] and [payload] escaped for emulation prevention. */
        fun escapeNal(first: Byte, second: Byte, payload: ByteArray): ByteArray {
            val out = ByteArray(2 + payload.size + payload.size / 2 + 1)
            out[0] = first
            out[1] = second
            var length = 2
            var zeros = 0
            for (byte in payload) {
                val value = byte.toInt() and 0xFF
                if (zeros >= 2 && value <= 3) {
                    out[length++] = 3
                    zeros = 0
                }
                out[length++] = byte
                zeros = if (value == 0) zeros + 1 else 0
            }
            return out.copyOf(length)
        }
    }
}

/** Codec strings for a profile 7 stream once it is rewritten as 8.1. */
internal object DolbyVisionCodecs {

    /** The profile in a codecs string such as `dvhe.07.06`, or null. */
    fun profileOf(codecs: String?): Int? = codecs?.split('.')?.getOrNull(1)?.toIntOrNull()

    /** True for HEVC-based Dolby Vision (`dvhe`, `dvh1`), the only kind the rewriter parses. */
    fun isHevc(codecs: String?): Boolean =
        codecs?.substringBefore('.')?.lowercase()?.let { it == "dvhe" || it == "dvh1" } == true

    /** `dvhe.05.06` → `dvhe.08.06`; the string unchanged for anything that is not HEVC profile 5. */
    fun profile5AsProfile8(codecs: String?): String? {
        val parts = codecs?.split('.') ?: return codecs
        if (parts.size < 3 || !isHevc(codecs) || parts[1].toIntOrNull() != 5) return codecs
        return (listOf(parts[0], "08") + parts.drop(2)).joinToString(".")
    }

    /** `dvhe.07.06` → `dvhe.08.06`; null for anything that is not HEVC profile 7. */
    fun profile7AsProfile8(codecs: String?): String? {
        val parts = codecs?.split('.') ?: return null
        if (parts.size < 3) return null
        val prefix = parts[0].lowercase()
        if (prefix != "dvhe" && prefix != "dvh1") return null
        if (parts[1].toIntOrNull() != 7) return null
        return (listOf(parts[0], "08") + parts.drop(2)).joinToString(".")
    }
}
