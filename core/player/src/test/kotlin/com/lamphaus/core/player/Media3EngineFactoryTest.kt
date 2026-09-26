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
    }
}
