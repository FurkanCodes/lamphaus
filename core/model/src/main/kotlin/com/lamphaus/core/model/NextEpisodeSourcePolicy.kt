package com.lamphaus.core.model

/**
 * Picks the next episode's source closest to the one playing now: the same
 * binge group wins as soon as any provider returns it; otherwise, once the
 * search settles, the same add-on, the same quality and the most release
 * tags in common (WEB-DL, x265, HDR, the release group…) decide, with ties
 * kept in provider order. The winner starts after a short visible countdown
 * as a new playback, exactly like a source picked on the sources screen.
 */
object NextEpisodeSourcePolicy {
    /** The search stops waiting for slower providers after this long. */
    const val SEARCH_TIMEOUT_MILLIS = 10_000L

    /** "Playing via … in 3s" before switching. */
    const val START_COUNTDOWN_SECONDS = 3

    private val QUALITY = Regex("2160p|4k|1080p|720p|480p", RegexOption.IGNORE_CASE)
    private val TOKEN_SEPARATOR = Regex("[^\\p{L}\\p{N}]+")

    fun isBingeMatch(stream: StreamCandidate, bingeGroup: String?): Boolean {
        val target = bingeGroup?.trim().orEmpty()
        return target.isNotEmpty() && stream.bingeGroup?.trim() == target
    }

    /** [candidates] must already be in provider order. */
    fun <T> select(
        candidates: List<T>,
        stream: (T) -> StreamCandidate,
        preferredProviderId: String?,
        preferredBingeGroup: String?,
        currentLabel: String? = null,
    ): T? {
        val currentQuality = quality(currentLabel)
        val currentTokens = tokens(currentLabel)
        return candidates.withIndex()
            .maxWithOrNull(
                compareBy<IndexedValue<T>> {
                    closeness(stream(it.value), preferredProviderId, preferredBingeGroup, currentQuality, currentTokens)
                }.thenByDescending { it.index },
            )
            ?.value
    }

    internal fun closeness(
        stream: StreamCandidate,
        providerId: String?,
        bingeGroup: String?,
        currentQuality: String?,
        currentTokens: Set<String>,
    ): Int {
        var score = 0
        if (isBingeMatch(stream, bingeGroup)) score += 1_000
        if (providerId != null && stream.providerId == providerId) score += 100
        val label = stream.closenessLabel()
        if (currentQuality != null && quality(stream.quality ?: label) == currentQuality) score += 10
        score += (tokens(label) intersect currentTokens).size.coerceAtMost(9)
        return score
    }

    /** Nuvio names the source by the stream's own name, falling back to the provider. */
    fun sourceName(stream: StreamCandidate, providerName: String?): String =
        stream.name.lineSequence().map(String::trim).firstOrNull(String::isNotEmpty)
            ?: providerName?.trim().orEmpty()

    private fun quality(text: String?): String? =
        text?.let(QUALITY::find)?.value?.lowercase()?.let { if (it == "4k") "2160p" else it }

    private fun tokens(text: String?): Set<String> =
        text.orEmpty().lowercase().split(TOKEN_SEPARATOR).filter { it.length >= 2 }.toSet()
}

/** What identifies a source to a viewer: its name, title, description, file and quality. */
fun StreamCandidate.closenessLabel(): String =
    listOfNotNull(name, title, description, filename, quality).joinToString(" ")
