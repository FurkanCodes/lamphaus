package com.lamphaus.app.ui

import com.lamphaus.core.model.SpoilerProtectionSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpoilerProtectionPolicyTest {
    @Test
    fun `master off wins over every content setting`() {
        val settings = SpoilerProtectionSettings(enabled = false)

        SpoilerContent.entries.forEach { content ->
            assertFalse(settings.shouldBlur(content, watched = false))
        }
    }

    @Test
    fun `completed content wins over enabled protection`() {
        SpoilerContent.entries.forEach { content ->
            assertFalse(SpoilerProtectionSettings().shouldBlur(content, watched = true))
        }
    }

    @Test
    fun `each child setting controls only its matching episode content`() {
        val settings = SpoilerProtectionSettings(
            blurEpisodeArtwork = false,
            blurEpisodeSynopsis = false,
        )

        SpoilerContent.entries.forEach { content ->
            assertFalse(settings.shouldBlur(content, watched = false))
        }
        assertTrue(settings.copy(blurEpisodeArtwork = true).shouldBlur(SpoilerContent.EPISODE_ARTWORK, false))
        assertFalse(settings.copy(blurEpisodeArtwork = true).shouldBlur(SpoilerContent.EPISODE_SYNOPSIS, false))
        assertTrue(settings.copy(blurEpisodeSynopsis = true).shouldBlur(SpoilerContent.EPISODE_SYNOPSIS, false))
        assertFalse(settings.copy(blurEpisodeSynopsis = true).shouldBlur(SpoilerContent.EPISODE_ARTWORK, false))
    }

    @Test
    fun `defaults protect all episode content`() {
        SpoilerContent.entries.forEach { content ->
            assertTrue(SpoilerProtectionSettings().shouldBlur(content, watched = false))
        }
    }
}
