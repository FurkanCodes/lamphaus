package com.lamphaus.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import com.lamphaus.core.data.preferences.UserPreferences
import com.lamphaus.core.model.NextEpisodeThresholdMode
import com.lamphaus.core.model.PlaybackSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-local playback settings persistence (SHR-ARC-05): round-trip through
 * DataStore with out-of-range threshold values defensively clamped on read.
 */
@RunWith(AndroidJUnit4::class)
class UserPreferencesPlaybackTest {
    @Test
    fun playbackSettingsPersistWithDefensiveClamps() = runBlocking {
        val preferences = UserPreferences(ApplicationProvider.getApplicationContext())

        preferences.setPlaybackSettings(
            PlaybackSettings(
                skipIntroEnabled = false,
                skipEndingEnabled = true,
                nextEpisodeEnabled = false,
                nextEpisodeThresholdMode = NextEpisodeThresholdMode.MINUTES_BEFORE_END,
                nextEpisodeThresholdPercent = 120f,
                nextEpisodeThresholdMinutesBeforeEnd = 0f,
            ),
        )

        val stored = preferences.current().playback
        assertFalse(stored.skipIntroEnabled)
        assertTrue(stored.skipEndingEnabled)
        assertFalse(stored.nextEpisodeEnabled)
        assertEquals(NextEpisodeThresholdMode.MINUTES_BEFORE_END, stored.nextEpisodeThresholdMode)
        assertEquals(99.5f, stored.nextEpisodeThresholdPercent)
        assertEquals(1f, stored.nextEpisodeThresholdMinutesBeforeEnd)
    }
}
