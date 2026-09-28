package com.lamphaus.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamTraitsParserTest {

    @Test
    fun `disc remux release names every trait`() {
        val traits = StreamTraitsParser.parse(listOf("Movie.2019.2160p.BluRay.REMUX.DV.HDR.HEVC.TrueHD.7.1.Atmos-GRP"))
        assertEquals(
            StreamTraits(
                height = 2160,
                videoCodec = VideoCodecFamily.HEVC,
                dolbyVision = true,
                hdr = true,
                remux = true,
                audio = EncodedAudioFormat.TRUEHD,
            ),
            traits,
        )
    }

    @Test
    fun `web release with atmos in dd plus is e-ac-3 joc`() {
        val traits = StreamTraitsParser.parse(listOf("Show.S01E01.2160p.WEB-DL.DDP5.1.Atmos.DV.H.265-GRP"))
        assertEquals(EncodedAudioFormat.EAC3_JOC, traits.audio)
        assertEquals(VideoCodecFamily.HEVC, traits.videoCodec)
        assertTrue(traits.dolbyVision)
    }

    @Test
    fun `provider formatted fields and tags are read together`() {
        val traits = StreamTraitsParser.parse(listOf("Torrentio\n4k", "DTS-HD MA 5.1", "HDR10+"))
        assertEquals(2160, traits.height)
        assertEquals(EncodedAudioFormat.DTS_HD, traits.audio)
        assertTrue(traits.hdr)
    }

    @Test
    fun `words containing trait letters are not traits`() {
        val traits = StreamTraitsParser.parse(listOf("DVDRip Address Addict HDRip.XviD"))
        assertEquals(false, traits.dolbyVision)
        assertEquals(EncodedAudioFormat.NONE, traits.audio)
        assertNull(traits.height)
    }
}

class SourceFitPolicyTest {

    private val dolbyVisionTv = PlaybackCapabilities(
        supportsDolbyVision = true,
        supportsHdr10 = true,
        supportsAc3Passthrough = true,
        supportsEac3Passthrough = true,
        supportsEac3JocPassthrough = true,
        supportsTrueHdPassthrough = true,
        hardwareVideoMaxHeight = mapOf(VideoCodecFamily.HEVC to 2160, VideoCodecFamily.AVC to 1080),
        dolbyVisionDecoderProfiles = setOf(5, 7, 8),
    )
    private val config = DevicePlaybackConfig()
    private val remux = StreamTraits(
        height = 2160,
        videoCodec = VideoCodecFamily.HEVC,
        dolbyVision = true,
        hdr = true,
        remux = true,
        audio = EncodedAudioFormat.TRUEHD,
    )

    @Test
    fun `demanding source the device handles plays natively`() {
        assertEquals(SourceFit(emptyList()), SourceFitPolicy.evaluate(remux, dolbyVisionTv, config))
    }

    @Test
    fun `ordinary source says nothing`() {
        val ordinary = StreamTraits(height = 1080, videoCodec = VideoCodecFamily.AVC)
        assertNull(SourceFitPolicy.evaluate(ordinary, dolbyVisionTv, config))
        assertNull(SourceFitPolicy.evaluate(StreamTraits(), dolbyVisionTv, config))
    }

    @Test
    fun `profile 7 remux without a profile 7 decoder plays as hdr10`() {
        val fit = SourceFitPolicy.evaluate(remux, dolbyVisionTv.copy(dolbyVisionDecoderProfiles = setOf(5, 8)), config)
        assertEquals(listOf(SourceFitNote.DOLBY_VISION_AS_HDR10), fit?.notes)
    }

    @Test
    fun `web dolby vision plays natively without a profile 7 decoder`() {
        val web = remux.copy(remux = false, audio = EncodedAudioFormat.EAC3_JOC)
        val fit = SourceFitPolicy.evaluate(web, dolbyVisionTv.copy(dolbyVisionDecoderProfiles = setOf(5, 8)), config)
        assertEquals(true, fit?.native)
    }

    @Test
    fun `hdr10 setting names the dolby vision fallback`() {
        val fit = SourceFitPolicy.evaluate(
            remux,
            dolbyVisionTv,
            config.copy(dolbyVisionHandling = DolbyVisionHandling.HDR10_BASE_LAYER),
        )
        assertEquals(listOf(SourceFitNote.DOLBY_VISION_AS_HDR10), fit?.notes)
    }

    @Test
    fun `sdr screen shows hdr as sdr`() {
        val sdr = dolbyVisionTv.copy(supportsDolbyVision = false, supportsHdr10 = false)
        assertEquals(listOf(SourceFitNote.HDR_AS_SDR), SourceFitPolicy.evaluate(remux, sdr, config)?.notes)
        val hdr10 = StreamTraits(height = 2160, videoCodec = VideoCodecFamily.HEVC, hdr = true)
        assertEquals(listOf(SourceFitNote.HDR_AS_SDR), SourceFitPolicy.evaluate(hdr10, sdr, config)?.notes)
    }

    @Test
    fun `no hardware decoder at that size is software decoding`() {
        val av1 = StreamTraits(height = 2160, videoCodec = VideoCodecFamily.AV1)
        assertEquals(listOf(SourceFitNote.SOFTWARE_VIDEO), SourceFitPolicy.evaluate(av1, dolbyVisionTv, config)?.notes)
        val avc4k = StreamTraits(height = 2160, videoCodec = VideoCodecFamily.AVC)
        assertEquals(listOf(SourceFitNote.SOFTWARE_VIDEO), SourceFitPolicy.evaluate(avc4k, dolbyVisionTv, config)?.notes)
    }

    @Test
    fun `unnamed 4k codec is taken as hevc`() {
        val fit = SourceFitPolicy.evaluate(StreamTraits(height = 2160), dolbyVisionTv, config)
        assertEquals(true, fit?.native)
    }

    @Test
    fun `receiver without that bitstream decodes the audio`() {
        val noTrueHd = dolbyVisionTv.copy(supportsTrueHdPassthrough = false)
        val fit = SourceFitPolicy.evaluate(remux, noTrueHd, config)
        assertEquals(listOf(SourceFitNote.AUDIO_DECODED), fit?.notes)
        assertEquals(EncodedAudioFormat.TRUEHD, fit?.decodedAudio)
    }

    @Test
    fun `decoding without any receiver is not news`() {
        val speakers = dolbyVisionTv.copy(
            supportsAc3Passthrough = false,
            supportsEac3Passthrough = false,
            supportsEac3JocPassthrough = false,
            supportsTrueHdPassthrough = false,
        )
        assertEquals(true, SourceFitPolicy.evaluate(remux, speakers, config)?.native)
        val forcedDecode = config.copy(audioOutputMode = AudioOutputMode.FORCE_DECODE)
        assertEquals(true, SourceFitPolicy.evaluate(remux, dolbyVisionTv, forcedDecode)?.native)
    }

    @Test
    fun `atmos in e-ac-3 passes to a receiver that takes e-ac-3`() {
        val eac3Only = dolbyVisionTv.copy(supportsEac3JocPassthrough = false)
        val web = StreamTraits(height = 1080, videoCodec = VideoCodecFamily.AVC, audio = EncodedAudioFormat.EAC3_JOC)
        assertEquals(true, SourceFitPolicy.evaluate(web, eac3Only, config)?.native)
    }

    @Test
    fun `several differences are all reported`() {
        val phone = PlaybackCapabilities(hardwareVideoMaxHeight = mapOf(VideoCodecFamily.HEVC to 1080))
        val fit = SourceFitPolicy.evaluate(remux, phone, config)
        assertEquals(listOf(SourceFitNote.SOFTWARE_VIDEO, SourceFitNote.HDR_AS_SDR), fit?.notes)
    }
}
