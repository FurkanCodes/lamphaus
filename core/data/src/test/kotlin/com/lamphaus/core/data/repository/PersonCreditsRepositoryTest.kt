package com.lamphaus.core.data.repository

import com.lamphaus.core.data.cloud.CloudNotConfiguredException
import com.lamphaus.core.data.cloud.PersonCreditsRemoteSource
import com.lamphaus.core.model.PersonFilmography
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** MOB-SRCH-01: person pages reuse a fresh filmography and keep failures local. */
class PersonCreditsRepositoryTest {
    private class FakePersonCreditsRemote : PersonCreditsRemoteSource {
        var calls = 0
        var failure: Throwable? = null

        override suspend fun fetch(request: com.lamphaus.core.model.PersonCreditsRequest): PersonFilmography {
            calls++
            failure?.let { throw it }
            return PersonFilmography(personId = request.personId, name = "Person ${request.personId}")
        }
    }

    @Test
    fun freshFilmographyIsServedFromMemory() = runTest {
        val remote = FakePersonCreditsRemote()
        var now = 0L
        val repository = PersonCreditsRepository(remote, ttlMillis = 1_000, clock = { now })

        repository.filmography("7")
        now = 999
        repository.filmography("7")

        assertEquals(1, remote.calls)
    }

    @Test
    fun staleFilmographyIsFetchedAgain() = runTest {
        val remote = FakePersonCreditsRemote()
        var now = 0L
        val repository = PersonCreditsRepository(remote, ttlMillis = 1_000, clock = { now })

        repository.filmography("7")
        now = 1_000
        repository.filmography("7")

        assertEquals(2, remote.calls)
    }

    @Test
    fun failureIsReturnedAndNotCached() = runTest {
        val remote = FakePersonCreditsRemote().apply { failure = IllegalStateException("offline") }
        val repository = PersonCreditsRepository(remote)

        assertTrue(repository.filmography("7").isFailure)
        remote.failure = null
        assertEquals("Person 7", repository.filmography("7").getOrThrow().name)
    }

    @Test
    fun withoutCloudLookupsAreUnavailable() = runTest {
        val repository = PersonCreditsRepository(remote = null)

        assertFalse(repository.available)
        assertTrue(repository.filmography("7").exceptionOrNull() is CloudNotConfiguredException)
    }
}
