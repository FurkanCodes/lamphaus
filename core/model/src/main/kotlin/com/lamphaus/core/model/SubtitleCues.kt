package com.lamphaus.core.model

/**
 * Sidecar subtitle parsing for Sync by line (plan §4). Supports SRT, WebVTT,
 * TTML, and ASS/SSA — the downloadable text formats the timing panel offers.
 * Parsing is line-based and total: malformed input yields the cues it could
 * salvage, never an exception.
 */
data class SubtitleCue(
    val startMillis: Long,
    val endMillis: Long,
    val text: String,
)

object SubtitleCueParser {

    private val SRT_TIME = Regex(
        "(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{1,3})\\s*-->\\s*(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{1,3})",
    )
    private val ASS_TIME = Regex("(\\d):(\\d{2}):(\\d{2})[.,](\\d{2})")
    private val ASS_OVERRIDE_TAGS = Regex("\\{[^}]*\\}")
    private val HTML_TAGS = Regex("<[^>]*>")

    /** Parses any supported sidecar payload by sniffing its shape. */
    fun parse(raw: String): List<SubtitleCue> = when {
        raw.contains("-->") && raw.contains("WEBVTT") -> parseWebVtt(raw)
        raw.contains("-->") -> parseSrt(raw)
        raw.contains("[Events]", ignoreCase = true) ||
            raw.contains("Dialogue:", ignoreCase = true) -> parseAss(raw)

        raw.contains("<tt", ignoreCase = true) || raw.contains("<p ", ignoreCase = true) -> parseTtml(raw)
        else -> emptyList()
    }

    fun parseSrt(raw: String): List<SubtitleCue> = parseArrowFormat(raw)

    fun parseWebVtt(raw: String): List<SubtitleCue> =
        parseArrowFormat(raw.lines().filterNot { line ->
            val trimmed = line.trim()
            trimmed.startsWith("NOTE") || trimmed.startsWith("STYLE") || trimmed.startsWith("REGION")
        }.joinToString("\n"))

    private fun parseArrowFormat(raw: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val lines = raw.lines()
        var index = 0
        while (index < lines.size) {
            val timingMatch = SRT_TIME.find(lines[index])
            if (timingMatch == null) {
                index += 1
                continue
            }
            val start = timingMatch.groupValues.let { g ->
                hoursMinutesMillis(g[1], g[2], g[3], g[4])
            }
            val end = timingMatch.groupValues.let { g ->
                hoursMinutesMillis(g[5], g[6], g[7], g[8])
            }
            index += 1
            val text = buildList {
                while (index < lines.size && lines[index].isNotBlank()) {
                    add(lines[index])
                    index += 1
                }
            }.joinToString("\n") { it.replace(HTML_TAGS, "") }.trim()
            if (text.isNotEmpty()) cues += SubtitleCue(start, end.coerceAtLeast(start), text)
            index += 1
        }
        return cues
    }

    /**
     * ASS/SSA: field order comes from the last Format line in [Events];
     * the common default (Layer, Start, End, Style, Name, ...) is assumed
     * when absent.
     */
    fun parseAss(raw: String): List<SubtitleCue> {
        var formatFields: List<String> = listOf(
            "layer", "start", "end", "style", "name", "marginl", "marginr", "marginv", "effect", "text",
        )
        val cues = mutableListOf<SubtitleCue>()
        for (line in raw.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("Format:", ignoreCase = true)) {
                formatFields = trimmed.substringAfter(':')
                    .split(',')
                    .map { it.trim().lowercase() }
                continue
            }
            if (!trimmed.startsWith("Dialogue:", ignoreCase = true)) continue
            val values = trimmed.substringAfter(':').split(",".toRegex(), limit = formatFields.size)
            if (values.size < formatFields.size) continue
            val byField = formatFields.zip(values)
            val start = byField.firstOrNull { it.first == "start" }?.second?.let(::parseAssTime) ?: continue
            val end = byField.firstOrNull { it.first == "end" }?.second?.let(::parseAssTime) ?: continue
            val text = byField.last().second
                .replace(ASS_OVERRIDE_TAGS, "")
                .replace("\\N", "\n")
                .replace("\\n", "\n")
                .trim()
            if (text.isNotEmpty()) cues += SubtitleCue(start, end.coerceAtLeast(start), text)
        }
        return cues
    }

    private fun parseAssTime(value: String): Long? {
        val match = ASS_TIME.find(value.trim()) ?: return null
        val g = match.groupValues
        // ASS centiseconds → millis.
        return hoursMinutesMillis(g[1], g[2], g[3], g[4].padEnd(3, '0'))
    }

    /** Light TTML: `<p begin=... end=...>text</p>` with clock or seconds times. */
    fun parseTtml(raw: String): List<SubtitleCue> {
        val cueRegex = Regex("<p\\b[^>]*>", RegexOption.IGNORE_CASE)
        val beginRegex = Regex("begin\\s*=\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
        val endRegex = Regex("end\\s*=\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
        val cues = mutableListOf<SubtitleCue>()
        for (match in cueRegex.findAll(raw)) {
            val tag = match.value
            val start = beginRegex.find(tag)?.groupValues?.get(1)?.let(::parseTtmlTime) ?: continue
            val end = endRegex.find(tag)?.groupValues?.get(1)?.let(::parseTtmlTime) ?: continue
            val bodyStart = match.range.last + 1
            val bodyEnd = raw.indexOf("</p>", bodyStart, ignoreCase = true)
            if (bodyEnd < 0) continue
            val text = raw.substring(bodyStart, bodyEnd)
                .replace(HTML_TAGS, "")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .trim()
            if (text.isNotEmpty()) cues += SubtitleCue(start, end.coerceAtLeast(start), text)
        }
        return cues
    }

    private fun parseTtmlTime(value: String): Long? {
        val trimmed = value.trim()
        trimmed.substringBefore(' ').let { clock ->
            if (clock.contains(':')) {
                val parts = clock.split(":")
                if (parts.size == 3) {
                    return hoursMinutesMillis(parts[0], parts[1], parts[2].substringBefore('.'), parts[2].substringAfter('.', "").padEnd(3, '0').ifEmpty { "0" })
                }
            }
        }
        // Metric: "12.345s" or bare seconds.
        val seconds = trimmed.removeSuffix("s").toDoubleOrNull() ?: return null
        return (seconds * 1000).toLong()
    }

    private fun hoursMinutesMillis(h: String, m: String, s: String, fraction: String): Long {
        val fractionMillis = fraction.take(3).padEnd(3, '0').toLongOrNull() ?: 0
        return h.toLongOrNull()!! * 3_600_000L + m.toLong()!! * 60_000L + s.toLong()!! * 1_000L + fractionMillis
    }
}

/**
 * Charset detection for downloaded sidecars (plan §4): BOM first, then a
 * strict UTF-8 decode, then Latin-1 (which always succeeds).
 */
object SubtitleCharset {

    private fun u8(byte: Byte): Int = byte.toInt() and 0xFF

    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && u8(bytes[0]) == 0xEF && u8(bytes[1]) == 0xBB && u8(bytes[2]) == 0xBF) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && u8(bytes[0]) == 0xFF && u8(bytes[1]) == 0xFE) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && u8(bytes[0]) == 0xFE && u8(bytes[1]) == 0xFF) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        try {
            return String(bytes, Charsets.UTF_8).let { decoded ->
                // Stray replacement characters mean the payload was not UTF-8.
                if (decoded.contains('\uFFFD')) String(bytes, Charsets.ISO_8859_1) else decoded
            }
        } catch (_: Exception) {
            return String(bytes, Charsets.ISO_8859_1)
        }
    }

}

/**
 * The Sync-by-line picker window (plan §4): cues within ±3 minutes of the
 * captured video time, capped at 90 rows, ordered, nearest cue highlighted
 * by index.
 */
object SubtitleCueWindow {

    const val WINDOW_MILLIS = 3 * 60 * 1000L
    const val MAX_ROWS = 90

    fun select(cues: List<SubtitleCue>, capturedVideoTimeMillis: Long): Pair<List<SubtitleCue>, Int> {
        if (cues.isEmpty()) return emptyList<SubtitleCue>() to -1
        val inWindow = cues
            .filter { it.startMillis >= capturedVideoTimeMillis - WINDOW_MILLIS && it.startMillis <= capturedVideoTimeMillis + WINDOW_MILLIS }
            .sortedBy(SubtitleCue::startMillis)
        if (inWindow.isEmpty()) return emptyList<SubtitleCue>() to -1
        val nearest = inWindow.indexOfFirst { it.startMillis >= capturedVideoTimeMillis }
            .let { if (it == -1) inWindow.lastIndex else it }
        val from = (nearest - MAX_ROWS / 2).coerceAtLeast(0)
        val window = inWindow.subList(from, (from + MAX_ROWS).coerceAtMost(inWindow.size))
        return window to (nearest - from)
    }
}
