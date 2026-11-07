package com.lamphaus.core.player

import androidx.media3.common.C
import androidx.media3.exoplayer.LoadingInfo
import androidx.media3.exoplayer.source.CompositeSequenceableLoader
import androidx.media3.exoplayer.source.SequenceableLoader
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * PLY-NET-01, third_party/media3/patches/0002: Media3 alone advances only the
 * loader furthest behind, so a selected subtitle whose host answers slowly, or
 * never, froze the audio and video loaded beside it.
 */
class SubtitleLoadingTest {
    private class FakeLoader(var nextPositionUs: Long) : SequenceableLoader {
        var continued = 0

        override fun getBufferedPositionUs(): Long = nextPositionUs

        override fun getNextLoadPositionUs(): Long = nextPositionUs

        override fun continueLoading(loadingInfo: LoadingInfo): Boolean {
            continued++
            return false
        }

        override fun isLoading(): Boolean = false

        override fun reevaluateBuffer(positionUs: Long) = Unit
    }

    private val media = FakeLoader(nextPositionUs = 5_000_000)
    private val subtitle = FakeLoader(nextPositionUs = 0)

    private fun continueLoading(playbackPositionUs: Long = 1_000_000) {
        CompositeSequenceableLoader(
            listOf(media, subtitle),
            listOf(listOf(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO), listOf(C.TRACK_TYPE_TEXT)),
        ).continueLoading(LoadingInfo.Builder().setPlaybackPositionUs(playbackPositionUs).build())
    }

    @Test
    fun `a subtitle stuck at its start does not hold back the media beside it`() {
        continueLoading()
        assertEquals(1, media.continued)
        assertEquals(1, subtitle.continued)
    }

    @Test
    fun `media keeps loading once the subtitle has finished`() {
        subtitle.nextPositionUs = C.TIME_END_OF_SOURCE
        continueLoading()
        assertEquals(1, media.continued)
        assertEquals(0, subtitle.continued)
    }

    @Test
    fun `media tracks still wait for each other`() {
        val audio = FakeLoader(nextPositionUs = 2_000_000)
        CompositeSequenceableLoader(
            listOf(media, audio),
            listOf(listOf(C.TRACK_TYPE_VIDEO), listOf(C.TRACK_TYPE_AUDIO)),
        ).continueLoading(LoadingInfo.Builder().setPlaybackPositionUs(1_000_000).build())
        assertEquals(0, media.continued)
        assertEquals(1, audio.continued)
    }
}
