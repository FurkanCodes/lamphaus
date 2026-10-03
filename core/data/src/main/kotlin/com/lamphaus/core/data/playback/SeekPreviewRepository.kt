package com.lamphaus.core.data.playback

import com.lamphaus.core.data.cloud.SeekPreviewManifest
import com.lamphaus.core.data.cloud.SeekPreviewRemoteSource
import com.lamphaus.core.data.cloud.SeekPreviewRequest
import com.lamphaus.core.model.Episode
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.MediaType
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Key-free downloads of the signed manifest and its sprite sheets. Behind an
 * interface so the repository is testable with fakes (SHR-ARC-15).
 */
interface SeekPreviewDownloader {
    /** The body of [url], or null on any failure. */
    suspend fun text(url: String): String?

    /** Writes [url] to [target]; false on any failure (no partial file is left). */
    suspend fun file(url: String, target: File): Boolean
}

class OkHttpSeekPreviewDownloader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        // Signed URLs only: a redirect could only lead somewhere unsigned.
        .followRedirects(false)
        .followSslRedirects(false)
        .build(),
) : SeekPreviewDownloader {

    override suspend fun text(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        }.getOrNull()
    }

    override suspend fun file(url: String, target: File): Boolean = withContext(Dispatchers.IO) {
        val partial = File(target.parentFile, "${target.name}.part")
        val written = runCatching {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                val body = response.body
                if (!response.isSuccessful || body == null) return@use false
                target.parentFile?.mkdirs()
                partial.outputStream().use { output -> body.byteStream().copyTo(output) }
                partial.renameTo(target)
            }
        }.getOrDefault(false)
        if (!written) partial.delete()
        written
    }
}

/**
 * Seek-preview thumbnails from the account's Seekr integration (PLY-SEEK-01).
 *
 * Lookups go through [remote] (the key never reaches the device); the
 * manifest and sheets are fetched directly. Every failure is a normal "no
 * previews" result: scrubbing works exactly as before without them. Seekr
 * meters titles per key and day, so callers load a track only once the viewer
 * starts seeking, and an account without a key is asked again only after a
 * pause, or at once when [invalidate] says the integration changed here.
 */
class SeekPreviewRepository(
    private val remote: SeekPreviewRemoteSource?,
    private val downloader: SeekPreviewDownloader = OkHttpSeekPreviewDownloader(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val tracks = ConcurrentHashMap<String, SeekPreviewTrack>()
    private val missing = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var pausedUntilMillis = 0L

    /** The integration changed on this device (key saved or removed): ask again now. */
    fun invalidate() {
        pausedUntilMillis = 0L
        missing.clear()
        tracks.clear()
    }

    suspend fun track(media: MediaPreview, episode: Episode?, durationMillis: Long): SeekPreviewTrack? {
        val source = remote ?: return null
        val request = requestFor(media, episode, durationMillis) ?: return null
        val cacheKey = "${request.type}:${request.id}:${request.season}:${request.episode}:${request.durationMs}"
        tracks[cacheKey]?.let { return it }
        if (clock() < pausedUntilMillis || cacheKey in missing) return null
        val manifest = try {
            source.manifest(request)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Momentary: a later seek in this or another playback retries.
            return null
        }
        return when (manifest) {
            is SeekPreviewManifest.Unavailable -> {
                when (manifest.reason) {
                    // A key saved on another device is picked up after the pause.
                    SeekPreviewManifest.Reason.NOT_CONNECTED,
                    SeekPreviewManifest.Reason.KEY_REJECTED,
                    -> pausedUntilMillis = clock() + NO_KEY_PAUSE_MILLIS
                    SeekPreviewManifest.Reason.RATE_LIMITED -> pausedUntilMillis = clock() + RATE_LIMIT_PAUSE_MILLIS
                    SeekPreviewManifest.Reason.NOT_FOUND -> missing += cacheKey
                }
                null
            }
            is SeekPreviewManifest.Available -> {
                // `st=1`: cue times arrive already on the playing timeline.
                val separator = if ('?' in manifest.vttUrl) '&' else '?'
                val vtt = downloader.text("${manifest.vttUrl}${separator}st=1") ?: return null
                val cues = SeekPreviewVtt.parse(vtt, manifest.vttUrl)
                if (cues.isEmpty()) {
                    missing += cacheKey
                    null
                } else {
                    SeekPreviewTrack(cues).also { tracks[cacheKey] = it }
                }
            }
        }
    }

    /** Downloads one sprite sheet into [target]; true when it is there. */
    suspend fun downloadSheet(url: String, target: File): Boolean =
        target.isFile || downloader.file(url, target)

    private companion object {
        const val RATE_LIMIT_PAUSE_MILLIS = 60 * 60 * 1000L
        const val NO_KEY_PAUSE_MILLIS = 15 * 60 * 1000L
    }
}

/**
 * Only public identities are ever sent: an IMDb or TMDB id, plus season and
 * episode for a series. Provider-scoped ids return null without any request.
 */
internal fun requestFor(media: MediaPreview, episode: Episode?, durationMillis: Long): SeekPreviewRequest? {
    if (durationMillis <= 0L) return null
    val id = media.id
    val publicId = id.startsWith("tt") && id.drop(2).all(Char::isDigit) && id.length > 2 ||
        Regex("tmdb:(?:[a-z]+:)?\\d+").matches(id)
    if (!publicId) return null
    return if (media.type == MediaType.SERIES) {
        val season = episode?.season ?: return null
        val number = episode.episode ?: return null
        SeekPreviewRequest(type = "series", id = id, season = season, episode = number, durationMs = durationMillis)
    } else {
        SeekPreviewRequest(type = "movie", id = id, durationMs = durationMillis)
    }
}
