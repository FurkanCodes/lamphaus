package com.lamphaus.app.ui

import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.ProviderCatalog
import com.lamphaus.core.model.ProviderResult
import com.lamphaus.core.model.ProviderSubscription
import com.lamphaus.core.provider.ProviderClient
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

internal data class HomeCatalogWindow(
    val sections: List<CatalogSection>,
    val consumedTargetCount: Int,
    val hasMore: Boolean,
)

internal class HomeCatalogLoader(
    private val providerClient: ProviderClient,
    providers: List<ProviderSubscription>,
    private val currentYear: Int,
    maxConcurrency: Int = HOME_CATALOG_MAX_CONCURRENCY,
    private val logger: (String) -> Unit = {},
) {
    private sealed interface Target {
        val section: CatalogSection

        data class Request(
            val subscription: ProviderSubscription,
            override val section: CatalogSection,
        ) : Target

        data class Ready(override val section: CatalogSection) : Target
    }

    private class ActiveRequest(val target: Target.Request) {
        @Volatile
        var resolved: Boolean = false
    }

    private val providers = providers
        .filter(ProviderSubscription::enabled)
        .sortedWith(compareBy<ProviderSubscription> { it.sortOrder }.thenBy { it.id })
    private val pendingTargets = ArrayDeque<Target>()
    private val sectionIds = mutableSetOf<String>()
    private val semaphore: Semaphore
    private val loadMutex = Mutex()
    private val activeRequestCount = AtomicInteger(0)
    private var providerCursor = 0
    private var consumedTargetCount = 0
    private var activeRequests: List<ActiveRequest> = emptyList()

    init {
        require(maxConcurrency > 0) { "maxConcurrency must be positive" }
        semaphore = Semaphore(maxConcurrency)
    }

    suspend fun loadNextWindow(
        childFilterEnabled: Boolean,
        onPrepared: (HomeCatalogWindow) -> Unit,
        onResolved: (CatalogSection) -> Unit,
    ): HomeCatalogWindow = loadMutex.withLock {
        if (activeRequests.isNotEmpty()) {
            val retryWindow = currentActiveWindow()
            resolveActiveRequests(childFilterEnabled, onResolved)
            return@withLock retryWindow
        }

        discoverTargetsForWindow()
        val preparedTargets = buildList {
            repeat(minOf(HOME_CATALOG_WINDOW_SIZE, pendingTargets.size)) {
                add(pendingTargets.removeFirst())
            }
        }
        consumedTargetCount += preparedTargets.size
        activeRequests = preparedTargets
            .filterIsInstance<Target.Request>()
            .map(::ActiveRequest)
        val window = HomeCatalogWindow(
            sections = preparedTargets.map(Target::section),
            consumedTargetCount = consumedTargetCount,
            hasMore = hasMoreTargets(),
        )
        onPrepared(window)
        resolveActiveRequests(childFilterEnabled, onResolved)
        window
    }

    private suspend fun discoverTargetsForWindow() {
        if (pendingTargets.size >= HOME_CATALOG_WINDOW_SIZE || providerCursor >= providers.size) return

        do {
            val chunkEnd = minOf(providerCursor + HOME_CATALOG_WINDOW_SIZE, providers.size)
            val chunk = providers.subList(providerCursor, chunkEnd)
            val manifests = coroutineScope {
                chunk.map { subscription ->
                    async {
                        semaphore.withPermit {
                            val active = activeRequestCount.incrementAndGet()
                            logger("manifest start provider=${subscription.id} active=$active")
                            try {
                                subscription to providerClient.manifest(subscription.manifestUrl)
                            } finally {
                                logger(
                                    "manifest end provider=${subscription.id} " +
                                        "active=${activeRequestCount.decrementAndGet()}",
                                )
                            }
                        }
                    }
                }.awaitAll()
            }
            providerCursor = chunkEnd
            manifests.forEach { (subscription, result) ->
                enqueueManifestTargets(subscription, result)
            }
        } while (pendingTargets.isEmpty() && providerCursor < providers.size)
    }

    private fun enqueueManifestTargets(
        subscription: ProviderSubscription,
        result: ProviderResult<com.lamphaus.core.model.ProviderManifest>,
    ) {
        when (result) {
            is ProviderResult.Failure -> {
                logger("manifest failure provider=${subscription.id} message=${result.safeMessage}")
                enqueue(
                    Target.Ready(
                        CatalogSection(
                            id = "${subscription.id}:error",
                            providerId = subscription.id,
                            title = subscription.displayName,
                            providerName = subscription.displayName,
                            items = emptyList(),
                            errorMessage = result.safeMessage,
                            initialLoading = false,
                        ),
                    ),
                )
            }

            is ProviderResult.Success -> {
                val includeCuratedGenres = subscription.id == CINEMETA_PROVIDER_ID ||
                    subscription.manifestUrl == CINEMETA_MANIFEST_URL
                var addedCount = 0
                result.value.catalogs.forEach { catalog ->
                    catalog.homePlans(includeCuratedGenres, currentYear).forEach { plan ->
                        val target = catalogTarget(subscription, catalog, plan)
                        if (enqueue(target)) addedCount++
                    }
                }
                logger(
                    "manifest success provider=${subscription.id} catalogs=${result.value.catalogs.size} " +
                        "homeTargets=$addedCount",
                )
            }
        }
    }

    private fun catalogTarget(
        subscription: ProviderSubscription,
        catalog: ProviderCatalog,
        plan: CatalogHomePlan,
    ): Target = when (plan) {
        is CatalogHomePlan.Request -> {
            val section = CatalogSection(
                id = "home:${canonicalCatalogRequestIdentity(subscription.id, plan.query)}",
                providerId = subscription.id,
                title = plan.title,
                providerName = subscription.displayName,
                items = emptyList(),
                baseQuery = plan.query,
                supportsSkip = catalog.supportsSkip(),
                skipStep = catalog.initialSkipStep(),
                hasMore = catalog.supportsSkip(),
                initialLoading = true,
            )
            Target.Request(subscription, section)
        }

        is CatalogHomePlan.Unavailable -> Target.Ready(
            CatalogSection(
                id = "${subscription.id}:${catalog.type}:${catalog.id}:unavailable",
                providerId = subscription.id,
                title = plan.title,
                providerName = subscription.displayName,
                items = emptyList(),
                errorMessage = plan.reason,
                initialLoading = false,
            ),
        )
    }

    private fun enqueue(target: Target): Boolean {
        if (!sectionIds.add(target.section.id)) return false
        pendingTargets.addLast(target)
        return true
    }

    private suspend fun resolveActiveRequests(
        childFilterEnabled: Boolean,
        onResolved: (CatalogSection) -> Unit,
    ) {
        val requests = activeRequests
        if (requests.isEmpty()) return

        var firstFailure: Throwable? = null
        try {
            val failures = supervisorScope {
                requests.map { activeRequest ->
                    async {
                        try {
                            val resolved = resolve(activeRequest.target, childFilterEnabled)
                            onResolved(resolved)
                            activeRequest.resolved = true
                            null
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Throwable) {
                            error
                        }
                    }
                }.awaitAll()
            }
            firstFailure = failures.firstOrNull { it != null }
        } finally {
            activeRequests = requests.filterNot(ActiveRequest::resolved)
        }
        firstFailure?.let { throw it }
    }

    private suspend fun resolve(
        target: Target.Request,
        childFilterEnabled: Boolean,
    ): CatalogSection = semaphore.withPermit {
        val query = target.section.baseQuery
        val active = activeRequestCount.incrementAndGet()
        logger("catalog start provider=${target.subscription.id} catalog=${query.catalogId} active=$active")
        try {
            when (val result = providerClient.catalog(
                target.subscription.manifestUrl,
                target.subscription.id,
                query,
            )) {
                is ProviderResult.Success -> {
                    logger(
                        "catalog success provider=${target.subscription.id} " +
                            "catalog=${query.catalogId} items=${result.value.size}",
                    )
                    firstCatalogPage(
                        target.section,
                        result.value,
                        filterForChildProfile(result.value, childFilterEnabled),
                    ).copy(initialLoading = false)
                }

                is ProviderResult.Failure -> {
                    logger(
                        "catalog failure provider=${target.subscription.id} " +
                            "catalog=${query.catalogId} message=${result.safeMessage}",
                    )
                    target.section.copy(
                        errorMessage = result.safeMessage,
                        hasMore = false,
                        initialLoading = false,
                    )
                }
            }
        } finally {
            logger(
                "catalog end provider=${target.subscription.id} catalog=${query.catalogId} " +
                    "active=${activeRequestCount.decrementAndGet()}",
            )
        }
    }

    private fun currentActiveWindow() = HomeCatalogWindow(
        sections = activeRequests.map { it.target.section },
        consumedTargetCount = consumedTargetCount,
        hasMore = hasMoreTargets(),
    )

    private fun hasMoreTargets(): Boolean = pendingTargets.isNotEmpty() || providerCursor < providers.size

    private fun filterForChildProfile(
        items: List<MediaPreview>,
        enabled: Boolean,
    ): List<MediaPreview> = if (enabled) {
        items.filter { !it.contentRating.isNullOrBlank() }
    } else {
        items
    }
}
