package com.lamphaus.core.player.dolbyvision

/**
 * Turns a Dolby Vision profile 7 access unit into profile 8.1, the way
 * dovi_tool's `convert --discard -m 2` does: the enhancement layer is
 * dropped, every RPU is converted, and the HDR10 base layer passes through
 * byte for byte. Profile 7 UHD Blu-ray remuxes keep both layers in one track
 * (the enhancement layer wrapped in NAL units of type 63), which most Dolby
 * Vision decoders refuse; profile 8.1 they play.
 *
 * Works on Annex-B data, as Media3's MP4 and Matroska extractors hand video
 * samples to their track outputs. One instance per track; not thread safe.
 */
internal class Profile7AccessUnitRewriter {

    var output = ByteArray(INITIAL_CAPACITY)
        private set

    /** RPUs that could not be converted and were left out; the frame keeps its base layer. */
    var droppedRpus = 0L
        private set

    private var scratch = ByteArray(RPU_SCRATCH_CAPACITY)

    /** Rewrites `data[from, to)` into [output] and returns the rewritten length. */
    fun rewrite(data: ByteArray, from: Int, to: Int): Int {
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
            length = writeNal(data, nalStart, nalEnd, length)
            startCode = next
        }
        return length
    }

    private fun writeNal(data: ByteArray, start: Int, end: Int, position: Int): Int {
        if (end - start < 2) return position
        val type = nalType(data, start)
        val layerId = ((data[start].toInt() and 0x01) shl 5) or ((data[start + 1].toInt() and 0xF8) ushr 3)
        return when {
            type == NAL_TYPE_RPU -> {
                val rpu = convertRpu(data, start, end)
                if (rpu == null) {
                    droppedRpus++
                    position
                } else {
                    appendNal(rpu, 0, rpu.size, position)
                }
            }
            // The enhancement layer: wrapped in type 63, or carried as layer 1.
            type == NAL_TYPE_ENHANCEMENT_LAYER || layerId > 0 -> position
            else -> appendNal(data, start, end, position)
        }
    }

    private fun appendNal(source: ByteArray, start: Int, end: Int, position: Int): Int {
        ensureCapacity(position + START_CODE.size + end - start)
        START_CODE.copyInto(output, position)
        source.copyInto(output, position + START_CODE.size, start, end)
        return position + START_CODE.size + end - start
    }

    /**
     * The converted RPU NAL unit (header included, escaped), the original one
     * when it is already profile 8, or null when it cannot be converted.
     * Corrupt metadata must never stop playback, so every failure is a drop.
     */
    private fun convertRpu(data: ByteArray, start: Int, end: Int): ByteArray? {
        if (scratch.size < end - start) scratch = ByteArray(end - start)
        val payloadLength = unescape(data, start + 2, end, scratch)
        return try {
            val rpu = DolbyVisionRpu.parse(scratch, payloadLength)
            when {
                rpu.convertToProfile81() -> escapeRpu(rpu.write())
                rpu.profile == 8 -> RPU_NAL_HEADER + data.copyOfRange(start + 2, end)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun ensureCapacity(capacity: Int) {
        if (output.size >= capacity) return
        output = output.copyOf(maxOf(capacity, output.size * 2))
    }

    companion object {
        const val NAL_TYPE_RPU = 62
        const val NAL_TYPE_ENHANCEMENT_LAYER = 63
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
        fun escapeRpu(payload: ByteArray): ByteArray {
            val out = ByteArray(RPU_NAL_HEADER.size + payload.size + payload.size / 2 + 1)
            RPU_NAL_HEADER.copyInto(out)
            var length = RPU_NAL_HEADER.size
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
