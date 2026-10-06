package com.lamphaus.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DolbyVisionPolicyTest {

    @Test
    fun `auto prefers native 5 and 8 on a dv display`() {
        for (profile in listOf(DolbyVisionProfile.PROFILE_5, DolbyVisionProfile.PROFILE_8)) {
            assertEquals(
                DolbyVisionAction.NATIVE,
                DolbyVisionPolicy.resolve(
                    DolbyVisionHandling.AUTO, profile,
                    displaySupportsDolbyVision = true, outputIsHdr = true, libDoviAvailable = true,
                ),
            )
        }
    }

    @Test
    fun `profile 7 converts when libdovi ships and falls back to hdr10 otherwise`() {
        assertEquals(
            DolbyVisionAction.CONVERT_PROFILE7_TO_81,
            DolbyVisionPolicy.resolve(
                DolbyVisionHandling.AUTO, DolbyVisionProfile.PROFILE_7,
                displaySupportsDolbyVision = true, outputIsHdr = true, libDoviAvailable = true,
            ),
        )
        assertEquals(
            DolbyVisionAction.HDR10_BASE_LAYER,
            DolbyVisionPolicy.resolve(
                DolbyVisionHandling.AUTO, DolbyVisionProfile.PROFILE_7,
                displaySupportsDolbyVision = true, outputIsHdr = true, libDoviAvailable = false,
            ),
        )
    }

    @Test
    fun `native only refuses conversion and tone mapping`() {
        assertEquals(
            DolbyVisionAction.HDR10_BASE_LAYER,
            DolbyVisionPolicy.resolve(
                DolbyVisionHandling.NATIVE_ONLY, DolbyVisionProfile.PROFILE_7,
                displaySupportsDolbyVision = true, outputIsHdr = true, libDoviAvailable = true,
            ),
        )
        assertEquals(
            DolbyVisionAction.HDR10_BASE_LAYER,
            DolbyVisionPolicy.resolve(
                DolbyVisionHandling.NATIVE_ONLY, DolbyVisionProfile.PROFILE_5,
                displaySupportsDolbyVision = false, outputIsHdr = false, libDoviAvailable = true,
            ),
        )
    }

    @Test
    fun `tone mapping only when output is sdr and nothing else applies`() {
        assertEquals(
            DolbyVisionAction.TONE_MAP_TO_SDR,
            DolbyVisionPolicy.resolve(
                DolbyVisionHandling.AUTO, DolbyVisionProfile.PROFILE_5,
                displaySupportsDolbyVision = false, outputIsHdr = false, libDoviAvailable = true,
            ),
        )
        assertEquals(
            DolbyVisionAction.NATIVE,
            DolbyVisionPolicy.resolve(
                DolbyVisionHandling.AUTO, DolbyVisionProfile.PROFILE_5,
                displaySupportsDolbyVision = false, outputIsHdr = true, libDoviAvailable = true,
            ),
        )
    }

    @Test
    fun `explicit user policies win`() {
        assertEquals(
            DolbyVisionAction.DISABLED,
            DolbyVisionPolicy.resolve(
                DolbyVisionHandling.DISABLED, DolbyVisionProfile.PROFILE_5,
                displaySupportsDolbyVision = true, outputIsHdr = true, libDoviAvailable = true,
            ),
        )
        assertEquals(
            DolbyVisionAction.HDR10_BASE_LAYER,
            DolbyVisionPolicy.resolve(
                DolbyVisionHandling.HDR10_BASE_LAYER, DolbyVisionProfile.PROFILE_5,
                displaySupportsDolbyVision = true, outputIsHdr = true, libDoviAvailable = true,
            ),
        )
        assertEquals(
            DolbyVisionAction.CONVERT_PROFILE7_TO_81,
            DolbyVisionPolicy.resolve(
                DolbyVisionHandling.CONVERT_PROFILE7_TO_81, DolbyVisionProfile.PROFILE_7,
                displaySupportsDolbyVision = false, outputIsHdr = true, libDoviAvailable = true,
            ),
        )
    }

    @Test
    fun `non dv streams pass hdr untouched`() {
        assertEquals(
            DolbyVisionAction.NATIVE,
            DolbyVisionPolicy.resolve(
                DolbyVisionHandling.AUTO, DolbyVisionProfile.NONE,
                displaySupportsDolbyVision = false, outputIsHdr = true, libDoviAvailable = false,
            ),
        )
    }

    @Test
    fun `auto converts profile 7 for a decoder that takes 8 but not 7, as Nuvio does`() {
        fun auto(profile: Int?, decoders: Set<Int>, display: Boolean?) =
            DolbyVisionPolicy.trackAction(DolbyVisionHandling.AUTO, profile, decoders, display)
        assertEquals(DolbyVisionTrackAction.CONVERT_TO_MEL, auto(7, setOf(5, 8), display = true))
        assertEquals(DolbyVisionTrackAction.CONVERT_TO_MEL, auto(7, setOf(5, 8), display = null))
        assertEquals(DolbyVisionTrackAction.PASS_THROUGH, auto(7, setOf(5, 7, 8), display = true))
        assertEquals(DolbyVisionTrackAction.STRIP_TO_BASE_LAYER, auto(7, setOf(5), display = true))
        assertEquals(DolbyVisionTrackAction.STRIP_TO_BASE_LAYER, auto(7, emptySet(), display = true))
        assertEquals(DolbyVisionTrackAction.PASS_THROUGH, auto(8, setOf(5, 8), display = true))
        // A display known to lack Dolby Vision plays the HDR10 base layer of 7 and 8.
        assertEquals(DolbyVisionTrackAction.STRIP_TO_BASE_LAYER, auto(7, setOf(5, 8), display = false))
        assertEquals(DolbyVisionTrackAction.STRIP_TO_BASE_LAYER, auto(8, setOf(5, 8), display = false))
        // Profile 5 has no base layer; unknown profiles are left alone.
        assertEquals(DolbyVisionTrackAction.PASS_THROUGH, auto(5, setOf(5), display = false))
        assertEquals(DolbyVisionTrackAction.PASS_THROUGH, auto(null, setOf(8), display = false))
    }

    @Test
    fun `explicit Dolby Vision settings override auto`() {
        fun action(handling: DolbyVisionHandling, profile: Int) =
            DolbyVisionPolicy.trackAction(handling, profile, setOf(5, 7, 8), displayDolbyVision = true)
        assertEquals(DolbyVisionTrackAction.CONVERT_TO_81, action(DolbyVisionHandling.CONVERT_PROFILE7_TO_81, 7))
        assertEquals(DolbyVisionTrackAction.PASS_THROUGH, action(DolbyVisionHandling.CONVERT_PROFILE7_TO_81, 8))
        assertEquals(DolbyVisionTrackAction.STRIP_TO_BASE_LAYER, action(DolbyVisionHandling.HDR10_BASE_LAYER, 7))
        assertEquals(DolbyVisionTrackAction.STRIP_TO_BASE_LAYER, action(DolbyVisionHandling.DISABLED, 8))
        assertEquals(DolbyVisionTrackAction.PASS_THROUGH, action(DolbyVisionHandling.DISABLED, 5))
        assertEquals(DolbyVisionTrackAction.PASS_THROUGH, action(DolbyVisionHandling.NATIVE_ONLY, 7))
    }

    @Test
    fun `PLY-DV-01 profile 5 to 8_1 signals in auto and converts with Convert to DV8_1`() {
        fun p5(handling: DolbyVisionHandling, enabled: Boolean) =
            DolbyVisionPolicy.trackAction(handling, 5, setOf(8), displayDolbyVision = true, profile5To81 = enabled)
        assertEquals(DolbyVisionTrackAction.PASS_THROUGH, p5(DolbyVisionHandling.AUTO, enabled = false))
        assertEquals(DolbyVisionTrackAction.SIGNAL_PROFILE5_AS_81, p5(DolbyVisionHandling.AUTO, enabled = true))
        assertEquals(DolbyVisionTrackAction.CONVERT_PROFILE5_TO_81, p5(DolbyVisionHandling.CONVERT_PROFILE7_TO_81, enabled = true))
        assertEquals(DolbyVisionTrackAction.PASS_THROUGH, p5(DolbyVisionHandling.NATIVE_ONLY, enabled = true))
        assertEquals(DolbyVisionTrackAction.PASS_THROUGH, p5(DolbyVisionHandling.DISABLED, enabled = true))
    }

    @Test
    fun `PLY-DV-01 preserving the mapping applies to Convert to DV8_1 only`() {
        assertEquals(
            DolbyVisionTrackAction.CONVERT_TO_81_PRESERVING_MAPPING,
            DolbyVisionPolicy.trackAction(DolbyVisionHandling.CONVERT_PROFILE7_TO_81, 7, setOf(8), true, preserveMapping = true),
        )
        assertEquals(
            DolbyVisionTrackAction.CONVERT_TO_MEL,
            DolbyVisionPolicy.trackAction(DolbyVisionHandling.AUTO, 7, setOf(8), true, preserveMapping = true),
        )
    }

    @Test
    fun `SHR-PROD-12 a remux plays as Dolby Vision natively or converted`() {
        assertEquals(true, DolbyVisionPolicy.playsProfile7AsDolbyVision(DolbyVisionHandling.AUTO, setOf(5, 8), true))
        assertEquals(true, DolbyVisionPolicy.playsProfile7AsDolbyVision(DolbyVisionHandling.AUTO, setOf(7), true))
        assertEquals(false, DolbyVisionPolicy.playsProfile7AsDolbyVision(DolbyVisionHandling.AUTO, setOf(5), true))
        assertEquals(false, DolbyVisionPolicy.playsProfile7AsDolbyVision(DolbyVisionHandling.HDR10_BASE_LAYER, setOf(8), true))
        assertEquals(false, DolbyVisionPolicy.playsProfile7AsDolbyVision(DolbyVisionHandling.AUTO, setOf(8), false))
    }
}

class AudioRoutePolicyTest {

    private val fullReceiver = PlaybackCapabilities(
        supportsAc3Passthrough = true,
        supportsEac3Passthrough = true,
        supportsEac3JocPassthrough = true,
        supportsTrueHdPassthrough = true,
        supportsDtsPassthrough = true,
    )

    private val tvSpeakers = PlaybackCapabilities()

    @Test
    fun `auto passes immersive formats to a capable receiver`() {
        assertEquals(
            AudioOutputDecision.Passthrough(EncodedAudioFormat.EAC3_JOC),
            AudioRoutePolicy.resolve(AudioOutputMode.AUTO, DownmixMode.AUTO, fullReceiver, EncodedAudioFormat.EAC3_JOC),
        )
        assertEquals(
            AudioOutputDecision.Passthrough(EncodedAudioFormat.TRUEHD),
            AudioRoutePolicy.resolve(AudioOutputMode.AUTO, DownmixMode.AUTO, fullReceiver, EncodedAudioFormat.TRUEHD),
        )
    }

    @Test
    fun `auto decodes with a safe downmix when the route cannot carry the format`() {
        assertEquals(
            AudioOutputDecision.Decode(toStereo = true),
            AudioRoutePolicy.resolve(AudioOutputMode.AUTO, DownmixMode.AUTO, tvSpeakers, EncodedAudioFormat.TRUEHD),
        )
        // A multichannel-capable route keeps the original mix on AUTO.
        val pcm51 = tvSpeakers.copy(maxPcmChannelCount = 6)
        assertEquals(
            AudioOutputDecision.Decode(toStereo = false),
            AudioRoutePolicy.resolve(AudioOutputMode.AUTO, DownmixMode.AUTO, pcm51, EncodedAudioFormat.TRUEHD),
        )
    }

    @Test
    fun `force decode honors the downmix setting`() {
        assertEquals(
            AudioOutputDecision.Decode(toStereo = true),
            AudioRoutePolicy.resolve(AudioOutputMode.FORCE_DECODE, DownmixMode.STEREO, fullReceiver, EncodedAudioFormat.EAC3),
        )
        assertEquals(
            AudioOutputDecision.Decode(toStereo = false),
            AudioRoutePolicy.resolve(AudioOutputMode.FORCE_DECODE, DownmixMode.NEVER, fullReceiver, EncodedAudioFormat.EAC3),
        )
    }

    @Test
    fun `force passthrough still respects route capability`() {
        assertEquals(
            AudioOutputDecision.Passthrough(EncodedAudioFormat.DTS),
            AudioRoutePolicy.resolve(AudioOutputMode.FORCE_PASSTHROUGH, DownmixMode.AUTO, fullReceiver, EncodedAudioFormat.DTS),
        )
        assertEquals(
            AudioOutputDecision.Decode(toStereo = false),
            AudioRoutePolicy.resolve(AudioOutputMode.FORCE_PASSTHROUGH, DownmixMode.NEVER, tvSpeakers, EncodedAudioFormat.DTS),
        )
        assertEquals(
            AudioOutputDecision.Decode(toStereo = true),
            AudioRoutePolicy.resolve(AudioOutputMode.FORCE_PASSTHROUGH, DownmixMode.AUTO, tvSpeakers, EncodedAudioFormat.DTS),
        )
    }
}

class SubtitleStylePolicyTest {
    @Test
    fun `editor ranges clamp`() {
        assertEquals(50, SubtitleStylePolicy.clampSizePercent(10))
        assertEquals(300, SubtitleStylePolicy.clampSizePercent(1000))
        assertEquals(0f, SubtitleStylePolicy.clampPositionFraction(-1f))
        assertEquals(1f, SubtitleStylePolicy.clampOpacity(2f))
        assertEquals(8f, SubtitleStylePolicy.clampOutlineWidthDp(99f))
    }
}
