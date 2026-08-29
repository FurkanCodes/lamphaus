package com.lamphaus.core.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lamphaus.core.model.DiagnosticsConsent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

private val Context.dataStore by preferencesDataStore("lamphaus_preferences")

enum class ThemePreference { SYSTEM, LIGHT, DARK }

data class UserSettings(
    val activeProfileId: String? = null,
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val dynamicColor: Boolean = true,
    val kenBurnsEnabled: Boolean = true,
    val diagnostics: DiagnosticsConsent = DiagnosticsConsent(),
    val updatedAtEpochMillis: Long = 0,
)

/**
 * The account-following subset of [UserSettings], mirrored into the
 * `user_settings` cloud row (jsonb payload; last-writer-wins via
 * [updatedAtEpochMillis]). Deliberately excludes the active-profile choice:
 * which profile is selected stays device-local.
 */
@Serializable
data class SyncedSettings(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val dynamicColor: Boolean = true,
    val kenBurnsEnabled: Boolean = true,
    val diagnostics: DiagnosticsConsent = DiagnosticsConsent(),
    val updatedAtEpochMillis: Long = 0,
)

class UserPreferences(private val context: Context) {
    val settings: Flow<UserSettings> = context.dataStore.data.map { values ->
        UserSettings(
            activeProfileId = values[ACTIVE_PROFILE],
            theme = values[THEME]?.let { runCatching { ThemePreference.valueOf(it) }.getOrNull() }
                ?: ThemePreference.SYSTEM,
            dynamicColor = values[DYNAMIC_COLOR] ?: true,
            kenBurnsEnabled = values[KEN_BURNS_ENABLED] ?: true,
            diagnostics = DiagnosticsConsent(
                crashReports = values[CRASH_REPORTS] ?: false,
                performanceMetrics = values[PERFORMANCE] ?: false,
            ),
            updatedAtEpochMillis = values[SETTINGS_UPDATED] ?: 0L,
        )
    }

    suspend fun current(): UserSettings = settings.first()

    suspend fun setActiveProfile(id: String?) {
        context.dataStore.edit { values ->
            if (id == null) values.remove(ACTIVE_PROFILE) else values[ACTIVE_PROFILE] = id
        }
    }

    /** Hardware identity of the TV this install lives on (ANDROID_ID). */
    val pairingDeviceId: Flow<String?> = context.dataStore.data.map { it[PAIRING_DEVICE_ID] }

    suspend fun setPairingDeviceId(id: String?) {
        context.dataStore.edit { values ->
            if (id == null) values.remove(PAIRING_DEVICE_ID) else values[PAIRING_DEVICE_ID] = id
        }
    }

    suspend fun setTheme(theme: ThemePreference) {
        context.dataStore.edit {
            it[THEME] = theme.name
            it[SETTINGS_UPDATED] = System.currentTimeMillis()
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit {
            it[DYNAMIC_COLOR] = enabled
            it[SETTINGS_UPDATED] = System.currentTimeMillis()
        }
    }

    suspend fun setKenBurnsEnabled(enabled: Boolean) {
        context.dataStore.edit {
            it[KEN_BURNS_ENABLED] = enabled
            it[SETTINGS_UPDATED] = System.currentTimeMillis()
        }
    }

    suspend fun setDiagnostics(consent: DiagnosticsConsent) {
        context.dataStore.edit {
            it[CRASH_REPORTS] = consent.crashReports
            it[PERFORMANCE] = consent.performanceMetrics
            it[SETTINGS_UPDATED] = System.currentTimeMillis()
        }
    }

    /**
     * Adopts an inbound cloud row that won last-writer-wins locally. The row's
     * timestamp becomes the local one so older echoes keep losing.
     */
    suspend fun applyRemoteSettings(remote: SyncedSettings) {
        context.dataStore.edit {
            it[THEME] = remote.theme.name
            it[DYNAMIC_COLOR] = remote.dynamicColor
            it[KEN_BURNS_ENABLED] = remote.kenBurnsEnabled
            it[CRASH_REPORTS] = remote.diagnostics.crashReports
            it[PERFORMANCE] = remote.diagnostics.performanceMetrics
            it[SETTINGS_UPDATED] = remote.updatedAtEpochMillis
        }
    }

    /**
     * Clears the account-synced surface on leaving the account: theme and
     * especially diagnostics consent must not leak into a successor account.
     * The active-profile choice is device-local and survives.
     */
    suspend fun clearSyncedSettings() {
        context.dataStore.edit {
            it.remove(THEME)
            it.remove(DYNAMIC_COLOR)
            it.remove(KEN_BURNS_ENABLED)
            it.remove(CRASH_REPORTS)
            it.remove(PERFORMANCE)
            it.remove(SETTINGS_UPDATED)
        }
    }

    private companion object {
        val ACTIVE_PROFILE = stringPreferencesKey("active_profile")
        val PAIRING_DEVICE_ID = stringPreferencesKey("pairing_device_id")
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEN_BURNS_ENABLED = booleanPreferencesKey("ken_burns_enabled")
        val CRASH_REPORTS = booleanPreferencesKey("crash_reports")
        val PERFORMANCE = booleanPreferencesKey("performance_metrics")
        val SETTINGS_UPDATED = longPreferencesKey("settings_updated_epoch_millis")
    }
}
