package com.lamphaus.core.data.cloud

import com.lamphaus.core.model.ArtworkAsset
import com.lamphaus.core.model.ArtworkCandidates
import com.lamphaus.core.model.ArtworkProvider
import com.lamphaus.core.model.ArtworkProviderResult
import com.lamphaus.core.model.ArtworkProviderStatus
import com.lamphaus.core.model.ArtworkLookupStatus
import com.lamphaus.core.model.ArtworkOverride
import com.lamphaus.core.model.MediaType
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
    fun `artwork status preserves both provider records`() {
        val response = json.decodeFromString<ArtworkKeyStatusResponse>(
            """{"providers":[{"provider":"tmdb","configured":true},{"provider":"fanart","configured":false}]}""",
        )

        assertEquals(
            listOf(ArtworkProvider.TMDB, ArtworkProvider.FANART),
            response.providers.map { it.provider },
        )
        assertEquals(listOf(true, false), response.providers.map { it.configured })
    }

    @Test
    fun `legacy artwork status keeps the existing TMDB key visible`() {
        val response = json.decodeFromString<ArtworkKeyStatusResponse>(
            """{"configured":true,"provider":"tmdb"}""",
        )

        assertEquals(
            listOf(
                ArtworkProviderStatus(ArtworkProvider.TMDB, configured = true),
                ArtworkProviderStatus(ArtworkProvider.FANART, configured = false),
            ),
            response.toModels(),
        )
    }

    @Test
    fun `artwork key requests are provider scoped`() {
        assertEquals(
            """{"provider":"fanart","api_key":"fanart-secret"}""",
            json.encodeToString(ArtworkKeyUpsert(ArtworkProvider.FANART, "fanart-secret")),
        )
        assertEquals(
            """{"provider":"tmdb"}""",
            json.encodeToString(ArtworkKeyDelete(ArtworkProvider.TMDB)),
        )
    }

    @Test
    fun `artwork request carries media type and combined response maps assets`() {
        assertEquals(
            """{"media_key":"tmdb:123","name":"Example","release_year":2024,"media_type":"series"}""",
            json.encodeToString(
                ArtworkCandidatesRequest(
                    mediaKey = "tmdb:123",
                    name = "Example",
                    releaseYear = 2024,
                    mediaType = MediaType.SERIES,
                ),
            ),
        )
        val response = json.decodeFromString<ArtworkCandidatesResponse>(
            """
            {
              "posters":[{"provider":"tmdb","reference":"/tmdb.jpg"},{"provider":"fanart","reference":"https://fanart.example/poster.jpg"}],
              "backdrops":[{"provider":"fanart","reference":"https://fanart.example/background.jpg"}],
              "logos":[{"provider":"tmdb","reference":"/tmdb-logo.png"}],
              "provider_results":[
                {"provider":"tmdb","status":"success"},
                {"provider":"fanart","status":"lookup_failed"}
              ]
            }
            """.trimIndent(),
        ).toModel()

        assertEquals(
            listOf(
                ArtworkAsset(ArtworkProvider.TMDB, "/tmdb.jpg"),
                ArtworkAsset(ArtworkProvider.FANART, "https://fanart.example/poster.jpg"),
            ),
            response.posters,
        )
        assertEquals(ArtworkProvider.FANART, response.backdrops.single().provider)
        assertEquals(ArtworkProvider.TMDB, response.logos.single().provider)
        assertEquals(
            listOf(ArtworkLookupStatus.SUCCESS, ArtworkLookupStatus.LOOKUP_FAILED),
            response.providerResults.map(ArtworkProviderResult::status),
        )
    }

    @Test
    fun `legacy artwork candidates remain TMDB assets`() {
        val response = json.decodeFromString<LegacyArtworkCandidatesResponse>(
            """{"provider":"tmdb","posters":["/legacy-poster.jpg"],"backdrops":["/legacy-backdrop.jpg"],"logos":["/legacy-logo.png"]}""",
        ).toModel()

        assertEquals(ArtworkAsset(ArtworkProvider.TMDB, "/legacy-poster.jpg"), response.posters.single())
        assertEquals(ArtworkAsset(ArtworkProvider.TMDB, "/legacy-backdrop.jpg"), response.backdrops.single())
        assertEquals(ArtworkAsset(ArtworkProvider.TMDB, "/legacy-logo.png"), response.logos.single())
    }

    @Test
    fun `provider qualified override rows round trip and legacy rows default to TMDB`() {
        val override = ArtworkOverride(
            profileId = "profile",
            mediaKey = "tmdb:123",
            poster = ArtworkAsset(ArtworkProvider.TMDB, "/poster.jpg"),
            backdrop = ArtworkAsset(ArtworkProvider.FANART, "https://fanart.example/backdrop.jpg"),
            logo = ArtworkAsset(ArtworkProvider.FANART, "https://fanart.example/logo.png"),
            updatedAtEpochMillis = 42,
        )

        assertEquals(override, ArtworkOverrideRow.of(override).toModel())
        assertEquals(
            ArtworkOverride(
                profileId = "profile",
                mediaKey = "legacy",
                poster = ArtworkAsset(ArtworkProvider.TMDB, "/legacy.jpg"),
                updatedAtEpochMillis = 7,
            ),
            ArtworkOverrideRow(
                profileId = "profile",
                mediaKey = "legacy",
                posterPath = "/legacy.jpg",
                updatedAtEpochMillis = 7,
            ).toModel(),
        )
        assertEquals(
            null,
            ArtworkOverrideRow(
                profileId = "profile",
                mediaKey = "blank",
                posterPath = " ",
                posterProvider = ArtworkProvider.FANART,
                updatedAtEpochMillis = 8,
            ).toModel().poster,
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
