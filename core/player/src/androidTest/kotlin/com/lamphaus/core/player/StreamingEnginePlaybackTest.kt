package com.lamphaus.core.player

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.upstream.NativeBuffers
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lamphaus.core.model.DevicePlaybackConfig
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PLY-NET-01 end to end: the session player built with native memory and
 * parallel connections plays a progressive file from a local server to its
 * end (QA-01, QA-06). The sample is Media3's Apache-2.0 test asset
 * sample_with_increasing_timestamps_320w_240h.mp4.
 */
@RunWith(AndroidJUnit4::class)
class StreamingEnginePlaybackTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val content = instrumentation.context.assets.open("streaming_sample.mp4").use { it.readBytes() }
    private val server = MockWebServer()
    private val ranges = Collections.synchronizedList(mutableListOf<String>())

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val range = request.getHeader("Range")
                    ?: return MockResponse().setResponseCode(200).setBody(Buffer().write(content))
                ranges += range
                val parts = range.removePrefix("bytes=").split("-")
                val start = parts[0].toLong()
                val end = (parts.getOrNull(1)?.takeIf(String::isNotEmpty)?.toLong() ?: (content.size - 1L))
                    .coerceAtMost(content.size - 1L)
                return MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Range", "bytes $start-$end/${content.size}")
                    .setBody(Buffer().write(content, start.toInt(), (end - start + 1).toInt()))
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun playsToTheEndOverParallelConnectionsWithNativeMemory() {
        val peak = play(DevicePlaybackConfig(nativeMemoryBuffer = true, parallelConnections = true, parallelChunkSizeKb = 256))
        assertTrue("buffered media should live off-heap", peak > 0)
        assertTrue("expected ranged chunk requests, got $ranges", ranges.size >= 3)
    }

    @Test
    fun playsToTheEndOnTheDefaultPath() {
        play(DevicePlaybackConfig())
    }

    /** Plays the sample to its end and returns the peak off-heap bytes seen. */
    private fun play(config: DevicePlaybackConfig): Long {
        val url = server.url("/movie.mp4").toString()
        PlaybackHeaderRegistry.begin(url, emptyMap(), "video/mp4")
        val ended = CountDownLatch(1)
        val error = AtomicReference<PlaybackException?>()
        val peakNativeBytes = AtomicLong()
        lateinit var player: ExoPlayer
        instrumentation.runOnMainSync {
            player = Media3EngineFactory.createPlayer(instrumentation.targetContext, config)
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    peakNativeBytes.accumulateAndGet(NativeBuffers.getAllocatedBytes(), ::maxOf)
                    if (playbackState == Player.STATE_ENDED) ended.countDown()
                }

                override fun onIsLoadingChanged(isLoading: Boolean) {
                    peakNativeBytes.accumulateAndGet(NativeBuffers.getAllocatedBytes(), ::maxOf)
                }

                override fun onPlayerError(playbackError: PlaybackException) {
                    error.set(playbackError)
                    ended.countDown()
                }
            })
            player.playbackParameters = PlaybackParameters(4f)
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            player.play()
        }
        try {
            assertTrue("playback did not end", ended.await(90, TimeUnit.SECONDS))
            assertNull(error.get())
            return peakNativeBytes.get()
        } finally {
            instrumentation.runOnMainSync { player.release() }
            PlaybackHeaderRegistry.end(url)
        }
    }
}
