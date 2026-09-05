package com.lamphaus.app.player

import com.lamphaus.core.model.SubtitleCue
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerUiPolicyTest {
    @Test
    fun `TV-FOC-01 sync window centers the cue nearest the captured instant`() {
        val cues = (0 until 20).map { index ->
            SubtitleCue(index * 1_000L, index * 1_000L + 700L, "Line $index")
        }

        assertEquals((7..13).map { "Line $it" }, nearbySubtitleCues(cues, 10_200L).map { it.text })
    }

    @Test
    fun `sync window clamps at the beginning and keeps chronological order`() {
        val cues = (0 until 10).map { index -> SubtitleCue(index * 1_000L, index * 1_000L + 500L, "$index") }

        assertEquals((0..6).map(Int::toString), nearbySubtitleCues(cues, 100L).map { it.text })
    }

    @Test
    fun `signed delay communicates direction without losing zero`() {
        assertEquals("0.0 s", formatSignedDelay(0L))
        assertEquals("+1.3 s", formatSignedDelay(1_250L))
        assertEquals("-0.8 s", formatSignedDelay(-750L))
    }

    @Test
    fun `TV-NAV-05 subtitle rail groups regional variants by base language`() {
        assertEquals("en", normalizedSubtitleLanguageKey("en-US"))
        assertEquals("en", normalizedSubtitleLanguageKey("EN_gb".replace('_', '-')))
        assertEquals("pt", normalizedSubtitleLanguageKey("pt-BR"))
    }

    @Test
    fun `QA-05 subtitle rail keeps missing language tracks reachable`() {
        assertEquals(SUBTITLE_LANGUAGE_UNKNOWN, normalizedSubtitleLanguageKey(null))
        assertEquals(SUBTITLE_LANGUAGE_UNKNOWN, normalizedSubtitleLanguageKey(""))
        assertEquals(SUBTITLE_LANGUAGE_UNKNOWN, normalizedSubtitleLanguageKey("und"))
        assertEquals(
            "Unknown language",
            subtitleLanguageDisplayName(
                languageKey = SUBTITLE_LANGUAGE_UNKNOWN,
                displayLocale = Locale.ENGLISH,
                unknownLabel = "Unknown language",
            ),
        )
    }

    @Test
    fun `TV-TYP-01 subtitle language name respects the display locale`() {
        assertEquals(
            "Anglais",
            subtitleLanguageDisplayName(
                languageKey = "en",
                displayLocale = Locale.FRENCH,
                unknownLabel = "Inconnue",
            ),
        )
    }
}
