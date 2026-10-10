package com.lamphaus.app.ui

import com.lamphaus.core.model.MediaPreview
internal const val HOME_CATALOG_MAX_CONCURRENCY = 4
internal const val HOME_CATALOG_WINDOW_SIZE = 4
/**
 * QA-08: keep about two TV screens of rows ready or in flight below the last
 * visible row, so steady D-pad scrolling rarely reaches the loading footer.
 */
internal const val HOME_CATALOG_PREFETCH_DISTANCE = 8

/** Windows that may resolve at once; the loader's semaphore still bounds requests. */
internal const val HOME_CATALOG_MAX_WINDOWS_IN_FLIGHT = 2

/**
 * [pendingRows] are prepared rows not yet revealed; they count as rows ahead
 * because they will appear below the list as they settle.
 */
internal fun shouldPrefetchHomeCatalogBatch(
    lastVisibleIndex: Int?,
    totalListItems: Int,
    pendingRows: Int,
    hasMore: Boolean,
    failed: Boolean,
): Boolean {
    if (!hasMore || failed) return false
    if (totalListItems <= 0) return pendingRows == 0
    if (lastVisibleIndex == null) return false
    val rowsAhead = (totalListItems - 1 - lastVisibleIndex) + pendingRows
    return rowsAhead < HOME_CATALOG_PREFETCH_DISTANCE
}

/** A prepared Home row that is not revealed yet; [resolved] is null while loading. */
internal data class PendingHomeRow(
    val id: String,
    val resolved: CatalogSection? = null,
)

internal data class HomeRowReveal(
    val revealed: List<CatalogSection>,
    val remaining: List<PendingHomeRow>,
)

/**
 * Reveals only the settled prefix of [pending], in order, so rows never
 * appear above ones still loading and nothing already shown moves
 * (TV-CNT-02). Rows that settle empty are dropped before they are ever shown;
 * error rows stay attached to their provider (SHR-PROD-04).
 */
internal fun revealSettledHomeRows(pending: List<PendingHomeRow>): HomeRowReveal {
    val settled = pending.takeWhile { it.resolved != null }
    return HomeRowReveal(
        revealed = settled.mapNotNull { row ->
            row.resolved?.takeIf(CatalogSection::isRenderableHomeCatalogSection)
        },
        remaining = pending.drop(settled.size),
    )
}

internal fun appendHomeCatalogBatch(
    existing: List<CatalogSection>,
    incoming: List<CatalogSection>,
): List<CatalogSection> {
    val seenIds = existing.mapTo(mutableSetOf(), CatalogSection::id)
    return existing + incoming.filter { seenIds.add(it.id) }
}

/**
 * Publishes incrementally resolved sections in deterministic provider/catalog
 * order, dropping anything that has not resolved yet (PERF-06).
 */
internal fun orderedResolvedSections(
    orderedIds: List<String>,
    resolved: Map<String, CatalogSection>,
): List<CatalogSection> = orderedIds.mapNotNull(resolved::get)
internal fun CatalogSection.isRenderableHomeCatalogSection(): Boolean =
    initialLoading || items.isNotEmpty() || hasMore || errorMessage != null || loadMoreError != null

/**
 * Whether Home's first screen is finished: the first row the viewer sees has
 * its cards, or settled as an error, so the TV boot reveals a complete screen
 * rather than placeholders (TV-MOT-01, TV-CNT-02). Continue Watching comes
 * from the device and is ready before this. Rows that settle empty never
 * show, so the next one counts; no rows at all is finished too.
 */
internal fun homeFirstScreenReady(initialContentLoading: Boolean, sections: List<CatalogSection>): Boolean {
    if (initialContentLoading) return false
    val first = sections.firstOrNull(CatalogSection::isRenderableHomeCatalogSection) ?: return true
    return !first.initialLoading
}



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
    val hideUnreleased: Boolean = false,
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
