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
}
