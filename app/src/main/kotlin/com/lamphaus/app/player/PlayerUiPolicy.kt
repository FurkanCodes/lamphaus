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
    val normalized = languageTag
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let(Locale::forLanguageTag)
        ?.language
        ?.lowercase(Locale.ROOT)
        .orEmpty()
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
