package com.lamphaus.app.ui

import androidx.annotation.StringRes
import com.lamphaus.app.R
import com.lamphaus.core.model.StreamCandidate
import java.util.Locale

internal data class SourcePresentation(
    val title: String,
    val description: String?,
    val badges: List<String>,
    val size: String?,
    val transport: SourceTransport,
    val usesProviderFormatting: Boolean,
)

internal enum class SourceTransport(@StringRes val labelRes: Int) {
    DIRECT(R.string.source_type_direct),
    PEER(R.string.source_type_peer),
    EXTERNAL(R.string.source_type_external),
    VIDEO(R.string.source_type_video),
    ARCHIVE(R.string.source_type_archive),
    USENET(R.string.source_type_usenet),
}

/**
 * Provider text is presentation data. Keep it intact and only infer a compact
 * Lamphaus presentation when the response has no useful display fields.
 */
internal fun StreamCandidate.sourcePresentation(providerLabel: String?): SourcePresentation {
    val providerName = providerLabel.clean()
    val suppliedName = name.clean()
        ?.takeUnless { it.equals(DEFAULT_SOURCE_NAME, ignoreCase = true) }
    val suppliedTitle = title.clean()
    val suppliedDescription = description.clean()
    val suppliedBadges = (tags + listOfNotNull(quality.clean())).distinctIgnoringCase()
    val hasProviderFormatting = suppliedTitle != null || suppliedDescription != null ||
        suppliedBadges.isNotEmpty() || suppliedName?.equals(providerName, ignoreCase = true) == false

    if (hasProviderFormatting) {
        val heading = suppliedName
            ?: suppliedTitle
            ?: suppliedDescription?.lineSequence()?.first()
            ?: filename.clean()
            ?: providerName
            ?: DEFAULT_STREAM_TITLE
        val details = listOfNotNull(
            suppliedDescription?.takeUnless { it.equals(heading, ignoreCase = true) },
            suppliedTitle?.takeUnless { it.equals(heading, ignoreCase = true) },
        ).distinctIgnoringCase().joinToString("\n").ifBlank { null }
        val visibleCopy = listOfNotNull(heading, details).joinToString(" ")
        return SourcePresentation(
            title = heading,
            description = details,
            badges = suppliedBadges.filterNot { visibleCopy.contains(it, ignoreCase = true) }.take(MAX_BADGES),
            size = videoSize?.formatBytes(),
            transport = transport(),
            usesProviderFormatting = true,
        )
    }

    val fallbackText = listOfNotNull(filename.clean(), name.clean(), title.clean()).joinToString(" ")
    return SourcePresentation(
        title = filename.clean() ?: providerName ?: DEFAULT_STREAM_TITLE,
        description = providerName,
        badges = fallbackBadges(fallbackText),
        size = videoSize?.formatBytes(),
        transport = transport(),
        usesProviderFormatting = false,
    )
}

private fun StreamCandidate.transport(): SourceTransport = when {
    infoHash != null -> SourceTransport.PEER
    nzbUrl != null -> SourceTransport.USENET
    archiveFiles.isNotEmpty() -> SourceTransport.ARCHIVE
    ytId != null -> SourceTransport.VIDEO
    externalUrl != null -> SourceTransport.EXTERNAL
    else -> SourceTransport.DIRECT
}

private fun fallbackBadges(text: String): List<String> = FALLBACK_BADGES.mapNotNull { (pattern, label) ->
    label.takeIf { pattern.containsMatchIn(text) }
}.distinctIgnoringCase().take(MAX_BADGES)

private fun Long.formatBytes(): String {
    if (this <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    val format = if (value >= 10 || unit == 0) "%.0f %s" else "%.1f %s"
    return String.format(Locale.US, format, value, units[unit])
}

private fun String?.clean(): String? = this?.trim()?.takeIf(String::isNotBlank)

private fun List<String>.distinctIgnoringCase(): List<String> = distinctBy { it.lowercase(Locale.ROOT) }

private const val DEFAULT_SOURCE_NAME = "Source"
private const val DEFAULT_STREAM_TITLE = "Stream"
private const val MAX_BADGES = 5

private val FALLBACK_BADGES = listOf(
    Regex("\\b(?:2160p|4k)\\b", RegexOption.IGNORE_CASE) to "4K",
    Regex("\\b1080p\\b", RegexOption.IGNORE_CASE) to "1080p",
    Regex("\\b720p\\b", RegexOption.IGNORE_CASE) to "720p",
    Regex("\\b(?:blu[ ._-]?ray|bdrip|bdremux)\\b", RegexOption.IGNORE_CASE) to "Blu-ray",
    Regex("\\bweb[ ._-]?dl\\b", RegexOption.IGNORE_CASE) to "WEB-DL",
    Regex("\\bweb[ ._-]?rip\\b", RegexOption.IGNORE_CASE) to "WEBRip",
    Regex("\\b(?:dolby[ ._-]?vision|dovi|dv)\\b", RegexOption.IGNORE_CASE) to "Dolby Vision",
    Regex("\\bhdr10\\+?\\b|\\bhdr\\b", RegexOption.IGNORE_CASE) to "HDR",
    Regex("\\b(?:hevc|h[ ._-]?265|x265)\\b", RegexOption.IGNORE_CASE) to "HEVC",
    Regex("\\b(?:avc|h[ ._-]?264|x264)\\b", RegexOption.IGNORE_CASE) to "AVC",
    Regex("\\bav1\\b", RegexOption.IGNORE_CASE) to "AV1",
    Regex("\\batmos\\b", RegexOption.IGNORE_CASE) to "Atmos",
)
