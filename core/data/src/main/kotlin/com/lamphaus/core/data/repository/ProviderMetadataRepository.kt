package com.lamphaus.core.data.repository

import com.lamphaus.core.model.Episode
import com.lamphaus.core.model.MediaDetail
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.ProviderResult
import com.lamphaus.core.model.ProviderSubscription
import com.lamphaus.core.provider.ProviderAggregator
import com.lamphaus.core.provider.ProviderClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Provider metadata outside a details page (SHR-ARC-04): the first add-on
 * answer that satisfies a need, and a series' episode list kept for a few
 * hours. Home's up-next cards, trailers, the new-episode check, and the
 * Continue watching widget all read through here, so a background worker
 * asks add-ons exactly as the app does. Provider responses come from the
 * provider client's own cache when fresh, so repeated lookups stay cheap.
 */
class ProviderMetadataRepository(
    private val client: ProviderClient,
    private val aggregator: ProviderAggregator,
    private val providers: suspend () -> List<ProviderSubscription>,
    private val clock: () -> Long = System::currentTimeMillis,
    private val episodesTtlMillis: Long = SERIES_EPISODES_TTL_MILLIS,
) {
    private val mutex = Mutex()
    private val episodesByKey = object : LinkedHashMap<String, Pair<Long, List<Episode>>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, List<Episode>>>) =
            size > MAX_CACHED_SERIES
    }

    /**
     * The first provider metadata for [media] that satisfies [wanted], asking
     * the title's own add-ons first, then the rest in the viewer's order.
     */
    suspend fun findMeta(media: MediaPreview, wanted: (MediaDetail) -> Boolean): MediaDetail? {
        val ordered = providers()
            .filter(ProviderSubscription::enabled)
            .sortedWith(
                compareBy<ProviderSubscription> { if (it.id in media.providerIds) 0 else 1 }
                    .thenBy(ProviderSubscription::sortOrder)
                    .thenBy(ProviderSubscription::id),
            )
        for (provider in ordered) {
            val manifest = client.manifest(provider.manifestUrl)
            if (manifest !is ProviderResult.Success ||
                !aggregator.supports(manifest.value, "meta", media.rawType, media.id)
            ) {
                continue
            }
            val meta = (client.meta(provider.manifestUrl, provider.id, media.rawType, media.id)
                as? ProviderResult.Success<MediaDetail>)?.value
            if (meta != null && wanted(meta)) return meta
        }
        return null
    }

    /** A series' episodes from provider metadata, kept for [episodesTtlMillis]. */
    suspend fun getSeriesEpisodes(media: MediaPreview): List<Episode> {
        val now = clock()
        mutex.withLock {
            episodesByKey[media.stableKey]
                ?.takeIf { (fetchedAt) -> now - fetchedAt < episodesTtlMillis }
                ?.let { (_, episodes) -> return episodes }
        }
        val episodes = findMeta(media) { it.episodes.isNotEmpty() }?.episodes.orEmpty()
        mutex.withLock { episodesByKey[media.stableKey] = now to episodes }
        return episodes
    }

    /** Remembers episodes a details page already loaded, so later lookups skip the network. */
    suspend fun rememberEpisodes(media: MediaPreview, episodes: List<Episode>) {
        if (episodes.isEmpty()) return
        mutex.withLock { episodesByKey[media.stableKey] = clock() to episodes }
    }

    private companion object {
        const val SERIES_EPISODES_TTL_MILLIS = 6L * 60 * 60 * 1000
        const val MAX_CACHED_SERIES = 100
    }
}
