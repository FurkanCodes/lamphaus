package com.lamphaus.app.sync

import android.os.SystemClock
import com.lamphaus.core.data.cloud.CloudChanges
import com.lamphaus.core.data.cloud.CloudCollection
import com.lamphaus.core.data.cloud.CloudLog
import com.lamphaus.core.data.cloud.CloudSyncGateway
import com.lamphaus.core.data.repository.LibraryRepository
import com.lamphaus.core.data.repository.applyCloudDelta
import com.lamphaus.core.data.repository.reconcileLibrary
import com.lamphaus.core.data.repository.reconcileProgress
import com.lamphaus.core.model.ArtworkOverride
import com.lamphaus.core.model.ArtworkProviderStatus
import com.lamphaus.core.model.Profile
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Push-to-pull account sync (SHR-ARC-05, SHR-ARC-13). Room stays this
 * device's source of truth. Instead of holding a live connection per table,
 * the device downloads only what the account changed since its cursor:
 * after sign-in, when a screen starts, when the network returns, and when
 * another device's change arrives as a push or an on-screen live signal.
 *
 * Application-scoped so a push can sync while no screen exists. Pulls run
 * one at a time; a request answered by a pull that started after it is
 * coalesced away.
 */
class AccountSync(
    private val gateway: CloudSyncGateway,
    private val libraryRepository: LibraryRepository,
    private val cursors: SyncCursors,
    private val providerState: AccountProviderState,
    private val settings: SyncedSettingsSync,
    private val pushTokens: SyncPushTokens,
    private val installationId: String,
    private val television: Boolean,
    private val signedInUserId: () -> String?,
    private val elapsedMillis: () -> Long = SystemClock::elapsedRealtime,
) {
    enum class Reason {
        /** The first pull after sign-in or launch: always runs. */
        SIGN_IN,

        /** A screen started; skipped right after another pull. */
        SCREEN,

        /** Another device changed the account. */
        SIGNAL,

        /** The network came back. */
        NETWORK,
    }

    /** How this installation hears about other devices' changes. */
    enum class SignalMode {
        /** Firebase Cloud Messaging, also while the app is closed. */
        PUSH,

        /** No Google Play services: the live signal, only while on screen. */
        LIVE,
    }

    /** What the first pull after sign-in needs to decide how the account boots. */
    data class Outcome(val profileCount: Int)

    private val mutex = Mutex()

    @Volatile private var lastStartedAt = Long.MIN_VALUE
    @Volatile private var lastSucceededAt = Long.MIN_VALUE
    private var lastOutcome: Pair<String, Outcome>? = null

    /** Artwork overrides live in memory; [overridesUserId] owns the complete set. */
    private var overridesUserId: String? = null
    private val overrides = MutableStateFlow<Map<String, Map<String, ArtworkOverride>>>(emptyMap())

    private val mutableSignalMode = MutableStateFlow<SignalMode?>(null)
    val signalMode: StateFlow<SignalMode?> = mutableSignalMode.asStateFlow()

    private val providerChangeCount = MutableStateFlow(0L)

    /** Counts the pulls that reported changed add-ons or artwork keys. */
    val providerChanges: StateFlow<Long> = providerChangeCount.asStateFlow()

    /**
     * The change number to record once a fetch of the account's add-ons and
     * artwork keys that starts now succeeds, or null while this device's copy
     * is current. A device that never fetched them for [userId] is stale.
     */
    suspend fun staleProvidersAt(userId: String): Long? {
        val changed = providerState.changedAt(userId) ?: 0
        val fetched = providerState.fetchedAt(userId) ?: return changed
        return changed.takeIf { it > fetched }
    }

    suspend fun providersFetched(userId: String, at: Long) = providerState.setFetchedAt(userId, at)

    /** The artwork-key status this device last saw, so key-backed UI needs no request at launch. */
    suspend fun artworkStatuses(userId: String): List<ArtworkProviderStatus>? = providerState.artworkStatuses(userId)

    suspend fun rememberArtworkStatuses(userId: String, statuses: List<ArtworkProviderStatus>) =
        providerState.setArtworkStatuses(userId, statuses)

    fun artworkOverrides(profileId: String): Flow<List<ArtworkOverride>> =
        overrides.map { byProfile -> byProfile[profileId]?.values?.toList().orEmpty() }.distinctUntilChanged()

    /** Shows this device's own saved override at once; its upload signals the other devices. */
    fun rememberArtworkOverride(override: ArtworkOverride) {
        overrides.update { current ->
            val forProfile = current[override.profileId].orEmpty() + (override.mediaKey to override)
            current + (override.profileId to forProfile)
        }
    }

    /**
     * Downloads and applies what the account changed. Returns null when the
     * pull failed or [userId] is no longer signed in.
     */
    suspend fun pull(userId: String, reason: Reason): Outcome? {
        val requestedAt = elapsedMillis()
        if (reason == Reason.SCREEN && requestedAt - lastSucceededAt < SCREEN_PULL_INTERVAL_MILLIS) {
            return lastOutcomeFor(userId)
        }
        return mutex.withLock {
            if (reason != Reason.SIGN_IN && lastStartedAt > requestedAt) {
                lastOutcomeFor(userId)?.let { return@withLock it }
            }
            if (signedInUserId() != userId) return@withLock null
            lastStartedAt = elapsedMillis()
            pullLocked(userId, reason)
        }
    }

    /** Pushes this device's synced settings to the account row. */
    suspend fun pushSettings(userId: String) = settings.push(userId)

    /**
     * Registers where the account signals this installation: a push token,
     * or the live signal on devices without Google Play services.
     */
    suspend fun registerSignals(userId: String) {
        val token = pushTokens.token()
        mutableSignalMode.value = if (token != null) SignalMode.PUSH else SignalMode.LIVE
        gateway.registerSyncEndpoint(installationId, token, television)
    }

    /** The live signal for devices without push; collect it only while a screen shows. */
    fun liveSignals(userId: String): Flow<Unit> = gateway.changeSignals(userId)

    /**
     * Forgets the account on leaving it, after any running pull, so nothing
     * of it is applied over the wiped device. The push token goes too.
     */
    suspend fun forgetAccount() {
        mutex.withLock {
            lastOutcome = null
            lastSucceededAt = Long.MIN_VALUE
            overridesUserId = null
            overrides.value = emptyMap()
            mutableSignalMode.value = null
        }
        pushTokens.forget()
    }

    private fun lastOutcomeFor(userId: String): Outcome? = lastOutcome?.takeIf { it.first == userId }?.second

    private suspend fun pullLocked(userId: String, reason: Reason): Outcome? {
        val localProfiles = libraryRepository.profiles().first()
        // A device without local rows (fresh install, wiped data) downloads everything.
        val since = if (localProfiles.isEmpty()) 0 else cursors.cursor(userId)
        val changes = gateway.pullChanges(userId, since, allArtworkOverrides = overridesUserId != userId)
            .getOrElse { error ->
                CloudLog.w("sync.pull failed (${reason.name.lowercase()}) — keeping local data", error)
                return null
            }
        // Leaving the account mid-pull must not write its rows over the wipe.
        if (signedInUserId() != userId) return null
        apply(userId, changes, localProfiles)
        cursors.setCursor(userId, changes.cursor)
        if (changes.providersChanged) {
            providerState.setChangedAt(userId, changes.cursor)
            providerChangeCount.update { it + 1 }
        }
        lastSucceededAt = elapsedMillis()
        CloudLog.d(
            "sync.pull ${reason.name.lowercase()} full=${changes.full} profiles=${changes.profiles.size} " +
                "library=${changes.library.size} progress=${changes.progress.size} " +
                "artwork=${changes.artworkOverrides.size}${if (changes.artworkOverridesComplete) " (all)" else ""} " +
                "removed=${changes.deletions.size}",
        )
        return Outcome(changes.profileCount).also { lastOutcome = userId to it }
    }

    private suspend fun apply(userId: String, changes: CloudChanges, localProfiles: List<Profile>) {
        changes.profiles.forEach { libraryRepository.saveProfile(it, null) }
        if (changes.full) {
            // Legacy local installs may hold placeholder ids (e.g. "primary") that never sync.
            val profileIds = (localProfiles.map(Profile::id) + changes.profiles.map(Profile::id))
                .filter(::isCloudBackedId)
                .toSet()
            profileIds.forEach { profileId ->
                libraryRepository.reconcileLibrary(profileId, changes.library.filter { it.profileId == profileId })
                libraryRepository.reconcileProgress(profileId, changes.progress.filter { it.profileId == profileId })
            }
        } else {
            libraryRepository.applyCloudDelta(changes.library, changes.progress, changes.deletions)
        }
        applyArtworkOverrides(userId, changes)
        settings.applyPulled(userId, changes.hasSettings, changes.settings)
    }

    private fun applyArtworkOverrides(userId: String, changes: CloudChanges) {
        if (changes.artworkOverridesComplete) {
            overrides.value = changes.artworkOverrides
                .groupBy(ArtworkOverride::profileId)
                .mapValues { (_, rows) -> rows.associateBy(ArtworkOverride::mediaKey) }
            overridesUserId = userId
            return
        }
        val removed = changes.deletions.filter { it.collection == CloudCollection.ARTWORK_OVERRIDE }
        if (removed.isEmpty() && changes.artworkOverrides.isEmpty()) return
        overrides.update { current ->
            val next = current.mapValues { (_, rows) -> rows.toMutableMap() }.toMutableMap()
            removed.forEach { next[it.profileId]?.remove(it.key) }
            changes.artworkOverrides.forEach { row ->
                next.getOrPut(row.profileId) { mutableMapOf() }[row.mediaKey] = row
            }
            next
        }
    }

    companion object {
        /** A screen that starts within this long of the last pull reuses it. */
        const val SCREEN_PULL_INTERVAL_MILLIS = 30_000L

        /** Placeholder ids from legacy local installs cannot exist in Postgres. */
        fun isCloudBackedId(id: String) = runCatching { UUID.fromString(id) }.isSuccess
    }
}
