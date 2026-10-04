package com.lamphaus.core.data.playback

import java.net.URI

/** One thumbnail: a rectangle inside a sprite sheet. */
data class SeekPreviewTile(
    val sheetUrl: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

/** The thumbnail shown from [startMillis] until [endMillis]. */
data class SeekPreviewCue(
    val startMillis: Long,
    val endMillis: Long,
    val tile: SeekPreviewTile,
)

/**
 * Seek-preview thumbnails for one playback, ordered by time (PLY-SEEK-01).
 * Cues are on the sprites' source timeline; a playing position maps onto it
 * as `position / scale` (Seekr's `scale`, 1.0 when the durations match). No
 * other offset is guessed.
 */
class SeekPreviewTrack(cues: List<SeekPreviewCue>, private val scale: Double = 1.0) {
    val cues: List<SeekPreviewCue> = cues.sortedBy(SeekPreviewCue::startMillis)

    /** Every sheet in first-use order, for prefetching. */
    val sheetUrls: List<String> get() = cues.map { it.tile.sheetUrl }.distinct()

    /**
     * The last cue starting at or before [positionMillis]; positions before the
     * first cue or after the last clamp to it. Null only for an empty track.
     */
    fun cueAt(positionMillis: Long): SeekPreviewCue? {
        if (cues.isEmpty()) return null
        val sourceMillis = if (scale > 0.0 && scale != 1.0) Math.round(positionMillis / scale) else positionMillis
        var low = 0
        var high = cues.lastIndex
        var found = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (cues[mid].startMillis <= sourceMillis) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return cues[if (found >= 0) found else 0]
    }
}

/**
 * The sprite manifest: plain WebVTT whose cue payloads are tile URLs with an
 * `#xywh=x,y,w,h` fragment. Relative tile URLs resolve against the manifest;
 * only https tiles are kept, so a manifest can never point the player at a
 * local file or plain-text host.
 */
object SeekPreviewVtt {

    fun parse(vtt: String, manifestUrl: String): List<SeekPreviewCue> {
        val base = runCatching { URI(manifestUrl) }.getOrNull()
        val lines = vtt.lines()
        val cues = ArrayList<SeekPreviewCue>()
        lines.forEachIndexed { index, raw ->
            val line = raw.trim()
            if ("-->" !in line) return@forEachIndexed
            val parts = line.split("-->")
            if (parts.size != 2) return@forEachIndexed
            val start = parseTime(parts[0].trim()) ?: return@forEachIndexed
            // Cue settings may follow the end time.
            val end = parseTime(parts[1].trim().substringBefore(' ')) ?: return@forEachIndexed
            var next = index + 1
            while (next < lines.size && lines[next].isBlank()) next++
            val payload = lines.getOrNull(next)?.trim() ?: return@forEachIndexed
            if ("-->" in payload) return@forEachIndexed
            val tile = parseTile(payload, base) ?: return@forEachIndexed
            cues += SeekPreviewCue(startMillis = start, endMillis = end, tile = tile)
        }
        return cues
    }

    private fun parseTile(payload: String, base: URI?): SeekPreviewTile? {
        val hash = payload.lastIndexOf('#')
        if (hash < 0) return null
        val fragment = payload.substring(hash + 1)
        if (!fragment.startsWith("xywh=")) return null
        val coordinates = fragment.removePrefix("xywh=").split(',').map { it.trim().toIntOrNull() }
        if (coordinates.size != 4 || coordinates.any { it == null || it < 0 }) return null
        val (x, y, width, height) = coordinates.map { it!! }
        if (width == 0 || height == 0) return null
        val reference = payload.substring(0, hash)
        val resolved = runCatching {
            if (base != null) base.resolve(reference).toString() else URI(reference).toString()
        }.getOrNull() ?: return null
        if (!resolved.startsWith("https://")) return null
        return SeekPreviewTile(sheetUrl = resolved, x = x, y = y, width = width, height = height)
    }

    /** `hh:mm:ss.mmm` or `mm:ss.mmm`. */
    internal fun parseTime(value: String): Long? {
        val parts = value.split(':')
        val seconds = parts.lastOrNull()?.toDoubleOrNull() ?: return null
        val minutes = parts.getOrNull(parts.size - 2)?.toLongOrNull() ?: return null
        val hours = if (parts.size == 3) parts[0].toLongOrNull() ?: return null else 0L
        if (parts.size !in 2..3) return null
        return hours * 3_600_000L + minutes * 60_000L + Math.round(seconds * 1000.0)
    }
}
