package com.lamphaus.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TvHomeLayoutTest {
    @Test
    fun `TV-CNT-01 an unset or unknown layout falls back to spotlight`() {
        assertEquals(TvHomeLayout.SPOTLIGHT, TvHomeLayout.fromName(null))
        assertEquals(TvHomeLayout.SPOTLIGHT, TvHomeLayout.fromName("GRID"))
        assertEquals(TvHomeLayout.CLASSIC, TvHomeLayout.fromName("CLASSIC"))
        assertEquals(TvHomeLayout.SHOWCASE, TvHomeLayout.fromName("SHOWCASE"))
        assertEquals(TvHomeLayout.MARQUEE, TvHomeLayout.fromName("MARQUEE"))
        assertEquals(TvHomeLayout.SPOTLIGHT, AppUiState().tvHomeLayout)
    }

    @Test
    fun `TV-CNT-05 TV-CNT-06 the new layouts combine the hero and the widening card`() {
        assertEquals(true to false, TvHomeLayout.CLASSIC.hasHero to TvHomeLayout.CLASSIC.widensCards)
        assertEquals(false to true, TvHomeLayout.SPOTLIGHT.hasHero to TvHomeLayout.SPOTLIGHT.widensCards)
        assertEquals(false to true, TvHomeLayout.SHOWCASE.hasHero to TvHomeLayout.SHOWCASE.widensCards)
        assertEquals(true to true, TvHomeLayout.MARQUEE.hasHero to TvHomeLayout.MARQUEE.widensCards)
    }

    @Test
    fun `the device-local layout survives leaving an account`() {
        val state = AppUiState(tvHomeLayout = TvHomeLayout.CLASSIC)

        assertEquals(TvHomeLayout.CLASSIC, state.clearAccountData().tvHomeLayout)
    }

    @Test
    fun `TV-NAV-01 an unset or unknown navigation style falls back to the side rail`() {
        assertEquals(TvNavigationStyle.SIDE_RAIL, TvNavigationStyle.fromName(null))
        assertEquals(TvNavigationStyle.SIDE_RAIL, TvNavigationStyle.fromName("DRAWER"))
        assertEquals(TvNavigationStyle.TOP_BAR, TvNavigationStyle.fromName("TOP_BAR"))
        assertEquals(TvNavigationStyle.SIDE_RAIL, AppUiState().tvNavigationStyle)
    }

    @Test
    fun `the device-local navigation style survives leaving an account`() {
        val state = AppUiState(tvNavigationStyle = TvNavigationStyle.TOP_BAR)

        assertEquals(TvNavigationStyle.TOP_BAR, state.clearAccountData().tvNavigationStyle)
    }

    @Test
    fun `TV-CLR-01 the black background is off until chosen and survives leaving an account`() {
        assertEquals(false, AppUiState().tvBlackBackground)

        assertEquals(true, AppUiState(tvBlackBackground = true).clearAccountData().tvBlackBackground)
    }
}
