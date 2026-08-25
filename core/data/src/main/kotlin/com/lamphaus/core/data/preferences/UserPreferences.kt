package com.lamphaus.core.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lamphaus.core.model.DiagnosticsConsent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("lamphaus_preferences")

enum class ThemePreference { SYSTEM, LIGHT, DARK }

data class UserSettings(
    val activeProfileId: String? = null,
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val dynamicColor: Boolean = true,
    val diagnostics: DiagnosticsConsent = DiagnosticsConsent(),
)

class UserPreferences(private val context: Context) {
    val settings: Flow<UserSettings> = context.dataStore.data.map { values ->
        UserSettings(
            activeProfileId = values[ACTIVE_PROFILE],
            theme = values[THEME]?.let { runCatching { ThemePreference.valueOf(it) }.getOrNull() }
                ?: ThemePreference.SYSTEM,
            dynamicColor = values[DYNAMIC_COLOR] ?: true,
            diagnostics = DiagnosticsConsent(
                crashReports = values[CRASH_REPORTS] ?: false,
                performanceMetrics = values[PERFORMANCE] ?: false,
            ),
        )
    }

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
        context.dataStore.edit { it[THEME] = theme.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[DYNAMIC_COLOR] = enabled }
    }

    suspend fun setDiagnostics(consent: DiagnosticsConsent) {
        context.dataStore.edit {
            it[CRASH_REPORTS] = consent.crashReports
            it[PERFORMANCE] = consent.performanceMetrics
        }
    }

    private companion object {
        val ACTIVE_PROFILE = stringPreferencesKey("active_profile")
        val PAIRING_DEVICE_ID = stringPreferencesKey("pairing_device_id")
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val CRASH_REPORTS = booleanPreferencesKey("crash_reports")
        val PERFORMANCE = booleanPreferencesKey("performance_metrics")
    }
}

