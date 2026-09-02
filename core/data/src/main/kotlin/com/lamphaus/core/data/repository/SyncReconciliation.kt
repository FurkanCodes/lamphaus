package com.lamphaus.core.data.repository

import com.lamphaus.core.model.LibraryEntry
import com.lamphaus.core.model.WatchProgress
import kotlinx.coroutines.flow.first

/**
 * Cloud→Room reconciliation for authenticated realtime rounds (SHR-ARC-05).
 * A successful cloud emission is authoritative only for keys that were seen
 * in a previous successful cloud snapshot. A local-only key may be waiting
 * for upload, so absence alone must never delete it.
 *
 * Callers must NOT run these against a failed flow round — Supabase gateway
 * library/progress flows propagate network errors instead of emitting empty
 * lists, so the collector simply skips the round on failure and no
 * network-error empty result is ever mistaken for a deletion set.
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
