package com.lamphaus.core.model

/**
 * Track selection and playback-timing policy (plan §3/§4). Pure and total:
 * every function returns a deterministic result for any input so the engines
 * and the UI stay thin.
 */

// ── BCP-47 normalization ────────────────────────────────────────────────────

/**
 * Normalizes a language tag for comparison: trims, unifies `_`/`-` separators,
 * lowercases the language subtag and uppercases the region (`en_us` → `en-US`),
 * and maps "und"/empty to "". Malformed input degrades to its lowercased
 * primary subtag instead of throwing.
 */
fun normalizeBcp47Tag(raw: String?): String {
    val cleaned = raw?.trim()?.replace('_', '-')?.takeIf(String::isNotEmpty) ?: return ""
    val subtags = cleaned.split("-").filter(String::isNotEmpty)
    if (subtags.isEmpty()) return ""
    val language = subtags[0].lowercase()
    if (language == "und") return ""
    if (language.length < 2 || !language.all { it in 'a'..'z' }) return ""
    val rest = subtags.drop(1)
        .joinToString("-") { subtag ->
            when {
                subtag.length == 4 && subtag.all { it.isLetter() } ->
                    subtag.lowercase().replaceFirstChar(Char::uppercaseChar)
                subtag.length == 2 && subtag.all { it.isLetter() } -> subtag.uppercase()
                else -> subtag.lowercase()
            }
        }
    return if (rest.isEmpty()) language else "$language-$rest"
}

/** Primary language subtag of a normalized tag ("en-US" → "en"). */
fun baseLanguage(normalizedTag: String): String =
    normalizedTag.substringBefore('-').lowercase()

/**
 * True when a track's language satisfies a preferred tag: exact normalized
 * match, or the track carries the preferred base language with no competing
 * region requirement. A blank preferred tag never matches.
 */
fun languageMatches(trackLanguage: String?, preferredTag: String): Boolean {
    val preferred = normalizeBcp47Tag(preferredTag)
    if (preferred.isEmpty()) return false
    val track = normalizeBcp47Tag(trackLanguage)
    if (track.isEmpty()) return false
    if (track == preferred) return true
    return baseLanguage(track) == baseLanguage(preferred)
}

// ── Track roles ─────────────────────────────────────────────────────────────

enum class TrackRole { FORCED, SDH, COMMENTARY, AUDIO_DESCRIPTION, ORIGINAL }

/** Textual role detection from the label a provider attached to a track. */
fun subtitleRoles(label: String?, isForcedFlag: Boolean = false): Set<TrackRole> {
    val roles = mutableSetOf<TrackRole>()
    if (isForcedFlag) roles += TrackRole.FORCED
    // Providers attach labels like "English (CC)" or "Director's Commentary";
    // punctuation is stripped so word checks stay simple.
    val text = label?.lowercase()?.replace(Regex("[^a-z0-9]+"), " ") ?: return roles
    if ("forced" in text) roles += TrackRole.FORCED
    if (" sdh " in " $text " || "hearing" in text || "cc" in text.split(" ")) roles += TrackRole.SDH
    if ("commentary" in text || "comment" in text) roles += TrackRole.COMMENTARY
    if ("audio description" in text || " ad " in " $text ") roles += TrackRole.AUDIO_DESCRIPTION
    if ("original" in text) roles += TrackRole.ORIGINAL
    return roles
}

data class AudioTrackInfo(
    val id: String,
    val languageTag: String? = null,
    val label: String? = null,
    val channelCount: Int = 0,
    val isDefault: Boolean = false,
    val roles: Set<TrackRole> = emptySet(),
)

data class SubtitleTrackInfo(
    val id: String,
    val languageTag: String? = null,
    val label: String? = null,
    val isDefault: Boolean = false,
    /** False for bitmap sides (PGS/DVB): selectable, but no sync-by-line. */
    val isTextual: Boolean = true,
    val roles: Set<TrackRole> = emptySet(),
)

private fun orderedLanguages(profile: ProfilePlaybackPreferences, deviceLanguageTag: String, primary: String): List<String> =
    listOf(primary, profile.secondaryAudioLanguageTag, deviceLanguageTag)
        .map(::normalizeBcp47Tag)
        .filter(String::isNotEmpty)
        .distinct()

/**
 * Audio selection precedence (plan §3): session id → source id → semantic
 * language → profile/secondary/device languages → stream default → first.
 */
fun selectAudioTrack(
    tracks: List<AudioTrackInfo>,
    sessionSelectionTrackId: String?,
    sourceSelectionTrackId: String?,
    semantic: MediaPlaybackSelection?,
    profile: ProfilePlaybackPreferences,
    deviceLanguageTag: String,
): AudioTrackInfo? {
    if (tracks.isEmpty()) return null
    tracks.firstOrNull { it.id == sessionSelectionTrackId }?.let { return it }
    tracks.firstOrNull { it.id == sourceSelectionTrackId }?.let { return it }

    val semanticLanguage = normalizeBcp47Tag(semantic?.audioLanguageTag)
    if (semanticLanguage.isNotEmpty()) {
        tracks.firstOrNull { languageMatches(it.languageTag, semanticLanguage) }?.let { return it }
    }

    val profilePrimary = profile.audioLanguageTag
    val languages = if (profilePrimary.isNotBlank()) {
        orderedLanguages(profile, deviceLanguageTag, profilePrimary)
    } else {
        // Original → device language → stream default: "original" is carried
        // by the stream's own default flag, tried after the device language.
        orderedLanguages(ProfilePlaybackPreferences(), deviceLanguageTag, normalizeBcp47Tag(deviceLanguageTag))
    }
    for (language in languages) {
        // Prefer commentary-free tracks within a language.
        tracks.firstOrNull { languageMatches(it.languageTag, language) && TrackRole.COMMENTARY !in it.roles }
            ?.let { return it }
        tracks.firstOrNull { languageMatches(it.languageTag, language) }?.let { return it }
    }
    return tracks.firstOrNull { it.isDefault } ?: tracks.first()
}

private fun prefersForced(semantic: MediaPlaybackSelection?, mode: SubtitleDefaultMode): Boolean =
    semantic?.subtitlesForcedOnly ?: (mode == SubtitleDefaultMode.FORCED_ONLY)

/**
 * Subtitle selection precedence (plan §3). Returns the track id to activate,
 * or null when no subtitles should play. [OFF] always disables subtitles.
 */
fun selectSubtitleTrack(
    tracks: List<SubtitleTrackInfo>,
    sessionSelectionTrackId: String?,
    sourceSelectionTrackId: String?,
    semantic: MediaPlaybackSelection?,
    profile: ProfilePlaybackPreferences,
    deviceLanguageTag: String,
): String? {
    // Precedence 1-2: the session and source exact picks beat every default
    // mode, including OFF.
    tracks.firstOrNull { it.id == sessionSelectionTrackId }?.let { return it.id }
    tracks.firstOrNull { it.id == sourceSelectionTrackId }?.let { return it.id }
    if (profile.subtitleDefaultMode == SubtitleDefaultMode.OFF && semantic?.subtitlesForcedOnly == null) {
        return null
    }
    val forcedOnly = prefersForced(semantic, profile.subtitleDefaultMode)
    val wantedLanguages = listOfNotNull(
        semantic?.subtitleLanguageTag,
        profile.preferredSubtitleLanguageTag.ifBlank { null },
        profile.secondarySubtitleLanguageTag.ifBlank { null },
        deviceLanguageTag,
    ).map(::normalizeBcp47Tag).filter(String::isNotEmpty).distinct()
    if (forcedOnly) {
        for (language in wantedLanguages) {
            tracks.firstOrNull { languageMatches(it.languageTag, language) && TrackRole.FORCED in it.roles }
                ?.let { return it.id }
        }
        // Forced-only with no forced track: subtitles stay off rather than
        // turning on an unwanted full translation (plan §3).
        return null
    }
    for (language in wantedLanguages) {
        // Prefer a plain subtitle over SDH/commentary within a language.
        tracks.firstOrNull {
            languageMatches(it.languageTag, language) &&
                (TrackRole.SDH !in it.roles && TrackRole.COMMENTARY !in it.roles)
        }?.let { return it.id }
        tracks.firstOrNull { languageMatches(it.languageTag, language) }?.let { return it.id }
    }
    return tracks.firstOrNull { it.isDefault }?.id
}

// ── Delay clamping (plan §2/§4) ─────────────────────────────────────────────

const val SUBTITLE_DELAY_LIMIT_MILLIS = 180_000L
const val SUBTITLE_DELAY_STEP_MILLIS = 100L
const val AUDIO_DELAY_LIMIT_MILLIS = 3_000L
const val AUDIO_DELAY_STEP_MILLIS = 25L
const val LINE_SYNC_LEAD_MILLIS = 300L

fun clampSubtitleDelayMillis(rawMillis: Long): Long =
    rawMillis.coerceIn(-SUBTITLE_DELAY_LIMIT_MILLIS, SUBTITLE_DELAY_LIMIT_MILLIS)

fun stepSubtitleDelay(currentMillis: Long, steps: Int): Long =
    clampSubtitleDelayMillis(currentMillis + steps * SUBTITLE_DELAY_STEP_MILLIS)

fun clampAudioDelayMillis(rawMillis: Long): Long =
    rawMillis.coerceIn(-AUDIO_DELAY_LIMIT_MILLIS, AUDIO_DELAY_LIMIT_MILLIS)

fun stepAudioDelay(currentMillis: Long, steps: Int): Long =
    clampAudioDelayMillis(currentMillis + steps * AUDIO_DELAY_STEP_MILLIS)

/**
 * Sync-by-line (plan §4): the viewer presses Sync when hearing the selected
 * line; the subtitle shift is the captured video time minus the cue start,
 * minus a 300 ms perception lead so the line does not land late.
 */
fun syncByLineDelayMillis(capturedVideoTimeMillis: Long, selectedCueStartMillis: Long): Long =
    clampSubtitleDelayMillis(capturedVideoTimeMillis - selectedCueStartMillis - LINE_SYNC_LEAD_MILLIS)

// ── Display mode selection (plan §2) ────────────────────────────────────────

private val FRACTIONAL_REFRESH_RATES = listOf(
    24000.0 / 1001.0, // 23.976
    30000.0 / 1001.0, // 29.97
    60000.0 / 1001.0, // 59.94
    120000.0 / 1001.0, // 119.88
)

/** Tight enough to keep 23.976/24, 29.97/30, and 59.94/60 distinct. */
private const val REFRESH_MATCH_TOLERANCE_HZ = 0.01

fun refreshRateMatches(candidateHz: Float, videoFrameRate: Float): Boolean {
    val candidate = candidateHz.toDouble()
    val video = videoFrameRate.toDouble()
    if (video <= 0.0) return false
    if (kotlin.math.abs(candidate - video) <= REFRESH_MATCH_TOLERANCE_HZ) return true
    return FRACTIONAL_REFRESH_RATES.any { fraction ->
        kotlin.math.abs(video - fraction) <= REFRESH_MATCH_TOLERANCE_HZ &&
            kotlin.math.abs(candidate - fraction) <= REFRESH_MATCH_TOLERANCE_HZ
    }
}

/**
 * Mode-selection order (plan §2): exact resolution + refresh → exact refresh
 * → closest refresh among same-aspect modes → keep the current mode (null
 * means "no switch"). Resolution matching is the caller's gate; this only
 * picks the mode.
 */
fun selectDisplayMode(
    current: DisplayModeCandidate,
    videoWidth: Int,
    videoHeight: Int,
    videoFrameRate: Float,
    availableModes: List<DisplayModeCandidate>,
): DisplayModeCandidate? {
    if (availableModes.isEmpty() || videoWidth <= 0 || videoHeight <= 0) return null
    val exactResolutionAndRate = availableModes.firstOrNull { mode ->
        mode.width == videoWidth && mode.height == videoHeight && refreshRateMatches(mode.refreshRateHz, videoFrameRate)
    }
    if (exactResolutionAndRate != null) return exactResolutionAndRate.takeIf { it != current }

    val exactRate = availableModes.filter { refreshRateMatches(it.refreshRateHz, videoFrameRate) }
    if (exactRate.isNotEmpty()) {
        // Same-aspect first so 16:9 content does not jump to an odd panel mode.
        val videoAspect = videoWidth.toDouble() / videoHeight.toDouble()
        val currentAspect = current.width.toDouble() / current.height.toDouble()
        val chosen = exactRate.minByOrNull { mode ->
            val modeAspect = mode.width.toDouble() / mode.height.toDouble()
            kotlin.math.abs(modeAspect - videoAspect) * 10 + kotlin.math.abs(modeAspect - currentAspect)
        } ?: exactRate.first()
        return chosen.takeIf { it != current }
    }

    val closest = availableModes.filter { it.height == current.height || it.width == current.width }
        .minByOrNull { kotlin.math.abs(it.refreshRateHz - videoFrameRate) }
    return closest
        ?.takeIf { !refreshRateMatches(current.refreshRateHz, videoFrameRate) && it != current }
}
