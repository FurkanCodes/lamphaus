package com.lamphaus.core.player.mpv

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import com.lamphaus.core.player.EngineHandoffState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Plan §1/§3: Media3 overrides and preferences reach mpv, and a hand-off keeps the viewer's tracks. */
class MpvTrackMappingTest {
    private fun group(type: String, mpvId: String, mime: String) =
        TrackGroup(Format.Builder().setSampleMimeType(mime).build())
            .copyWithId(MpvTrackMapping.groupId(type, mpvId))

    @Test
    fun `overrides select mpv ids and preferences become language lists`() {
        val parameters = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT.buildUpon()
            .setOverrideForType(TrackSelectionOverride(group("audio", "2", MimeTypes.AUDIO_AC3), 0))
            .setOverrideForType(TrackSelectionOverride(group("sub", "5", MimeTypes.APPLICATION_SUBRIP), 0))
            .setPreferredAudioLanguages("ja", "en")
            .setPreferredTextLanguages("en")
            .build()

        val plan = MpvTrackMapping.plan(parameters)

        assertEquals("2", plan.aid)
        assertEquals("5", plan.sid)
        assertEquals("ja,en", plan.alang)
        assertEquals("en", plan.slang)
    }

    @Test
    fun `disabled text turns mpv subtitles off`() {
        val parameters = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()

        val plan = MpvTrackMapping.plan(parameters)

        assertNull(plan.aid)
        assertEquals("no", plan.sid)
    }

    @Test
    fun `codecs map to the formats the panels name`() {
        assertEquals(MimeTypes.APPLICATION_SUBRIP, MpvTrackMapping.mimeType("sub", "subrip"))
        assertEquals(MimeTypes.APPLICATION_PGS, MpvTrackMapping.mimeType("sub", "hdmv_pgs_subtitle"))
        assertEquals(MimeTypes.TEXT_SSA, MpvTrackMapping.mimeType("sub", "ass"))
        assertEquals(MimeTypes.AUDIO_TRUEHD, MpvTrackMapping.mimeType("audio", "truehd"))
        assertEquals(MimeTypes.AUDIO_E_AC3, MpvTrackMapping.mimeType("audio", "eac3"))
    }

    @Test
    fun `add-on subtitles keep their id and flags carry over`() {
        val external = MpvTrackEntry(
            type = "sub",
            id = "7",
            external = true,
            externalFilename = "https://subs.example/en.srt",
            forced = true,
            default = true,
        )
        val ids = mapOf("https://subs.example/en.srt" to "opensubtitles-123")

        assertEquals("opensubtitles-123", MpvTrackMapping.formatId(external, ids))
        assertEquals("sub_3", MpvTrackMapping.formatId(MpvTrackEntry(type = "sub", id = "3"), ids))
        assertEquals(C.SELECTION_FLAG_FORCED or C.SELECTION_FLAG_DEFAULT, MpvTrackMapping.selectionFlags(external))
    }

    @Test
    fun `a hand-off restores the add-on subtitle and the audio language`() {
        val entries = listOf(
            MpvTrackEntry(type = "audio", id = "1", language = "eng"),
            MpvTrackEntry(type = "audio", id = "2", language = "jpn"),
            MpvTrackEntry(type = "sub", id = "1", language = "eng"),
            MpvTrackEntry(type = "sub", id = "2", language = "eng", external = true, externalFilename = "https://subs/en.srt"),
        )
        val state = EngineHandoffState(
            audioLanguage = "ja",
            // Media3's merged-source prefix: the add-on's own id follows it.
            subtitleTrackId = "1:addon-en",
            subtitleLanguage = "en",
        )

        val (aid, sid) = MpvTrackMapping.restoreSelection(entries, state, mapOf("https://subs/en.srt" to "addon-en"))

        assertEquals("2", aid)
        assertEquals("2", sid)
    }

    @Test
    fun `a hand-off keeps subtitles off`() {
        val entries = listOf(MpvTrackEntry(type = "sub", id = "1", language = "en"))

        val (_, sid) = MpvTrackMapping.restoreSelection(entries, EngineHandoffState(subtitlesOff = true), emptyMap())

        assertEquals("no", sid)
    }
}
