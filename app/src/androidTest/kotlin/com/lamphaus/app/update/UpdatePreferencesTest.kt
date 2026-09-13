package com.lamphaus.app.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class UpdatePreferencesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Before
    fun clearBefore() {
        stored.edit().clear().commit()
    }

    @After
    fun clearAfter() {
        stored.edit().clear().commit()
    }

    @Test
    fun QA04_incompatiblePersistedUpdateStateRecoversWithoutStartupCrash() {
        stored.edit()
            .putString("last_auto_check", "legacy")
            .putLong("consecutive_failures", 3L)
            .putBoolean("installer_session_id", true)
            .commit()

        val preferences = UpdatePreferences(context)

        assertEquals(0L, preferences.lastAutoCheckMillis)
        assertEquals(0, preferences.consecutiveFailures)
        assertEquals(-1, preferences.installerSessionId)
        assertEquals(false, stored.contains("last_auto_check"))
        assertEquals(false, stored.contains("consecutive_failures"))
        assertEquals(false, stored.contains("installer_session_id"))
    }

    private companion object {
        const val PREFS_NAME = "lamphaus_updates"
    }
}
