package com.lamphaus.core.data.repository

import com.lamphaus.core.data.cloud.CloudCollection
import com.lamphaus.core.data.cloud.CloudDeletion
import com.lamphaus.core.model.LibraryEntry
import com.lamphaus.core.model.WatchProgress
import kotlinx.coroutines.flow.first

/**
 * Cloud→Room reconciliation for a full account snapshot (SHR-ARC-05).
 * A successful snapshot is authoritative only for keys that were seen in a
 * previous successful cloud snapshot. A local-only key may be waiting for
 * upload, so absence alone must never delete it.
 *
 * Callers must NOT run these against a failed pull: a network error is never
 * an empty snapshot, so no failure is ever mistaken for a deletion set.
 */
suspend fun LibraryRepository.reconcileProgress(profileId: String, cloud: List<WatchProgress>) {
    val cloudVideoIds = cloud.map(WatchProgress::videoId).toSet()
    val previouslyCloudBacked = cloudSyncKeys(profileId, CloudSyncCollection.PROGRESS)
    val remotelyDeleted = previouslyCloudBacked - cloudVideoIds
    progress(profileId).first()
        .filter { row -> row.videoId in remotelyDeleted }
        .forEach { deleted -> removeProgress(profileId, deleted.videoId) }
    cloud.forEach { saveProgress(it) }
    replaceCloudSyncKeys(profileId, CloudSyncCollection.PROGRESS, cloudVideoIds)
}

suspend fun LibraryRepository.reconcileLibrary(profileId: String, cloud: List<LibraryEntry>) {
    val cloudMediaKeys = cloud.map(LibraryEntry::mediaKey).toSet()
    val previouslyCloudBacked = cloudSyncKeys(profileId, CloudSyncCollection.LIBRARY)
    val remotelyDeleted = previouslyCloudBacked - cloudMediaKeys
    library(profileId).first()
        .filter { entry -> entry.mediaKey in remotelyDeleted }
        .forEach { deleted -> removeLibrary(profileId, deleted.mediaKey) }
    cloud.forEach { saveLibrary(it) }
    replaceCloudSyncKeys(profileId, CloudSyncCollection.LIBRARY, cloudMediaKeys)
}

/**
 * Applies an incremental pull: only rows another device changed, plus the
 * rows it removed. Removals go first because a row that is still live is
 * always newer than any removal of the same key. A pull also returns this
 * device's own earlier uploads, and playback keeps saving locally between
 * uploads, so a progress row older than the local one is skipped. The
 * cloud-confirmed keys follow along, so a later full snapshot reconciles
 * against the truth.
 */
suspend fun LibraryRepository.applyCloudDelta(
    library: List<LibraryEntry>,
    progress: List<WatchProgress>,
    deletions: List<CloudDeletion>,
) {
    val removedLibrary = deletions.filter { it.collection == CloudCollection.LIBRARY }
    val removedProgress = deletions.filter { it.collection == CloudCollection.PROGRESS }
    removedLibrary.forEach { removeLibrary(it.profileId, it.key) }
    removedProgress.forEach { removeProgress(it.profileId, it.key) }
    library.forEach { saveLibrary(it) }
    progress.forEach { row ->
        val local = progressEntry(row.profileId, row.videoId)
        if (local == null || row.updatedAtEpochMillis >= local.updatedAtEpochMillis) saveProgress(row)
    }

    val libraryProfiles = removedLibrary.map(CloudDeletion::profileId) + library.map(LibraryEntry::profileId)
    libraryProfiles.toSet().forEach { profileId ->
        val keys = cloudSyncKeys(profileId, CloudSyncCollection.LIBRARY) -
            removedLibrary.filter { it.profileId == profileId }.map(CloudDeletion::key).toSet() +
            library.filter { it.profileId == profileId }.map(LibraryEntry::mediaKey)
        replaceCloudSyncKeys(profileId, CloudSyncCollection.LIBRARY, keys)
    }
    val progressProfiles = removedProgress.map(CloudDeletion::profileId) + progress.map(WatchProgress::profileId)
    progressProfiles.toSet().forEach { profileId ->
        val keys = cloudSyncKeys(profileId, CloudSyncCollection.PROGRESS) -
            removedProgress.filter { it.profileId == profileId }.map(CloudDeletion::key).toSet() +
            progress.filter { it.profileId == profileId }.map(WatchProgress::videoId)
        replaceCloudSyncKeys(profileId, CloudSyncCollection.PROGRESS, keys)
    }
}
