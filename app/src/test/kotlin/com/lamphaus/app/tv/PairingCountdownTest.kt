package com.lamphaus.app.tv

import org.junit.Assert.assertEquals
import org.junit.Test

class PairingCountdownTest {
    private val nowEpochMillis = 1_000_000L

    @Test
    fun `caps unexpected server expiry at pairing ttl`() {
        val serverExpiry = nowEpochMillis + 161 * 60_000L

        val boundedExpiry = pairingCountdownExpiry(serverExpiry, nowEpochMillis)

        assertEquals(nowEpochMillis + 5 * 60_000L, boundedExpiry)
        assertEquals(5 * 60, pairingSecondsLeft(boundedExpiry, nowEpochMillis))
    }

    @Test
    fun `preserves server expiry inside pairing ttl`() {
        val serverExpiry = nowEpochMillis + 90_000L

        val boundedExpiry = pairingCountdownExpiry(serverExpiry, nowEpochMillis)

        assertEquals(serverExpiry, boundedExpiry)
        assertEquals(90, pairingSecondsLeft(boundedExpiry, nowEpochMillis))
    }

    @Test
    fun `reports unknown countdown without expiry`() {
        assertEquals(-1, pairingSecondsLeft(null, nowEpochMillis))
    }
}
