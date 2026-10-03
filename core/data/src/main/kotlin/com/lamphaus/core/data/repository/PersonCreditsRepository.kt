package com.lamphaus.core.data.repository

import com.lamphaus.core.data.cloud.CloudNotConfiguredException
import com.lamphaus.core.data.cloud.PersonCreditsRemoteSource
import com.lamphaus.core.model.PersonCreditsRequest
import com.lamphaus.core.model.PersonFilmography
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A person's titles, kept in memory for [ttlMillis] so going back and forth
 * between a details page and its cast stays instant. Filmographies change
 * rarely and are cheap to fetch again, so nothing is persisted.
 */
class PersonCreditsRepository(
    private val remote: PersonCreditsRemoteSource?,
    private val ttlMillis: Long = 6L * 60 * 60 * 1000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val cache = object : LinkedHashMap<String, Pair<Long, PersonFilmography>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, PersonFilmography>>) =
            size > MAX_PEOPLE
    }

    /** Whether lookups can work at all; without a cloud the cast stays plain text. */
    val available: Boolean get() = remote != null

    suspend fun filmography(personId: String): Result<PersonFilmography> {
        val remote = remote ?: return Result.failure(CloudNotConfiguredException())
        mutex.withLock {
            cache[personId]?.takeIf { (fetchedAt) -> clock() - fetchedAt < ttlMillis }
                ?.let { (_, cached) -> return Result.success(cached) }
        }
        return runCatching { remote.fetch(PersonCreditsRequest(personId)) }
            .onSuccess { fresh -> mutex.withLock { cache[personId] = clock() to fresh } }
    }

    suspend fun clear() = mutex.withLock { cache.clear() }

    private companion object {
        const val MAX_PEOPLE = 32
    }
}
