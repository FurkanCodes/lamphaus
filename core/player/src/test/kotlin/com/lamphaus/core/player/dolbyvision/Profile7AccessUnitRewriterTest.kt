package com.lamphaus.core.player.dolbyvision

import com.lamphaus.core.player.dolbyvision.TestRpus.Layer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Profile7AccessUnitRewriterTest {

    private val startCode = byteArrayOf(0, 0, 0, 1)

    // HEVC NAL headers: type << 1 in the first byte, layer id across both, temporal id 1.
    private val vps = byteArrayOf(0x40, 0x01, 0x0C, 0x01, 0x00, 0x00, 0x03, 0x00, 0x7A)
    private val slice = byteArrayOf(0x26, 0x01, 0xAF.toByte(), 0x00, 0x00, 0x03, 0x01, 0x42, 0x00, 0x00, 0x03, 0x00, 0x55)
    private val enhancementLayer = byteArrayOf(0x7E, 0x01, 0x26, 0x09, 0x11, 0x22, 0x33)
    private val layerOneSlice = byteArrayOf(0x02, 0x09, 0x44, 0x55)

    private fun nal(vararg units: ByteArray): ByteArray =
        units.fold(ByteArray(0)) { acc, unit -> acc + startCode + unit }

    private fun rpuNal(layer: Layer, identityMapping: Boolean = false) =
        Profile7AccessUnitRewriter.escapeRpu(TestRpus.rpu(layer, identityMapping))

    private fun rewrite(data: ByteArray, rewriter: Profile7AccessUnitRewriter = Profile7AccessUnitRewriter()): ByteArray {
        val length = rewriter.rewrite(data, 0, data.size)
        return rewriter.output.copyOf(length)
    }

    @Test
    fun `profile 7 access unit becomes base layer plus an 8_1 RPU`() {
        val accessUnit = nal(vps, slice, enhancementLayer, enhancementLayer, rpuNal(Layer.FEL))
        val expected = nal(vps, slice, rpuNal(Layer.NONE, identityMapping = true))
        assertArrayEquals(expected, rewrite(accessUnit))
    }

    @Test
    fun `base layer bytes, escapes included, are untouched`() {
        val accessUnit = nal(slice, rpuNal(Layer.MEL), vps)
        assertArrayEquals(nal(slice, rpuNal(Layer.NONE), vps), rewrite(accessUnit))
    }

    @Test
    fun `enhancement layer carried as layer one is dropped`() {
        val accessUnit = nal(slice, layerOneSlice, rpuNal(Layer.MEL))
        assertArrayEquals(nal(slice, rpuNal(Layer.NONE)), rewrite(accessUnit))
    }

    @Test
    fun `three-byte start codes and trailing zeros are normalised`() {
        val accessUnit = byteArrayOf(0, 0, 1) + slice + byteArrayOf(0, 0) + byteArrayOf(0, 0, 1) + rpuNal(Layer.MEL)
        assertArrayEquals(nal(slice, rpuNal(Layer.NONE)), rewrite(accessUnit))
    }

    @Test
    fun `an unreadable RPU is dropped and counted`() {
        val rewriter = Profile7AccessUnitRewriter()
        val broken = byteArrayOf(0x7C, 0x01, 0x19, 0x08, 0x09, 0x12, 0x34, 0x56, 0x78, 0x80.toByte())
        assertArrayEquals(nal(slice), rewrite(nal(slice, broken), rewriter))
        assertEquals(1L, rewriter.droppedRpus)
    }

    @Test
    fun `a profile 8 RPU passes through`() {
        val accessUnit = nal(slice, rpuNal(Layer.NONE))
        assertArrayEquals(accessUnit, rewrite(accessUnit))
    }

    @Test
    fun `the output buffer is reused and grows as needed`() {
        val rewriter = Profile7AccessUnitRewriter()
        val largeSlice = byteArrayOf(0x26, 0x01) + ByteArray(3_000_000) { 0x55 }
        assertArrayEquals(nal(largeSlice, rpuNal(Layer.NONE)), rewrite(nal(largeSlice, rpuNal(Layer.MEL)), rewriter))
        assertArrayEquals(nal(slice, rpuNal(Layer.NONE)), rewrite(nal(slice, rpuNal(Layer.MEL)), rewriter))
    }

    @Test
    fun `rewrites a sample in the middle of a buffer`() {
        val accessUnit = nal(slice, enhancementLayer, rpuNal(Layer.MEL))
        val padded = byteArrayOf(9, 9, 9) + accessUnit + byteArrayOf(0, 0, 1, 0x26)
        val rewriter = Profile7AccessUnitRewriter()
        val length = rewriter.rewrite(padded, 3, 3 + accessUnit.size)
        assertArrayEquals(nal(slice, rpuNal(Layer.NONE)), rewriter.output.copyOf(length))
    }

    @Test
    fun `finds in-band RPUs`() {
        val withRpu = nal(slice, rpuNal(Layer.FEL))
        assertTrue(Profile7AccessUnitRewriter.containsRpu(withRpu, 0, withRpu.size))
        val baseOnly = nal(vps, slice)
        assertFalse(Profile7AccessUnitRewriter.containsRpu(baseOnly, 0, baseOnly.size))
    }

    @Test
    fun `start code search skips escaped zeros`() {
        val data = byteArrayOf(5, 0, 0, 3, 1, 0, 0, 2, 0, 0, 0, 1, 7)
        assertEquals(9, Profile7AccessUnitRewriter.findStartCode(data, 0, data.size))
        assertEquals(-1, Profile7AccessUnitRewriter.findStartCode(data, 0, 9))
    }

    @Test
    fun `escaping round-trips through unescaping`() {
        val payload = byteArrayOf(0x19, 0, 0, 0, 0, 0, 1, 0, 0, 2, 0, 0, 3, 0, 0, 4, 0x80.toByte())
        val escaped = Profile7AccessUnitRewriter.escapeRpu(payload)
        assertEquals(-1, Profile7AccessUnitRewriter.findStartCode(escaped, 0, escaped.size))
        val out = ByteArray(escaped.size)
        val length = Profile7AccessUnitRewriter.unescape(escaped, 2, escaped.size, out)
        assertArrayEquals(payload, out.copyOf(length))
    }

    @Test
    fun `codec strings move from profile 7 to 8`() {
        assertEquals("dvhe.08.06", DolbyVisionCodecs.profile7AsProfile8("dvhe.07.06"))
        assertEquals("dvh1.08.09", DolbyVisionCodecs.profile7AsProfile8("dvh1.07.09"))
        assertNull(DolbyVisionCodecs.profile7AsProfile8("dvhe.08.06"))
        assertNull(DolbyVisionCodecs.profile7AsProfile8("dvav.09.05"))
        assertNull(DolbyVisionCodecs.profile7AsProfile8("hvc1.2.4.L153"))
        assertNull(DolbyVisionCodecs.profile7AsProfile8(null))
    }
}
