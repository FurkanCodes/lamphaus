package com.lamphaus.core.data.cloud

import com.lamphaus.core.model.ProviderSubscription
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the wire contract with the provider-config Edge Functions:
 * response parsing must survive unknown fields, and the upsert body must
 * carry the manifest URL inside the server-encrypted `config` object.
 */
class ProviderConfigMappingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `list response maps every field into subscriptions`() {
        val body = """
            {"configs":[
              {
                "provider_id": "com.example.addon@abc123",
                "display_name": "Example Add-on",
                "enabled": false,
                "sort_order": 3,
                "updated_at_epoch_millis": 1720000000000,
                "config": {"manifest_url": "https://example.com/manifest.json"},
                "future_field": {}
              }
            ]}
        """.trimIndent()

        val providers = json.decodeFromString<ProviderConfigsResponse>(body).configs.map { it.toModel() }

        assertEquals(1, providers.size)
        with(providers.single()) {
            assertEquals("com.example.addon@abc123", id)
            assertEquals("https://example.com/manifest.json", manifestUrl)
            assertEquals("Example Add-on", displayName)
            assertEquals(false, enabled)
            assertEquals(3, sortOrder)
            assertEquals(1720000000000, updatedAtEpochMillis)
        }
    }

    @Test
    fun `rows missing optional fields degrade to safe defaults`() {
        val body = """
            {"configs":[
              {"provider_id": "bare", "config": {}},
              {"provider_id": "no-config"}
            ]}
        """.trimIndent()

        val providers = json.decodeFromString<ProviderConfigsResponse>(body).configs.map { it.toModel() }

        assertEquals(listOf("", ""), providers.map(ProviderSubscription::manifestUrl))
        assertEquals(listOf("", ""), providers.map(ProviderSubscription::displayName))
        assertEquals(listOf(true, true), providers.map(ProviderSubscription::enabled))
    }

    @Test
    fun `upsert request carries manifest url inside the encrypted config`() {
        val provider = ProviderSubscription(
            id = "com.example.addon@abc123",
            manifestUrl = "https://example.com/manifest.json",
            displayName = "Example Add-on",
            enabled = false,
            sortOrder = 7,
        )

        val body = json.encodeToString(ProviderConfigUpsert.of(provider))

        assertEquals(
            """{"provider_id":"com.example.addon@abc123",""" +
                """"config":{"manifest_url":"https://example.com/manifest.json"},""" +
                """"display_name":"Example Add-on","enabled":false,"sort_order":7}""",
            body,
        )
    }

    @Test
    fun `delete request names only the provider`() {
        assertEquals(
            """{"provider_id":"com.example.addon@abc123"}""",
            json.encodeToString(ProviderConfigDelete("com.example.addon@abc123")),
        )
    }
}
