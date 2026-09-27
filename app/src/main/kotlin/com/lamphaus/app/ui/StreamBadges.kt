package com.lamphaus.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lamphaus.core.data.repository.StreamBadgeFilter
import com.lamphaus.core.data.repository.StreamBadgeImport
import com.lamphaus.core.model.StreamCandidate

/**
 * Imported Nuvio-compatible stream badges, compiled once per import. As in
 * Nuvio, every enabled rule that matches adds its badge, in file order, and
 * badges with the same art appear once.
 */
@Immutable
class StreamBadgeMatcher(import: StreamBadgeImport?) {
    private val rules: List<Pair<Regex, StreamBadgeFilter>> = import?.filters.orEmpty()
        .filter { it.isEnabled && it.imageURL.isNotBlank() }
        .mapNotNull { filter -> runCatching { Regex(filter.pattern) }.getOrNull()?.let { it to filter } }

    val isActive: Boolean get() = rules.isNotEmpty()

    fun badgesFor(source: StreamCandidate): List<StreamBadgeFilter> {
        if (rules.isEmpty()) return emptyList()
        val candidates = listOfNotNull(source.filename, source.name, source.title, source.description)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
        if (candidates.isEmpty()) return emptyList()
        // Patterns may span fields (for example a lookahead over the whole name).
        val texts = if (candidates.size > 1) candidates + candidates.joinToString(" ") else candidates
        val matched = LinkedHashMap<String, StreamBadgeFilter>()
        rules.forEach { (regex, filter) ->
            if (texts.any { regex.containsMatchIn(it) }) matched.putIfAbsent(filter.imageURL, filter)
        }
        return matched.values.toList()
    }
}

/** The active badge rules for source lists; hosts provide it from app state. */
val LocalStreamBadges = staticCompositionLocalOf { StreamBadgeMatcher(null) }

private val BadgeShape = RoundedCornerShape(4.dp)

/**
 * The badge art row on a source card: 20dp chips with the file's own image,
 * fill, and border, as Nuvio draws them. The image carries the meaning; its
 * name is the accessible label.
 */
@Composable
fun StreamBadgeRow(badges: List<StreamBadgeFilter>, modifier: Modifier = Modifier) {
    if (badges.isEmpty()) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        badges.forEach { badge -> StreamBadgeChip(badge) }
    }
}

@Composable
private fun StreamBadgeChip(badge: StreamBadgeFilter) {
    val fill = remember(badge.tagColor, badge.tagStyle) {
        badge.tagColor.toBadgeColor()?.takeIf { badge.tagStyle.equals("filled", ignoreCase = true) }
    }
    val outline = remember(badge.borderColor) { badge.borderColor.toBadgeColor() }
    Box(
        modifier = Modifier
            .height(20.dp)
            .clip(BadgeShape)
            .then(if (fill != null) Modifier.background(fill, BadgeShape) else Modifier)
            .then(if (outline != null) Modifier.border(1.dp, outline, BadgeShape) else Modifier)
            .padding(horizontal = 3.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = badge.imageURL,
            contentDescription = badge.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.height(16.dp).widthIn(min = 20.dp, max = 92.dp),
        )
    }
}

/** `#AARRGGBB` (Android order, alpha first) or `#RRGGBB`. */
internal fun String.toBadgeColor(): Color? {
    val hex = trim().removePrefix("#")
    val argb = when (hex.length) {
        6 -> "FF$hex"
        8 -> hex
        else -> return null
    }
    return argb.toLongOrNull(16)?.let { Color(it) }
}
