package com.lamphaus.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundArtworkSettingTest {
    @Test
    fun `automatic default is on except on low-memory devices`() {
        assertTrue(effectiveBackgroundArtwork(choice = null, lowRamDevice = false))
        assertFalse(effectiveBackgroundArtwork(choice = null, lowRamDevice = true))
    }

    @Test
    fun `an explicit choice always wins`() {
        assertTrue(effectiveBackgroundArtwork(choice = true, lowRamDevice = true))
        assertFalse(effectiveBackgroundArtwork(choice = false, lowRamDevice = false))
    }

    @Test
    fun `the device-local choice survives leaving an account`() {
        val state = AppUiState(backgroundArtworkEnabled = false)

        assertFalse(state.clearAccountData().backgroundArtworkEnabled)
    }
}
