package com.lamphaus.app.player

import com.lamphaus.core.model.DevicePlaybackConfig
import com.lamphaus.core.model.DisplayModeCandidate
import com.lamphaus.core.model.FrameRateMatching
import com.lamphaus.core.model.ResolutionMatching
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackDisplayModeControllerTest {
    private val uhd60 = PlaybackOutputMode(1, DisplayModeCandidate(3840, 2160, 60f))
    private val hd60 = PlaybackOutputMode(2, DisplayModeCandidate(1920, 1080, 60f))
    private val hd24 = PlaybackOutputMode(3, DisplayModeCandidate(1920, 1080, 24f))
    private val uhd24 = PlaybackOutputMode(4, DisplayModeCandidate(3840, 2160, 24f))
    private var config = DevicePlaybackConfig(
        frameRateMatching = FrameRateMatching.ALWAYS,
        resolutionMatching = ResolutionMatching.MATCH_SOURCE,
    )
    private val output = FakeDisplayHost(uhd60, listOf(uhd60, hd60, hd24, uhd24))
    private val surface = FakeSurfaceFrameRateHost()
    private val decisions = mutableListOf<PlaybackDisplayModeController.DisplayModeDecision>()
    private val controller = PlaybackDisplayModeController(output, { config }, decisions::add, surface)

    @Test
    fun `QA-06 always requests non seamless surface rate after stability`() {
        controller.onVideoFormat(1920, 1080, 23.976f)
        controller.tick(1_999)
        assertNull(surface.requestedFrameRate)
        controller.tick(1)
        assertEquals(23.976f, surface.requestedFrameRate)
    }

    @Test
    fun `PLY-IMM-04 clears surface rate on restore and live strategy change`() {
        controller.onVideoFormat(1920, 1080, 24f)
        controller.tick(2_000)
        assertEquals(24f, surface.requestedFrameRate)

        config = config.copy(frameRateMatching = FrameRateMatching.SEAMLESS_ONLY)
        controller.tick(500)
        assertNull(surface.requestedFrameRate)

        config = config.copy(frameRateMatching = FrameRateMatching.ALWAYS)
        controller.tick(500)
        controller.tick(1_500)
        assertEquals(24f, surface.requestedFrameRate)
        controller.restore()
        assertNull(surface.requestedFrameRate)
    }

    @Test
    fun `QA-06 waits for stability and confirms the physical switch`() {
        controller.onVideoFormat(1920, 800, 24f)
        controller.tick(1000)
        assertEquals(0, output.preferredModeId)
        controller.onVideoFormat(1920, 800, 24f)
        controller.tick(1000)
        assertEquals(hd24.id, output.preferredModeId)
        assertNull(decisions.last().appliedMode)
        output.currentMode = hd24
        controller.tick(500)
        assertEquals(hd24.candidate, decisions.last().appliedMode)
    }

    @Test
    fun `QA-06 evaluates a new rendition after the first switch`() {
        controller.onVideoFormat(1920, 1080, 24f)
        controller.tick(2000)
        output.currentMode = hd24
        controller.tick(500)
        controller.onVideoFormat(3840, 2160, 24f)
        controller.tick(2000)
        assertEquals(uhd24.id, output.preferredModeId)
    }

    @Test
    fun `QA-06 settings off restores system policy and does not pin physical mode`() {
        controller.onVideoFormat(1920, 1080, 24f)
        controller.tick(2000)
        output.currentMode = hd24
        controller.tick(500)
        config = config.copy(frameRateMatching = FrameRateMatching.OFF, resolutionMatching = ResolutionMatching.OFF)
        controller.tick(2000)
        assertEquals(0, output.preferredModeId)
        controller.restore()
        controller.tick(2000)
        assertEquals(0, output.preferredModeId)
    }

    @Test
    fun `PLY-IMM-04 restores a preexisting window preference and clears source state`() {
        output.preferredModeId = 9
        val matcher = PlaybackDisplayModeController(output, { config })
        matcher.onVideoFormat(1920, 1080, 24f)
        matcher.tick(2000)
        matcher.restore()
        assertEquals(9, output.preferredModeId)
        matcher.tick(2000)
        assertEquals(9, output.preferredModeId)
    }

    @Test
    fun `QA-07 resolution opt in works without metadata under default seamless policy`() {
        config = config.copy(frameRateMatching = FrameRateMatching.SEAMLESS_ONLY)
        controller.onVideoFormat(1920, 800, 0f)
        controller.tick(2000)
        assertEquals(hd60.id, output.preferredModeId)
    }

    @Test
    fun `QA-07 seamless only rejects unadvertised refresh changes`() {
        config = config.copy(frameRateMatching = FrameRateMatching.SEAMLESS_ONLY, resolutionMatching = ResolutionMatching.OFF)
        controller.onVideoFormat(3840, 2160, 24f)
        controller.tick(2000)
        assertEquals(0, output.preferredModeId)
    }

    @Test
    fun `QA-07 seamless ranking considers advertised alternatives`() {
        config = config.copy(frameRateMatching = FrameRateMatching.SEAMLESS_ONLY, resolutionMatching = ResolutionMatching.OFF)
        output.currentMode = uhd60.copy(alternativeRefreshRates = listOf(24f))
        controller.onVideoFormat(1920, 1080, 24f)
        controller.tick(2000)
        assertEquals(uhd24.id, output.preferredModeId)
    }

    @Test
    fun `QA-06 refused switch is reported without repeated requests`() {
        controller.onVideoFormat(1920, 1080, 24f)
        controller.tick(2000)
        controller.tick(5000)
        assertEquals(PlaybackDisplayModeController.DisplayModeReason.NOT_APPLIED, decisions.last().reason)
        assertNull(decisions.last().appliedMode)
        val count = decisions.size
        controller.tick(5000)
        assertEquals(count, decisions.size)
    }

    private class FakeDisplayHost(
        override var currentMode: PlaybackOutputMode?,
        override val supportedModes: List<PlaybackOutputMode>,
        override var preferredModeId: Int = 0,
    ) : PlaybackDisplayHost

    private class FakeSurfaceFrameRateHost : PlaybackSurfaceFrameRateHost {
        var requestedFrameRate: Float? = null

        override fun requestFrameRate(frameRateHz: Float) {
            requestedFrameRate = frameRateHz
        }

        override fun clearFrameRate() {
            requestedFrameRate = null
        }
    }
}
