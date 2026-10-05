package com.lamphaus.core.player.dolbyvision

/** Thrown when an RPU does not follow the syntax this converter understands. */
internal class RpuFormatException(message: String) : Exception(message)

/** MSB-first reader over unescaped RPU payload bytes `[0, limit)`. */
internal class RpuBitReader(private val data: ByteArray, private val limit: Int = data.size) {
    var position = 0
        private set

    val available: Int get() = limit * 8 - position

    val byteAligned: Boolean get() = position % 8 == 0

    fun bit(): Boolean {
        if (position >= limit * 8) throw RpuFormatException("RPU ended early")
        val value = (data[position ushr 3].toInt() ushr (7 - (position and 7))) and 1
        position++
        return value == 1
    }

    /** An unsigned value of [count] bits, `0..64`. */
    fun bits(count: Int): Long {
        if (count !in 0..64) throw RpuFormatException("Invalid field width $count")
        if (count > available) throw RpuFormatException("RPU ended early")
        var value = 0L
        repeat(count) { value = (value shl 1) or if (bit()) 1L else 0L }
        return value
    }

    fun int(count: Int): Int = bits(count).toInt()

    /** Exp-Golomb `ue(v)`. */
    fun ue(): Long {
        var leadingZeros = 0
        while (!bit()) {
            leadingZeros++
            // RPU syntax elements are small; a longer prefix is corrupt data.
            if (leadingZeros > 32) throw RpuFormatException("Invalid ue(v)")
        }
        return ((1L shl leadingZeros) - 1) + bits(leadingZeros)
    }

    /** Exp-Golomb `se(v)`. */
    fun se(): Long {
        val code = ue()
        return if (code and 1L == 1L) (code + 1) / 2 else -(code / 2)
    }

    fun skipToByteBoundary() {
        while (!byteAligned) bit()
    }
}

/** MSB-first writer producing unescaped RPU payload bytes. */
internal class RpuBitWriter(initialCapacity: Int = 512) {
    private var buffer = ByteArray(initialCapacity)
    var position = 0
        private set

    val byteAligned: Boolean get() = position % 8 == 0

    fun bit(value: Boolean) {
        val index = position ushr 3
        if (index >= buffer.size) buffer = buffer.copyOf(buffer.size * 2)
        if (value) buffer[index] = (buffer[index].toInt() or (0x80 ushr (position and 7))).toByte()
        position++
    }

    fun bits(count: Int, value: Long) {
        for (shift in count - 1 downTo 0) bit((value ushr shift) and 1L == 1L)
    }

    fun ue(value: Long) {
        val code = value + 1
        val width = 64 - java.lang.Long.numberOfLeadingZeros(code)
        bits(width - 1, 0)
        bits(width, code)
    }

    fun se(value: Long) = ue(if (value > 0) value * 2 - 1 else -value * 2)

    fun byteAlign() {
        while (!byteAligned) bit(false)
    }

    /** The written bytes; the writer must be byte aligned. */
    fun toByteArray(): ByteArray {
        check(byteAligned) { "Unaligned RPU" }
        return buffer.copyOf(position ushr 3)
    }
}

/** CRC-32/MPEG-2, the checksum that closes every Dolby Vision RPU. */
internal object Crc32Mpeg2 {
    private val table = IntArray(256) { index ->
        var crc = index shl 24
        repeat(8) { crc = if (crc and 0x80000000.toInt() != 0) (crc shl 1) xor 0x04C11DB7 else crc shl 1 }
        crc
    }

    fun compute(data: ByteArray, from: Int, to: Int): Int {
        var crc = -1
        for (i in from until to) {
            crc = (crc shl 8) xor table[((crc ushr 24) xor (data[i].toInt() and 0xFF)) and 0xFF]
        }
        return crc
    }
}
