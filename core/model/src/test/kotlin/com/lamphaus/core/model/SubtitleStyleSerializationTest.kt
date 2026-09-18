package com.lamphaus.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** QA-01: profile subtitle styling remains compatible across app versions. */
class SubtitleStyleSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `older style payload defaults to the system font`() {
        val decoded = json.decodeFromString<SubtitleStyle>(
            """{"sizePercent":115,"preserveEmbeddedStyles":false}""",
        )

        assertEquals(115, decoded.sizePercent)
        assertEquals(SubtitleFontFamily.SYSTEM, decoded.fontFamily)
    }

    @Test
    fun `custom font family round trips`() {
        val style = SubtitleStyle(
            fontFamily = SubtitleFontFamily.MONOSPACE,
            preserveEmbeddedStyles = false,
        )

        assertEquals(style, json.decodeFromString<SubtitleStyle>(json.encodeToString(style)))
    }
}
