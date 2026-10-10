package com.lamphaus.core.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lamphaus.core.model.DiagnosticsConsent
import com.lamphaus.core.model.NextEpisodePolicy
import com.lamphaus.core.model.NextEpisodeThresholdMode
import com.lamphaus.core.model.AudioOutputMode
import com.lamphaus.core.model.DecoderPriority
import com.lamphaus.core.model.DevicePlaybackConfig
import com.lamphaus.core.model.StreamingChoices
import com.lamphaus.core.model.AudioChoices
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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

private val Context.dataStore by preferencesDataStore("lamphaus_preferences")

enum class ThemePreference { SYSTEM, LIGHT, DARK }

data class UserSettings(
    val activeProfileId: String? = null,
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val dynamicColor: Boolean = true,
    val kenBurnsEnabled: Boolean = true,
    val localOnlyArtworkKeys: Boolean = false,
    /**
     * Device-local TV choice for the dimmed artwork behind browsing screens.
     * Null means automatic (off on low-memory devices); not synced, because
     * the right answer depends on each TV.
     */
    val backgroundArtwork: Boolean? = null,
    /** Device-local TV Home layout name; null means the default (spotlight). */
    val tvHomeLayout: String? = null,
    /** Device-local TV navigation placement name; null means the default (side rail). */
    val tvNavigationStyle: String? = null,
    /** Device-local TV choice: black background and panels instead of the dark grey scheme. */
    val tvBlackBackground: Boolean = false,
    /** Device-local TV choice: pages fade in when they open (TV-MOT-01). On by default. */
    val tvPageTransitions: Boolean = true,
    /**
     * Device-local trailer choice. Null means the platform default: the
     * mobile Trailer button is on, TV Spotlight previews are opt-in.
     */
    val trailers: Boolean? = null,
    /** Device-local: leave not-yet-released titles out of Home and Discover rows. */
    val hideUnreleased: Boolean = false,
    /** Device-local: source cards say how each source will play here (SHR-PROD-12). */
    val sourceFit: Boolean = true,
    /** Device-local: a series' details page recaps the last finished episode (SHR-PROD-13). */
    val seriesRecap: Boolean = true,
    /** Device-local: Home's "Fits tonight" row and season time left (SHR-PROD-14). */
    val fitsTonight: Boolean = true,
    /** Device-local "Ends before" time for Fits tonight, in minutes after midnight; null is the default. */
    val bedtimeMinutes: Int? = null,
    /** Device-local: Continue watching on the Google TV home screen (TV-HOME-01). */
    val googleTvHome: Boolean = true,
    /** Up-next cards the viewer removed, as `mediaKey|finishedVideoId`; a newer finish shows it again. */
    val upNextDismissed: Set<String> = emptySet(),
    /** Device-local mobile opt-in: notify when a followed series gets a new episode (SHR-PROD-16). */
    val newEpisodeAlerts: Boolean = false,
    /** Device-local: the new-episode invitation was answered, so it never shows again (MOB-NOT-02). */
    val newEpisodePromptAnswered: Boolean = false,
    /** Device-local: Library shows last month's private recap in the first week (SHR-PROD-17). */
    val monthlyRecap: Boolean = true,
    /** Device-local: the recap month (`yyyy-MM`) the viewer dismissed. */
    val recapDismissedMonth: String? = null,
    /** Device-local TV opt-in: the ambient screensaver covers the app after idle (TV-AMB-01). */
    val tvIdleAmbient: Boolean = false,
    /** Device-local: minutes without the remote before the idle ambient starts (TV-AMB-01). */
    val tvIdleAmbientMinutes: Int = DEFAULT_TV_IDLE_AMBIENT_MINUTES,
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
    /** Null when the row was written by a version that does not sync stream badges. */
    val streamBadges: SyncedStreamBadges? = null,
    val updatedAtEpochMillis: Long = 0,
)

/**
 * The account's imported stream-badge file, by address only: each device
 * downloads and validates the file itself. A null [sourceUrl] means none.
 */
@Serializable
data class SyncedStreamBadges(val sourceUrl: String? = null)

/** Bookkeeping for one profile's new-episode check; null [checkedAtEpochMillis] means never run. */
data class NewEpisodeCheckState(
    val checkedAtEpochMillis: Long? = null,
    val notifiedVideoIds: Set<String> = emptySet(),
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
            backgroundArtwork = values[BACKGROUND_ARTWORK],
            tvHomeLayout = values[TV_HOME_LAYOUT],
            tvNavigationStyle = values[TV_NAVIGATION_STYLE],
            tvBlackBackground = values[TV_BLACK_BACKGROUND] ?: false,
            tvPageTransitions = values[TV_PAGE_TRANSITIONS] ?: true,
            trailers = values[TRAILERS],
            hideUnreleased = values[HIDE_UNRELEASED] ?: false,
            sourceFit = values[SOURCE_FIT] ?: true,
            seriesRecap = values[SERIES_RECAP] ?: true,
            fitsTonight = values[FITS_TONIGHT] ?: true,
            bedtimeMinutes = values[BEDTIME_MINUTES],
            googleTvHome = values[GOOGLE_TV_HOME] ?: true,
            upNextDismissed = values[UP_NEXT_DISMISSED].orEmpty(),
            newEpisodeAlerts = values[NEW_EPISODE_ALERTS] ?: false,
            newEpisodePromptAnswered = values[NEW_EPISODE_PROMPT_ANSWERED] ?: false,
            monthlyRecap = values[MONTHLY_RECAP] ?: true,
            recapDismissedMonth = values[RECAP_DISMISSED_MONTH],
            tvIdleAmbient = values[TV_IDLE_AMBIENT] ?: false,
            tvIdleAmbientMinutes = values[TV_IDLE_AMBIENT_MINUTES]
                ?.takeIf { it in TV_IDLE_AMBIENT_MINUTE_CHOICES }
                ?: DEFAULT_TV_IDLE_AMBIENT_MINUTES,
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
                autoPlayNext = values[PLAYBACK_AUTO_PLAY_NEXT],
                askBeforeNext = values[PLAYBACK_ASK_BEFORE_NEXT],
                endPromptAutoClose = values[PLAYBACK_END_PROMPT_AUTO_CLOSE],
                autoSkipIntro = values[PLAYBACK_AUTO_SKIP_INTRO],
                autoSkipRecap = values[PLAYBACK_AUTO_SKIP_RECAP],
                autoSkipOutro = values[PLAYBACK_AUTO_SKIP_OUTRO],
                autoSkipCredits = values[PLAYBACK_AUTO_SKIP_CREDITS],
                defaultPlayer = values[PLAYBACK_DEFAULT_PLAYER],
                externalForwardSubtitles = values[PLAYBACK_EXTERNAL_SUBTITLES],
                externalSendSkipSegments = values[PLAYBACK_EXTERNAL_SKIP_SEGMENTS],
            ),
            devicePlayback = devicePlaybackConfigFromKeys(
                engine = values[PLAYBACK_ENGINE],
                dolbyVision = values[PLAYBACK_DOLBY_VISION],
                frameRateMatching = values[PLAYBACK_FRAME_RATE_MATCHING],
                resolutionMatching = values[PLAYBACK_RESOLUTION_MATCHING],
                audioOutputMode = values[PLAYBACK_AUDIO_OUTPUT],
                decoderPriority = values[PLAYBACK_DECODER_PRIORITY],
                downmixMode = values[PLAYBACK_DOWNMIX],
                nightListening = values[PLAYBACK_NIGHT_LISTENING],
                nativeMemoryBuffer = values[PLAYBACK_NATIVE_MEMORY],
                parallelConnections = values[PLAYBACK_PARALLEL],
                parallelConnectionCount = values[PLAYBACK_PARALLEL_CONNECTIONS],
                parallelChunkSizeKb = values[PLAYBACK_PARALLEL_CHUNK_KB],
                dolbyVisionPreserveMapping = values[PLAYBACK_DV_PRESERVE_MAPPING],
                dolbyVisionProfile5To81 = values[PLAYBACK_DV_PROFILE5_TO_81],
                stripHdr10Plus = values[PLAYBACK_STRIP_HDR10_PLUS],
                tunneledPlayback = values[PLAYBACK_TUNNELED],
                skipSilence = values[PLAYBACK_SKIP_SILENCE],
                downmixChannels = values[PLAYBACK_DOWNMIX_CHANNELS],
                downmixKeepVolume = values[PLAYBACK_DOWNMIX_KEEP_VOLUME],
                forceAc3Transcode = values[PLAYBACK_FORCE_AC3],
                autoSwitchEngineOnStartupError = values[PLAYBACK_AUTO_SWITCH_ENGINE],
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

    /**
     * The account change number this device last pulled (push-to-pull sync),
     * or 0 when it has none for [userId] and must download the whole account.
     */
    suspend fun syncCursor(userId: String): Long {
        val stored = context.dataStore.data.first()[SYNC_CURSOR] ?: return 0
        val owner = stored.substringBefore(SYNC_CURSOR_SEPARATOR)
        return if (owner == userId) stored.substringAfter(SYNC_CURSOR_SEPARATOR).toLongOrNull() ?: 0 else 0
    }

    suspend fun setSyncCursor(userId: String, cursor: Long) {
        context.dataStore.edit { it[SYNC_CURSOR] = "$userId$SYNC_CURSOR_SEPARATOR$cursor" }
    }

    /** The account whose profiles, library, and history this device holds (SHR-PROD-06). */
    suspend fun localDataOwner(): String? = context.dataStore.data.first()[LOCAL_DATA_OWNER]

    suspend fun setLocalDataOwner(userId: String?) {
        context.dataStore.edit { values ->
            if (userId == null) values.remove(LOCAL_DATA_OWNER) else values[LOCAL_DATA_OWNER] = userId
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

    /** Marks an account-synced change made outside this store, such as a stream-badge import. */
    suspend fun touchSyncedSettings() {
        context.dataStore.edit { it[SETTINGS_UPDATED] = System.currentTimeMillis() }
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

    /** Device-local; deliberately does not touch the synced-settings timestamp. */
    suspend fun setBackgroundArtwork(enabled: Boolean) {
        context.dataStore.edit {
            it[BACKGROUND_ARTWORK] = enabled
        }
    }

    /** Device-local; deliberately does not touch the synced-settings timestamp. */
    suspend fun setTvHomeLayout(name: String) {
        context.dataStore.edit {
            it[TV_HOME_LAYOUT] = name
        }
    }

    /** Device-local; deliberately does not touch the synced-settings timestamp. */
    suspend fun setTvNavigationStyle(name: String) {
        context.dataStore.edit {
            it[TV_NAVIGATION_STYLE] = name
        }
    }

    /** Device-local; deliberately does not touch the synced-settings timestamp. */
    suspend fun setTvBlackBackground(enabled: Boolean) {
        context.dataStore.edit {
            it[TV_BLACK_BACKGROUND] = enabled
        }
    }

    /** Device-local; deliberately does not touch the synced-settings timestamp. */
    suspend fun setTvPageTransitions(enabled: Boolean) {
        context.dataStore.edit {
            it[TV_PAGE_TRANSITIONS] = enabled
        }
    }

    /** Device-local; deliberately does not touch the synced-settings timestamp. */
    suspend fun setTrailers(enabled: Boolean) {
        context.dataStore.edit {
            it[TRAILERS] = enabled
        }
    }

    /** Device-local. Keeps the newest entries so the set cannot grow without bound. */
    suspend fun dismissUpNext(key: String) {
        context.dataStore.edit {
            it[UP_NEXT_DISMISSED] = (it[UP_NEXT_DISMISSED].orEmpty() + key).toList().takeLast(200).toSet()
        }
    }

    /** Device-local; deliberately does not touch the synced-settings timestamp. */
    suspend fun setHideUnreleased(enabled: Boolean) {
        context.dataStore.edit {
            it[HIDE_UNRELEASED] = enabled
        }
    }

    /** Device-local; deliberately does not touch the synced-settings timestamp. */
    suspend fun setSourceFit(enabled: Boolean) {
        context.dataStore.edit {
            it[SOURCE_FIT] = enabled
        }
    }

    /** Device-local; deliberately does not touch the synced-settings timestamp. */
    suspend fun setSeriesRecap(enabled: Boolean) {
        context.dataStore.edit {
            it[SERIES_RECAP] = enabled
        }
    }

    /** Device-local; deliberately does not touch the synced-settings timestamp. */
    suspend fun setFitsTonight(enabled: Boolean) {
        context.dataStore.edit {
            it[FITS_TONIGHT] = enabled
        }
    }

    /** Device-local; deliberately does not touch the synced-settings timestamp. */
    /** Device-local; deliberately does not touch the synced-settings timestamp. */
    suspend fun setGoogleTvHome(enabled: Boolean) {
        context.dataStore.edit {
            it[GOOGLE_TV_HOME] = enabled
        }
    }

    /** Device-local; turning alerts on or off also answers the invitation. */
    suspend fun setNewEpisodeAlerts(enabled: Boolean) {
        context.dataStore.edit {
            it[NEW_EPISODE_ALERTS] = enabled
            it[NEW_EPISODE_PROMPT_ANSWERED] = true
        }
    }

    /** Device-local: "Not now" on the invitation. */
    suspend fun answerNewEpisodePrompt() {
        context.dataStore.edit { it[NEW_EPISODE_PROMPT_ANSWERED] = true }
    }

    /** Device-local; deliberately does not touch the synced-settings timestamp. */
    suspend fun setMonthlyRecap(enabled: Boolean) {
        context.dataStore.edit { it[MONTHLY_RECAP] = enabled }
    }

    /** Device-local: null brings a dismissed recap back (Undo). */
    suspend fun setRecapDismissedMonth(month: String?) {
        context.dataStore.edit {
            if (month == null) it.remove(RECAP_DISMISSED_MONTH) else it[RECAP_DISMISSED_MONTH] = month
        }
    }

    /** Device-local; deliberately does not touch the synced-settings timestamp. */
    suspend fun setTvIdleAmbient(enabled: Boolean) {
        context.dataStore.edit { it[TV_IDLE_AMBIENT] = enabled }
    }

    /** Device-local; one of [TV_IDLE_AMBIENT_MINUTE_CHOICES]. */
    suspend fun setTvIdleAmbientMinutes(minutes: Int) {
        require(minutes in TV_IDLE_AMBIENT_MINUTE_CHOICES)
        context.dataStore.edit { it[TV_IDLE_AMBIENT_MINUTES] = minutes }
    }

    /**
     * A profile's recent searches, newest first (MOB-SRCH-01). Device-local and
     * never synced or logged: queries are private (SHR-PROD-06).
     */
    fun searchHistory(profileId: String): Flow<List<String>> =
        context.dataStore.data.map { decodeStrings(it[searchHistoryKey(profileId)]) }

    suspend fun updateSearchHistory(profileId: String, transform: (List<String>) -> List<String>) {
        context.dataStore.edit { values ->
            val key = searchHistoryKey(profileId)
            val updated = transform(decodeStrings(values[key]))
            if (updated.isEmpty()) values.remove(key) else values[key] = historyJson.encodeToString(updated)
        }
    }

    /** What the new-episode check has already seen for a profile (SHR-PROD-16). */
    suspend fun newEpisodeState(profileId: String): NewEpisodeCheckState {
        val values = context.dataStore.data.first()
        return NewEpisodeCheckState(
            checkedAtEpochMillis = values[longPreferencesKey("$NEW_EPISODE_CHECKED_PREFIX$profileId")],
            notifiedVideoIds = values[stringSetPreferencesKey("$NEW_EPISODE_NOTIFIED_PREFIX$profileId")].orEmpty(),
        )
    }

    suspend fun saveNewEpisodeState(profileId: String, state: NewEpisodeCheckState) {
        context.dataStore.edit { values ->
            state.checkedAtEpochMillis?.let { values[longPreferencesKey("$NEW_EPISODE_CHECKED_PREFIX$profileId")] = it }
            values[stringSetPreferencesKey("$NEW_EPISODE_NOTIFIED_PREFIX$profileId")] =
                state.notifiedVideoIds.toList().takeLast(NEW_EPISODE_NOTIFIED_LIMIT).toSet()
        }
    }

    /**
     * Forgets per-person history on leaving an account: recent searches,
     * new-episode bookkeeping, and the recap dismissal (SHR-PROD-06).
     */
    suspend fun clearPersonalHistory() {
        context.dataStore.edit { values ->
            values.asMap().keys
                .filter { key ->
                    key.name.startsWith(SEARCH_HISTORY_PREFIX) ||
                        key.name.startsWith(NEW_EPISODE_CHECKED_PREFIX) ||
                        key.name.startsWith(NEW_EPISODE_NOTIFIED_PREFIX)
                }
                .toList()
                .forEach { values.remove(it) }
            values.remove(RECAP_DISMISSED_MONTH)
        }
    }

    suspend fun setBedtimeMinutes(minutes: Int) {
        context.dataStore.edit {
            it[BEDTIME_MINUTES] = minutes.mod(24 * 60)
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
            it[PLAYBACK_AUTO_PLAY_NEXT] = settings.autoPlayNextEpisode
            it[PLAYBACK_ASK_BEFORE_NEXT] = settings.askBeforeNextEpisode
            it[PLAYBACK_END_PROMPT_AUTO_CLOSE] = settings.endPromptAutoClose
            it[PLAYBACK_AUTO_SKIP_INTRO] = settings.autoSkipIntro
            it[PLAYBACK_AUTO_SKIP_RECAP] = settings.autoSkipRecap
            it[PLAYBACK_AUTO_SKIP_OUTRO] = settings.autoSkipOutro
            it[PLAYBACK_AUTO_SKIP_CREDITS] = settings.autoSkipCredits
            it[PLAYBACK_DEFAULT_PLAYER] = settings.defaultPlayer.name
            it[PLAYBACK_EXTERNAL_SUBTITLES] = settings.externalForwardSubtitles
            it[PLAYBACK_EXTERNAL_SKIP_SEGMENTS] = settings.externalSendSkipSegments
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
            it[PLAYBACK_NIGHT_LISTENING] = config.nightListening
            it[PLAYBACK_NATIVE_MEMORY] = config.nativeMemoryBuffer
            it[PLAYBACK_PARALLEL] = config.parallelConnections
            it[PLAYBACK_PARALLEL_CONNECTIONS] = StreamingChoices.connections(config.parallelConnectionCount)
            it[PLAYBACK_PARALLEL_CHUNK_KB] = StreamingChoices.chunkSizeKb(config.parallelChunkSizeKb)
            it[PLAYBACK_DV_PRESERVE_MAPPING] = config.dolbyVisionPreserveMapping
            it[PLAYBACK_DV_PROFILE5_TO_81] = config.dolbyVisionProfile5To81
            it[PLAYBACK_STRIP_HDR10_PLUS] = config.stripHdr10Plus
            it[PLAYBACK_TUNNELED] = config.tunneledPlayback
            it[PLAYBACK_SKIP_SILENCE] = config.skipSilence
            it[PLAYBACK_DOWNMIX_CHANNELS] = AudioChoices.downmixChannels(config.downmixChannels)
            it[PLAYBACK_DOWNMIX_KEEP_VOLUME] = config.downmixKeepVolume
            it[PLAYBACK_FORCE_AC3] = config.forceAc3Transcode
            it[PLAYBACK_AUTO_SWITCH_ENGINE] = config.autoSwitchEngineOnStartupError
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
            it.remove(SYNC_CURSOR)
        }
    }

    private companion object {
        const val SEARCH_HISTORY_PREFIX = "search_history_"
        const val NEW_EPISODE_CHECKED_PREFIX = "new_episode_checked_"
        const val NEW_EPISODE_NOTIFIED_PREFIX = "new_episode_notified_"
        const val NEW_EPISODE_NOTIFIED_LIMIT = 500
        val historyJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

        fun searchHistoryKey(profileId: String) = stringPreferencesKey("$SEARCH_HISTORY_PREFIX$profileId")

        fun decodeStrings(raw: String?): List<String> =
            raw?.let { runCatching { historyJson.decodeFromString<List<String>>(it) }.getOrNull() }.orEmpty()

        val NEW_EPISODE_ALERTS = booleanPreferencesKey("new_episode_alerts")
        val NEW_EPISODE_PROMPT_ANSWERED = booleanPreferencesKey("new_episode_prompt_answered")
        val MONTHLY_RECAP = booleanPreferencesKey("monthly_recap")
        val RECAP_DISMISSED_MONTH = stringPreferencesKey("recap_dismissed_month")
        val TV_IDLE_AMBIENT = booleanPreferencesKey("tv_idle_ambient")
        val TV_IDLE_AMBIENT_MINUTES = intPreferencesKey("tv_idle_ambient_minutes")
        val ACTIVE_PROFILE = stringPreferencesKey("active_profile")
        val PAIRING_DEVICE_ID = stringPreferencesKey("pairing_device_id")
        val SYNC_CURSOR = stringPreferencesKey("sync_cursor")
        val LOCAL_DATA_OWNER = stringPreferencesKey("local_data_owner")
        const val SYNC_CURSOR_SEPARATOR = '|'
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEN_BURNS_ENABLED = booleanPreferencesKey("ken_burns_enabled")
        val CRASH_REPORTS = booleanPreferencesKey("crash_reports")
        val LOCAL_ONLY_ARTWORK_KEYS = booleanPreferencesKey("local_only_artwork_keys")
        val BACKGROUND_ARTWORK = booleanPreferencesKey("background_artwork")
        val TV_HOME_LAYOUT = stringPreferencesKey("tv_home_layout")
        val TV_NAVIGATION_STYLE = stringPreferencesKey("tv_navigation_style")
        val TV_BLACK_BACKGROUND = booleanPreferencesKey("tv_black_background")
        val TV_PAGE_TRANSITIONS = booleanPreferencesKey("tv_page_transitions")
        val TRAILERS = booleanPreferencesKey("trailers")
        val HIDE_UNRELEASED = booleanPreferencesKey("hide_unreleased")
        val SOURCE_FIT = booleanPreferencesKey("source_fit")
        val SERIES_RECAP = booleanPreferencesKey("series_recap")
        val FITS_TONIGHT = booleanPreferencesKey("fits_tonight")
        val GOOGLE_TV_HOME = booleanPreferencesKey("google_tv_home")
        val BEDTIME_MINUTES = intPreferencesKey("bedtime_minutes")
        val UP_NEXT_DISMISSED = stringSetPreferencesKey("up_next_dismissed")
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
        val PLAYBACK_AUTO_PLAY_NEXT = booleanPreferencesKey("playback_auto_play_next")
        val PLAYBACK_ASK_BEFORE_NEXT = booleanPreferencesKey("playback_ask_before_next")
        val PLAYBACK_END_PROMPT_AUTO_CLOSE = booleanPreferencesKey("playback_end_prompt_auto_close")
        val PLAYBACK_AUTO_SKIP_INTRO = booleanPreferencesKey("playback_auto_skip_intro")
        val PLAYBACK_AUTO_SKIP_RECAP = booleanPreferencesKey("playback_auto_skip_recap")
        val PLAYBACK_AUTO_SKIP_OUTRO = booleanPreferencesKey("playback_auto_skip_outro")
        val PLAYBACK_AUTO_SKIP_CREDITS = booleanPreferencesKey("playback_auto_skip_credits")
        val PLAYBACK_DEFAULT_PLAYER = stringPreferencesKey("playback_default_player")
        val PLAYBACK_EXTERNAL_SUBTITLES = booleanPreferencesKey("playback_external_subtitles")
        val PLAYBACK_EXTERNAL_SKIP_SEGMENTS = booleanPreferencesKey("playback_external_skip_segments")
        val PLAYBACK_ENGINE = stringPreferencesKey("playback_engine")
        val PLAYBACK_DOLBY_VISION = stringPreferencesKey("playback_dolby_vision")
        val PLAYBACK_FRAME_RATE_MATCHING = stringPreferencesKey("playback_frame_rate_matching")
        val PLAYBACK_RESOLUTION_MATCHING = stringPreferencesKey("playback_resolution_matching")
        val PLAYBACK_AUDIO_OUTPUT = stringPreferencesKey("playback_audio_output")
        val PLAYBACK_DECODER_PRIORITY = stringPreferencesKey("playback_decoder_priority")
        val PLAYBACK_DOWNMIX = stringPreferencesKey("playback_downmix")
        val PLAYBACK_NIGHT_LISTENING = booleanPreferencesKey("playback_night_listening")
        val PLAYBACK_NATIVE_MEMORY = booleanPreferencesKey("playback_native_memory")
        val PLAYBACK_PARALLEL = booleanPreferencesKey("playback_parallel_connections")
        val PLAYBACK_PARALLEL_CONNECTIONS = intPreferencesKey("playback_parallel_connection_count")
        val PLAYBACK_PARALLEL_CHUNK_KB = intPreferencesKey("playback_parallel_chunk_kb")
        val PLAYBACK_DV_PRESERVE_MAPPING = booleanPreferencesKey("playback_dv_preserve_mapping")
        val PLAYBACK_DV_PROFILE5_TO_81 = booleanPreferencesKey("playback_dv_profile5_to_81")
        val PLAYBACK_STRIP_HDR10_PLUS = booleanPreferencesKey("playback_strip_hdr10_plus")
        val PLAYBACK_TUNNELED = booleanPreferencesKey("playback_tunneled")
        val PLAYBACK_SKIP_SILENCE = booleanPreferencesKey("playback_skip_silence")
        val PLAYBACK_DOWNMIX_CHANNELS = intPreferencesKey("playback_downmix_channels")
        val PLAYBACK_DOWNMIX_KEEP_VOLUME = booleanPreferencesKey("playback_downmix_keep_volume")
        val PLAYBACK_FORCE_AC3 = booleanPreferencesKey("playback_force_ac3")
        val PLAYBACK_AUTO_SWITCH_ENGINE = booleanPreferencesKey("playback_auto_switch_engine")
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
    autoPlayNext: Boolean? = null,
    askBeforeNext: Boolean? = null,
    endPromptAutoClose: Boolean? = null,
    autoSkipIntro: Boolean? = null,
    autoSkipRecap: Boolean? = null,
    autoSkipOutro: Boolean? = null,
    autoSkipCredits: Boolean? = null,
    defaultPlayer: String? = null,
    externalForwardSubtitles: Boolean? = null,
    externalSendSkipSegments: Boolean? = null,
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
    autoPlayNextEpisode = autoPlayNext ?: true,
    askBeforeNextEpisode = askBeforeNext ?: false,
    endPromptAutoClose = endPromptAutoClose ?: false,
    autoSkipIntro = autoSkipIntro ?: false,
    autoSkipRecap = autoSkipRecap ?: false,
    autoSkipOutro = autoSkipOutro ?: false,
    autoSkipCredits = autoSkipCredits ?: false,
    defaultPlayer = defaultPlayer
        ?.let { name -> runCatching { com.lamphaus.core.model.DefaultPlayer.valueOf(name) }.getOrNull() }
        ?: com.lamphaus.core.model.DefaultPlayer.INTERNAL,
    externalForwardSubtitles = externalForwardSubtitles ?: false,
    externalSendSkipSegments = externalSendSkipSegments ?: false,
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
    nightListening: Boolean? = null,
    nativeMemoryBuffer: Boolean? = null,
    parallelConnections: Boolean? = null,
    parallelConnectionCount: Int? = null,
    parallelChunkSizeKb: Int? = null,
    dolbyVisionPreserveMapping: Boolean? = null,
    dolbyVisionProfile5To81: Boolean? = null,
    stripHdr10Plus: Boolean? = null,
    tunneledPlayback: Boolean? = null,
    skipSilence: Boolean? = null,
    downmixChannels: Int? = null,
    downmixKeepVolume: Boolean? = null,
    forceAc3Transcode: Boolean? = null,
    autoSwitchEngineOnStartupError: Boolean? = null,
): DevicePlaybackConfig {

    return DevicePlaybackConfig(
        engineKind = parseEnum(engine, PlaybackEngineKind.AUTO),
        dolbyVisionHandling = parseEnum(dolbyVision, DolbyVisionHandling.AUTO),
        frameRateMatching = parseEnum(frameRateMatching, FrameRateMatching.SEAMLESS_ONLY),
        resolutionMatching = parseEnum(resolutionMatching, ResolutionMatching.OFF),
        audioOutputMode = parseEnum(audioOutputMode, AudioOutputMode.AUTO),
        decoderPriority = parseEnum(decoderPriority, DecoderPriority.AUTO),
        downmixMode = parseEnum(downmixMode, DownmixMode.AUTO),
        nightListening = nightListening ?: false,
        nativeMemoryBuffer = nativeMemoryBuffer ?: false,
        parallelConnections = parallelConnections ?: false,
        parallelConnectionCount = StreamingChoices.connections(parallelConnectionCount),
        parallelChunkSizeKb = StreamingChoices.chunkSizeKb(parallelChunkSizeKb),
        dolbyVisionPreserveMapping = dolbyVisionPreserveMapping ?: false,
        dolbyVisionProfile5To81 = dolbyVisionProfile5To81 ?: false,
        stripHdr10Plus = stripHdr10Plus ?: false,
        tunneledPlayback = tunneledPlayback ?: false,
        skipSilence = skipSilence ?: false,
        downmixChannels = AudioChoices.downmixChannels(downmixChannels),
        downmixKeepVolume = downmixKeepVolume ?: true,
        forceAc3Transcode = forceAc3Transcode ?: false,
        autoSwitchEngineOnStartupError = autoSwitchEngineOnStartupError ?: false,
    )
}

/** Idle delays the TV ambient offers (TV-AMB-01). */
val TV_IDLE_AMBIENT_MINUTE_CHOICES = listOf(1, 2, 5, 10, 15, 30)
const val DEFAULT_TV_IDLE_AMBIENT_MINUTES = 5
