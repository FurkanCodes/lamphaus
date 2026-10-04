package com.lamphaus.app.player

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lamphaus.core.data.playback.SeekPreviewCue
import com.lamphaus.core.data.playback.SeekPreviewRepository
import com.lamphaus.core.data.playback.SeekPreviewTrack
import com.lamphaus.core.model.Episode
import com.lamphaus.core.model.MediaPreview
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Thumbnails for the position being sought to (PLY-SEEK-01); null while none is known. */
internal fun interface SeekPreviewSource {
    suspend fun thumbnail(positionMillis: Long, durationMillis: Long): ImageBitmap?

    /** Loads the preview track ahead of the first seek, once the duration is known. */
    suspend fun prepare(durationMillis: Long) {}
}

/** Provided by the player screen; absent (null) means scrubbing shows only the time. */
internal val LocalSeekPreviews = staticCompositionLocalOf<SeekPreviewSource?> { null }

/**
 * Seek previews for one playback: one per player activity, since every
 * episode or source switch starts a fresh one.
 *
 * The track is requested once the playing duration is known, so the first
 * seek already has frames; sheets then download in the background. Sprite sheets are kept as files in this playback's own
 * [directory] and only the 320×180 tile in view is decoded, so memory stays
 * small however long the title is (QA-08). [release] deletes the files.
 */
internal class PlaybackSeekPreviews(
    private val repository: SeekPreviewRepository,
    private val media: MediaPreview,
    private val episode: Episode?,
    private val directory: File,
    private val scope: CoroutineScope,
) : SeekPreviewSource {
    private val lock = Mutex()
    private var track: SeekPreviewTrack? = null
    private var trackDuration = 0L
    private var lastAttemptMillis = Long.MIN_VALUE / 2
    private var prefetchJob: Job? = null
    private val decoders = LinkedHashMap<String, BitmapRegionDecoder>()
    private val sheetGates = ConcurrentHashMap<String, Mutex>()
    private val tiles = object : LruCache<String, Bitmap>(TILE_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    /** Ends this playback's previews and deletes its sheets. */
    fun release() {
        prefetchJob?.cancel()
        val sheets = directory
        // The activity's scope is already ending; cleanup must still run, and
        // decoders may be mid-decode on another thread, so recycle under the lock.
        CoroutineScope(Dispatchers.IO).launch {
            lock.withLock { closeDecoders() }
            tiles.evictAll()
            sheets.deleteRecursively()
        }
    }

    override suspend fun prepare(durationMillis: Long) {
        lock.withLock { ensureTrack(durationMillis) }
    }

    override suspend fun thumbnail(positionMillis: Long, durationMillis: Long): ImageBitmap? {
        val cue = lock.withLock {
            ensureTrack(durationMillis)
            track?.cueAt(positionMillis)
        } ?: return null
        return tile(cue)?.asImageBitmap()
    }

    /** Called under [lock]. */
    private suspend fun ensureTrack(durationMillis: Long) {
        if (durationMillis <= 0L) return
        val now = SystemClock.elapsedRealtime()
        // Without a track, ask again only after a pause: a scrub sends many
        // positions, and an offline lookup must not repeat for each one.
        if ((track == null && now - lastAttemptMillis >= RETRY_GAP_MILLIS) || (track != null && trackDuration != durationMillis)) {
            lastAttemptMillis = now
            track = repository.track(media, episode, durationMillis)
            trackDuration = durationMillis
            track?.let(::prefetch)
        }
    }

    private suspend fun tile(cue: SeekPreviewCue): Bitmap? {
        val tile = cue.tile
        val key = "${tile.sheetUrl}#${tile.x},${tile.y},${tile.width},${tile.height}"
        tiles.get(key)?.let { return it }
        val file = ensureSheet(tile.sheetUrl) ?: return null
        return withContext(Dispatchers.IO) {
            lock.withLock {
                runCatching {
                    val decoder = decoders[file.path] ?: openDecoder(file).also { opened ->
                        decoders[file.path] = opened
                        // A sheet holds minutes of thumbnails; two cover a scrub across a boundary.
                        while (decoders.size > OPEN_SHEETS) {
                            val eldest = decoders.keys.first()
                            decoders.remove(eldest)?.recycle()
                        }
                    }
                    val region = Rect(tile.x, tile.y, tile.x + tile.width, tile.y + tile.height)
                    if (!Rect(0, 0, decoder.width, decoder.height).contains(region)) return@runCatching null
                    decoder.decodeRegion(region, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 })
                }.getOrNull()?.also { tiles.put(key, it) }
            }
        }
    }

    /** Sheets download one at a time in the background once the track is known. */
    private fun prefetch(track: SeekPreviewTrack) {
        prefetchJob?.cancel()
        prefetchJob = scope.launch(Dispatchers.IO) {
            track.sheetUrls.take(MAX_PREFETCH_SHEETS).forEach { url -> ensureSheet(url) }
        }
    }

    /** One download per sheet, whether the prefetch or a scrub asks first. */
    private suspend fun ensureSheet(url: String): File? {
        val file = sheetFile(url)
        val gate = sheetGates.computeIfAbsent(url) { Mutex() }
        return gate.withLock { file.takeIf { repository.downloadSheet(url, it) } }
    }

    private fun sheetFile(url: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        return File(directory, digest.joinToString("") { "%02x".format(it) }.take(32) + ".img")
    }

    @Suppress("DEPRECATION")
    private fun openDecoder(file: File): BitmapRegionDecoder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            BitmapRegionDecoder.newInstance(file.path)
        } else {
            checkNotNull(BitmapRegionDecoder.newInstance(file.path, false))
        }

    private fun closeDecoders() {
        decoders.values.forEach(BitmapRegionDecoder::recycle)
        decoders.clear()
    }

    private companion object {
        const val TILE_CACHE_BYTES = 8 * 1024 * 1024
        const val OPEN_SHEETS = 2
        const val MAX_PREFETCH_SHEETS = 40
        const val RETRY_GAP_MILLIS = 30_000L
    }
}

/**
 * The thumbnail for [positionMillis] while it is non-null. Keeps showing the
 * last frame while the next one decodes, so a fast scrub never flickers; draws
 * nothing until a first thumbnail exists or when previews are unavailable.
 */
@Composable
internal fun SeekPreviewThumbnail(
    positionMillis: Long?,
    durationMillis: Long,
    width: Dp,
    modifier: Modifier = Modifier,
) {
    val source = LocalSeekPreviews.current ?: return
    var image by remember(source) { mutableStateOf<ImageBitmap?>(null) }
    val position by rememberUpdatedState(positionMillis)
    val duration by rememberUpdatedState(durationMillis)
    LaunchedEffect(source) {
        // Conflated, not restarted: an in-flight decode finishes and the
        // newest position follows, instead of every move cancelling the last.
        // A scrub ending (null) clears the frame, so the next scrub, episode,
        // or title never opens on a stale one.
        snapshotFlow { position }
            .conflate()
            .collect { target ->
                image = if (target == null) null else source.thumbnail(target, duration) ?: image
            }
    }
    if (positionMillis == null) return
    val shown = image ?: return
    Image(
        bitmap = shown,
        // The time beside it already speaks the position.
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(width, width * 9f / 16f)
            .clip(RoundedCornerShape(4.dp))
            .background(PlayerSurface)
            .border(1.dp, PlayerOnSurface.copy(alpha = 0.24f), RoundedCornerShape(4.dp)),
    )
}
