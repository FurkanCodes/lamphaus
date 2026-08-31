package com.lamphaus.app.ui

import com.lamphaus.core.model.MediaPreview
internal const val HOME_CATALOG_MAX_CONCURRENCY = 4
internal const val HOME_CATALOG_WINDOW_SIZE = 4
internal const val HOME_CATALOG_PREFETCH_DISTANCE = 2
internal const val HOME_CATALOG_SCROLL_SETTLE_MILLIS = 240L

internal fun shouldPrefetchHomeCatalogBatch(
    lastVisibleIndex: Int?,
    totalListItems: Int,
    hasMore: Boolean,
    loading: Boolean,
    failed: Boolean,
): Boolean {
    if (!hasMore || loading || failed) return false
    if (totalListItems <= 0) return true
    return lastVisibleIndex != null &&
        lastVisibleIndex >= totalListItems - 1 - HOME_CATALOG_PREFETCH_DISTANCE
}

internal fun appendHomeCatalogBatch(
    existing: List<CatalogSection>,
    incoming: List<CatalogSection>,
): List<CatalogSection> {
    val seenIds = existing.mapTo(mutableSetOf(), CatalogSection::id)
    return existing + incoming.filter { seenIds.add(it.id) }
}
internal fun CatalogSection.isRenderableHomeCatalogSection(): Boolean =
    initialLoading || items.isNotEmpty() || hasMore || errorMessage != null || loadMoreError != null



internal data class CatalogProviderFingerprint(
    val id: String,
    val manifestUrl: String,
    val displayName: String,
    val enabled: Boolean,
    val sortOrder: Int,
)

internal data class CatalogRefreshFingerprint(
    val userId: String,
    val childFilterEnabled: Boolean,
    val providers: List<CatalogProviderFingerprint>,
)

internal class CatalogRefreshGate {
    private var lastFingerprint: CatalogRefreshFingerprint? = null

    fun shouldStart(fingerprint: CatalogRefreshFingerprint, force: Boolean): Boolean {
        if (!force && fingerprint == lastFingerprint) return false
        lastFingerprint = fingerprint
        return true
    }

    fun reset() {
        lastFingerprint = null
    }
}

internal fun firstCatalogPage(
    section: CatalogSection,
    rawItems: List<MediaPreview>,
    visibleItems: List<MediaPreview> = rawItems,
): CatalogSection {
    val uniqueRawItems = rawItems.distinctBy(MediaPreview::stableKey)
    val effectiveStep = if (uniqueRawItems.isNotEmpty() && uniqueRawItems.size < section.skipStep) {
        uniqueRawItems.size
    } else {
        section.skipStep
    }
    return section.copy(
        items = visibleItems.distinctBy(MediaPreview::stableKey),
        errorMessage = null,
        nextSkip = if (section.supportsSkip) effectiveStep else 0,
        skipStep = effectiveStep,
        hasMore = section.supportsSkip && uniqueRawItems.isNotEmpty(),
        loadingMore = false,
        loadMoreError = null,
    )
}

internal fun mergeCatalogPage(
    section: CatalogSection,
    rawItems: List<MediaPreview>?,
    visibleItems: List<MediaPreview> = rawItems.orEmpty(),
    errorMessage: String? = null,
): CatalogSection {
    if (errorMessage != null) {
        return section.copy(loadingMore = false, loadMoreError = errorMessage)
    }
    val uniqueIncoming = rawItems.orEmpty().distinctBy(MediaPreview::stableKey)
    val existingKeys = section.items.mapTo(mutableSetOf(), MediaPreview::stableKey)
    val appendedVisible = visibleItems
        .distinctBy(MediaPreview::stableKey)
        .filterNot { it.stableKey in existingKeys }
    val terminal = uniqueIncoming.isEmpty() || uniqueIncoming.all { it.stableKey in existingKeys }
    return section.copy(
        items = section.items + appendedVisible,
        errorMessage = null,
        nextSkip = if (section.supportsSkip) section.nextSkip + section.skipStep else section.nextSkip,
        hasMore = section.hasMore && !terminal,
        loadingMore = false,
        loadMoreError = null,
    )
}

internal fun mergeCatalogRefresh(
    previous: List<CatalogSection>,
    refreshed: List<CatalogSection>,
): List<CatalogSection> = refreshed.flatMap { section ->
    if (section.initialLoading) {
        val stale = previous.firstOrNull {
            it.id == section.id && it.providerId == section.providerId
        }
        listOf(if (stale == null) section else section.copy(items = stale.items))
    } else if (section.errorMessage == null) {
        listOf(section)
    } else if (section.id == "${section.providerId}:error") {
        val staleSections = previous.filter { it.providerId == section.providerId && it.items.isNotEmpty() }
        if (staleSections.isEmpty()) {
            listOf(section)
        } else {
            staleSections.map { stale ->
                stale.copy(errorMessage = section.errorMessage)
            }
        }
    } else {
        val stale = previous.firstOrNull {
            it.id == section.id && it.providerId == section.providerId && it.items.isNotEmpty()
        }
        listOf(
            if (stale == null) section else section.copy(items = stale.items),
        )
    }
}
