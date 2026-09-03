package com.lamphaus.core.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lamphaus.core.model.DiagnosticsConsent
import com.lamphaus.core.model.NextEpisodePolicy
import com.lamphaus.core.model.NextEpisodeThresholdMode
import com.lamphaus.core.model.AudioOutputMode
import com.lamphaus.core.model.DecoderPriority
import com.lamphaus.core.model.DevicePlaybackConfig
import com.lamphaus.core.model.DolbyVisionHandling
import com.lamphaus.core.model.DownmixMode
import com.lamphaus.core.model.FrameRateMatching
import com.lamphaus.core.model.PlaybackEngineKind
import com.lamphaus.core.model.ResolutionMatching
import com.lamphaus.core.model.PlaybackSettings
import com.lamphaus.core.model.SpoilerProtectionSettings
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
    val localOnlyArtworkKeys: Boolean = false,
    val diagnostics: DiagnosticsConsent = DiagnosticsConsent(),
    val spoilerProtection: SpoilerProtectionSettings = SpoilerProtectionSettings(),
    val playback: PlaybackSettings = PlaybackSettings(),
    /** Device-local player V2 knobs (engine, HDR, frame-rate, audio policy). */
    val devicePlayback: DevicePlaybackConfig = DevicePlaybackConfig(),
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
    val spoilerProtection: SpoilerProtectionSettings = SpoilerProtectionSettings(),
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
            localOnlyArtworkKeys = values[LOCAL_ONLY_ARTWORK_KEYS] ?: false,
            diagnostics = DiagnosticsConsent(
                crashReports = values[CRASH_REPORTS] ?: false,
                performanceMetrics = values[PERFORMANCE] ?: false,
            ),
            spoilerProtection = SpoilerProtectionSettings(
                enabled = values[SPOILER_PROTECTION_ENABLED] ?: true,
                blurEpisodeArtwork = values[SPOILER_BLUR_EPISODE_ARTWORK] ?: true,
                blurEpisodeSynopsis = values[SPOILER_BLUR_EPISODE_SYNOPSIS] ?: true,
            ),
            playback = playbackSettingsFromKeys(
                skipIntro = values[PLAYBACK_SKIP_INTRO],
                skipEnding = values[PLAYBACK_SKIP_ENDING],
                nextEpisode = values[PLAYBACK_NEXT_EPISODE],
                thresholdMode = values[PLAYBACK_NEXT_EPISODE_MODE],
                thresholdPercent = values[PLAYBACK_NEXT_EPISODE_PERCENT],
                thresholdMinutes = values[PLAYBACK_NEXT_EPISODE_MINUTES],
            ),
            devicePlayback = devicePlaybackConfigFromKeys(
                engine = values[PLAYBACK_ENGINE],
                dolbyVision = values[PLAYBACK_DOLBY_VISION],
                frameRateMatching = values[PLAYBACK_FRAME_RATE_MATCHING],
                resolutionMatching = values[PLAYBACK_RESOLUTION_MATCHING],
                audioOutputMode = values[PLAYBACK_AUDIO_OUTPUT],
                decoderPriority = values[PLAYBACK_DECODER_PRIORITY],
                downmixMode = values[PLAYBACK_DOWNMIX],
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

    suspend fun setLocalOnlyArtworkKeys(enabled: Boolean) {
        context.dataStore.edit {
            it[LOCAL_ONLY_ARTWORK_KEYS] = enabled
        }
    }


    suspend fun setDiagnostics(consent: DiagnosticsConsent) {
        context.dataStore.edit {
            it[CRASH_REPORTS] = consent.crashReports
            it[PERFORMANCE] = consent.performanceMetrics
            it[SETTINGS_UPDATED] = System.currentTimeMillis()
        }
    }

    suspend fun setSpoilerProtection(settings: SpoilerProtectionSettings) {
        context.dataStore.edit {
            it[SPOILER_PROTECTION_ENABLED] = settings.enabled
            it[SPOILER_BLUR_EPISODE_ARTWORK] = settings.blurEpisodeArtwork
            it[SPOILER_BLUR_EPISODE_SYNOPSIS] = settings.blurEpisodeSynopsis
            it[SETTINGS_UPDATED] = System.currentTimeMillis()
        }
    }

    /** Playback behavior is device-local: phones and TVs may intentionally use different controls. */
    suspend fun setPlaybackSettings(settings: PlaybackSettings) {
        context.dataStore.edit {
            it[PLAYBACK_SKIP_INTRO] = settings.skipIntroEnabled
            it[PLAYBACK_SKIP_ENDING] = settings.skipEndingEnabled
            it[PLAYBACK_NEXT_EPISODE] = settings.nextEpisodeEnabled
            it[PLAYBACK_NEXT_EPISODE_MODE] = settings.nextEpisodeThresholdMode.name
            it[PLAYBACK_NEXT_EPISODE_PERCENT] = settings.nextEpisodeThresholdPercent
            it[PLAYBACK_NEXT_EPISODE_MINUTES] = settings.nextEpisodeThresholdMinutesBeforeEnd
        }
    }

    /** Player V2 device-local knobs: engine, HDR/DV, frame-rate, audio policy. */
    suspend fun setDevicePlayback(config: DevicePlaybackConfig) {
        context.dataStore.edit {
            it[PLAYBACK_ENGINE] = config.engineKind.name
            it[PLAYBACK_DOLBY_VISION] = config.dolbyVisionHandling.name
            it[PLAYBACK_FRAME_RATE_MATCHING] = config.frameRateMatching.name
            it[PLAYBACK_RESOLUTION_MATCHING] = config.resolutionMatching.name
            it[PLAYBACK_AUDIO_OUTPUT] = config.audioOutputMode.name
            it[PLAYBACK_DECODER_PRIORITY] = config.decoderPriority.name
            it[PLAYBACK_DOWNMIX] = config.downmixMode.name
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
            it[SPOILER_PROTECTION_ENABLED] = remote.spoilerProtection.enabled
            it[SPOILER_BLUR_EPISODE_ARTWORK] = remote.spoilerProtection.blurEpisodeArtwork
            it[SPOILER_BLUR_EPISODE_SYNOPSIS] = remote.spoilerProtection.blurEpisodeSynopsis
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
            it.remove(SPOILER_PROTECTION_ENABLED)
            it.remove(SPOILER_BLUR_EPISODE_ARTWORK)
            it.remove(SPOILER_BLUR_EPISODE_SYNOPSIS)
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
        val LOCAL_ONLY_ARTWORK_KEYS = booleanPreferencesKey("local_only_artwork_keys")
        val PERFORMANCE = booleanPreferencesKey("performance_metrics")
        val SPOILER_PROTECTION_ENABLED = booleanPreferencesKey("spoiler_protection_enabled")
        val SPOILER_BLUR_EPISODE_ARTWORK = booleanPreferencesKey("spoiler_blur_episode_artwork")
        val SPOILER_BLUR_EPISODE_SYNOPSIS = booleanPreferencesKey("spoiler_blur_episode_synopsis")
        val PLAYBACK_SKIP_INTRO = booleanPreferencesKey("playback_skip_intro")
        val PLAYBACK_SKIP_ENDING = booleanPreferencesKey("playback_skip_ending")
        val PLAYBACK_NEXT_EPISODE = booleanPreferencesKey("playback_next_episode")
        val PLAYBACK_NEXT_EPISODE_MODE = stringPreferencesKey("playback_next_episode_mode")
        val PLAYBACK_NEXT_EPISODE_PERCENT = floatPreferencesKey("playback_next_episode_percent")
        val PLAYBACK_NEXT_EPISODE_MINUTES = floatPreferencesKey("playback_next_episode_minutes")
        val PLAYBACK_ENGINE = stringPreferencesKey("playback_engine")
        val PLAYBACK_DOLBY_VISION = stringPreferencesKey("playback_dolby_vision")
        val PLAYBACK_FRAME_RATE_MATCHING = stringPreferencesKey("playback_frame_rate_matching")
        val PLAYBACK_RESOLUTION_MATCHING = stringPreferencesKey("playback_resolution_matching")
        val PLAYBACK_AUDIO_OUTPUT = stringPreferencesKey("playback_audio_output")
        val PLAYBACK_DECODER_PRIORITY = stringPreferencesKey("playback_decoder_priority")
        val PLAYBACK_DOWNMIX = stringPreferencesKey("playback_downmix")
        val SETTINGS_UPDATED = longPreferencesKey("settings_updated_epoch_millis")
    }
}

/**
 * Maps raw DataStore values into [PlaybackSettings]. Missing keys (older
 * payloads) fall back to the shipped defaults, and the threshold floats are
 * defensively clamped so corrupted or hand-edited values cannot produce an
 * absurd trigger point.
 */
internal fun playbackSettingsFromKeys(
    skipIntro: Boolean?,
    skipEnding: Boolean?,
    nextEpisode: Boolean?,
    thresholdMode: String?,
    thresholdPercent: Float?,
    thresholdMinutes: Float?,
): PlaybackSettings = PlaybackSettings(
    skipIntroEnabled = skipIntro ?: true,
    skipEndingEnabled = skipEnding ?: true,
    nextEpisodeEnabled = nextEpisode ?: true,
    nextEpisodeThresholdMode = thresholdMode
        ?.let { mode -> runCatching { NextEpisodeThresholdMode.valueOf(mode) }.getOrNull() }
        ?: NextEpisodeThresholdMode.PERCENTAGE,
    nextEpisodeThresholdPercent = NextEpisodePolicy.clampedPercent(thresholdPercent ?: 98f),
    nextEpisodeThresholdMinutesBeforeEnd =
        NextEpisodePolicy.clampedMinutesBeforeEnd(thresholdMinutes ?: 2f),
)

/**
 * Maps raw DataStore values into [DevicePlaybackConfig]. Missing keys (older
 * payloads) fall back to the shipped defaults, and unknown names (payloads
 * written by newer builds) fall back too instead of crashing the read.
 */
private inline fun <reified T : Enum<T>> parseEnum(raw: String?, fallback: T): T =
    raw?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

internal fun devicePlaybackConfigFromKeys(
    engine: String?,
    dolbyVision: String?,
    frameRateMatching: String?,
    resolutionMatching: String?,
    audioOutputMode: String?,
    decoderPriority: String?,
    downmixMode: String?,
): DevicePlaybackConfig {

    return DevicePlaybackConfig(
        engineKind = parseEnum(engine, PlaybackEngineKind.AUTO),
        dolbyVisionHandling = parseEnum(dolbyVision, DolbyVisionHandling.AUTO),
        frameRateMatching = parseEnum(frameRateMatching, FrameRateMatching.SEAMLESS_ONLY),
        resolutionMatching = parseEnum(resolutionMatching, ResolutionMatching.OFF),
        audioOutputMode = parseEnum(audioOutputMode, AudioOutputMode.AUTO),
        decoderPriority = parseEnum(decoderPriority, DecoderPriority.AUTO),
        downmixMode = parseEnum(downmixMode, DownmixMode.AUTO),
    )
}
