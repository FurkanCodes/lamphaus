package com.lamphaus.app.ui

import com.lamphaus.core.model.ProviderSubscription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInAddonsTest {
    @Test
    fun `official opensubtitles addon is installed as an enabled built-in`() {
        val addon = builtInAddonFor(
            OPEN_SUBTITLES_PROVIDER_ID,
            OPEN_SUBTITLES_MANIFEST_URL,
        )

        assertNotNull(addon)
        val subscription = requireNotNull(addon).subscription(now = 123L)
        assertEquals("OpenSubtitles v3", subscription.displayName)
        assertEquals(OPEN_SUBTITLES_MANIFEST_URL, subscription.manifestUrl)
        assertTrue(subscription.enabled)
        assertTrue(subscription.sortOrder < 0)
        assertEquals(123L, subscription.updatedAtEpochMillis)
    }

    @Test
    fun `official manifest address resolves to the stable built-in identity`() {
        val addon = builtInAddonFor("unexpected-id", OPEN_SUBTITLES_MANIFEST_URL)

        assertEquals(OPEN_SUBTITLES_PROVIDER_ID, addon?.id)
    }

    @Test
    fun `opensubtitles uses imdb movie and episode ids`() {
        val addon = requireNotNull(builtInAddonFor(OPEN_SUBTITLES_PROVIDER_ID, "ignored"))
            .subscription(now = 0L)

        assertEquals("tt6718170", addon.subtitleVideoId("tt6718170", null, null, "fallback"))
        assertEquals("tt3107288:1:1", addon.subtitleVideoId("tt3107288", 1, 1, "fallback"))
    }

    @Test
    fun `non imdb and third party video ids stay provider native`() {
        val addon = requireNotNull(builtInAddonFor(OPEN_SUBTITLES_PROVIDER_ID, "ignored"))
            .subscription(now = 0L)
        val thirdParty = ProviderSubscription("other", "https://example.com/manifest.json", "Other")

        assertEquals("provider:episode", addon.subtitleVideoId("tmdb:42", 1, 1, "provider:episode"))
        assertEquals("custom:1:2", thirdParty.subtitleVideoId("tt1234567", 1, 2, "custom:1:2"))
    }
}
