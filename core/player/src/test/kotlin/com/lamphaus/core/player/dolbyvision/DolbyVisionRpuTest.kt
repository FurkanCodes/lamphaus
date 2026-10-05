package com.lamphaus.core.player.dolbyvision

import com.lamphaus.core.player.dolbyvision.TestRpus.Layer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DolbyVisionRpuTest {

    @Test
    fun `exp-golomb codes match the spec`() {
        val writer = RpuBitWriter()
        // ue: 0 → 1, 1 → 010, 3 → 00100; se: -2 → ue(4) 00101, 3 → ue(5) 00110:
        // 1010 0010 | 0001 0100 | 110 then alignment.
        writer.ue(0)
        writer.ue(1)
        writer.ue(3)
        writer.se(-2)
        writer.se(3)
        writer.byteAlign()
        val bytes = writer.toByteArray()
        assertArrayEquals(byteArrayOf(0xA2.toByte(), 0x14, 0xC0.toByte()), bytes)
        val reader = RpuBitReader(bytes)
        assertEquals(0L, reader.ue())
        assertEquals(1L, reader.ue())
        assertEquals(3L, reader.ue())
        assertEquals(-2L, reader.se())
        assertEquals(3L, reader.se())
    }

    @Test
    fun `crc is CRC-32 MPEG-2`() {
        val check = "123456789".toByteArray()
        assertEquals(0x0376E6E7, Crc32Mpeg2.compute(check, 0, check.size))
    }

    @Test
    fun `parsing and writing an RPU is lossless`() {
        for (source in listOf(
            TestRpus.rpu(Layer.FEL),
            TestRpus.rpu(Layer.MEL),
            TestRpus.rpu(Layer.NONE),
            TestRpus.rpu(Layer.FEL, usePrevious = true),
            TestRpus.rpu(Layer.MEL, cmV40 = false),
        )) {
            assertArrayEquals(source, DolbyVisionRpu.parse(source).write())
        }
    }

    @Test
    fun `profiles follow libdovi's guess`() {
        assertEquals(7, DolbyVisionRpu.parse(TestRpus.rpu(Layer.FEL)).profile)
        assertEquals(7, DolbyVisionRpu.parse(TestRpus.rpu(Layer.MEL)).profile)
        assertEquals(8, DolbyVisionRpu.parse(TestRpus.rpu(Layer.NONE)).profile)
    }

    @Test
    fun `full enhancement layer converts to 8_1 with an identity mapping`() {
        val rpu = DolbyVisionRpu.parse(TestRpus.rpu(Layer.FEL))
        assertTrue(rpu.mapping!!.isFullEnhancementLayer)
        assertTrue(rpu.convertToProfile81())
        assertArrayEquals(TestRpus.rpu(Layer.NONE, identityMapping = true), rpu.write())
    }

    @Test
    fun `minimal enhancement layer keeps its mapping without the residual`() {
        val rpu = DolbyVisionRpu.parse(TestRpus.rpu(Layer.MEL))
        assertFalse(rpu.mapping!!.isFullEnhancementLayer)
        assertTrue(rpu.convertToProfile81())
        assertArrayEquals(TestRpus.rpu(Layer.NONE), rpu.write())
    }

    @Test
    fun `an RPU reusing the previous mapping converts its header and colour data`() {
        val rpu = DolbyVisionRpu.parse(TestRpus.rpu(Layer.FEL, usePrevious = true))
        assertTrue(rpu.convertToProfile81())
        assertArrayEquals(TestRpus.rpu(Layer.NONE, usePrevious = true), rpu.write())
    }

    @Test
    fun `converted RPUs parse back as profile 8`() {
        for (layer in listOf(Layer.FEL, Layer.MEL)) {
            val converted = DolbyVisionRpu.parse(TestRpus.rpu(layer)).apply { convertToProfile81() }.write()
            assertEquals(8, DolbyVisionRpu.parse(converted).profile)
        }
    }

    @Test
    fun `profile 8 is left alone`() {
        val rpu = DolbyVisionRpu.parse(TestRpus.rpu(Layer.NONE))
        assertFalse(rpu.convertToProfile81())
    }

    @Test(expected = RpuFormatException::class)
    fun `a damaged RPU is rejected by its checksum`() {
        val damaged = TestRpus.rpu(Layer.FEL).also { it[20] = (it[20].toInt() xor 0x10).toByte() }
        DolbyVisionRpu.parse(damaged)
    }

    @Test(expected = RpuFormatException::class)
    fun `a truncated RPU is rejected`() {
        val source = TestRpus.rpu(Layer.MEL)
        DolbyVisionRpu.parse(source.copyOf(source.size / 2) + byteArrayOf(0x80.toByte()))
    }

    @Test
    fun `trailing zero bytes are ignored`() {
        val source = TestRpus.rpu(Layer.NONE)
        assertArrayEquals(source, DolbyVisionRpu.parse(source + ByteArray(3)).write())
    }
}
