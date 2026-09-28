package com.lamphaus.app.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.lamphaus.app.R
import com.lamphaus.core.model.Episode
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.MediaType
import com.lamphaus.core.model.WatchProgress
import com.lamphaus.core.model.hasAired
import com.lamphaus.core.model.isUnreleased
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.TimeZone
import kotlinx.coroutines.delay

/** Home's generated "Fits tonight" row (SHR-PROD-14); never a provider catalog. */
internal const val FITS_TONIGHT_SECTION_ID = "lamphaus:fits-tonight"

/** The row appears only in the evening: from this long before the end time until it passes. */
internal const val FITS_TONIGHT_WINDOW_MINUTES = 5 * 60
internal const val FITS_TONIGHT_MAX_ITEMS = 20

/** Default and offered end times, as minutes after midnight; after-midnight times mean that night. */
internal const val DEFAULT_BEDTIME_MINUTES = 23 * 60 + 30
internal val BEDTIME_OPTIONS = listOf(22 * 60, 22 * 60 + 30, 23 * 60, 23 * 60 + 30, 0, 30, 60)

/**
 * Minutes from now until the next [bedtimeMinutes] of the day, when that is
 * within the evening window; null otherwise (daytime, or already past it).
 */
internal fun minutesUntilBedtime(
    nowEpochMillis: Long,
    bedtimeMinutes: Int,
    zone: ZoneId = ZoneId.systemDefault(),
): Int? {
    val now = Instant.ofEpochMilli(nowEpochMillis).atZone(zone)
    val minuteOfDay = now.hour * 60 + now.minute
    val left = (bedtimeMinutes - minuteOfDay).mod(24 * 60)
    return left.takeIf { it in 1..FITS_TONIGHT_WINDOW_MINUTES }
}

/**
 * Movies from the loaded Home rows that end before bedtime if started now:
 * their catalog names a running time no longer than [minutesLeft], they are
 * out, and the viewer has not finished them. Rows keep their add-on order.
 */
internal fun fitsTonight(
    sections: List<CatalogSection>,
    completedVideoIds: Set<String>,
    minutesLeft: Int,
    nowEpochMillis: Long = System.currentTimeMillis(),
): List<MediaPreview> {
    val picked = LinkedHashMap<String, MediaPreview>()
    for (section in sections) {
        if (section.id == FITS_TONIGHT_SECTION_ID) continue
        for (media in section.items) {
            val runtime = media.runtimeMinutes ?: continue
            if (media.type != MediaType.MOVIE || runtime > minutesLeft) continue
            if (media.id in completedVideoIds || media.isUnreleased(nowEpochMillis)) continue
            picked.putIfAbsent(media.stableKey, media)
            if (picked.size == FITS_TONIGHT_MAX_ITEMS) return picked.values.toList()
        }
    }
    return picked.values.toList()
}

/** What remains of a season: episodes the viewer has not finished, and their total time. */
internal data class SeasonTimeLeft(val episodes: Int, val minutes: Int)

/**
 * The aired, unfinished episodes of [season] and roughly how long they run,
 * from the series' per-episode [runtimeMinutes] (or what is left of a
 * started one). Only for a season the viewer has begun; null when there is
 * no running time, nothing begun, or nothing left.
 */
internal fun seasonTimeLeft(
    episodes: List<Episode>,
    season: Int?,
    progress: List<WatchProgress>,
    completedVideoIds: Set<String>,
    runtimeMinutes: Int?,
    nowEpochMillis: Long = System.currentTimeMillis(),
): SeasonTimeLeft? {
    if (season == null || season == 0 || runtimeMinutes == null || runtimeMinutes <= 0) return null
    val inSeason = episodes.filter { it.season == season }.distinctBy(Episode::id)
    val rows = progress.associateBy(WatchProgress::videoId)
    fun finished(episode: Episode) = episode.id in completedVideoIds || rows[episode.id]?.completed == true
    if (inSeason.none { finished(it) || rows[it.id] != null }) return null
    val left = inSeason.filter { !finished(it) && it.hasAired(nowEpochMillis) }
    if (left.isEmpty()) return null
    val minutes = left.sumOf { episode ->
        rows[episode.id]?.takeIf { it.durationMillis > 0 }
            ?.let { ((it.durationMillis - it.positionMillis).coerceAtLeast(0) / 60_000).toInt() }
            ?: runtimeMinutes
    }
    return SeasonTimeLeft(left.size, minutes.coerceAtLeast(1))
}

/** "22:30" or "10:30 PM", following the device's 12/24-hour choice. */
internal fun bedtimeLabel(context: Context, minutes: Int): String {
    val format = DateFormat.getTimeFormat(context)
    format.timeZone = TimeZone.getTimeZone("UTC")
    return format.format(Date(minutes.toLong() * 60_000))
}

/**
 * The "Fits tonight" row for Home, re-evaluated each minute so it appears in
 * the evening, narrows as bedtime nears, and leaves once it passes. Null when
 * [enabled] is off, outside the evening window, or when nothing fits.
 */
@Composable
internal fun rememberFitsTonightSection(
    sections: List<CatalogSection>,
    completedVideoIds: Set<String>,
    enabled: Boolean,
    bedtimeMinutes: Int,
): CatalogSection? {
    if (!enabled) return null
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(60_000 - value % 60_000)
            value = System.currentTimeMillis()
        }
    }
    val minutesLeft = minutesUntilBedtime(now, bedtimeMinutes) ?: return null
    val items = remember(sections, completedVideoIds, minutesLeft) {
        fitsTonight(sections, completedVideoIds, minutesLeft, now)
    }
    if (items.isEmpty()) return null
    val context = LocalContext.current
    val title = stringResource(
        R.string.fits_tonight_title,
        bedtimeLabel(context, bedtimeMinutes),
    )
    val subtitle = stringResource(R.string.fits_tonight_subtitle)
    return remember(items, title, subtitle) {
        CatalogSection(
            id = FITS_TONIGHT_SECTION_ID,
            providerId = "",
            title = title,
            providerName = subtitle,
            items = items,
        )
    }
}

/** "4 episodes left · about 3h 10m". */
@Composable
internal fun seasonTimeLeftText(left: SeasonTimeLeft): String {
    val minutes = left.minutes
    val duration = when {
        minutes >= 60 && minutes % 60 == 0 -> stringResource(R.string.detail_hours, minutes / 60)
        minutes >= 60 -> stringResource(R.string.detail_hours_minutes, minutes / 60, minutes % 60)
        else -> stringResource(R.string.minutes_format, minutes)
    }
    return pluralStringResource(R.plurals.season_time_left, left.episodes, left.episodes, duration)
}
