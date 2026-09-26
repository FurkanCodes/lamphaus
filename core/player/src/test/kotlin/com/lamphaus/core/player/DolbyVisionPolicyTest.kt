package com.lamphaus.core.player

import androidx.media3.common.MimeTypes
import com.lamphaus.core.model.DolbyVisionHandling
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DolbyVisionPolicyTest {
    @Test
    fun `auto keeps native DV and falls back only when the decoder cannot take it`() {
        assertFalse(DolbyVisionPolicy.shouldPlayBaseLayer(DolbyVisionHandling.AUTO, nativeDecodeSupported = true))
        assertTrue(DolbyVisionPolicy.shouldPlayBaseLayer(DolbyVisionHandling.AUTO, nativeDecodeSupported = false))
    }

    @Test
    fun `explicit settings win`() {
        assertTrue(DolbyVisionPolicy.shouldPlayBaseLayer(DolbyVisionHandling.HDR10_BASE_LAYER, true))
        assertTrue(DolbyVisionPolicy.shouldPlayBaseLayer(DolbyVisionHandling.DISABLED, true))
        assertFalse(DolbyVisionPolicy.shouldPlayBaseLayer(DolbyVisionHandling.NATIVE_ONLY, false))
    }

    @Test
    fun `profile 7 remux plays its HEVC base layer`() {
        assertEquals(MimeTypes.VIDEO_H265, DolbyVisionPolicy.baseLayerMimeType("dvhe.07.06"))
        assertEquals(MimeTypes.VIDEO_H264, DolbyVisionPolicy.baseLayerMimeType("dvav.09.05"))
        assertEquals(MimeTypes.VIDEO_AV1, DolbyVisionPolicy.baseLayerMimeType("dav1.10.09"))
    }

    @Test
    fun `profile 5 has no backward compatible layer`() {
        assertNull(DolbyVisionPolicy.baseLayerMimeType("dvhe.05.06"))
        assertNull(DolbyVisionPolicy.baseLayerMimeType("dvh1.05.09"))
    }
}
