package com.lamphaus.app.ui

import com.lamphaus.core.data.cloud.AccountState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceBindingPolicyTest {

    @Test
    fun `transient failures keep retrying on capped schedule`() {
        val delays = (0..8).map(::deviceBindingBackoffMillis)

        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L, 30_000L, 30_000L), delays)
        assertTrue(shouldRetryDeviceBinding(AccountState.SignedIn("user", null, null), Result.failure(Exception("offline"))))
    }

    @Test
    fun `success stops and signed out cancels retry`() {
        assertFalse(shouldRetryDeviceBinding(AccountState.SignedIn("user", null, null), Result.success(Unit)))
        assertFalse(shouldRetryDeviceBinding(AccountState.SignedOut, Result.failure(Exception("offline"))))
    }

    @Test
    fun `unbindable device is terminal regardless of sign-in state`() {
        assertTrue(isTerminalDeviceBindingError(RuntimeException(DEVICE_UNBINDABLE_ERROR)))
        assertTrue(
            isTerminalDeviceBindingError(
                IllegalStateException("rpc failed", RuntimeException(DEVICE_UNBINDABLE_ERROR)),
            ),
        )
        // Transient failures are never terminal, even while signed in — the
        // capped backoff loop keeps retrying (DeviceBindingPolicyTest covers
        // the schedule; the collector stops only on these markers).
        assertFalse(isTerminalDeviceBindingError(RuntimeException("offline")))
        assertFalse(isTerminalDeviceBindingError(null))
    }
}
