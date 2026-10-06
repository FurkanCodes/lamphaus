package com.lamphaus.core.player

import android.media.MediaCodecList
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SniffFailure
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.text.SubtitleParser
import com.lamphaus.core.model.DolbyVisionHandling
import com.lamphaus.core.player.dolbyvision.DolbyVisionCodecs
import com.lamphaus.core.player.dolbyvision.Profile7AccessUnitRewriter
import java.io.EOFException
import com.lamphaus.core.model.DolbyVisionPolicy as DolbyVisionModelPolicy

/**
 * Dolby Vision profiles (5, 7, 8, …) the device's decoders accept, read once:
 * decoders never change while the app runs. Emulator decoders are skipped, as
 * the player skips them (see [PlaybackEnginePolicy]).
 */
internal object DolbyVisionDecoders {
    val profiles: Set<Int> by lazy {
        if (DeviceEnvironment.isAndroidEmulator()) return@lazy emptySet()
        runCatching { MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.toList() }
            .getOrDefault(emptyList())
            .filter { !it.isEncoder && MimeTypes.VIDEO_DOLBY_VISION in it.supportedTypes }
            .flatMap { info ->
                runCatching { info.getCapabilitiesForType(MimeTypes.VIDEO_DOLBY_VISION).profileLevels.toList() }
                    .getOrDefault(emptyList())
            }
            // Each DolbyVisionProfile* constant is 1 shl its profile number.
            .mapNotNull { level -> Integer.numberOfTrailingZeros(level.profile).takeIf { level.profile > 0 } }
            .toSet()
    }
}

/**
 * Plays Dolby Vision profile 7 (UHD Blu-ray remuxes, both layers in one
 * track) as profile 8.1, as Nuvio does, on devices whose decoder takes 8 but
 * not 7: the track is announced as `dvhe.08`, the enhancement layer is
 * dropped, and each RPU is converted ([Profile7AccessUnitRewriter]). The
 * picture is the same HDR10 base layer with Dolby Vision's dynamic metadata,
 * instead of plain HDR10.
 *
 * A track is only rewritten once its first sample shows in-band RPUs, so
 * dual-track profile 7 (an enhancement layer in its own track) and every
 * other format pass through untouched.
 */
@UnstableApi
internal class DolbyVisionProfile7ExtractorsFactory(private val delegate: ExtractorsFactory) : ExtractorsFactory {

    @Deprecated("Media3's legacy subtitle path; forwarded so the wrapped factory keeps its configuration.")
    @OptIn(ExperimentalApi::class)
    override fun experimentalSetTextTrackTranscodingEnabled(textTrackTranscodingEnabled: Boolean): ExtractorsFactory {
        @Suppress("DEPRECATION")
        delegate.experimentalSetTextTrackTranscodingEnabled(textTrackTranscodingEnabled)
        return this
    }

    override fun setSubtitleParserFactory(subtitleParserFactory: SubtitleParser.Factory): ExtractorsFactory {
        delegate.setSubtitleParserFactory(subtitleParserFactory)
        return this
    }

    @OptIn(ExperimentalApi::class)
    override fun experimentalSetCodecsToParseWithinGopSampleDependencies(
        codecsToParseWithinGopSampleDependencies: Int,
    ): ExtractorsFactory {
        delegate.experimentalSetCodecsToParseWithinGopSampleDependencies(codecsToParseWithinGopSampleDependencies)
        return this
    }

    override fun createExtractors(): Array<Extractor> = wrap(delegate.createExtractors())

    override fun createExtractors(uri: Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> =
        wrap(delegate.createExtractors(uri, responseHeaders))

    private fun wrap(extractors: Array<Extractor>): Array<Extractor> =
        Array(extractors.size) { Profile7ConvertingExtractor(extractors[it]) }

    companion object {
        /** [factory], converting profile 7 when [handling] and this device's decoders call for it. */
        fun forDevice(factory: ExtractorsFactory, handling: DolbyVisionHandling): ExtractorsFactory =
            if (DolbyVisionModelPolicy.convertsProfile7(handling, DolbyVisionDecoders.profiles)) {
                DolbyVisionProfile7ExtractorsFactory(factory)
            } else {
                factory
            }
    }
}

@UnstableApi
private class Profile7ConvertingExtractor(private val delegate: Extractor) : Extractor {
    private var output: Profile7ConvertingExtractorOutput? = null

    override fun sniff(input: ExtractorInput): Boolean = delegate.sniff(input)

    override fun getSniffFailureDetails(): List<SniffFailure> = delegate.sniffFailureDetails

    override fun init(output: ExtractorOutput) {
        val converting = Profile7ConvertingExtractorOutput(output)
        this.output = converting
        delegate.init(converting)
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int = delegate.read(input, seekPosition)

    override fun seek(position: Long, timeUs: Long) {
        output?.discardPartialSamples()
        delegate.seek(position, timeUs)
    }

    override fun release() = delegate.release()

    // Media3 checks the real extractor (e.g. for MP3 seeking) through this.
    override fun getUnderlyingImplementation(): Extractor = delegate.underlyingImplementation
}

@UnstableApi
private class Profile7ConvertingExtractorOutput(private val delegate: ExtractorOutput) : ExtractorOutput {
    private val videoTracks = HashMap<Int, Profile7ConvertingTrackOutput>()

    override fun track(id: Int, type: Int): TrackOutput {
        if (type != C.TRACK_TYPE_VIDEO) return delegate.track(id, type)
        return videoTracks.getOrPut(id) { Profile7ConvertingTrackOutput(delegate.track(id, type)) }
    }

    override fun endTracks() = delegate.endTracks()

    override fun seekMap(seekMap: SeekMap) = delegate.seekMap(seekMap)

    fun discardPartialSamples() = videoTracks.values.forEach { it.discardPartialSample() }
}

/**
 * Forwards everything untouched until the track's format is Dolby Vision
 * profile 7. From then on it holds the format and each sample's bytes until
 * the sample is complete, so the first sample can decide whether the track is
 * rewritten and every later one can be rewritten whole.
 */
@UnstableApi
private class Profile7ConvertingTrackOutput(private val delegate: TrackOutput) : TrackOutput {
    private enum class Mode { PASS_THROUGH, UNDECIDED, CONVERTING }

    private var mode = Mode.PASS_THROUGH
    private var heldFormat: Format? = null
    private var pending = ByteArray(0)
    private var pendingLength = 0

    /** The current sample carries encryption data; encrypted samples are never rewritten. */
    private var pendingEncrypted = false
    private val rewriter by lazy(LazyThreadSafetyMode.NONE) { Profile7AccessUnitRewriter() }
    private val forwardBuffer = ParsableByteArray()
    private val sizeField = ByteArray(4)

    override fun durationUs(durationUs: Long) = delegate.durationUs(durationUs)

    override fun format(format: Format) {
        val profile8Codecs = DolbyVisionCodecs.profile7AsProfile8(format.codecs)
            .takeIf { format.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION }
        when {
            profile8Codecs == null -> {
                // Not (or no longer) profile 7: anything held goes out as it came.
                flushPending()
                heldFormat = null
                mode = Mode.PASS_THROUGH
                delegate.format(format)
            }
            mode == Mode.CONVERTING -> delegate.format(format.buildUpon().setCodecs(profile8Codecs).build())
            else -> {
                heldFormat = format
                mode = Mode.UNDECIDED
            }
        }
    }

    override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean, sampleDataPart: Int): Int {
        if (mode == Mode.PASS_THROUGH) return delegate.sampleData(input, length, allowEndOfInput, sampleDataPart)
        ensureCapacity(pendingLength + length)
        val read = input.read(pending, pendingLength, length)
        if (read == C.RESULT_END_OF_INPUT) {
            if (allowEndOfInput) return C.RESULT_END_OF_INPUT
            throw EOFException()
        }
        pendingLength += read
        if (sampleDataPart == TrackOutput.SAMPLE_DATA_PART_ENCRYPTION) pendingEncrypted = true
        return read
    }

    override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
        if (mode == Mode.PASS_THROUGH) return delegate.sampleData(data, length, sampleDataPart)
        ensureCapacity(pendingLength + length)
        data.readBytes(pending, pendingLength, length)
        pendingLength += length
        if (sampleDataPart == TrackOutput.SAMPLE_DATA_PART_ENCRYPTION) pendingEncrypted = true
    }

    override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?) {
        if (mode == Mode.PASS_THROUGH) return delegate.sampleMetadata(timeUs, flags, size, offset, cryptoData)
        val end = pendingLength - offset
        val start = end - size
        val access = if (start < 0 || cryptoData != null || pendingEncrypted) null else accessUnit(flags, start, end)
        if (mode == Mode.UNDECIDED) decide(access)
        if (mode == Mode.CONVERTING && access != null) {
            val length = rewriter.rewrite(pending, access.first, access.last + 1)
            var written = length
            if (flags and C.BUFFER_FLAG_HAS_SUPPLEMENTAL_DATA != 0) {
                // [main size][main][supplemental], as SampleDataQueue reads it back.
                for (i in 0..3) sizeField[i] = (length ushr (24 - 8 * i)).toByte()
                forward(sizeField, 0, 4)
                forward(rewriter.output, 0, length)
                forward(pending, access.last + 1, end - access.last - 1)
                written += 4 + end - access.last - 1
            } else {
                forward(rewriter.output, 0, length)
            }
            delegate.sampleMetadata(timeUs, flags, written, 0, null)
        } else if (start < 0) {
            // Not a layout Media3's extractors produce; give the bytes back as they came.
            flushPending()
            delegate.sampleMetadata(timeUs, flags, size, offset, cryptoData)
            return
        } else {
            forward(pending, start, size)
            delegate.sampleMetadata(timeUs, flags, size, 0, cryptoData)
        }
        // Bytes after the sample already belong to the next one.
        pending.copyInto(pending, 0, end, pendingLength)
        pendingLength = offset
        pendingEncrypted = false
        if (mode == Mode.PASS_THROUGH) flushPending()
    }

    /** Settles the held format on the track's first sample: rewritten only when RPUs travel in-band. */
    private fun decide(access: IntRange?) {
        val format = checkNotNull(heldFormat)
        heldFormat = null
        if (access != null && Profile7AccessUnitRewriter.containsRpu(pending, access.first, access.last + 1)) {
            mode = Mode.CONVERTING
            delegate.format(format.buildUpon().setCodecs(DolbyVisionCodecs.profile7AsProfile8(format.codecs)).build())
        } else {
            mode = Mode.PASS_THROUGH
            delegate.format(format)
        }
    }

    /** The video bytes of the sample in `pending[start, end)`, skipping a supplemental-data size prefix. */
    private fun accessUnit(flags: Int, start: Int, end: Int): IntRange? {
        if (flags and C.BUFFER_FLAG_HAS_SUPPLEMENTAL_DATA == 0) return start until end
        if (end - start < 4) return null
        var mainSize = 0
        for (i in 0..3) mainSize = (mainSize shl 8) or (pending[start + i].toInt() and 0xFF)
        if (mainSize < 0 || mainSize > end - start - 4) return null
        return (start + 4) until (start + 4 + mainSize)
    }

    /** Drops the bytes of a sample the extractor abandoned on seek. */
    fun discardPartialSample() {
        pendingLength = 0
        pendingEncrypted = false
    }

    private fun flushPending() {
        if (pendingLength > 0) forward(pending, 0, pendingLength)
        pendingLength = 0
        pendingEncrypted = false
    }

    private fun forward(data: ByteArray, from: Int, length: Int) {
        if (length == 0) return
        forwardBuffer.reset(data, from + length)
        forwardBuffer.setPosition(from)
        delegate.sampleData(forwardBuffer, length, TrackOutput.SAMPLE_DATA_PART_MAIN)
    }

    private fun ensureCapacity(capacity: Int) {
        if (pending.size < capacity) pending = pending.copyOf(maxOf(capacity, pending.size * 2, MIN_CAPACITY))
    }

    private companion object {
        const val MIN_CAPACITY = 1 shl 20
    }
}
