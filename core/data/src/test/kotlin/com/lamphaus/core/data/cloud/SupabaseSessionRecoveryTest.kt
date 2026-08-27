package com.lamphaus.core.data.cloud

import io.github.jan.supabase.auth.exception.AuthErrorCode
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseSessionRecoveryTest {

    @Test
    fun `stale access token refreshes then retries exactly once`() = runTest {
        var token = "stale"
        var refreshCalls = 0
        var operationCalls = 0
        val recovery = recovery(
            token = { token },
            refresh = {
                refreshCalls++
                token = "fresh"
            },
        )

        val result = recovery.withAuthRetry {
            operationCalls++
            if (operationCalls == 1) throw unauthorizedAsymmetricJwt()
            token
        }

        assertEquals("fresh", result)
        assertEquals(1, refreshCalls)
        assertEquals(2, operationCalls)
    }

    @Test
    fun `concurrent rejected operations share one refresh and both retry`() = runTest {
        var token = "stale"
        val refreshStarted = CompletableDeferred<Unit>()
        val releaseRefresh = CompletableDeferred<Unit>()
        val refreshCalls = AtomicInteger()
        val operationCalls = AtomicInteger()
        val recovery = recovery(
            token = { token },
            refresh = {
                refreshCalls.incrementAndGet()
                refreshStarted.complete(Unit)
                releaseRefresh.await()
                token = "fresh"
            },
        )

        val operations = listOf("first", "second").map { value ->
            async {
                var firstAttempt = true
                recovery.withAuthRetry {
                    operationCalls.incrementAndGet()
                    if (firstAttempt) {
                        firstAttempt = false
                        throw unauthorizedAsymmetricJwt()
                    }
                    value
                }
            }
        }
        refreshStarted.await()
        releaseRefresh.complete(Unit)

        assertEquals(listOf("first", "second"), operations.awaitAll())
        assertEquals(1, refreshCalls.get())
        assertEquals(4, operationCalls.get())
    }

    @Test
    fun `replacement token skips duplicate refresh`() = runTest {
        var refreshCalls = 0
        val result = recovery(
            token = { "replacement" },
            refresh = { refreshCalls++ },
        ).refreshRestoredSession("stale")

        assertEquals(SessionRefreshResult.REFRESHED, result)
        assertEquals(0, refreshCalls)
    }

    @Test
    fun `network and server refresh failures are retryable and preserve session`() = runTest {
        listOf(
            UnknownHostException("offline"),
            SupabaseFunctionException(429, "rate_limited", "rate limited"),
            SupabaseFunctionException(503, "server_error", "server error"),
        ).forEach { failure ->
            var clearCalls = 0
            val result = recovery(
                clear = { clearCalls++ },
                refresh = { throw failure },
            ).refreshRestoredSession("stale")

            assertEquals(SessionRefreshResult.RETRYABLE_FAILURE, result)
            assertEquals(0, clearCalls)
        }
    }

    @Test
    fun `documented terminal Auth codes are classified as terminal`() {
        listOf(
            AuthErrorCode.RefreshTokenNotFound,
            AuthErrorCode.RefreshTokenAlreadyUsed,
            AuthErrorCode.SessionExpired,
            AuthErrorCode.SessionNotFound,
            AuthErrorCode.UserNotFound,
            AuthErrorCode.UserBanned,
            AuthErrorCode.BadJwt,
        ).forEach { assertTrue(isTerminalAuthError(it)) }
        assertTrue(!isTerminalAuthError(AuthErrorCode.Conflict))
    }

    @Test
    fun `unrelated unauthorized response does not refresh`() = runTest {
        var refreshCalls = 0
        val error = SupabaseFunctionException(401, "ownership_denied", "forbidden")
        var thrown: Throwable? = null
        try {
            recovery(
                refresh = { refreshCalls++ },
            ).withAuthRetry { throw error }
        } catch (failure: Throwable) {
            thrown = failure
        }

        assertSame(error, thrown)
        assertEquals(0, refreshCalls)
    }

    @Test
    fun `cancellation propagates without refresh`() = runTest {
        val cancellation = CancellationException("cancelled")
        var thrown: Throwable? = null
        try {
            recovery().withAuthRetry { throw cancellation }
        } catch (failure: Throwable) {
            thrown = failure
        }

        assertSame(cancellation, thrown)
    }

    private fun recovery(
        token: () -> String? = { "stale" },
        refresh: suspend () -> Unit = {},
        clear: suspend () -> Unit = {},
    ) = SupabaseSessionRecovery(
        currentAccessToken = token,
        refreshCurrentSession = refresh,
        clearSession = clear,
        delayMillis = {},
        logger = {},
    )

    private fun unauthorizedAsymmetricJwt() =
        SupabaseFunctionException(401, "UNAUTHORIZED_ASYMMETRIC_JWT", "unauthorized")
}
