package com.lamphaus.app.diagnostics

import com.lamphaus.app.mobile.MobileDestination
import com.lamphaus.app.mobile.mobileScreen
import com.lamphaus.app.tv.TvDestination
import com.lamphaus.app.tv.tvScreen
import org.junit.Assert.assertEquals
import org.junit.Test

/** SHR-PROD-18: a crash names the page the viewer is on, whatever is stacked on it. */
class AppScreenTest {
    @Test
    fun `TV names the browsing page`() {
        assertEquals(AppScreen.HOME, tvScreen(TvDestination.HOME, detailOpen = false, personOpen = false))
        assertEquals(AppScreen.SERIES, tvScreen(TvDestination.SERIES, detailOpen = false, personOpen = false))
        assertEquals(AppScreen.SETTINGS, tvScreen(TvDestination.SETTINGS, detailOpen = false, personOpen = false))
    }

    @Test
    fun `TV names what sits over the browsing page`() {
        assertEquals(AppScreen.DETAILS, tvScreen(TvDestination.HOME, detailOpen = true, personOpen = false))
        assertEquals(AppScreen.PERSON, tvScreen(TvDestination.HOME, detailOpen = true, personOpen = true))
    }

    @Test
    fun `mobile details sit over settings and settings over the tab`() {
        assertEquals(AppScreen.LIBRARY, mobileScreen(MobileDestination.LIBRARY, settingsOpen = false, detailOpen = false, personOpen = false))
        assertEquals(AppScreen.SETTINGS, mobileScreen(MobileDestination.LIBRARY, settingsOpen = true, detailOpen = false, personOpen = false))
        assertEquals(AppScreen.DETAILS, mobileScreen(MobileDestination.LIBRARY, settingsOpen = true, detailOpen = true, personOpen = false))
        assertEquals(AppScreen.PERSON, mobileScreen(MobileDestination.LIBRARY, settingsOpen = false, detailOpen = true, personOpen = true))
    }
}
