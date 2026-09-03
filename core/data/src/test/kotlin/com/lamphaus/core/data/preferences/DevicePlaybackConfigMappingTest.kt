package com.lamphaus.core.data.preferences

import com.lamphaus.core.model.FrameRateMatching
import com.lamphaus.core.model.PlaybackEngineKind
import org.junit.Assert.assertEquals
import org.junit.Test

class DevicePlaybackConfigMappingTest {

    @Test
    fun `missing keys fall back to shipped defaults`() {
        val config = devicePlaybackConfigFromKeys(
            engine = null,
            dolbyVision = null,
            frameRateMatching = null,
            resolutionMatching = null,
            audioOutputMode = null,
            decoderPriority = null,
            downmixMode = null,
        )
        assertEquals(PlaybackEngineKind.AUTO, config.engineKind)
        assertEquals(FrameRateMatching.SEAMLESS_ONLY, config.frameRateMatching)
    }

    @Test
    fun `unknown enum names degrade instead of crashing the read`() {
        val config = devicePlaybackConfigFromKeys(
            engine = "QUANTUM",
            dolbyVision = "AUTO",
            frameRateMatching = "ALWAYS",
            resolutionMatching = "MATCH_SOURCE",
            audioOutputMode = "FORCE_DECODE",
            decoderPriority = "SOFTWARE_FIRST",
            downmixMode = "STEREO",
        )
        assertEquals(PlaybackEngineKind.AUTO, config.engineKind)
        assertEquals(FrameRateMatching.ALWAYS, config.frameRateMatching)
    }
}
