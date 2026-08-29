package com.lamphaus.core.data.cloud

import com.lamphaus.core.model.ArtworkAsset
import com.lamphaus.core.model.ArtworkCandidates
import com.lamphaus.core.model.ArtworkProviderId
import com.lamphaus.core.model.ArtworkProviderResult
import com.lamphaus.core.model.ArtworkProviderStatus
import com.lamphaus.core.model.ArtworkLookupStatus
import com.lamphaus.core.model.ArtworkOverride
import com.lamphaus.core.model.MediaType
import com.lamphaus.core.model.ProviderSubscription
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderConfigMappingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `list response maps every field into subscriptions`() {
        val body = """{"configs":[{"provider_id":"com.example.addon@abc123","display_name":"Example Add-on","enabled":false,"sort_order":3,"updated_at_epoch_millis":1720000000000,"config":{"manifest_url":"https://example.com/manifest.json"},"future_field":{}}]}"""
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
        val body = """{"configs":[{"provider_id":"bare","config":{}},{"provider_id":"no-config"}]}"""
        val providers = json.decodeFromString<ProviderConfigsResponse>(body).configs.map { it.toModel() }
        assertEquals(listOf("", ""), providers.map(ProviderSubscription::manifestUrl))
        assertEquals(listOf("", ""), providers.map(ProviderSubscription::displayName))
        assertEquals(listOf(true, true), providers.map(ProviderSubscription::enabled))
    }

    @Test
    fun `upsert request carries manifest url inside encrypted config`() {
        val provider = ProviderSubscription("com.example.addon@abc123", "https://example.com/manifest.json", "Example Add-on", false, 7)
        assertEquals("""{"provider_id":"com.example.addon@abc123","config":{"manifest_url":"https://example.com/manifest.json"},"display_name":"Example Add-on","enabled":false,"sort_order":7}""", json.encodeToString(ProviderConfigUpsert.of(provider)))
    }

    @Test
    fun `v2 status preserves opaque catalog records`() {
        val response = json.decodeFromString<ArtworkKeyStatusResponse>("""{"contract_version":2,"providers":[{"id":"fixture_art","display_name":"Fixture Art","purpose":"Test","help_text":"Help","key_page_url":"https://example.test/key","sort_order":4,"enabled":true,"configured":true}]}""")
        assertEquals(listOf(ArtworkProviderId("fixture_art")), response.toModels().map { it.provider })
        assertEquals("Fixture Art", response.toModels().single().displayName)
    }

    @Test
    fun `legacy artwork status keeps the existing providers visible`() {
        val response = json.decodeFromString<ArtworkKeyStatusResponse>("""{"configured":true,"provider":"tmdb"}""")
        assertEquals(listOf(ArtworkProviderStatus(ArtworkProviderId.TMDB, configured = true), ArtworkProviderStatus(ArtworkProviderId.FANART, configured = false)), response.toModels())
    }

    @Test
    fun `artwork key requests serialize opaque IDs and v2 resolve`() {
        assertEquals("""{"provider":"fixture_art","api_key":"fixture-secret"}""", json.encodeToString(ArtworkKeyUpsert("fixture_art", "fixture-secret")))
        assertEquals("""{"provider":"fixture_art"}""", json.encodeToString(ArtworkKeyDelete("fixture_art")))
        assertEquals("""{"contract_version":2,"media_key":"tmdb:123","name":"Example","release_year":2024,"media_type":"series"}""", json.encodeToString(ArtworkCandidatesRequest(mediaKey = "tmdb:123", name = "Example", releaseYear = 2024, mediaType = MediaType.SERIES)))
    }

    @Test
    fun `artwork response drops only malformed provider records`() {
        val response = json.decodeFromString<ArtworkCandidatesResponse>("""{"posters":[{"provider":"fixture_art","reference":"https://example.test/a"},{"provider":"bad id","reference":"https://example.test/b"}],"provider_results":[{"provider":"fixture_art","status":"success","display_name":"Fixture Art"},{"provider":"bad id","status":"success"}]}""").toModel()
        assertEquals(listOf(ArtworkAsset(ArtworkProviderId("fixture_art"), "https://example.test/a")), response.posters)
        assertEquals(listOf(ArtworkProviderId("fixture_art")), response.providerResults.map(ArtworkProviderResult::provider))
    }

    @Test
    fun `legacy artwork candidates remain TMDB assets`() {
        val response = json.decodeFromString<LegacyArtworkCandidatesResponse>("""{"provider":"tmdb","posters":["/legacy-poster.jpg"],"backdrops":["/legacy-backdrop.jpg"],"logos":["/legacy-logo.png"]}""").toModel()
        assertEquals(ArtworkAsset(ArtworkProviderId.TMDB, "/legacy-poster.jpg"), response.posters.single())
        assertEquals(ArtworkAsset(ArtworkProviderId.TMDB, "/legacy-backdrop.jpg"), response.backdrops.single())
        assertEquals(ArtworkAsset(ArtworkProviderId.TMDB, "/legacy-logo.png"), response.logos.single())
    }

    @Test
    fun `provider qualified override rows round trip and invalid slots stay empty`() {
        val override = ArtworkOverride("profile", "tmdb:123", ArtworkAsset(ArtworkProviderId.TMDB, "/poster.jpg"), ArtworkAsset(ArtworkProviderId.FANART, "https://fanart.example/backdrop.jpg"), ArtworkAsset(ArtworkProviderId.FANART, "https://fanart.example/logo.png"), 42)
        assertEquals(override, ArtworkOverrideRow.of(override).toModel())
        assertEquals(ArtworkAsset(ArtworkProviderId.TMDB, "/legacy.jpg"), ArtworkOverrideRow("profile", "legacy", posterPath = "/legacy.jpg", updatedAtEpochMillis = 7).toModel().poster)
        assertEquals(null, ArtworkOverrideRow("profile", "bad", posterPath = "/bad.jpg", posterProvider = "bad id", updatedAtEpochMillis = 8).toModel().poster)
    }

    @Test
    fun `delete request names only the provider`() {
        assertEquals("""{"provider_id":"com.example.addon@abc123"}""", json.encodeToString(ProviderConfigDelete("com.example.addon@abc123")))
    }
}
