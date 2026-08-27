package com.lamphaus.core.data.cloud

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SupabaseGatewayRecoveryBoundaryTest {

    @Test
    fun `provider unauthorized code is extracted without matching prose`() {
        val json = Json { ignoreUnknownKeys = true }

        assertEquals(
            "UNAUTHORIZED_ASYMMETRIC_JWT",
            extractFunctionErrorCode(json, "{\"error\":\"UNAUTHORIZED_ASYMMETRIC_JWT\"}"),
        )
        assertNull(extractFunctionErrorCode(json, "{\"message\":\"ownership denied\"}"))
    }

    @Test
    fun `PostgREST JWT errors distinguish expiry from future clock skew`() {
        assertEquals(true, isExpiredPostgrestJwt("PGRST303", "JWT expired"))
        assertEquals(false, isExpiredPostgrestJwt("PGRST303", "JWT issued at future"))
        assertEquals(true, isFuturePostgrestJwt("PGRST303", "JWT issued at future"))
        assertEquals(false, isFuturePostgrestJwt("PGRST303", "JWT expired"))
    }

    @Test
    fun `device registration retries once after refreshed bearer`() = runTest {
        var token = "stale"
        var attempts = 0
        var refreshes = 0
        val recovery = SupabaseSessionRecovery(
            currentAccessToken = { token },
            refreshCurrentSession = {
                refreshes++
                token = "fresh"
            },
            clearSession = {},
            delayMillis = {},
            logger = {},
        )

        val result = recovery.withAuthRetry {
            attempts++
            if (attempts == 1) throw SupabaseFunctionException(401, "UNAUTHORIZED_ASYMMETRIC_JWT", "")
            Unit
        }

        assertEquals(Unit, result)
        assertEquals(2, attempts)
        assertEquals(1, refreshes)
    }

    @Test
    fun `realtime flow resubscribes once after access token refresh`() = runTest {
        var token = "stale"
        var subscriptions = 0
        var refreshes = 0
        val recovery = SupabaseSessionRecovery(
            currentAccessToken = { token },
            refreshCurrentSession = {
                refreshes++
                token = "fresh"
            },
            clearSession = {},
            delayMillis = {},
            logger = {},
        )
        val source = flow {
            subscriptions++
            emit(subscriptions)
            if (subscriptions == 1) {
                throw SupabaseFunctionException(401, "UNAUTHORIZED_ASYMMETRIC_JWT", "")
            }
        }

        assertEquals(listOf(1, 2), source.withSessionRecovery(recovery).toList())
        assertEquals(2, subscriptions)
        assertEquals(1, refreshes)
    }
}
