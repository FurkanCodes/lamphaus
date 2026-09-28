package com.lamphaus.core.player.trailer

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.lamphaus.core.model.TrailerSource

/**
 * One ExoPlayer for trailers, kept apart from the main playback engine, as
 * NuvioTV's trailer pool does. A muted preview never loads the audio stream
 * or takes audio focus; a trailer with sound takes focus like any video.
 * The display's frame rate is never switched for a trailer.
 */
@OptIn(UnstableApi::class)
class TrailerPlayer(
    context: Context,
    maxVideoHeight: Int,
    private val withSound: Boolean,
) {
    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context.applicationContext)
        .setTrackSelector(
            DefaultTrackSelector(context.applicationContext).apply {
                setParameters(
                    buildUponParameters()
                        .setMaxVideoSize(Int.MAX_VALUE, maxVideoHeight)
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, !withSound),
                )
            },
        )
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(15_000, 50_000, 1_500, 3_000)
                .build(),
        )
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus = */ withSound,
        )
        .setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF)
        .build()
        .apply {
            repeatMode = Player.REPEAT_MODE_OFF
            volume = if (withSound) 1f else 0f
        }

    private var released = false

    val player: Player get() = exoPlayer

    fun play(source: TrailerSource) {
        if (released) return
        val http = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true)
        val mediaSource = if (source.isHls) {
            HlsMediaSource.Factory(http).createMediaSource(
                MediaItem.Builder().setUri(source.videoUrl).setMimeType(MimeTypes.APPLICATION_M3U8).build(),
            )
        } else {
            val progressive = ProgressiveMediaSource.Factory(YouTubeChunkedDataSourceFactory(http))
            val video = progressive.createMediaSource(MediaItem.fromUri(source.videoUrl))
            val audioUrl = source.audioUrl?.takeIf { withSound }
            if (audioUrl == null) {
                video
            } else {
                MergingMediaSource(video, progressive.createMediaSource(MediaItem.fromUri(audioUrl)))
            }
        }
        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    /** Safe after [release]: a card and its holder may be disposed in either order. */
    fun stop() {
        if (released) return
        exoPlayer.playWhenReady = false
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
    }

    fun release() {
        if (released) return
        released = true
        exoPlayer.release()
    }
}

/**
 * Appends YouTube's own `range=start-end` query parameter in 10 MB chunks.
 * googlevideo throttles, then drops, a connection that pulls a whole
 * adaptive stream at once, but serves ranged requests at full speed
 * (NuvioTV's `YoutubeChunkedDataSourceFactory`). Other hosts pass through.
 */
@OptIn(UnstableApi::class)
internal class YouTubeChunkedDataSourceFactory(
    private val upstream: DataSource.Factory,
    private val chunkSizeBytes: Long = 10L * 1024 * 1024,
) : DataSource.Factory {
    override fun createDataSource(): DataSource = ChunkedDataSource(upstream.createDataSource(), chunkSizeBytes)

    private class ChunkedDataSource(
        private val upstream: DataSource,
        private val chunkSize: Long,
    ) : DataSource {
        private var chunked = false
        private var spec: DataSpec? = null
        private var remaining = C.LENGTH_UNSET.toLong()
        private var chunkStart = 0L
        private var chunkEnd = 0L
        private var readInChunk = 0L

        override fun addTransferListener(transferListener: TransferListener) {
            upstream.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            chunked = dataSpec.uri.host.orEmpty().endsWith("googlevideo.com")
            if (!chunked) return upstream.open(dataSpec)
            spec = dataSpec
            chunkStart = dataSpec.position
            remaining = dataSpec.length
            openChunk()
            return remaining
        }

        private fun openChunk() {
            val base = checkNotNull(spec)
            chunkEnd = if (remaining != C.LENGTH_UNSET.toLong()) {
                minOf(chunkStart + chunkSize, chunkStart + remaining) - 1
            } else {
                chunkStart + chunkSize - 1
            }
            val uri = base.uri.buildUpon().appendQueryParameter("range", "$chunkStart-$chunkEnd").build()
            readInChunk = 0
            upstream.open(base.buildUpon().setUri(uri).setPosition(0).setLength(C.LENGTH_UNSET.toLong()).build())
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (!chunked) return upstream.read(buffer, offset, length)
            val read = upstream.read(buffer, offset, length)
            if (read != C.RESULT_END_OF_INPUT) {
                readInChunk += read
                return read
            }
            upstream.close()
            // A short chunk means the stream ended inside it.
            if (readInChunk < chunkEnd - chunkStart + 1) return C.RESULT_END_OF_INPUT
            chunkStart += readInChunk
            if (remaining != C.LENGTH_UNSET.toLong()) {
                remaining -= readInChunk
                if (remaining <= 0) return C.RESULT_END_OF_INPUT
            }
            return runCatching {
                openChunk()
                upstream.read(buffer, offset, length).also { if (it > 0) readInChunk += it }
            }.getOrDefault(C.RESULT_END_OF_INPUT)
        }

        override fun getUri(): Uri? = upstream.uri

        override fun close() {
            upstream.close()
            spec = null
        }
    }
}
