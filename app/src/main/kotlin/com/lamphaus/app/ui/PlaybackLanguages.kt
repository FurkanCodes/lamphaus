package com.lamphaus.app.ui

import java.util.Locale

/** One choice in the default audio or subtitle language list; an empty tag is the fallback choice. */
internal data class PlaybackLanguageOption(val tag: String, val label: String)

/**
 * Languages offered as playback defaults on TV and mobile (BCP-47). Add-ons
 * and containers that report ISO 639-2 codes ("eng", "pob") still match
 * through `normalizeBcp47Tag`.
 */
private val PLAYBACK_LANGUAGE_TAGS = listOf(
    "ar", "bg", "bn", "ca", "cs", "da", "de", "el", "en", "es", "et", "fa", "fi", "fr", "he",
    "hi", "hr", "hu", "id", "it", "ja", "ko", "lt", "lv", "ms", "nl", "no", "pl", "pt", "pt-BR",
    "ro", "ru", "sk", "sl", "sr", "sv", "ta", "te", "th", "tl", "tr", "uk", "ur", "vi", "zh",
)

/**
 * The empty-tag fallback ([fallbackLabel], e.g. "Original" or "Device
 * language") first, then every language by its name in [displayLocale].
 */
internal fun playbackLanguageOptions(fallbackLabel: String, displayLocale: Locale): List<PlaybackLanguageOption> =
    listOf(PlaybackLanguageOption("", fallbackLabel)) +
        PLAYBACK_LANGUAGE_TAGS
            .map { tag -> PlaybackLanguageOption(tag, playbackLanguageName(tag, displayLocale)) }
            .sortedBy { it.label.lowercase(displayLocale) }

/** The label for a stored tag: [fallbackLabel] when blank, else the language's own display name. */
internal fun playbackLanguageLabel(tag: String, fallbackLabel: String, displayLocale: Locale): String =
    if (tag.isBlank()) fallbackLabel else playbackLanguageName(tag, displayLocale)

private fun playbackLanguageName(tag: String, displayLocale: Locale): String =
    Locale.forLanguageTag(tag).getDisplayName(displayLocale)
        .ifBlank { tag }
        .replaceFirstChar { it.titlecase(displayLocale) }
