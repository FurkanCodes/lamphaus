package com.lamphaus.core.data.preferences

import com.lamphaus.core.model.DiagnosticsConsent
import com.lamphaus.core.model.SpoilerProtectionSettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the `user_settings.payload` jsonb contract: enums travel by name,
 * unknown fields from newer app versions are tolerated, and the timestamp
 * lives in the column, never inside the payload.
 */
class SyncedSettingsSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `defaults round trip`() {
        val settings = SyncedSettings()

        assertEquals(
            settings,
            json.decodeFromString<SyncedSettings>(json.encodeToString(SyncedSettings())),
        )
    }

    @Test
    fun `explicit values round trip`() {
        val settings = SyncedSettings(
            theme = ThemePreference.DARK,
            dynamicColor = false,
            kenBurnsEnabled = false,
            diagnostics = DiagnosticsConsent(crashReports = true, updatedAtEpochMillis = 42),
            spoilerProtection = SpoilerProtectionSettings(
                enabled = false,
                blurEpisodeArtwork = false,
                blurEpisodeSynopsis = true,
            ),
        )

        assertEquals(settings, json.decodeFromString<SyncedSettings>(json.encodeToString(settings)))
    }

    @Test
    fun `older payload defaults spoiler protection to enabled`() {
        val decoded = json.decodeFromString<SyncedSettings>(
            """{"theme":"LIGHT","dynamicColor":false,"kenBurnsEnabled":false}""",
        )

        assertEquals(SpoilerProtectionSettings(), decoded.spoilerProtection)
    }

    @Test
    fun `payload ignores fields written by newer versions`() {
        val decoded = json.decodeFromString<SyncedSettings>(
            """{"theme":"LIGHT","dynamicColor":true,"someFutureFlag":true}""",
        )

        assertEquals(ThemePreference.LIGHT, decoded.theme)
        assertEquals(true, decoded.dynamicColor)
    }
}
