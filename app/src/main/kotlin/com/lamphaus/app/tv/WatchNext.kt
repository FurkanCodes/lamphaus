package com.lamphaus.app.tv

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.net.toUri
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.lamphaus.app.ui.UpNextItem
import com.lamphaus.app.ui.UpNextKind
import com.lamphaus.app.ui.isResumable
import com.lamphaus.core.model.MediaType
import com.lamphaus.core.model.WatchProgress
import com.lamphaus.core.model.hasAired
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One card in Google TV's Continue watching row (TV-HOME-01). */
internal data class WatchNextEntry(
    /** The title's stable key; the card opens this title's details. */
    val mediaKey: String,
    val kind: Kind,
    val isEpisode: Boolean,
    val title: String,
    val episodeTitle: String?,
    val season: Int?,
    val episode: Int?,
    val positionMillis: Long,
    val durationMillis: Long,
    val engagedAtMillis: Long,
    val artUrl: String?,
    val artIsPoster: Boolean,
) {
    enum class Kind { CONTINUE, NEXT, NEW }
}

internal const val WATCH_NEXT_MAX_ENTRIES = 10

/**
 * The active profile's Continue watching, as Google TV cards: the most
 * recent unfinished movie or episode of each title, then each series' up
 * next episode once it has aired. Newest engagement first.
 */
internal fun watchNextEntries(
    progress: List<WatchProgress>,
    upNext: List<UpNextItem>,
    nowEpochMillis: Long = System.currentTimeMillis(),
): List<WatchNextEntry> {
    val resuming = progress.asSequence()
        .filter { it.isResumable() && it.preview != null }
        .groupBy(WatchProgress::mediaKey)
        .values
        .map { rows -> rows.maxBy(WatchProgress::updatedAtEpochMillis) }
        .map { row ->
            val media = requireNotNull(row.preview)
            WatchNextEntry(
                mediaKey = row.mediaKey,
                kind = WatchNextEntry.Kind.CONTINUE,
                isEpisode = media.type == MediaType.SERIES,
                title = media.name,
                episodeTitle = row.episodeLabel,
                season = null,
                episode = null,
                positionMillis = row.positionMillis,
                durationMillis = row.durationMillis,
                engagedAtMillis = row.updatedAtEpochMillis,
                artUrl = media.backgroundUrl ?: media.posterUrl,
                artIsPoster = media.backgroundUrl == null,
            )
        }
    val resumingKeys = resuming.mapTo(HashSet(), WatchNextEntry::mediaKey)
    val next = upNext
        .filter { it.media.stableKey !in resumingKeys && it.kind != UpNextKind.UPCOMING && it.episode.hasAired(nowEpochMillis) }
        .map { item ->
            WatchNextEntry(
                mediaKey = item.media.stableKey,
                kind = if (item.kind == UpNextKind.NEW) WatchNextEntry.Kind.NEW else WatchNextEntry.Kind.NEXT,
                isEpisode = true,
                title = item.media.name,
                episodeTitle = item.episode.title,
                season = item.episode.season,
                episode = item.episode.episode,
                positionMillis = 0,
                durationMillis = 0,
                engagedAtMillis = item.lastWatched.updatedAtEpochMillis,
                artUrl = item.episode.thumbnailUrl ?: item.media.backgroundUrl ?: item.media.posterUrl,
                artIsPoster = item.episode.thumbnailUrl == null && item.media.backgroundUrl == null,
            )
        }
    return (resuming + next).sortedByDescending(WatchNextEntry::engagedAtMillis).take(WATCH_NEXT_MAX_ENTRIES)
}

/**
 * Keeps this app's cards in Google TV's Continue watching row in step with
 * [watchNextEntries]. Only this app's own rows are read or written. A card
 * the viewer removed from the home screen stays removed until they watch
 * that title again. Does nothing on devices without the TV provider.
 */
internal class WatchNextPublisher(context: Context) {
    private val context = context.applicationContext
    private val helper by lazy { PreviewChannelHelper(this.context) }
    private val supported = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    suspend fun publish(entries: List<WatchNextEntry>) = withContext(Dispatchers.IO) {
        if (!supported) return@withContext
        runCatching {
            val existing = existingPrograms()
            val wanted = entries.associateBy(WatchNextEntry::mediaKey)
            existing.forEach { (key, row) ->
                val entry = wanted[key]
                val stillRemoved = !row.browsable && entry != null && entry.engagedAtMillis <= row.engagedAtMillis
                if (entry == null || (!row.browsable && !stillRemoved)) delete(row.id)
            }
            val kept = existingPrograms()
            wanted.values.forEach { entry ->
                val current = kept[entry.mediaKey]
                when {
                    current == null -> helper.publishWatchNextProgram(program(entry))
                    current.browsable -> helper.updateWatchNextProgram(program(entry), current.id)
                }
            }
        }
    }

    suspend fun clear() = publish(emptyList())

    /** One of this app's rows as the system holds it; false [browsable] means the viewer removed it. */
    private data class PublishedRow(val id: Long, val browsable: Boolean, val engagedAtMillis: Long)

    private fun existingPrograms(): Map<String, PublishedRow> {
        val cursor = context.contentResolver.query(
            TvContractCompat.WatchNextPrograms.CONTENT_URI,
            arrayOf(
                TvContractCompat.WatchNextPrograms._ID,
                TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID,
                TvContractCompat.WatchNextPrograms.COLUMN_BROWSABLE,
                TvContractCompat.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS,
            ),
            null,
            null,
            null,
        ) ?: return emptyMap()
        return cursor.use {
            buildMap {
                while (it.moveToNext()) {
                    val key = it.getString(1) ?: continue
                    put(key, PublishedRow(id = it.getLong(0), browsable = it.getInt(2) != 0, engagedAtMillis = it.getLong(3)))
                }
            }
        }
    }

    private fun delete(id: Long) {
        context.contentResolver.delete(TvContractCompat.buildWatchNextProgramUri(id), null, null)
    }

    // The builder's setters are inherited from a @RestrictTo base builder, so
    // lint flags the documented Watch Next calls; reading rows uses public columns.
    @SuppressLint("RestrictedApi")
    private fun program(entry: WatchNextEntry): WatchNextProgram {
        val builder = WatchNextProgram.Builder()
            .setType(
                if (entry.isEpisode) TvContractCompat.WatchNextPrograms.TYPE_TV_EPISODE
                else TvContractCompat.WatchNextPrograms.TYPE_MOVIE,
            )
            .setWatchNextType(
                when (entry.kind) {
                    WatchNextEntry.Kind.CONTINUE -> TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE
                    WatchNextEntry.Kind.NEXT -> TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_NEXT
                    WatchNextEntry.Kind.NEW -> TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_NEW
                },
            )
            .setLastEngagementTimeUtcMillis(entry.engagedAtMillis)
            .setTitle(entry.title)
            .setInternalProviderId(entry.mediaKey)
            .setIntentUri(openUri(entry.mediaKey))
        entry.episodeTitle?.let(builder::setEpisodeTitle)
        entry.season?.let { builder.setSeasonNumber(it) }
        entry.episode?.let { builder.setEpisodeNumber(it) }
        if (entry.durationMillis > 0) {
            builder.setDurationMillis(entry.durationMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            builder.setLastPlaybackPositionMillis(entry.positionMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        }
        entry.artUrl?.let { url ->
            builder.setPosterArtUri(url.toUri())
            builder.setPosterArtAspectRatio(
                if (entry.artIsPoster) TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3
                else TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9,
            )
        }
        return builder.build()
    }

    /** An explicit intent for [TvActivity] carrying the title to open. */
    private fun openUri(mediaKey: String): Uri =
        Intent(context, TvActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(watchNextUri(mediaKey))
            .toUri(Intent.URI_INTENT_SCHEME)
            .toUri()

    companion object {
        private const val SCHEME = "lamphaus"
        private const val HOST = "watch-next"

        fun watchNextUri(mediaKey: String): Uri =
            Uri.Builder().scheme(SCHEME).authority(HOST).appendQueryParameter("key", mediaKey).build()

        /** The title key a Google TV card opened, or null for any other launch. */
        fun mediaKeyFrom(intent: Intent?): String? {
            val data = intent?.data ?: return null
            if (data.scheme != SCHEME || data.host != HOST) return null
            return data.getQueryParameter("key")?.takeIf(String::isNotBlank)
        }
    }
}
