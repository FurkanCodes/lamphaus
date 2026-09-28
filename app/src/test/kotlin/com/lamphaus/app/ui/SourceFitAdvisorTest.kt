package com.lamphaus.app.ui

import com.lamphaus.core.model.DevicePlaybackConfig
import com.lamphaus.core.model.PlaybackCapabilities
import com.lamphaus.core.model.StreamCandidate
import com.lamphaus.core.model.VideoCodecFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceFitAdvisorTest {
    private val source = StreamCandidate(providerId = "p", name = "Movie.2160p.WEB-DL.HDR.HEVC")
    private val capabilities = PlaybackCapabilities(
        supportsHdr10 = true,
        hardwareVideoMaxHeight = mapOf(VideoCodecFamily.HEVC to 2160),
    )

    @Test
    fun `a read device says how the source plays`() {
        assertEquals(true, SourceFitAdvisor(capabilities, DevicePlaybackConfig()).fitFor(source)?.native)
    }

    @Test
    fun `setting off or device unread says nothing`() {
        // The apps pass null capabilities when Settings turns source fit off.
        assertNull(SourceFitAdvisor(null, DevicePlaybackConfig()).fitFor(source))
    }
}
