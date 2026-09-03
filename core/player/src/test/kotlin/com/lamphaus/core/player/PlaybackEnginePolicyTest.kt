package com.lamphaus.core.player

import com.lamphaus.core.model.PlaybackEngineKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackEnginePolicyTest {

    @Test
    fun `auto prefers mpv only when the format needs the native stack and mpv ships`() {
        val mpvFormat = MediaFormatProfile(container = "mkv", videoCodec = "dolby-vision-p7", media3CanPlay = false)
        assertEquals(PlaybackEngineKind.MPV, PlaybackEnginePolicy.resolveInitialEngine(PlaybackEngineKind.AUTO, mpvFormat, mpvAvailable = true))
        assertEquals(PlaybackEngineKind.MEDIA3, PlaybackEnginePolicy.resolveInitialEngine(PlaybackEngineKind.AUTO, mpvFormat, mpvAvailable = false))

        val plainFormat = MediaFormatProfile(container = "mp4", videoCodec = "avc", media3CanPlay = true)
        assertEquals(PlaybackEngineKind.MEDIA3, PlaybackEnginePolicy.resolveInitialEngine(PlaybackEngineKind.AUTO, plainFormat, mpvAvailable = true))
        assertEquals(PlaybackEngineKind.MEDIA3, PlaybackEnginePolicy.resolveInitialEngine(PlaybackEngineKind.AUTO, null, mpvAvailable = true))
    }

    @Test
    fun `explicit media3 override wins over format hints`() {
        val mpvFormat = MediaFormatProfile(media3CanPlay = false)
        assertEquals(PlaybackEngineKind.MEDIA3, PlaybackEnginePolicy.resolveInitialEngine(PlaybackEngineKind.MEDIA3, mpvFormat, mpvAvailable = true))
    }

    @Test
    fun `explicit mpv override falls back to media3 when mpv is unavailable`() {
        assertEquals(PlaybackEngineKind.MPV, PlaybackEnginePolicy.resolveInitialEngine(PlaybackEngineKind.MPV, null, mpvAvailable = true))
        assertEquals(PlaybackEngineKind.MEDIA3, PlaybackEnginePolicy.resolveInitialEngine(PlaybackEngineKind.MPV, null, mpvAvailable = false))
    }

    @Test
    fun `only decoder and format failures fall back to mpv`() {
        val decoderKinds = listOf(
            EngineFailureKind.UNSUPPORTED_FORMAT,
            EngineFailureKind.DECODER_INIT_FAILED,
            EngineFailureKind.DECODER_FAILED,
        )
        decoderKinds.forEach { kind ->
            assertTrue(PlaybackEnginePolicy.shouldFallbackToMpv(kind, PlaybackEngineKind.MEDIA3))
        }
        listOf(
            EngineFailureKind.NETWORK,
            EngineFailureKind.AUTHORIZATION,
            EngineFailureKind.PROVIDER,
            EngineFailureKind.UNKNOWN,
        ).forEach { kind ->
            assertFalse(PlaybackEnginePolicy.shouldFallbackToMpv(kind, PlaybackEngineKind.MEDIA3))
        }
        // No bouncing: an MPV session never hands back to Media3.
        decoderKinds.forEach { kind ->
            assertFalse(PlaybackEnginePolicy.shouldFallbackToMpv(kind, PlaybackEngineKind.MPV))
            assertFalse(PlaybackEnginePolicy.shouldFallbackToMedia3(kind, PlaybackEngineKind.MPV))
        }
    }

    @Test
    fun `fallback reasons exist only for handoff failures`() {
        assertEquals("format unsupported by Media3", PlaybackEnginePolicy.fallbackReason(EngineFailureKind.UNSUPPORTED_FORMAT))
        assertNull(PlaybackEnginePolicy.fallbackReason(EngineFailureKind.NETWORK))
    }
}
