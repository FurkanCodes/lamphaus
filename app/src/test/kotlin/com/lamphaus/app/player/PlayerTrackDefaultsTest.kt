package com.lamphaus.app.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import com.lamphaus.core.model.MediaPlaybackSelection
import com.lamphaus.core.model.ProfilePlaybackPreferences
import com.lamphaus.core.model.SubtitleDefaultMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan §3 on real engine tracks: the profile's languages, a title's
 * remembered choice first, never an undecodable track, and forced-only
 * that stays off without a forced track.
 */
class PlayerTrackDefaultsTest {
    private fun audio(id: String, language: String, default: Boolean = false) = Format.Builder()
        .setId(id)
        .setLanguage(language)
        .setSampleMimeType(MimeTypes.AUDIO_AC3)
        .setChannelCount(6)
        .setSelectionFlags(if (default) C.SELECTION_FLAG_DEFAULT else 0)
        .build()

    private fun subtitle(id: String, language: String, forced: Boolean = false, label: String? = null) = Format.Builder()
        .setId(id)
        .setLanguage(language)
        .setLabel(label)
        .setSampleMimeType(MimeTypes.APPLICATION_SUBRIP)
        .setSelectionFlags(if (forced) C.SELECTION_FLAG_FORCED else 0)
        .build()

    private fun tracks(vararg formats: Pair<Format, Boolean>): Tracks = Tracks(
        formats.map { (format, supported) ->
            Tracks.Group(
                TrackGroup(format),
                false,
                intArrayOf(if (supported) C.FORMAT_HANDLED else C.FORMAT_UNSUPPORTED_SUBTYPE),
                booleanArrayOf(false),
            )
        },
    )

    private fun decide(
        tracks: Tracks,
        profile: ProfilePlaybackPreferences,
        remembered: MediaPlaybackSelection? = null,
        device: String = "en-US",
    ) = decideTrackDefaults(tracks.trackCandidates(), profile, remembered, device)

    @Test
    fun `profile audio language wins over the stream default`() {
        val tracks = tracks(audio("en", "en", default = true) to true, audio("ja", "ja") to true)

        val decision = decide(tracks, ProfilePlaybackPreferences(audioLanguageTag = "ja"))

        assertEquals("ja", decision.audio?.format?.id)
    }

    @Test
    fun `original keeps the stream default`() {
        val tracks = tracks(audio("ja", "ja", default = true) to true, audio("en", "en") to true)

        val decision = decide(tracks, ProfilePlaybackPreferences())

        assertEquals("ja", decision.audio?.format?.id)
    }

    @Test
    fun `an undecodable track is never the default`() {
        val tracks = tracks(audio("ja-truehd", "ja") to false, audio("en", "en") to true)

        val decision = decide(tracks, ProfilePlaybackPreferences(audioLanguageTag = "ja"))

        assertEquals("en", decision.audio?.format?.id)
    }

    @Test
    fun `preferred subtitle language turns on that language`() {
        val tracks = tracks(
            audio("ja", "ja") to true,
            subtitle("tr", "tr") to true,
            subtitle("addon-en", "eng") to true,
        )
        val profile = ProfilePlaybackPreferences(
            subtitleDefaultMode = SubtitleDefaultMode.PREFERRED_LANGUAGE,
            preferredSubtitleLanguageTag = "en",
        )

        assertEquals("addon-en", decide(tracks, profile, device = "tr-TR").subtitle?.format?.id)
    }

    @Test
    fun `forced only picks the forced track and otherwise stays off`() {
        val withForced = tracks(
            audio("en", "en") to true,
            subtitle("en-full", "en") to true,
            subtitle("en-forced", "en", forced = true) to true,
        )
        val withoutForced = tracks(audio("en", "en") to true, subtitle("en-full", "en") to true)
        val profile = ProfilePlaybackPreferences(subtitleDefaultMode = SubtitleDefaultMode.FORCED_ONLY)

        assertEquals("en-forced", decide(withForced, profile).subtitle?.format?.id)
        assertNull(decide(withoutForced, profile).subtitle)
    }

    @Test
    fun `a remembered choice beats the profile`() {
        val tracks = tracks(
            audio("en", "en") to true,
            audio("ja", "ja") to true,
            subtitle("en", "en") to true,
            subtitle("de", "de") to true,
        )
        val profile = ProfilePlaybackPreferences(
            audioLanguageTag = "en",
            subtitleDefaultMode = SubtitleDefaultMode.OFF,
        )
        val remembered = MediaPlaybackSelection(audioLanguageTag = "ja", subtitleLanguageTag = "de", subtitlesForcedOnly = false)

        val decision = decide(tracks, profile, remembered)

        assertEquals("ja", decision.audio?.format?.id)
        assertEquals("de", decision.subtitle?.format?.id)
    }

    @Test
    fun `a viewer's pick is remembered by language and kind`() {
        val picked = subtitle("addon-en", "en", forced = true).rememberedSelection(C.TRACK_TYPE_TEXT, null, 10L)
        assertEquals("en", picked.subtitleLanguageTag)
        assertTrue(picked.subtitlesForcedOnly == true)

        val audio = audio("ja", "ja").rememberedSelection(C.TRACK_TYPE_AUDIO, picked, 20L)
        assertEquals("ja", audio.audioLanguageTag)
        assertEquals("en", audio.subtitleLanguageTag)

        val off = audio.withSubtitlesOff(30L)
        assertNull(off.subtitleLanguageTag)
        assertTrue(off.subtitlesForcedOnly == true)
        assertFalse(off.audioLanguageTag.isNullOrEmpty())
    }
}
