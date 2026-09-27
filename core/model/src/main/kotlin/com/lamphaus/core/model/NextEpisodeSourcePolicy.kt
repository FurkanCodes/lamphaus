package com.lamphaus.core.model

/**
 * Nuvio's next-episode source choice: a stream in the current binge group
 * wins as soon as any provider returns one; otherwise, once the search
 * settles, the current provider's first stream, then the first stream in
 * provider order. The winner starts after a short visible countdown.
 */
object NextEpisodeSourcePolicy {
    /** The search stops waiting for slower providers after this long. */
    const val SEARCH_TIMEOUT_MILLIS = 10_000L

    /** "Playing via … in 3s" before switching. */
    const val START_COUNTDOWN_SECONDS = 3

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
    ): T? = candidates.firstOrNull { isBingeMatch(stream(it), preferredBingeGroup) }
        ?: candidates.firstOrNull { preferredProviderId != null && stream(it).providerId == preferredProviderId }
        ?: candidates.firstOrNull()

    /** Nuvio names the source by the stream's own name, falling back to the provider. */
    fun sourceName(stream: StreamCandidate, providerName: String?): String =
        stream.name.lineSequence().map(String::trim).firstOrNull(String::isNotEmpty)
            ?: providerName?.trim().orEmpty()
}
