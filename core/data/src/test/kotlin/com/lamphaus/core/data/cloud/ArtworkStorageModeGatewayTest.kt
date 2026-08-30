package com.lamphaus.core.data.cloud

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkStorageModeGatewayTest {
    @Test
    fun `enabling clears cloud then local keys before persisting mode`() = runBlocking {
        val calls = mutableListOf<String>()

        val result = performArtworkStorageModeChange(
            currentLocalOnly = false,
            targetLocalOnly = true,
            clearCloud = { calls += "cloud" },
            clearLocal = { calls += "local" },
            persistMode = { enabled -> calls += "persist:$enabled" },
        )

        assertTrue(result.isSuccess)
        assertEquals(listOf("cloud", "local", "persist:true"), calls)
    }

    @Test
    fun `disabling clears local keys before persisting cloud mode`() = runBlocking {
        val calls = mutableListOf<String>()

        val result = performArtworkStorageModeChange(
            currentLocalOnly = true,
            targetLocalOnly = false,
            clearCloud = { calls += "cloud" },
            clearLocal = { calls += "local" },
            persistMode = { enabled -> calls += "persist:$enabled" },
        )

        assertTrue(result.isSuccess)
        assertEquals(listOf("local", "persist:false"), calls)
    }

    @Test
    fun `unchanged mode does not clear or persist`() = runBlocking {
        val calls = mutableListOf<String>()

        val result = performArtworkStorageModeChange(
            currentLocalOnly = true,
            targetLocalOnly = true,
            clearCloud = { calls += "cloud" },
            clearLocal = { calls += "local" },
            persistMode = { calls += "persist" },
        )

        assertTrue(result.isSuccess)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `cloud clear failure short circuits local clear and persistence`() = runBlocking {
        val calls = mutableListOf<String>()

        val result = performArtworkStorageModeChange(
            currentLocalOnly = false,
            targetLocalOnly = true,
            clearCloud = {
                calls += "cloud"
                error("cloud unavailable")
            },
            clearLocal = { calls += "local" },
            persistMode = { calls += "persist" },
        )

        assertFalse(result.isSuccess)
        assertEquals(listOf("cloud"), calls)
    }
}
