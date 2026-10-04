package com.lamphaus.app.player

import com.lamphaus.core.model.SubtitleCue
import java.util.Locale
import kotlin.math.abs

internal const val SUBTITLE_LANGUAGE_OFF = "off"
internal const val SUBTITLE_LANGUAGE_UNKNOWN = "und"

/**
 * Keeps sync-by-line usable on a television: show a small window around the
 * captured playback instant instead of making the viewer search the full file.
 */
internal fun nearbySubtitleCues(
    cues: List<SubtitleCue>,
    anchorMillis: Long,
    maximumCount: Int = 7,
): List<SubtitleCue> {
    if (cues.isEmpty() || maximumCount <= 0) return emptyList()
    if (cues.size <= maximumCount) return cues
    val nearestIndex = cues.indices.minBy { index -> abs(cues[index].startMillis - anchorMillis) }
    val before = maximumCount / 2
    val start = (nearestIndex - before).coerceIn(0, cues.size - maximumCount)
    return cues.subList(start, start + maximumCount)
}

internal fun formatSignedDelay(millis: Long): String = when {
    millis == 0L -> "0.0 s"
    else -> "%+.1f s".format(millis / 1_000.0)
}

/**
 * Groups regional variants into one television rail (for example en-US and
 * en-GB both live under English) while keeping malformed tags reachable.
 */
internal fun normalizedSubtitleLanguageKey(languageTag: String?): String {
    // Add-ons report ISO 639-2 codes ("eng", "pob") beside the containers'
    // two-letter ones; both land under the same language.
    val normalized = com.lamphaus.core.model.baseLanguage(com.lamphaus.core.model.normalizeBcp47Tag(languageTag))
    return normalized.takeIf { it.isNotEmpty() && it != SUBTITLE_LANGUAGE_UNKNOWN }
        ?: SUBTITLE_LANGUAGE_UNKNOWN
}

internal fun subtitleLanguageDisplayName(
    languageKey: String,
    displayLocale: Locale,
    unknownLabel: String,
): String {
    if (languageKey == SUBTITLE_LANGUAGE_UNKNOWN) return unknownLabel
    return Locale.forLanguageTag(languageKey).getDisplayLanguage(displayLocale)
        .takeIf(String::isNotBlank)
        ?.replaceFirstChar { character ->
            if (character.isLowerCase()) character.titlecase(displayLocale) else character.toString()
        }
        ?: unknownLabel
}

/** Quiet time after the last remote seek key before the accumulated seek runs. */
internal const val REMOTE_SEEK_COMMIT_DELAY_MILLIS = 450L

/** Playback time before seek previews load on their own (PLY-SEEK-01). */
internal const val SEEK_PREVIEW_PREPARE_AFTER_MILLIS = 15_000L
internal const val SEEK_PREVIEW_PREPARE_TICK_MILLIS = 500L

/**
 * Seek step for a remote key, growing while it is held (Nuvio's scrub
 * rates): 10 s for a tap, then 20 s, 30 s, and a minute on a long hold.
 * [repeatCount] is the key event's auto-repeat count.
 */
internal fun remoteSeekStepMillis(repeatCount: Int): Long = when {
    repeatCount >= 15 -> 60_000L
    repeatCount >= 8 -> 30_000L
    repeatCount >= 3 -> 20_000L
    else -> 10_000L
}

/** Idle time paused before the TV pause overlay replaces the chrome (Nuvio uses 5 s). */
internal const val PAUSE_OVERLAY_DELAY_MILLIS = 5_000L

/** Keys that act on playback even while the pause overlay is up. */
internal val PLAY_PAUSE_KEYS = setOf(
    androidx.compose.ui.input.key.Key.MediaPlayPause,
    androidx.compose.ui.input.key.Key.MediaPlay,
    androidx.compose.ui.input.key.Key.MediaPause,
)

/** Keys a focused skip button or next-episode card handles itself while the chrome is hidden. */
internal val CUE_KEYS = setOf(
    androidx.compose.ui.input.key.Key.DirectionCenter,
    androidx.compose.ui.input.key.Key.Enter,
    androidx.compose.ui.input.key.Key.NumPadEnter,
    androidx.compose.ui.input.key.Key.DirectionLeft,
    androidx.compose.ui.input.key.Key.DirectionRight,
)
