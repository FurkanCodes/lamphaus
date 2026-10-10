package com.lamphaus.app.sync

import com.lamphaus.core.data.cloud.CloudLog
import com.lamphaus.core.data.cloud.CloudSyncGateway
import com.lamphaus.core.data.preferences.SyncedSettings
import com.lamphaus.core.data.preferences.SyncedStreamBadges
import com.lamphaus.core.data.preferences.UserPreferences
import com.lamphaus.core.data.repository.StreamBadgeRepository

/** The account change number each device last pulled (push-to-pull sync). */
interface SyncCursors {
    suspend fun cursor(userId: String): Long

    suspend fun setCursor(userId: String, cursor: Long)
}

/** The account's synced settings row, as pulls deliver it and as this device changes it. */
interface SyncedSettingsSync {
    /** Applies a pull: [remote] is the row when it changed; [hasSettings] whether the account has one at all. */
    suspend fun applyPulled(userId: String, hasSettings: Boolean, remote: SyncedSettings?)

    /** Pushes this device's synced settings to the account row. */
    suspend fun push(userId: String)
}

class PreferenceSyncCursors(private val preferences: UserPreferences) : SyncCursors {
    override suspend fun cursor(userId: String): Long = preferences.syncCursor(userId)

    override suspend fun setCursor(userId: String, cursor: Long) = preferences.setSyncCursor(userId, cursor)
}

/**
 * Settings follow the account. Inbound rows win only when newer than the
 * last local mutation (LWW); an account without a row is seeded from this
 * device. Applies never re-push, so pulls cannot loop.
 */
class PreferenceSettingsSync(
    private val gateway: CloudSyncGateway,
    private val preferences: UserPreferences,
    private val streamBadges: StreamBadgeRepository,
) : SyncedSettingsSync {
    override suspend fun applyPulled(userId: String, hasSettings: Boolean, remote: SyncedSettings?) {
        if (!hasSettings) {
            gateway.saveSettings(userId, localSyncedSettings())
                .onFailure { error -> CloudLog.w("settings.seed failed — staying local", error) }
            return
        }
        if (remote == null) return
        val local = preferences.current()
        when {
            remote.updatedAtEpochMillis > local.updatedAtEpochMillis -> {
                preferences.applyRemoteSettings(remote)
                applyRemoteStreamBadges(remote.streamBadges)
            }

            // This device already adopted the row: a badge download that failed then retries.
            remote.updatedAtEpochMillis == local.updatedAtEpochMillis ->
                applyRemoteStreamBadges(remote.streamBadges)
        }
        // A row from a version that did not sync badges: this device's import fills it in.
        if (remote.streamBadges == null && streamBadges.sourceUrl() != null) {
            preferences.touchSyncedSettings()
            push(userId)
        }
    }

    override suspend fun push(userId: String) {
        gateway.saveSettings(userId, localSyncedSettings())
            .onFailure { error -> CloudLog.w("settings.push failed — converges next pull", error) }
    }

    /** Adopts the account's badge file; each device downloads it, and a failure keeps the current badges. */
    private suspend fun applyRemoteStreamBadges(remote: SyncedStreamBadges?) {
        if (remote == null) return
        val wanted = remote.sourceUrl
        if (wanted == streamBadges.sourceUrl()) return
        if (wanted == null) {
            streamBadges.remove()
        } else {
            streamBadges.import(wanted).onFailure {
                // The badge address stays out of logs; only the failure kind is recorded.
                CloudLog.w("streamBadges.apply failed (${it::class.simpleName}) — keeping this device's badges")
            }
        }
    }

    private suspend fun localSyncedSettings(): SyncedSettings {
        val local = preferences.current()
        return SyncedSettings(
            theme = local.theme,
            dynamicColor = local.dynamicColor,
            kenBurnsEnabled = local.kenBurnsEnabled,
            diagnostics = local.diagnostics,
            spoilerProtection = local.spoilerProtection,
            streamBadges = SyncedStreamBadges(streamBadges.sourceUrl()),
            updatedAtEpochMillis = local.updatedAtEpochMillis,
        )
    }
}
