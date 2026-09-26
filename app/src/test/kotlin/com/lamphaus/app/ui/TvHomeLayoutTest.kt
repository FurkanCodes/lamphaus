package com.lamphaus.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TvHomeLayoutTest {
    @Test
    fun `TV-CNT-01 an unset or unknown layout falls back to classic`() {
        assertEquals(TvHomeLayout.CLASSIC, TvHomeLayout.fromName(null))
        assertEquals(TvHomeLayout.CLASSIC, TvHomeLayout.fromName("GRID"))
        assertEquals(TvHomeLayout.SPOTLIGHT, TvHomeLayout.fromName("SPOTLIGHT"))
    }

    @Test
    fun `the device-local layout survives leaving an account`() {
        val state = AppUiState(tvHomeLayout = TvHomeLayout.SPOTLIGHT)

        assertEquals(TvHomeLayout.SPOTLIGHT, state.clearAccountData().tvHomeLayout)
    }
}
