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
        // PLY-NET-01: both streaming switches ship off, like Nuvio.
        assertEquals(false, config.nativeMemoryBuffer)
        assertEquals(false, config.parallelConnections)
        assertEquals(2, config.parallelConnectionCount)
        assertEquals(16 * 1024, config.parallelChunkSizeKb)
    }

    @Test
    fun `PLY-NET-01 corrupt streaming values are clamped to offered choices`() {
        val config = devicePlaybackConfigFromKeys(
            engine = null,
            dolbyVision = null,
            frameRateMatching = null,
            resolutionMatching = null,
            audioOutputMode = null,
            decoderPriority = null,
            downmixMode = null,
            parallelConnections = true,
            parallelConnectionCount = 40,
            parallelChunkSizeKb = 20_000,
        )
        assertEquals(true, config.parallelConnections)
        assertEquals(4, config.parallelConnectionCount)
        assertEquals(16_384, config.parallelChunkSizeKb)
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
