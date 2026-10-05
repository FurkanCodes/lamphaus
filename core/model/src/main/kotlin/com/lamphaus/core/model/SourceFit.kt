package com.lamphaus.core.model

/**
 * What a source says about itself before playback: the video and audio
 * traits its add-on text names (release names such as
 * "2160p.BluRay.REMUX.DV.HDR.HEVC.TrueHD.Atmos"). Unnamed traits stay unknown.
 */
data class StreamTraits(
    val height: Int? = null,
    val videoCodec: VideoCodecFamily? = null,
    val dolbyVision: Boolean = false,
    /** HDR10, HDR10+, HLG, or plain "HDR". */
    val hdr: Boolean = false,
    /** Disc remuxes; with Dolby Vision these are almost always profile 7. */
    val remux: Boolean = false,
    val audio: EncodedAudioFormat = EncodedAudioFormat.NONE,
)

fun StreamCandidate.streamTraits(): StreamTraits =
    StreamTraitsParser.parse(listOfNotNull(filename, name, title, description, quality) + tags)

object StreamTraitsParser {

    fun parse(texts: List<String>): StreamTraits {
        val text = texts.joinToString(" ")
        val remux = REMUX.containsMatchIn(text)
        return StreamTraits(
            height = HEIGHTS.firstOrNull { (pattern, _) -> pattern.containsMatchIn(text) }?.second,
            videoCodec = CODECS.firstOrNull { (pattern, _) -> pattern.containsMatchIn(text) }?.second,
            dolbyVision = DOLBY_VISION.containsMatchIn(text),
            hdr = HDR.containsMatchIn(text),
            remux = remux,
            audio = audio(text, remux),
        )
    }

    private fun audio(text: String, remux: Boolean): EncodedAudioFormat {
        val atmos = ATMOS.containsMatchIn(text)
        return when {
            TRUEHD.containsMatchIn(text) -> EncodedAudioFormat.TRUEHD
            DTS_HD.containsMatchIn(text) -> EncodedAudioFormat.DTS_HD
            DTS.containsMatchIn(text) -> EncodedAudioFormat.DTS
            EAC3.containsMatchIn(text) -> if (atmos) EncodedAudioFormat.EAC3_JOC else EncodedAudioFormat.EAC3
            // Atmos alone: discs carry it in TrueHD, streaming services in E-AC-3.
            atmos -> if (remux) EncodedAudioFormat.TRUEHD else EncodedAudioFormat.EAC3_JOC
            AC3.containsMatchIn(text) -> EncodedAudioFormat.AC3
            else -> EncodedAudioFormat.NONE
        }
    }

    private fun token(pattern: String) = Regex("(?<![A-Za-z0-9])(?:$pattern)(?![A-Za-z0-9])", RegexOption.IGNORE_CASE)

    private val HEIGHTS = listOf(
        token("2160p|4k|uhd") to 2160,
        token("1440p|2k") to 1440,
        token("1080p|1080i|fhd") to 1080,
        token("720p") to 720,
        token("576p|480p") to 480,
    )
    private val CODECS = listOf(
        token("hevc|h[ ._-]?265|x265") to VideoCodecFamily.HEVC,
        token("av1") to VideoCodecFamily.AV1,
        token("vp9") to VideoCodecFamily.VP9,
        token("avc|h[ ._-]?264|x264") to VideoCodecFamily.AVC,
    )
    private val DOLBY_VISION = token("dolby[ ._-]?vision|dovi|dv")
    private val HDR = token("hdr10\\+?|hdr10plus|hdr|hlg")
    private val REMUX = token("remux|bdremux")
    private val ATMOS = token("atmos")
    private val TRUEHD = token("true[ ._-]?hd")
    private val DTS_HD = token("dts[ ._:-]?(?:hd|x|ma)(?:[ ._-]?ma)?")
    private val DTS = token("dts")
    private val EAC3 = token("e[ ._-]?ac[ ._-]?3|ddp(?:[ ._]?[257][ ._]?[01])?|dd\\+|dolby digital plus")
    private val AC3 = token("ac[ ._-]?3|dd[ ._]?[257][ ._][01]|dd|dolby digital")
}

/** One way this device will play a source differently from how it was made. */
enum class SourceFitNote {
    /** No hardware decoder for this codec at this size: playback may stutter. */
    SOFTWARE_VIDEO,

    /** The Dolby Vision layer is dropped; the HDR10 base layer plays. */
    DOLBY_VISION_AS_HDR10,

    /** The screen cannot show HDR, so it is shown in SDR. */
    HDR_AS_SDR,

    /** The receiver cannot take this bitstream; it is decoded on the device. */
    AUDIO_DECODED,
}

/**
 * How a source will play here. Empty [notes] means natively: nothing is
 * dropped, converted, or decoded in software.
 */
data class SourceFit(
    val notes: List<SourceFitNote>,
    /** The bitstream behind [SourceFitNote.AUDIO_DECODED]. */
    val decodedAudio: EncodedAudioFormat = EncodedAudioFormat.NONE,
) {
    val native: Boolean get() = notes.isEmpty()
}

/**
 * Predicts, from a source's named traits, what the player will do with it on
 * this device and route (the same decisions [DolbyVisionPolicy] and
 * [AudioRoutePolicy] make once playback starts). Returns null when there is
 * nothing worth saying: the traits are unknown, or the source is ordinary
 * enough (1080p SDR stereo) that "plays natively" would be noise.
 */
object SourceFitPolicy {

    fun evaluate(traits: StreamTraits, capabilities: PlaybackCapabilities, config: DevicePlaybackConfig): SourceFit? {
        val notes = mutableListOf<SourceFitNote>()
        val height = traits.height ?: 0
        // 4K and HDR releases are HEVC unless named otherwise.
        val codec = traits.videoCodec
            ?: VideoCodecFamily.HEVC.takeIf { height >= 2160 || traits.dolbyVision || traits.hdr }
        if (codec != null) {
            val hardwareHeight = capabilities.hardwareVideoMaxHeight[codec] ?: 0
            if (hardwareHeight < maxOf(height, 1)) notes += SourceFitNote.SOFTWARE_VIDEO
        }

        val displayHdr = capabilities.supportsHdr10 || capabilities.supportsHdr10Plus || capabilities.supportsHlg
        if (traits.dolbyVision) {
            val handling = config.dolbyVisionHandling
            val profiles = capabilities.dolbyVisionDecoderProfiles
            // Remuxes are profile 7: played as is, or rewritten as 8.1.
            val decoderTakesIt = profiles.isNotEmpty() &&
                (!traits.remux || PROFILE_7 in profiles || DolbyVisionPolicy.convertsProfile7(handling, profiles))
            val native = capabilities.supportsDolbyVision && decoderTakesIt &&
                handling != DolbyVisionHandling.HDR10_BASE_LAYER && handling != DolbyVisionHandling.DISABLED
            if (!native) notes += if (displayHdr) SourceFitNote.DOLBY_VISION_AS_HDR10 else SourceFitNote.HDR_AS_SDR
        } else if (traits.hdr && !displayHdr) {
            notes += SourceFitNote.HDR_AS_SDR
        }

        // Decoding is the normal path without a receiver (phone speakers, TV
        // speakers); it is only news when a receiver takes other bitstreams.
        val receiver = config.audioOutputMode != AudioOutputMode.FORCE_DECODE && !config.nightListening &&
            PASSTHROUGH_FORMATS.any { AudioRoutePolicy.routeSupports(capabilities, it) }
        val audioPasses = traits.audio != EncodedAudioFormat.NONE && receiver && passes(capabilities, traits.audio)
        if (traits.audio != EncodedAudioFormat.NONE && receiver && !audioPasses) {
            notes += SourceFitNote.AUDIO_DECODED
        }

        if (notes.isNotEmpty()) {
            return SourceFit(
                notes = notes,
                decodedAudio = traits.audio.takeIf { SourceFitNote.AUDIO_DECODED in notes } ?: EncodedAudioFormat.NONE,
            )
        }
        val demanding = height >= 2160 || traits.dolbyVision || traits.hdr ||
            (audioPasses && traits.audio in PREMIUM_AUDIO)
        return if (demanding) SourceFit(emptyList()) else null
    }

    /** Atmos in E-AC-3 still reaches a receiver that takes plain E-AC-3. */
    private fun passes(capabilities: PlaybackCapabilities, format: EncodedAudioFormat): Boolean =
        AudioRoutePolicy.routeSupports(capabilities, format) ||
            (format == EncodedAudioFormat.EAC3_JOC && capabilities.supportsEac3Passthrough)

    private const val PROFILE_7 = 7
    private val PASSTHROUGH_FORMATS = EncodedAudioFormat.entries - EncodedAudioFormat.NONE
    private val PREMIUM_AUDIO = setOf(EncodedAudioFormat.TRUEHD, EncodedAudioFormat.DTS_HD, EncodedAudioFormat.EAC3_JOC)
}
