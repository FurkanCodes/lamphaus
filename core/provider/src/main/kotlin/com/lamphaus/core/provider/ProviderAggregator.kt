package com.lamphaus.core.provider

import com.lamphaus.core.model.CatalogQuery
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.ProviderFailureKind
import com.lamphaus.core.model.ProviderManifest
import com.lamphaus.core.model.ProviderResult
import com.lamphaus.core.model.ProviderSubscription
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class ResolvedProvider(
    val subscription: ProviderSubscription,
    val manifest: ProviderManifest,
)

data class CatalogSlice(
    val items: List<MediaPreview>,
    val failures: Map<String, ProviderResult.Failure>,
)

class ProviderAggregator(
    private val client: ProviderClient,
    maxConcurrency: Int = 4,
) {
    private val semaphore = Semaphore(maxConcurrency)

    suspend fun catalog(
        providers: List<ResolvedProvider>,
        query: CatalogQuery,
    ): CatalogSlice = coroutineScope {
        val eligible = providers
            .filter { it.subscription.enabled }
            .sortedBy { it.subscription.sortOrder }
            .filter { it.manifest.supports("catalog", query.type, null) }

        val results = eligible.map { provider ->
            async {
                semaphore.withPermit {
                    provider to client.catalog(
                        manifestUrl = provider.subscription.manifestUrl,
                        providerId = provider.subscription.id,
                        query = query,
                    )
                }
            }
        }.awaitAll()

        val failures = results.mapNotNull { (provider, result) ->
            (result as? ProviderResult.Failure)?.let { provider.subscription.id to it }
        }.toMap()
        val merged = linkedMapOf<String, MediaPreview>()
        results.forEach { (_, result) ->
            val items = (result as? ProviderResult.Success)?.value.orEmpty()
            items.forEach { incoming ->
                merged[incoming.stableKey] = merged[incoming.stableKey]?.merge(incoming) ?: incoming
            }
        }
        CatalogSlice(merged.values.toList(), failures)
    }

    fun validateCapability(
        manifest: ProviderManifest,
        resource: String,
        type: String,
        id: String? = null,
    ): ProviderResult<Unit> = if (manifest.supports(resource, type, id)) {
        ProviderResult.Success(Unit)
    } else {
        ProviderResult.Failure(ProviderFailureKind.UNSUPPORTED_CAPABILITY, "This provider does not support that request.")
    }

    private fun ProviderManifest.supports(resource: String, type: String, id: String?): Boolean {
        val descriptor = resources.firstOrNull { it.name == resource } ?: return false
        val supportsType = descriptor.types.isEmpty() || type in descriptor.types || type in types
        val supportsId = id == null || descriptor.idPrefixes.isEmpty() || descriptor.idPrefixes.any(id::startsWith)
        return supportsType && supportsId
    }

    private fun MediaPreview.merge(other: MediaPreview): MediaPreview = copy(
        posterUrl = posterUrl ?: other.posterUrl,
        backgroundUrl = backgroundUrl ?: other.backgroundUrl,
        logoUrl = logoUrl ?: other.logoUrl,
        description = description ?: other.description,
        releaseYear = releaseYear ?: other.releaseYear,
        genres = (genres + other.genres).distinct(),
        contentRating = contentRating ?: other.contentRating,
        providerIds = providerIds + other.providerIds,
    )
}

