package com.lamphaus.core.data.playback

import com.lamphaus.core.data.cloud.SeekPreviewManifest
import com.lamphaus.core.data.cloud.SeekPreviewRemoteSource
import com.lamphaus.core.data.cloud.SeekPreviewRequest
import com.lamphaus.core.model.Episode
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.MediaType
import android.util.Log
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
 * meters distinct movies and distinct episodes per key and day, so a used-up
 * allowance stops lookups of that kind until Seekr says it lifts (midnight
 * UTC), and an account without a key is asked again only after a pause, or
 * at once when [invalidate] says the integration changed here.
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

    /** Wall-clock end of each used-up allowance: Seekr resets at midnight UTC. */
    private val limitedUntil = ConcurrentHashMap<SeekPreviewManifest.Limit, Long>()

    /** The integration changed on this device (key saved or removed): ask again now. */
    fun invalidate() {
        pausedUntilMillis = 0L
        limitedUntil.clear()
        missing.clear()
        tracks.clear()
    }

    private fun limitFor(request: SeekPreviewRequest) =
        if (request.type == "series") SeekPreviewManifest.Limit.EPISODES else SeekPreviewManifest.Limit.MOVIES

    private fun limited(request: SeekPreviewRequest): Boolean {
        val now = clock()
        return (limitedUntil[limitFor(request)] ?: 0L) > now ||
            (limitedUntil[SeekPreviewManifest.Limit.ALL] ?: 0L) > now
    }

    suspend fun track(media: MediaPreview, episode: Episode?, durationMillis: Long): SeekPreviewTrack? {
        val source = remote ?: return null
        val request = requestFor(media, episode, durationMillis) ?: run {
            Log.d(TAG, "no lookup: ${media.type} needs a public id and an episode number (episode=${episode != null})")
            return null
        }
        // Only public ids, numbers, and outcomes are logged; never URLs or keys (SHR-PROD-06).
        val label = "${request.type} ${request.id} s=${request.season} e=${request.episode} duration=${request.durationMs}"
        val cacheKey = "${request.type}:${request.id}:${request.season}:${request.episode}:${request.durationMs}"
        tracks[cacheKey]?.let { return it }
        if (clock() < pausedUntilMillis || cacheKey in missing || limited(request)) {
            Log.d(TAG, "skipped $label: waiting after an earlier answer")
            return null
        }
        val manifest = try {
            source.manifest(request)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // Momentary: a later seek in this or another playback retries.
            Log.d(TAG, "lookup failed $label: ${error::class.simpleName}")
            return null
        }
        return when (manifest) {
            is SeekPreviewManifest.Unavailable -> {
                Log.d(TAG, "unavailable $label: ${manifest.reason} ${manifest.limit ?: ""}")
                when (manifest.reason) {
                    // A key saved on another device is picked up after the pause.
                    SeekPreviewManifest.Reason.NOT_CONNECTED,
                    SeekPreviewManifest.Reason.KEY_REJECTED,
                    -> pausedUntilMillis = clock() + NO_KEY_PAUSE_MILLIS
                    // Only that allowance waits, and only as long as Seekr says:
                    // asking again before then risks the key (PLY-SEEK-01).
                    SeekPreviewManifest.Reason.RATE_LIMITED -> limitedUntil[manifest.limit ?: SeekPreviewManifest.Limit.ALL] =
                        clock() + (manifest.retryAfterMillis ?: RATE_LIMIT_PAUSE_MILLIS)
                    SeekPreviewManifest.Reason.NOT_FOUND -> missing += cacheKey
                }
                null
            }
            is SeekPreviewManifest.Available -> {
                // The signed URL is fetched untouched: any added parameter
                // breaks its signature and the host refuses it.
                val vtt = downloader.text(manifest.vttUrl) ?: run {
                    Log.d(TAG, "manifest download failed $label")
                    return null
                }
                val cues = SeekPreviewVtt.parse(vtt, manifest.vttUrl)
                Log.d(TAG, "found $label: ${cues.size} cues, scale=${manifest.scale}")
                if (cues.isEmpty()) {
                    missing += cacheKey
                    null
                } else {
                    SeekPreviewTrack(cues, manifest.scale).also { tracks[cacheKey] = it }
                }
            }
        }
    }

    /** Downloads one sprite sheet into [target]; true when it is there. */
    suspend fun downloadSheet(url: String, target: File): Boolean =
        target.isFile || downloader.file(url, target)

    private companion object {
        const val TAG = "Lamphaus.Seek"
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
