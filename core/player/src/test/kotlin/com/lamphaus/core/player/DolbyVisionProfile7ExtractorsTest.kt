package com.lamphaus.core.player

import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import com.lamphaus.core.player.dolbyvision.Profile7AccessUnitRewriter
import com.lamphaus.core.player.dolbyvision.TestRpus
import com.lamphaus.core.player.dolbyvision.TestRpus.Layer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class DolbyVisionProfile7ExtractorsTest {

    // Media3 decides this from Build.FINGERPRINT, which is null on the plain
    // JVM; set it so ParsableByteArray never reads the stub.
    @Before
    fun enforceParsableByteArrayLimits() = ParsableByteArray.setShouldEnforceLimitOnLegacyMethods(true)

    @After
    fun resetParsableByteArrayLimits() = ParsableByteArray.setShouldEnforceLimitOnLegacyMethods(null)

    private val slice = byteArrayOf(0x26, 0x01, 0xAF.toByte(), 0x00, 0x00, 0x03, 0x01, 0x42)
    private val enhancementLayer = byteArrayOf(0x7E, 0x01, 0x26, 0x09, 0x11)
    private val profile7Rpu = Profile7AccessUnitRewriter.escapeRpu(TestRpus.rpu(Layer.MEL))
    private val profile81Rpu = Profile7AccessUnitRewriter.escapeRpu(TestRpus.rpu(Layer.NONE))

    private val profile7Format = Format.Builder()
        .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
        .setCodecs("dvhe.07.06")
        .setWidth(3840)
        .setHeight(2160)
        .build()

    private fun annexB(vararg units: ByteArray): ByteArray =
        units.fold(ByteArray(0)) { acc, unit -> acc + byteArrayOf(0, 0, 0, 1) + unit }

    @Test
    fun `profile 7 track is announced as 8_1 and its samples rewritten`() {
        val run = extract(profile7Format, listOf(annexB(slice, enhancementLayer, profile7Rpu), annexB(slice, profile7Rpu)))
        assertEquals(listOf("dvhe.08.06"), run.video.formats.map { it.codecs })
        assertEquals(MimeTypes.VIDEO_DOLBY_VISION, run.video.formats.single().sampleMimeType)
        assertEquals(2, run.video.samples.size)
        run.video.samples.forEach { assertArrayEquals(annexB(slice, profile81Rpu), it.data) }
        assertEquals(C.BUFFER_FLAG_KEY_FRAME, run.video.samples.first().flags)
    }

    @Test
    fun `profile 7 without in-band RPUs passes through untouched`() {
        val sample = annexB(slice, slice)
        val run = extract(profile7Format, listOf(sample))
        assertEquals(listOf("dvhe.07.06"), run.video.formats.map { it.codecs })
        assertArrayEquals(sample, run.video.samples.single().data)
    }

    @Test
    fun `other video passes through untouched`() {
        val hevc = Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H265).setCodecs("hvc1.2.4.L153").build()
        val sample = annexB(slice, profile7Rpu)
        val run = extract(hevc, listOf(sample))
        assertSame(hevc, run.video.formats.single())
        assertArrayEquals(sample, run.video.samples.single().data)
    }

    @Test
    fun `supplemental data keeps its layout around the rewritten sample`() {
        val main = annexB(slice, enhancementLayer, profile7Rpu)
        val supplemental = byteArrayOf(0x0A, 0x0B, 0x0C)
        val run = extract(profile7Format, listOf(main), supplemental = supplemental)
        val rewritten = annexB(slice, profile81Rpu)
        val sample = run.video.samples.single()
        assertArrayEquals(sizePrefix(rewritten.size) + rewritten + supplemental, sample.data)
        assertEquals(C.BUFFER_FLAG_KEY_FRAME or C.BUFFER_FLAG_HAS_SUPPLEMENTAL_DATA, sample.flags)
    }

    @Test
    fun `a seek drops the bytes of the abandoned sample`() {
        val output = RecordingExtractorOutput()
        val extractor = ScriptedExtractor { track ->
            track.format(profile7Format)
            write(track, annexB(slice, profile7Rpu))
            track.sampleMetadata(0, C.BUFFER_FLAG_KEY_FRAME, annexB(slice, profile7Rpu).size, 0, null)
            write(track, annexB(slice, enhancementLayer))
        }
        val wrapped = DolbyVisionProfile7ExtractorsFactory { arrayOf<Extractor>(extractor) }.createExtractors().single()
        wrapped.init(output)
        wrapped.read(emptyInput(), PositionHolder())
        wrapped.seek(0, 0)
        extractor.script = { track ->
            write(track, annexB(slice, profile7Rpu))
            track.sampleMetadata(41_708, 0, annexB(slice, profile7Rpu).size, 0, null)
        }
        wrapped.read(emptyInput(), PositionHolder())
        assertEquals(2, output.video.samples.size)
        assertArrayEquals(annexB(slice, profile81Rpu), output.video.samples.last().data)
    }

    @Test
    fun `audio tracks are not wrapped`() {
        val output = RecordingExtractorOutput()
        val extractor = ScriptedExtractor { }
        DolbyVisionProfile7ExtractorsFactory { arrayOf<Extractor>(extractor) }.createExtractors().single().init(output)
        assertSame(output.audio, checkNotNull(extractor.output).track(2, C.TRACK_TYPE_AUDIO))
    }

    private fun sizePrefix(size: Int) =
        byteArrayOf((size ushr 24).toByte(), (size ushr 16).toByte(), (size ushr 8).toByte(), size.toByte())

    /** Writes [samples] as MatroskaExtractor does: start code and payload in separate calls, some via a DataReader. */
    private fun extract(format: Format, samples: List<ByteArray>, supplemental: ByteArray? = null): RecordingExtractorOutput {
        val output = RecordingExtractorOutput()
        val extractor = ScriptedExtractor { track ->
            track.format(format)
            samples.forEachIndexed { index, sample ->
                var flags = if (index == 0) C.BUFFER_FLAG_KEY_FRAME else 0
                var size = sample.size
                if (supplemental != null) {
                    flags = flags or C.BUFFER_FLAG_HAS_SUPPLEMENTAL_DATA
                    track.sampleData(ParsableByteArray(sizePrefix(sample.size)), 4, TrackOutput.SAMPLE_DATA_PART_SUPPLEMENTAL)
                    size += 4 + supplemental.size
                }
                write(track, sample)
                if (supplemental != null) {
                    track.sampleData(ParsableByteArray(supplemental), supplemental.size, TrackOutput.SAMPLE_DATA_PART_SUPPLEMENTAL)
                }
                track.sampleMetadata(index * 41_708L, flags, size, 0, null)
            }
        }
        val wrapped = DolbyVisionProfile7ExtractorsFactory { arrayOf<Extractor>(extractor) }.createExtractors().single()
        wrapped.init(output)
        wrapped.read(emptyInput(), PositionHolder())
        return output
    }

    private fun write(track: TrackOutput, sample: ByteArray) {
        val half = sample.size / 2
        track.sampleData(ParsableByteArray(sample.copyOfRange(0, half)), half)
        val reader = ByteArrayReader(sample.copyOfRange(half, sample.size))
        var remaining = sample.size - half
        while (remaining > 0) remaining -= track.sampleData(reader, remaining, false)
    }

    private fun emptyInput(): ExtractorInput = DefaultExtractorInput(ByteArrayReader(ByteArray(0)), 0, C.LENGTH_UNSET.toLong())

    /** Hands out at most 3 bytes per read, as a network source may. */
    private class ByteArrayReader(private val data: ByteArray) : DataReader {
        private var position = 0

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position == data.size) return C.RESULT_END_OF_INPUT
            val count = minOf(length, 3, data.size - position)
            data.copyInto(buffer, offset, position, position + count)
            position += count
            return count
        }
    }

    private class ScriptedExtractor(var script: ScriptedExtractor.(TrackOutput) -> Unit) : Extractor {
        var output: ExtractorOutput? = null
        private var track: TrackOutput? = null

        override fun sniff(input: ExtractorInput) = true

        override fun init(output: ExtractorOutput) {
            this.output = output
            track = output.track(1, C.TRACK_TYPE_VIDEO)
            output.endTracks()
        }

        override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
            script(checkNotNull(track))
            return Extractor.RESULT_END_OF_INPUT
        }

        override fun seek(position: Long, timeUs: Long) = Unit

        override fun release() = Unit
    }

    private class RecordedSample(val data: ByteArray, val flags: Int)

    private class RecordingTrackOutput : TrackOutput {
        val formats = mutableListOf<Format>()
        val samples = mutableListOf<RecordedSample>()
        private var pending = ByteArray(0)

        override fun format(format: Format) {
            formats += format
        }

        override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean, sampleDataPart: Int): Int {
            val buffer = ByteArray(length)
            val read = input.read(buffer, 0, length)
            if (read > 0) pending += buffer.copyOf(read)
            return read
        }

        override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
            val buffer = ByteArray(length)
            data.readBytes(buffer, 0, length)
            pending += buffer
        }

        override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?) {
            check(formats.isNotEmpty()) { "Sample before format" }
            val end = pending.size - offset
            samples += RecordedSample(pending.copyOfRange(end - size, end), flags)
            pending = pending.copyOfRange(end, pending.size)
        }
    }

    private class RecordingExtractorOutput : ExtractorOutput {
        val video = RecordingTrackOutput()
        val audio = RecordingTrackOutput()

        override fun track(id: Int, type: Int): TrackOutput = if (type == C.TRACK_TYPE_AUDIO) audio else video

        override fun endTracks() = Unit

        override fun seekMap(seekMap: SeekMap) = Unit
    }
}
