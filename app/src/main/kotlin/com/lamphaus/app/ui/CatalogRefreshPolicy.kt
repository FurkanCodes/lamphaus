package com.lamphaus.app.ui

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

internal fun mergeCatalogRefresh(
    previous: List<CatalogSection>,
    refreshed: List<CatalogSection>,
): List<CatalogSection> = refreshed.flatMap { section ->
    if (section.errorMessage == null) {
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
