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
            .filter { supports(it.manifest, "catalog", query.type, null) }

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
    ): ProviderResult<Unit> = if (supports(manifest, resource, type, id)) {
        ProviderResult.Success(Unit)
    } else {
        ProviderResult.Failure(ProviderFailureKind.UNSUPPORTED_CAPABILITY, "This provider does not support that request.")
    }

    fun supports(manifest: ProviderManifest, resource: String, type: String, id: String? = null): Boolean {
        return manifest.resources
            .filter { it.name.equals(resource, ignoreCase = true) }
            .any { descriptor ->
                val supportedTypes = descriptor.types ?: manifest.types
                val supportedPrefixes = descriptor.idPrefixes ?: manifest.idPrefixes
                val supportsType = supportedTypes.isEmpty() || supportedTypes.any { it.equals(type, ignoreCase = true) }
                val supportsId = id == null || supportedPrefixes.isEmpty() || supportedPrefixes.any { id.startsWith(it) }
                supportsType && supportsId
            }
    }

    private fun MediaPreview.merge(other: MediaPreview): MediaPreview = copy(
        posterUrl = posterUrl ?: other.posterUrl,
        backgroundUrl = backgroundUrl ?: other.backgroundUrl,
        logoUrl = logoUrl ?: other.logoUrl,
        description = description ?: other.description,
        releaseYear = releaseYear ?: other.releaseYear,
        genres = (genres + other.genres).distinct(),
        contentRating = contentRating ?: other.contentRating,
        rating = rating ?: other.rating,
        posterShape = posterShape ?: other.posterShape,
        providerIds = providerIds + other.providerIds,
    )
}
