package com.lamphaus.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackSelectionTest {

    private fun audio(
        id: String,
        language: String?,
        channels: Int = 6,
        isDefault: Boolean = false,
        roles: Set<TrackRole> = emptySet(),
    ) = AudioTrackInfo(id = id, languageTag = language, channelCount = channels, isDefault = isDefault, roles = roles)

    private fun subtitle(
        id: String,
        language: String?,
        isDefault: Boolean = false,
        isTextual: Boolean = true,
        roles: Set<TrackRole> = emptySet(),
    ) = SubtitleTrackInfo(id = id, languageTag = language, isDefault = isDefault, isTextual = isTextual, roles = roles)

    private val profile = ProfilePlaybackPreferences(
        audioLanguageTag = "ja",
        preferredSubtitleLanguageTag = "en",
    )

    // ── BCP-47 ───────────────────────────────────────────────────────────

    @Test
    fun `normalizes separators case and region`() {
        assertEquals("en-US", normalizeBcp47Tag(" en_us "))
        assertEquals("zh-Hans-CN", normalizeBcp47Tag("ZH_hans_cn"))
        assertEquals("ja", normalizeBcp47Tag("JA"))
        assertEquals("", normalizeBcp47Tag("und"))
        assertEquals("", normalizeBcp47Tag(null))
        assertEquals("", normalizeBcp47Tag("  "))
        assertEquals("en", normalizeBcp47Tag("en"))
    }

    @Test
    fun `language matches base and exact but not unrelated`() {
        assertTrue(languageMatches("en-US", "en"))
        assertTrue(languageMatches("en", "en-US"))
        assertTrue(languageMatches("EN_us", "en-us"))
        assertFalse(languageMatches("en", "fr"))
        assertFalse(languageMatches(null, "en"))
        assertFalse(languageMatches("en", ""))
    }

    // ── Audio precedence ─────────────────────────────────────────────────

    @Test
    fun `audio session selection wins over everything`() {
        val tracks = listOf(audio("a1", "ja"), audio("a2", "en"))
        val selected = selectAudioTrack(
            tracks = tracks,
            sessionSelectionTrackId = "a2",
            sourceSelectionTrackId = "a1",
            semantic = null,
            profile = profile,
            deviceLanguageTag = "en",
        )
        assertEquals("a2", selected?.id)
    }

    @Test
    fun `audio falls to semantic language then profile then device`() {
        val tracks = listOf(audio("en-track", "en"), audio("de-track", "de"))
        val semantic = selectAudioTrack(tracks, null, null, MediaPlaybackSelection(audioLanguageTag = "de"), profile, "en")
        assertEquals("de-track", semantic?.id)

        val profilePick = selectAudioTrack(tracks, null, null, null, profile, "en")
        assertEquals("en-track", profilePick?.id)
    }

    @Test
    fun `audio original default prefers device language then stream default`() {
        val tracks = listOf(audio("fr", "fr"), audio("default-en", "en", isDefault = true), audio("it", "it"))
        val selected = selectAudioTrack(tracks, null, null, null, ProfilePlaybackPreferences(), "en")
        assertEquals("default-en", selected?.id)
    }

    @Test
    fun `audio avoids commentary tracks within a language`() {
        val tracks = listOf(audio("ja-commentary", "ja", roles = setOf(TrackRole.COMMENTARY)), audio("ja-clean", "ja"))
        val selected = selectAudioTrack(tracks, null, null, null, profile, "en")
        assertEquals("ja-clean", selected?.id)
    }

    @Test
    fun `audio source selection wins over semantic`() {
        val tracks = listOf(audio("a1", "ja"), audio("a2", "en"))
        val selected = selectAudioTrack(
            tracks, null, "a2",
            MediaPlaybackSelection(audioLanguageTag = "ja"), profile, "en",
        )
        assertEquals("a2", selected?.id)
    }

    // ── Subtitle precedence ──────────────────────────────────────────────

    @Test
    fun `forced only picks a forced track in the preferred language`() {
        val tracks = listOf(
            subtitle("en-full", "en"),
            subtitle("en-forced", "en", roles = setOf(TrackRole.FORCED)),
        )
        val selected = selectSubtitleTrack(tracks, null, null, null, profile, "en")
        assertEquals("en-forced", selected)
    }

    @Test
    fun `forced only stays off when no forced track exists`() {
        val tracks = listOf(subtitle("en-full", "en"))
        val selected = selectSubtitleTrack(tracks, null, null, null, profile, "en")
        assertNull(selected)
    }

    @Test
    fun `off mode disables subtitles unless picked this session`() {
        val tracks = listOf(subtitle("en", "en"))
        val offProfile = ProfilePlaybackPreferences(subtitleDefaultMode = SubtitleDefaultMode.OFF)
        assertNull(selectSubtitleTrack(tracks, null, null, null, offProfile, "en"))
        assertEquals("en", selectSubtitleTrack(tracks, "en", null, null, offProfile, "en"))
        assertEquals("en", selectSubtitleTrack(tracks, null, "en", null, offProfile, "en"))
    }

    @Test
    fun `semantic forced override beats preferred-language profile default`() {
        val tracks = listOf(subtitle("en-forced", "en", roles = setOf(TrackRole.FORCED)))
        val selected = selectSubtitleTrack(
            tracks, null, null,
            MediaPlaybackSelection(subtitlesForcedOnly = true),
            ProfilePlaybackPreferences(subtitleDefaultMode = SubtitleDefaultMode.PREFERRED_LANGUAGE),
            "en",
        )
        assertEquals("en-forced", selected)
    }

    @Test
    fun `preferred language prefers plain over sdh`() {
        val tracks = listOf(
            subtitle("en-sdh", "en", roles = setOf(TrackRole.SDH)),
            subtitle("en-plain", "en"),
        )
        val selected = selectSubtitleTrack(
            tracks, null, null, null,
            ProfilePlaybackPreferences(subtitleDefaultMode = SubtitleDefaultMode.PREFERRED_LANGUAGE),
            "en",
        )
        assertEquals("en-plain", selected)
    }

    @Test
    fun `session selection overrides semantic language`() {
        val tracks = listOf(subtitle("ja", "ja"), subtitle("en", "en"))
        val selected = selectSubtitleTrack(
            tracks, "ja", null,
            MediaPlaybackSelection(subtitleLanguageTag = "en"),
            ProfilePlaybackPreferences(subtitleDefaultMode = SubtitleDefaultMode.PREFERRED_LANGUAGE),
            "en",
        )
        assertEquals("ja", selected)
    }

    // ── Role detection ───────────────────────────────────────────────────

    @Test
    fun `labels detect forced sdh commentary and description roles`() {
        assertEquals(setOf(TrackRole.FORCED), subtitleRoles("Forced"))
        assertEquals(setOf(TrackRole.SDH), subtitleRoles("English SDH"))
        assertEquals(setOf(TrackRole.SDH), subtitleRoles("English (CC)"))
        assertEquals(setOf(TrackRole.COMMENTARY), subtitleRoles("Director's commentary"))
        assertEquals(setOf(TrackRole.AUDIO_DESCRIPTION), subtitleRoles("Audio Description"))
        assertTrue(subtitleRoles(null, isForcedFlag = true).contains(TrackRole.FORCED))
        assertTrue(subtitleRoles("Plain").isEmpty())
    }

    // ── Delays ───────────────────────────────────────────────────────────

    @Test
    fun `subtitle delay clamps and steps in 100ms within 180s`() {
        assertEquals(180_000L, clampSubtitleDelayMillis(999_999))
        assertEquals(-180_000L, clampSubtitleDelayMillis(-999_999))
        assertEquals(180_000L, stepSubtitleDelay(180_000L, 1))
        assertEquals(-180_000L, stepSubtitleDelay(-180_000L, -5))
        assertEquals(300L, stepSubtitleDelay(200L, 1))
    }

    @Test
    fun `audio delay clamps and steps in 25ms within 3s`() {
        assertEquals(3_000L, clampAudioDelayMillis(9_999))
        assertEquals(-3_000L, clampAudioDelayMillis(-9_999))
        assertEquals(3_000L, stepAudioDelay(3_000L, 1))
        assertEquals(-3_000L, stepAudioDelay(-3_000L, -1))
        assertEquals(75L, stepAudioDelay(50L, 1))
    }

    @Test
    fun `sync by line subtracts the 300ms perception lead`() {
        assertEquals(1_700L, syncByLineDelayMillis(capturedVideoTimeMillis = 5_000L, selectedCueStartMillis = 3_000L))
        assertEquals(-180_000L, syncByLineDelayMillis(1_000L, 500_000L))
    }

    // ── Display mode selection ───────────────────────────────────────────

    private val modes = listOf(
        DisplayModeCandidate(3840, 2160, 60f),
        DisplayModeCandidate(3840, 2160, 23.976f),
        DisplayModeCandidate(1920, 1080, 60f),
        DisplayModeCandidate(1920, 1080, 23.976f),
        DisplayModeCandidate(1280, 720, 60f),
    )

    @Test
    fun `prefers exact resolution and refresh`() {
        val current = DisplayModeCandidate(3840, 2160, 60f)
        val mode = selectDisplayMode(current, 1920, 1080, 23.976f, modes)
        assertEquals(DisplayModeCandidate(1920, 1080, 23.976f), mode)
    }

    @Test
    fun `distinguishes 23976 from 24`() {
        val with24 = modes + DisplayModeCandidate(1920, 1080, 24f)
        val mode = selectDisplayMode(DisplayModeCandidate(3840, 2160, 60f), 1920, 1080, 23.976f, with24)
        assertEquals(23.976f, mode?.refreshRateHz)
        val mode24 = selectDisplayMode(DisplayModeCandidate(3840, 2160, 60f), 1920, 1080, 24f, with24)
        assertEquals(24f, mode24?.refreshRateHz)
    }

    @Test
    fun `falls back to exact refresh at another resolution when source resolution is absent`() {
        val only = listOf(DisplayModeCandidate(1920, 1080, 60f), DisplayModeCandidate(1920, 1080, 23.976f))
        val mode = selectDisplayMode(DisplayModeCandidate(1920, 1080, 60f), 3840, 2160, 23.976f, only)
        assertEquals(23.976f, mode?.refreshRateHz)
    }

    @Test
    fun `keeps current mode when it already matches the source`() {
        val current = DisplayModeCandidate(1920, 1080, 60f)
        assertNull(selectDisplayMode(current, 1920, 1080, 60f, modes))
        // A resolution change is still honored when the mode differs.
        assertEquals(DisplayModeCandidate(3840, 2160, 60f), selectDisplayMode(current, 3840, 2160, 60f, modes))
    }

    @Test
    fun `ignores empty mode lists and invalid video`() {
        assertNull(selectDisplayMode(modes.first(), 1920, 1080, 24f, emptyList()))
        assertNull(selectDisplayMode(modes.first(), 0, 1080, 24f, modes))
    }
}
