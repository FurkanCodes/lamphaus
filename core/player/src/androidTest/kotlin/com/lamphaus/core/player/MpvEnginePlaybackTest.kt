package com.lamphaus.core.player

import android.graphics.SurfaceTexture
import android.os.Looper
import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lamphaus.core.player.mpv.MpvCertificates
import com.lamphaus.core.player.mpv.MpvLibrary
import com.lamphaus.core.player.mpv.MpvPlayer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PLY-ENG-01 end to end: the packaged libmpv plays a progressive file from a
 * local server into a surface, reports its first frame and its end, and
 * reports a file it cannot open as an error (QA-01, QA-06).
 */
@RunWith(AndroidJUnit4::class)
class MpvEnginePlaybackTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val content = instrumentation.context.assets.open("streaming_sample.mp4").use { it.readBytes() }
    private val server = MockWebServer()

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun libmpvIsPackaged() {
        assertEquals(MpvLibrary.Availability.AVAILABLE, MpvLibrary.availability)
    }

    @Test
    fun playsToTheEndIntoASurface() {
        serveWithRanges()
        val firstFrame = CountDownLatch(1)
        val ended = CountDownLatch(1)
        val error = AtomicReference<PlaybackException?>()
        val endPosition = AtomicLong()
        val url = server.url("/movie.mp4").toString()
        val texture = SurfaceTexture(false)
        val surface = Surface(texture)
        lateinit var player: MpvPlayer
        instrumentation.runOnMainSync {
            player = MpvPlayer(Looper.getMainLooper(), MpvCertificates.bundle(instrumentation.targetContext))
            player.addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() = firstFrame.countDown()

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        endPosition.set(player.currentPosition)
                        ended.countDown()
                    }
                }

                override fun onPlayerError(playbackError: PlaybackException) {
                    error.set(playbackError)
                    ended.countDown()
                }
            })
            player.setVideoSurface(surface)
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            player.play()
        }
        try {
            assertTrue("no first frame", firstFrame.await(30, TimeUnit.SECONDS))
            assertTrue("playback did not end", ended.await(90, TimeUnit.SECONDS))
            assertNull(error.get())
            assertTrue("ended early at ${endPosition.get()} ms", endPosition.get() > 1_000)
        } finally {
            instrumentation.runOnMainSync { player.release() }
            surface.release()
            texture.release()
        }
    }

    /**
     * The player screen drives libmpv through the MediaSession: the session
     * must pass on the media item and the controller's surface, which Media3
     * 1.11 hands over as a SurfaceHolder.
     */
    @Test
    fun playsThroughAMediaSession() {
        serveWithRanges()
        val url = server.url("/movie.mp4").toString()
        val firstFrame = CountDownLatch(1)
        val texture = SurfaceTexture(false)
        val surface = Surface(texture)
        val context = instrumentation.targetContext
        lateinit var player: MpvPlayer
        lateinit var session: MediaSession
        instrumentation.runOnMainSync {
            player = MpvPlayer(Looper.getMainLooper(), MpvCertificates.bundle(context))
            session = MediaSession.Builder(context, player).setId("mpv-test").build()
        }
        val controllerFuture = MediaController.Builder(context, session.token).buildAsync()
        val controller = controllerFuture.get(10, TimeUnit.SECONDS)
        try {
            instrumentation.runOnMainSync {
                controller.addListener(object : Player.Listener {
                    override fun onRenderedFirstFrame() = firstFrame.countDown()
                })
                controller.setVideoSurface(surface)
                controller.setMediaItem(MediaItem.fromUri(url))
                controller.prepare()
                controller.play()
            }
            assertTrue("no first frame through the session", firstFrame.await(30, TimeUnit.SECONDS))
        } finally {
            instrumentation.runOnMainSync {
                MediaController.releaseFuture(controllerFuture)
                session.release()
                player.release()
            }
            surface.release()
            texture.release()
        }
    }

    @Test
    fun reportsAFileItCannotOpen() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not a video"))
        val url = server.url("/broken.mp4").toString()
        val failed = CountDownLatch(1)
        val error = AtomicReference<PlaybackException?>()
        lateinit var player: MpvPlayer
        instrumentation.runOnMainSync {
            player = MpvPlayer(Looper.getMainLooper())
            player.addListener(object : Player.Listener {
                override fun onPlayerError(playbackError: PlaybackException) {
                    error.set(playbackError)
                    failed.countDown()
                }
            })
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
        }
        try {
            assertTrue("no error reported", failed.await(30, TimeUnit.SECONDS))
            assertNotNull(error.get())
        } finally {
            instrumentation.runOnMainSync { player.release() }
        }
    }

    private fun serveWithRanges() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val range = request.getHeader("Range")
                    ?: return MockResponse().setResponseCode(200).setHeader("Accept-Ranges", "bytes").setBody(Buffer().write(content))
                val parts = range.removePrefix("bytes=").split("-")
                val start = parts[0].toLong()
                val end = (parts.getOrNull(1)?.takeIf(String::isNotEmpty)?.toLong() ?: (content.size - 1L))
                    .coerceAtMost(content.size - 1L)
                return MockResponse()
                    .setResponseCode(206)
                    .setHeader("Accept-Ranges", "bytes")
                    .setHeader("Content-Range", "bytes $start-$end/${content.size}")
                    .setBody(Buffer().write(content, start.toInt(), (end - start + 1).toInt()))
            }
        }
    }
}
