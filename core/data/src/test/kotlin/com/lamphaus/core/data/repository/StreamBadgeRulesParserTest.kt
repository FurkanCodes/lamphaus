package com.lamphaus.core.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamBadgeRulesParserTest {
    @Test
    fun `parses nuvio fusion badge json and drops unusable filters`() {
        val import = StreamBadgeRulesParser.parse(
            sourceUrl = "https://example.com/badges.json",
            payload = """
                {
                  "groups": [{"id": "gq", "name": "Quality", "color": "#FF27C04F", "isExpanded": true}],
                  "filters": [
                    {"id": "q-r", "groupId": "gq", "name": "Remux", "pattern": "(?i)\\bremux\\b",
                     "imageURL": "https://img.example/remux.png", "isEnabled": true, "tagColor": "#E600E932",
                     "tagStyle": "filled", "textColor": "#27C04F", "borderColor": "#FF00FF37", "type": "filter"},
                    {"id": "off", "name": "Off", "pattern": "x", "isEnabled": false},
                    {"id": "no-name", "pattern": "x"},
                    {"id": "broken", "name": "Broken", "pattern": "(unclosed"},
                    {"id": "http", "name": "Plain", "pattern": "y", "imageURL": "http://img.example/y.png"}
                  ]
                }
            """.trimIndent(),
        )

        assertEquals(listOf("Remux", "Off", "Plain"), import.filters.map { it.name })
        assertEquals("filled", import.filters[0].tagStyle)
        assertEquals(2, import.enabledFilterCount)
        // Badge art must be HTTPS; a plain-HTTP image is dropped, not the rule.
        assertEquals("", import.filters[2].imageURL)
    }

    @Test
    fun `rejects files without usable badges`() {
        val error = runCatching { StreamBadgeRulesParser.parse("https://e/x.json", """{"filters": []}""") }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        val notJson = runCatching { StreamBadgeRulesParser.parse("https://e/x.json", "<html>") }.exceptionOrNull()
        assertTrue(notJson is IllegalArgumentException)
    }
}
