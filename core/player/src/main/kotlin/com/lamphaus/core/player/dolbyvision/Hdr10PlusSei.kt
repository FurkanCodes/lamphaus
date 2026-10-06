package com.lamphaus.core.player.dolbyvision

/**
 * Finds HDR10+ dynamic metadata in an HEVC SEI payload: a
 * user_data_registered_itu_t_t35 message (payload type 4) from the US
 * (country code 0xB5) by Samsung's HDR10+ registration (provider 0x003C,
 * oriented code 0x0001, application 4). Nuvio's "Strip HDR10+ Metadata"
 * removes these so a display or decoder that mishandles them sees plain HDR10.
 */
internal object Hdr10PlusSei {

    class Result(
        /** The SEI RBSP without HDR10+ messages, or null when nothing else remained. */
        val payload: ByteArray?,
        val removed: Int,
    )

    /**
     * [rbsp] (`rbsp[0, length)`) without its HDR10+ messages, or null when it
     * has none or cannot be parsed (the caller then keeps the NAL unit as is).
     */
    fun withoutHdr10Plus(rbsp: ByteArray, length: Int): Result? {
        var position = 0
        var removed = 0
        val kept = ArrayList<IntRange>()
        while (position < length && !(position == length - 1 && rbsp[position].toInt() and 0xFF == RBSP_STOP)) {
            val messageStart = position
            var payloadType = 0
            while (position < length && rbsp[position].toInt() and 0xFF == 0xFF) {
                payloadType += 255
                position++
            }
            if (position >= length) return null
            payloadType += rbsp[position++].toInt() and 0xFF
            var payloadSize = 0
            while (position < length && rbsp[position].toInt() and 0xFF == 0xFF) {
                payloadSize += 255
                position++
            }
            if (position >= length) return null
            payloadSize += rbsp[position++].toInt() and 0xFF
            val payloadStart = position
            position += payloadSize
            if (position > length) return null
            if (payloadType == T35 && isHdr10Plus(rbsp, payloadStart, payloadSize)) {
                removed++
            } else {
                kept += messageStart until position
            }
        }
        if (removed == 0) return null
        if (kept.isEmpty()) return Result(payload = null, removed = removed)
        val size = kept.sumOf { it.last - it.first + 1 } + 1
        val out = ByteArray(size)
        var offset = 0
        kept.forEach { range ->
            rbsp.copyInto(out, offset, range.first, range.last + 1)
            offset += range.last - range.first + 1
        }
        out[offset] = RBSP_STOP.toByte()
        return Result(out, removed)
    }

    private fun isHdr10Plus(data: ByteArray, start: Int, size: Int): Boolean {
        if (size < HEADER.size) return false
        return HEADER.indices.all { (data[start + it].toInt() and 0xFF) == HEADER[it] }
    }

    private const val T35 = 4
    private const val RBSP_STOP = 0x80

    /** itu_t_t35_country_code, terminal_provider_code (2), terminal_provider_oriented_code (2), application_identifier. */
    private val HEADER = intArrayOf(0xB5, 0x00, 0x3C, 0x00, 0x01, 0x04)
}
