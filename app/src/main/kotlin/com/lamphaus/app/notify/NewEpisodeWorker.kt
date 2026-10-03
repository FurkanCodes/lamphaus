package com.lamphaus.app.notify

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import com.lamphaus.app.LamphausApplication
import com.lamphaus.app.ui.upNextCandidates
import com.lamphaus.core.data.preferences.NewEpisodeCheckState
import com.lamphaus.core.model.FollowedSeries
import com.lamphaus.core.model.MediaType
import com.lamphaus.core.model.WatchProgress
import com.lamphaus.core.model.selectNewEpisodes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.TimeUnit

/**
 * The opt-in new-episode check (SHR-PROD-16). Every few hours, online and
 * with the battery not low, it asks the viewer's
 * own add-ons for the episode lists of followed series (Library plus
 * recently watched), exactly as Home's up-next does, and announces
 * episodes that aired since the last check. Nothing leaves the device but
 * the add-on requests the app would make anyway.
 */
internal class NewEpisodeWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as LamphausApplication).container
        val settings = container.preferences.current()
        val notifier = NewEpisodeNotifier(applicationContext)
        if (!settings.newEpisodeAlerts) return Result.success()
        val profileId = settings.activeProfileId
            ?: container.libraryRepository.profiles().first().firstOrNull()?.id
            ?: return Result.success()

        val now = System.currentTimeMillis()
        val progress = container.libraryRepository.progress(profileId).first()
        val watched = progress.filter(WatchProgress::completed).mapTo(HashSet()) { it.videoId }
        notifier.removeWatched(watched)

        val librarySeries = container.libraryRepository.library(profileId).first()
            .map { it.preview }
            .filter { it.type == MediaType.SERIES }
        val recentSeries = upNextCandidates(progress, settings.upNextDismissed, now).mapNotNull { it.preview }
        val candidates = (recentSeries + librarySeries).distinctBy { it.stableKey }.take(MAX_SERIES)
        if (candidates.isEmpty()) return Result.success()

        val gate = Semaphore(LOOKUP_PARALLELISM)
        val followed = coroutineScope {
            candidates.map { media ->
                async {
                    gate.withPermit {
                        runCatching { container.providerMetadataRepository.getSeriesEpisodes(media) }
                            .onFailure { if (it is CancellationException) throw it }
                            .getOrNull()
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { FollowedSeries(media, it) }
                    }
                }
            }.awaitAll().filterNotNull()
        }
        // Nothing answered (offline, add-ons down): keep the last check time so
        // the next run still sees what aired in between.
        if (followed.isEmpty()) return Result.retry()

        val state = container.preferences.newEpisodeState(profileId)
        val result = selectNewEpisodes(
            followed = followed,
            watchedVideoIds = watched,
            announcedVideoIds = state.notifiedVideoIds,
            lastCheckedAtEpochMillis = state.checkedAtEpochMillis,
            nowEpochMillis = now,
        )
        if (result.alerts.isNotEmpty() && notifier.canNotify()) {
            val posters = result.alerts.mapNotNull { alert ->
                alert.media.posterUrl?.let { url -> loadPoster(url)?.let { alert.media.stableKey to it } }
            }.toMap()
            notifier.post(result.alerts, posters)
        }
        container.preferences.saveNewEpisodeState(
            profileId,
            NewEpisodeCheckState(checkedAtEpochMillis = now, notifiedVideoIds = result.announcedVideoIds),
        )
        return Result.success()
    }

    private suspend fun loadPoster(url: String): Bitmap? = runCatching {
        val result = SingletonImageLoader.get(applicationContext).execute(
            ImageRequest.Builder(applicationContext)
                .data(url)
                .size(Size(POSTER_SIZE_PX, POSTER_SIZE_PX))
                .allowHardware(false)
                .build(),
        )
        (result as? SuccessResult)?.image?.toBitmap()
    }.onFailure { if (it is CancellationException) throw it }.getOrNull()

    companion object {
        private const val UNIQUE_WORK = "new-episode-check"
        private const val MAX_SERIES = 30
        private const val LOOKUP_PARALLELISM = 4
        private const val POSTER_SIZE_PX = 256
        private const val INTERVAL_HOURS = 6L

        /**
         * Starts or stops the periodic check. Only phones and tablets run it;
         * a television never schedules background work for this (TV-FND-01).
         */
        fun sync(context: Context, enabled: Boolean) {
            val workManager = WorkManager.getInstance(context)
            val isTv = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
            if (!enabled || isTv) {
                workManager.cancelUniqueWork(UNIQUE_WORK)
                if (!enabled) NewEpisodeNotifier(context).removeAll()
                return
            }
            NewEpisodeNotifier(context).ensureChannel()
            val request = PeriodicWorkRequestBuilder<NewEpisodeWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .build()
            workManager.enqueueUniquePeriodicWork(UNIQUE_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
