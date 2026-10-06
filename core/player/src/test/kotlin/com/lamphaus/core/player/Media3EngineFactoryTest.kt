package com.lamphaus.core.player

import androidx.media3.common.C
import com.lamphaus.core.model.DevicePlaybackConfig
import com.lamphaus.core.model.FrameRateMatching
import org.junit.Assert.assertEquals
import org.junit.Test

class Media3EngineFactoryTest {
    @Test
    fun `QA-06 always disables Media3 surface voting`() {
        val config = DevicePlaybackConfig(frameRateMatching = FrameRateMatching.ALWAYS)

        assertEquals(
            C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF,
            Media3EngineFactory.videoChangeFrameRateStrategy(config),
        )
    }

    @Test
    fun `QA-06 seamless only keeps Media3 seamless surface voting`() {
        val config = DevicePlaybackConfig(frameRateMatching = FrameRateMatching.SEAMLESS_ONLY)

        assertEquals(
            C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS,
            Media3EngineFactory.videoChangeFrameRateStrategy(config),
        )
    }

    @Test
    fun `engine settings rebuild the player, frame-rate matching applies live`() {
        val base = DevicePlaybackConfig()

        assertEquals(false, Media3EngineFactory.needsRebuild(base, base.copy(frameRateMatching = FrameRateMatching.ALWAYS)))
        assertEquals(true, Media3EngineFactory.needsRebuild(base, base.copy(downmixMode = com.lamphaus.core.model.DownmixMode.STEREO)))
        assertEquals(
            true,
            Media3EngineFactory.needsRebuild(base, base.copy(dolbyVisionHandling = com.lamphaus.core.model.DolbyVisionHandling.HDR10_BASE_LAYER)),
        )
        assertEquals(
            true,
            Media3EngineFactory.needsRebuild(base, base.copy(audioOutputMode = com.lamphaus.core.model.AudioOutputMode.FORCE_DECODE)),
        )
        // Night listening builds its processor and PCM-only sink with the player (SHR-PROD-15).
        assertEquals(true, Media3EngineFactory.needsRebuild(base, base.copy(nightListening = true)))
        // The streaming engine is built with the player too (PLY-NET-01).
        assertEquals(true, Media3EngineFactory.needsRebuild(base, base.copy(nativeMemoryBuffer = true)))
        assertEquals(true, Media3EngineFactory.needsRebuild(base, base.copy(parallelConnections = true)))
        assertEquals(true, Media3EngineFactory.needsRebuild(base, base.copy(parallelConnectionCount = 3)))
        assertEquals(true, Media3EngineFactory.needsRebuild(base, base.copy(parallelChunkSizeKb = 8_192)))
    }

    @Test
    fun `PLY-NET-01 heap chunks prefetch one per connection plus one, native ones two per connection`() {
        val mib = 1024L * 1024L
        val config = DevicePlaybackConfig(parallelConnections = true)
        val heap = Media3EngineFactory.parallelSettings(config, maxHeapBytes = 512 * mib, nativeRamBytes = null)
        assertEquals(2, heap.connections)
        assertEquals(16 * mib, heap.chunkBytes)
        assertEquals(3, heap.depth)
        assertEquals(7, heap.sessionChunkCap)
        val native = Media3EngineFactory.parallelSettings(config, maxHeapBytes = 512 * mib, nativeRamBytes = 4L * 1024 * mib)
        assertEquals(4, native.depth)
        // Heap-bound devices cap chunks at 16 MiB.
        val big = Media3EngineFactory.parallelSettings(config.copy(parallelChunkSizeKb = 65_536), 256 * mib, null)
        assertEquals(16 * mib, big.chunkBytes)
    }
}
