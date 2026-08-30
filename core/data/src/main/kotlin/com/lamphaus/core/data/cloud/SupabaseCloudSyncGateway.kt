package com.lamphaus.core.data.cloud

import android.util.Log
import com.lamphaus.core.data.preferences.SyncedSettings
import com.lamphaus.core.model.ArtworkLookupStatus
import com.lamphaus.core.model.ArtworkAsset
import com.lamphaus.core.model.ArtworkCandidates
import com.lamphaus.core.model.ArtworkOverride
import com.lamphaus.core.model.ArtworkProviderId
import com.lamphaus.core.model.ArtworkProviderResult
import com.lamphaus.core.model.ArtworkProviderStatus
import com.lamphaus.core.model.LibraryEntry
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.MediaType
import com.lamphaus.core.model.Profile
import com.lamphaus.core.model.ProfileKind
import com.lamphaus.core.model.ProviderSubscription
import com.lamphaus.core.model.WatchProgress
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.filter.FilterOperation
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.selectAsFlow
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal fun <T> Flow<T>.withSessionRecovery(recovery: SupabaseSessionRecovery): Flow<T> = flow {
    recovery.withAuthRetry {
        collect { emit(it) }
    }
}

/**
 * Cloud sync over Supabase Postgrest + Realtime.
 *
 * Reads use [selectAsFlow]: an initial fetch followed by live re-emission on
 * every postgres change, with channel lifecycle handled by the SDK. Writes are
 * last-writer-wins upserts keyed by [updated_at_epoch_millis] columns while Room
 * Provider configuration travels through catalog-aware Edge Functions because
 * provider_configs has deny-all RLS and never exposes encrypted keys directly.
 */
@OptIn(SupabaseExperimental::class)
class SupabaseCloudSyncGateway(
    private val supabase: SupabaseClient,
    private val sessionRecovery: SupabaseSessionRecovery,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : CloudSyncGateway {

    override fun profiles(userId: String): Flow<List<Profile>> =
        supabase.from(TABLE_PROFILES)
            .selectAsFlow(ProfileRow::id, filter = FilterOperation("user_id", FilterOperator.EQ, userId))
            .withSessionRecovery(sessionRecovery)
            .map { rows -> rows.map { it.toModel() } }
            .recoverWithEmpty()
    override fun library(userId: String, profileId: String): Flow<List<LibraryEntry>> =
        supabase.from(TABLE_LIBRARY)
            .selectAsFlow(
                listOf(LibraryEntryRow::profileId, LibraryEntryRow::mediaKey),
                filter = FilterOperation("profile_id", FilterOperator.EQ, profileId),
            )
            .withSessionRecovery(sessionRecovery)
            .map { rows -> rows.map { it.toModel(json) } }
            .recoverWithEmpty()
    override fun progress(userId: String, profileId: String): Flow<List<WatchProgress>> =
        supabase.from(TABLE_PROGRESS)
            .selectAsFlow(
                listOf(WatchProgressRow::profileId, WatchProgressRow::videoId),
                filter = FilterOperation("profile_id", FilterOperator.EQ, profileId),
            )
            .withSessionRecovery(sessionRecovery)
            .map { rows -> rows.map { it.toModel() } }
            .recoverWithEmpty()
    override suspend fun saveProfile(userId: String, profile: Profile): Result<Unit> = runCatching {
        sessionRecovery.withAuthRetry {
            supabase.from(TABLE_PROFILES)
                .upsert(listOf(ProfileRow.of(userId, profile))) { onConflict = "id" }
        }
    }
    override suspend fun saveLibrary(userId: String, entry: LibraryEntry): Result<Unit> = runCatching {
        sessionRecovery.withAuthRetry {
            supabase.from(TABLE_LIBRARY)
                .upsert(listOf(LibraryEntryRow.of(userId, entry, json))) { onConflict = "profile_id,media_key" }
        }
    }
    override suspend fun saveProgress(userId: String, progress: WatchProgress): Result<Unit> = runCatching {
        sessionRecovery.withAuthRetry {
            supabase.from(TABLE_PROGRESS)
                .upsert(listOf(WatchProgressRow.of(userId, progress))) { onConflict = "profile_id,video_id" }
        }
    }
    override fun settings(userId: String): Flow<SyncedSettings?> =
        supabase.from(TABLE_USER_SETTINGS)
            .selectAsFlow(UserSettingsRow::userId, filter = FilterOperation("user_id", FilterOperator.EQ, userId))
            .withSessionRecovery(sessionRecovery)
            .map { rows -> rows.firstOrNull()?.toModel(json) }
            .recoverWithNull()

    override suspend fun saveSettings(userId: String, settings: SyncedSettings): Result<Unit> = runCatching {
        sessionRecovery.withAuthRetry {
            supabase.from(TABLE_USER_SETTINGS)
                .upsert(listOf(UserSettingsRow.of(userId, settings, json))) { onConflict = "user_id" }
        }
    }


    // ── Provider configs travel through Edge Functions (plan D4/F5): the
    // table is deny-all RLS, so Postgrest/realtime never see it. Pull-based
    // by necessity — deletions converge at the next session start.
    override suspend fun saveProvider(userId: String, provider: ProviderSubscription): Result<Unit> = runCatching {
        sessionRecovery.withAuthRetry {
            supabase.functions.buildEdgeFunction(FUNCTION_SAVE_PROVIDER_CONFIG)
                .invoke(json.encodeToString(ProviderConfigUpsert.of(provider))) {
                    contentType(ContentType.Application.Json)
                }
                .bodyOrThrow()
            Unit
        }
    }

    override suspend fun deleteProvider(userId: String, providerId: String): Result<Unit> = runCatching {
        sessionRecovery.withAuthRetry {
            supabase.functions.buildEdgeFunction(FUNCTION_DELETE_PROVIDER_CONFIG)
                .invoke(json.encodeToString(ProviderConfigDelete(providerId))) {
                    contentType(ContentType.Application.Json)
                }
                .bodyOrThrow()
            Unit
        }
    }

    override suspend fun providers(userId: String): Result<List<ProviderSubscription>> = runCatching {
        sessionRecovery.withAuthRetry {
            val body = supabase.functions.buildEdgeFunction(FUNCTION_LIST_PROVIDER_CONFIGS)
                .invoke("{}") { contentType(ContentType.Application.Json) }
                .bodyOrThrow()
            // The artwork BYOK key rides the same encrypted table; it is not
            // an add-on and must never surface in the sources UI.
            json.decodeFromString<ProviderConfigsResponse>(body).configs
                .filterNot { it.providerId.startsWith("artwork.", ignoreCase = true) }
                .map { it.toModel() }
        }
    }

    // ── Artwork (BYOK metadata provider) ─────────────────────────────────
    override fun artworkOverrides(userId: String, profileId: String): Flow<List<ArtworkOverride>> =
        supabase.from(TABLE_ARTWORK_OVERRIDES)
            .selectAsFlow(
                listOf(ArtworkOverrideRow::profileId, ArtworkOverrideRow::mediaKey),
                filter = FilterOperation("profile_id", FilterOperator.EQ, profileId),
            )
            .withSessionRecovery(sessionRecovery)
            .map { rows -> rows.map { it.toModel() } }
            .recoverWithEmpty()

    override suspend fun saveArtworkOverride(userId: String, override: ArtworkOverride): Result<Unit> = runCatching {
        sessionRecovery.withAuthRetry {
            supabase.from(TABLE_ARTWORK_OVERRIDES)
                .upsert(listOf(ArtworkOverrideRow.of(override))) { onConflict = "profile_id,media_key" }
        }
    }

    override suspend fun deleteArtworkOverride(userId: String, profileId: String, mediaKey: String): Result<Unit> = runCatching {
        sessionRecovery.withAuthRetry {
            supabase.from(TABLE_ARTWORK_OVERRIDES)
                .delete {
                    filter {
                        eq("profile_id", profileId)
                        eq("media_key", mediaKey)
                    }
                }
        }
    }

    override suspend fun artworkProviderStatuses(userId: String): Result<List<ArtworkProviderStatus>> =
        CloudLog.tracedResult("artwork.status") {
            sessionRecovery.withAuthRetry {
                val body = supabase.functions.buildEdgeFunction(FUNCTION_ARTWORK_KEY_STATUS)
                    .invoke(json.encodeToString(ArtworkContractRequest())) { contentType(ContentType.Application.Json) }
                    .bodyOrThrow()
                json.decodeFromString<ArtworkKeyStatusResponse>(body).toModels()
            }
        }

    override suspend fun saveArtworkKey(
        userId: String,
        provider: ArtworkProviderId,
        apiKey: String,
    ): Result<Unit> = runCatching {
        sessionRecovery.withAuthRetry {
            supabase.functions.buildEdgeFunction(FUNCTION_SAVE_ARTWORK_CONFIG)
                .invoke(json.encodeToString(ArtworkKeyUpsert(provider = provider.value, apiKey = apiKey))) {
                    contentType(ContentType.Application.Json)
                }
                .bodyOrThrow()
            Unit
        }
    }

    override suspend fun deleteArtworkKey(userId: String, provider: ArtworkProviderId): Result<Unit> = runCatching {
        sessionRecovery.withAuthRetry {
            supabase.functions.buildEdgeFunction(FUNCTION_DELETE_ARTWORK_CONFIG)
                .invoke(json.encodeToString(ArtworkKeyDelete(provider.value))) {
                    contentType(ContentType.Application.Json)
                }
                .bodyOrThrow()
            Unit
        }
    }

    override suspend fun clearArtworkKeys(userId: String): Result<Unit> =
        CloudLog.tracedResult("artwork.clear") {
            sessionRecovery.withAuthRetry {
                supabase.functions.buildEdgeFunction(FUNCTION_DELETE_ARTWORK_CONFIG)
                    .invoke(json.encodeToString(ArtworkKeysClear())) {
                        contentType(ContentType.Application.Json)
                    }
                    .bodyOrThrow()
                Unit
            }
        }
    override suspend fun artworkCandidates(
        userId: String,
        mediaKey: String,
        name: String,
        releaseYear: Int?,
        mediaType: MediaType,
    ): Result<ArtworkCandidates> = runCatching {
        sessionRecovery.withAuthRetry {
            val body = try {
                supabase.functions.buildEdgeFunction(FUNCTION_RESOLVE_ARTWORK)
                    .invoke(
                        json.encodeToString(
                            ArtworkCandidatesRequest(
                                mediaKey = mediaKey,
                                name = name,
                                releaseYear = releaseYear,
                                mediaType = mediaType,
                            ),
                        ),
                    ) { contentType(ContentType.Application.Json) }
                    .bodyOrThrow()
            } catch (error: SupabaseFunctionException) {
                if (error.responseCode == ARTWORK_KEYS_NOT_CONFIGURED) {
                    throw ArtworkKeysNotConfiguredException()
                }
                throw error
            }
            val decoded = runCatching {
                json.decodeFromString<ArtworkCandidatesResponse>(body).toModel()
            }.getOrElse {
                json.decodeFromString<LegacyArtworkCandidatesResponse>(body).toModel()
            }
            decoded
        }
    }

    /** Edge Functions answer errors as non-2xx JSON; surface status and code. */
    private suspend fun HttpResponse.bodyOrThrow(): String {
        val body = bodyAsText()
        if (!status.isSuccess()) {
            val responseCode = extractFunctionErrorCode(json, body)
            throw SupabaseFunctionException(
                statusCode = status.value,
                responseCode = responseCode,
                message = "edge function returned ${status.value}: ${CloudLog.clamp(CloudLog.sanitize(body))}",
            )
        }
        return body
    }

    // ── row DTOs (snake_case columns ⇄ camelCase models) ─────────────────

    @Serializable
    private data class ProfileRow(
        @SerialName("id") val id: String,
        @SerialName("user_id") val userId: String,
        @SerialName("name") val name: String,
        @SerialName("avatar_key") val avatarKey: String,
        @SerialName("kind") val kind: String,
        @SerialName("has_pin") val hasPin: Boolean,
        @SerialName("hide_unrated") val hideUnrated: Boolean,
        @SerialName("updated_at_epoch_millis") val updatedAtEpochMillis: Long,
    ) {
        fun toModel() = Profile(
            id = id,
            name = name,
            avatarKey = avatarKey,
            kind = ProfileKind.valueOf(kind),
            hasPin = hasPin,
            hideUnrated = hideUnrated,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )

        companion object {
            fun of(userId: String, profile: Profile) = ProfileRow(
                id = profile.id,
                userId = userId,
                name = profile.name,
                avatarKey = profile.avatarKey,
                kind = profile.kind.name,
                hasPin = profile.hasPin,
                hideUnrated = profile.hideUnrated,
                updatedAtEpochMillis = profile.updatedAtEpochMillis,
            )
        }
    }

    @Serializable
    private data class LibraryEntryRow(
        @SerialName("user_id") val userId: String,
        @SerialName("profile_id") val profileId: String,
        @SerialName("media_key") val mediaKey: String,
        @SerialName("preview") val preview: JsonElement,
        @SerialName("added_at_epoch_millis") val addedAtEpochMillis: Long,
        @SerialName("updated_at_epoch_millis") val updatedAtEpochMillis: Long,
    ) {
        fun toModel(json: Json) = LibraryEntry(
            profileId = profileId,
            mediaKey = mediaKey,
            preview = json.decodeFromJsonElement<MediaPreview>(preview),
            addedAtEpochMillis = addedAtEpochMillis,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )

        companion object {
            fun of(userId: String, entry: LibraryEntry, json: Json) = LibraryEntryRow(
                userId = userId,
                profileId = entry.profileId,
                mediaKey = entry.mediaKey,
                preview = json.encodeToJsonElement(entry.preview),
                addedAtEpochMillis = entry.addedAtEpochMillis,
                updatedAtEpochMillis = entry.updatedAtEpochMillis,
            )
        }
    }

    @Serializable
    private data class WatchProgressRow(
        @SerialName("user_id") val userId: String,
        @SerialName("profile_id") val profileId: String,
        @SerialName("media_key") val mediaKey: String,
        @SerialName("video_id") val videoId: String,
        @SerialName("position_millis") val positionMillis: Long,
        @SerialName("duration_millis") val durationMillis: Long,
        @SerialName("completed") val completed: Boolean,
        @SerialName("updated_at_epoch_millis") val updatedAtEpochMillis: Long,
    ) {
        fun toModel() = WatchProgress(
            profileId = profileId,
            mediaKey = mediaKey,
            videoId = videoId,
            positionMillis = positionMillis,
            durationMillis = durationMillis,
            completed = completed,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )

        companion object {
            fun of(userId: String, progress: WatchProgress) = WatchProgressRow(
                userId = userId,
                profileId = progress.profileId,
                mediaKey = progress.mediaKey,
                videoId = progress.videoId,
                positionMillis = progress.positionMillis,
                durationMillis = progress.durationMillis,
                completed = progress.completed,
                updatedAtEpochMillis = progress.updatedAtEpochMillis,
            )
        }
    }

    @Serializable
    private data class UserSettingsRow(
        @SerialName("user_id") val userId: String,
        @SerialName("payload") val payload: JsonElement,
        @SerialName("updated_at_epoch_millis") val updatedAtEpochMillis: Long,
    ) {
        fun toModel(json: Json) =
            json.decodeFromJsonElement<SyncedSettings>(payload).copy(updatedAtEpochMillis = updatedAtEpochMillis)

        companion object {
            /** The column owns LWW ordering; the payload stays timestamp-free. */
            fun of(userId: String, settings: SyncedSettings, json: Json) = UserSettingsRow(
                userId = userId,
                payload = json.encodeToJsonElement(settings.copy(updatedAtEpochMillis = 0)),
                updatedAtEpochMillis = settings.updatedAtEpochMillis,
            )
        }
    }

    companion object {
        private const val TAG = "SupabaseSync"
        private const val TABLE_PROFILES = "profiles"
        private const val TABLE_LIBRARY = "library_entries"
        private const val TABLE_PROGRESS = "watch_progress"
        private const val TABLE_USER_SETTINGS = "user_settings"
        private const val TABLE_ARTWORK_OVERRIDES = "media_artwork_overrides"
        private const val FUNCTION_SAVE_PROVIDER_CONFIG = "save-provider-config"
        private const val FUNCTION_DELETE_PROVIDER_CONFIG = "delete-provider-config"
        private const val FUNCTION_LIST_PROVIDER_CONFIGS = "list-provider-configs"
        private const val FUNCTION_SAVE_ARTWORK_CONFIG = "save-artwork-config"
        private const val FUNCTION_DELETE_ARTWORK_CONFIG = "delete-artwork-config"
        private const val FUNCTION_ARTWORK_KEY_STATUS = "artwork-key-status"
        private const val FUNCTION_RESOLVE_ARTWORK = "resolve-artwork"
        /**
         * Sync failures (network drops, clock skew rejections, schema drift) must
         * degrade to "no cloud rows this round" instead of crashing the app.
         * Consumers only upsert received rows, so an empty emission wipes nothing.
         */
        private fun <T> Flow<List<T>>.recoverWithEmpty(): Flow<List<T>> = catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Sync refresh failed; keeping local data", error)
            emit(emptyList())
        }

        private fun Flow<SyncedSettings?>.recoverWithNull(): Flow<SyncedSettings?> = catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Settings refresh failed; keeping local data", error)
            emit(null)
        }
    }
}

// ── provider-config wire format (contract with supabase/functions/*) ────
// Internal rather than private so unit tests can pin the contract.

/** One decrypted row from `list-provider-configs`. */
@Serializable
internal data class ProviderConfigRow(
    @SerialName("provider_id") val providerId: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("enabled") val enabled: Boolean = true,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("updated_at_epoch_millis") val updatedAtEpochMillis: Long = 0,
    @SerialName("config") val config: JsonObject = JsonObject(emptyMap()),
) {
    fun toModel() = ProviderSubscription(
        id = providerId,
        manifestUrl = config[KEY_MANIFEST_URL]?.jsonPrimitive?.contentOrNull.orEmpty(),
        displayName = displayName.orEmpty(),
        enabled = enabled,
        sortOrder = sortOrder,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

@Serializable
internal data class ProviderConfigsResponse(
    @SerialName("configs") val configs: List<ProviderConfigRow> = emptyList(),
)

/** Body for `save-provider-config`; the function encrypts `config` server-side. */
@Serializable
internal data class ProviderConfigUpsert(
    @SerialName("provider_id") val providerId: String,
    @SerialName("config") val config: JsonObject,
    @SerialName("display_name") val displayName: String,
    @SerialName("enabled") val enabled: Boolean,
    @SerialName("sort_order") val sortOrder: Int,
) {
    companion object {
        fun of(provider: ProviderSubscription) = ProviderConfigUpsert(
            providerId = provider.id,
            config = buildJsonObject { put(KEY_MANIFEST_URL, provider.manifestUrl) },
            displayName = provider.displayName,
            enabled = provider.enabled,
            sortOrder = provider.sortOrder,
        )
    }
}

@Serializable
internal data class ProviderConfigDelete(
    @SerialName("provider_id") val providerId: String,
)

private const val KEY_MANIFEST_URL = "manifest_url"

/** Reserved provider_configs rows holding encrypted artwork BYOK keys. */
private const val ARTWORK_PROVIDER_PREFIX = "artwork."
private const val ARTWORK_KEYS_NOT_CONFIGURED = "artwork_key_not_configured"

@Serializable
internal data class ArtworkContractRequest(
    @EncodeDefault
    @SerialName("contract_version") val contractVersion: Int = 2,
)

@Serializable
internal data class ArtworkKeyUpsert(
    @SerialName("provider") val provider: String,
    @SerialName("api_key") val apiKey: String,
)

@Serializable
internal data class ArtworkKeyDelete(
    @SerialName("provider") val provider: String,
)

@Serializable
internal data class ArtworkKeysClear(
    @EncodeDefault
    @SerialName("all") val all: Boolean = true,
)

@Serializable
internal data class ArtworkCandidatesRequest(
    @EncodeDefault
    @SerialName("contract_version") val contractVersion: Int = 2,
    @SerialName("media_key") val mediaKey: String,
    @SerialName("name") val name: String,
    @SerialName("release_year") val releaseYear: Int? = null,
    @SerialName("media_type") val mediaType: MediaType,
)

@Serializable
internal data class ArtworkCandidatesResponse(
    @SerialName("posters") val posters: List<ArtworkAssetWire> = emptyList(),
    @SerialName("backdrops") val backdrops: List<ArtworkAssetWire> = emptyList(),
    @SerialName("logos") val logos: List<ArtworkAssetWire> = emptyList(),
    @SerialName("provider_results") val providerResults: List<ArtworkProviderResultWire> = emptyList(),
) {
    fun toModel() = ArtworkCandidates(
        posters = posters.mapNotNull(ArtworkAssetWire::toModel),
        backdrops = backdrops.mapNotNull(ArtworkAssetWire::toModel),
        logos = logos.mapNotNull(ArtworkAssetWire::toModel),
        providerResults = providerResults.mapNotNull(ArtworkProviderResultWire::toModel),
    )
}

@Serializable
internal data class LegacyArtworkCandidatesResponse(
    @SerialName("posters") val posters: List<String> = emptyList(),
    @SerialName("backdrops") val backdrops: List<String> = emptyList(),
    @SerialName("logos") val logos: List<String> = emptyList(),
) {
    fun toModel() = ArtworkCandidates(
        posters = posters.map { ArtworkAsset(ArtworkProviderId.TMDB, it) },
        backdrops = backdrops.map { ArtworkAsset(ArtworkProviderId.TMDB, it) },
        logos = logos.map { ArtworkAsset(ArtworkProviderId.TMDB, it) },
    )
}

@Serializable
internal data class ArtworkAssetWire(
    @SerialName("provider") val provider: String,
    @SerialName("reference") val reference: String,
) {
    fun toModel(): ArtworkAsset? =
        ArtworkProviderId.parseOrNull(provider)?.let { ArtworkAsset(provider = it, reference = reference) }
}

@Serializable
internal data class ArtworkProviderResultWire(
    @SerialName("provider") val provider: String,
    @SerialName("status") val status: String,
    @SerialName("display_name") val displayName: String? = null,
) {
    fun toModel(): ArtworkProviderResult? {
        val id = ArtworkProviderId.parseOrNull(provider) ?: return null
        val lookupStatus = when (status) {
            "success" -> ArtworkLookupStatus.SUCCESS
            "no_match" -> ArtworkLookupStatus.NO_MATCH
            "missing_external_id" -> ArtworkLookupStatus.MISSING_EXTERNAL_ID
            "invalid_key" -> ArtworkLookupStatus.INVALID_KEY
            "lookup_failed" -> ArtworkLookupStatus.LOOKUP_FAILED
            else -> return null
        }
        return ArtworkProviderResult(id, lookupStatus, displayName?.takeIf(String::isNotBlank) ?: id.value)
    }
}

@Serializable
internal data class ArtworkKeyStatusResponse(
    @SerialName("contract_version") val contractVersion: Int? = null,
    @SerialName("providers") val providers: List<ArtworkProviderStatusWire> = emptyList(),
    @SerialName("configured") val legacyConfigured: Boolean? = null,
) {
    fun toModels(): List<ArtworkProviderStatus> {
        if (contractVersion == 2) {
            return providers.mapNotNull(ArtworkProviderStatusWire::toModel)
        }
        return listOf(
            ArtworkProviderStatus(ArtworkProviderId.TMDB, configured = providers.any { it.providerId() == "tmdb" && it.configured } || legacyConfigured == true),
            ArtworkProviderStatus(ArtworkProviderId.FANART, configured = providers.any { it.providerId() == "fanart" && it.configured }),
        )
    }
}

@Serializable
internal data class ArtworkProviderStatusWire(
    @SerialName("id") val id: String? = null,
    @SerialName("provider") val legacyProvider: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("purpose") val purpose: String = "",
    @SerialName("help_text") val helpText: String = "",
    @SerialName("key_page_url") val keyPageUrl: String = "",
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("enabled") val enabled: Boolean = true,
    @SerialName("configured") val configured: Boolean = false,
) {
    fun providerId(): String? = id ?: legacyProvider

    fun toModel(): ArtworkProviderStatus? {
        val providerId = providerId() ?: return null
        val parsedId = ArtworkProviderId.parseOrNull(providerId) ?: return null
        return ArtworkProviderStatus(
            parsedId,
            displayName?.takeIf(String::isNotBlank) ?: parsedId.value,
            purpose,
            helpText,
            keyPageUrl,
            sortOrder,
            enabled,
            configured,
        )
    }
}

@Serializable
internal data class ArtworkOverrideRow(
    @SerialName("profile_id") val profileId: String,
    @SerialName("media_key") val mediaKey: String,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("poster_provider") val posterProvider: String = ArtworkProviderId.TMDB.value,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("backdrop_provider") val backdropProvider: String = ArtworkProviderId.TMDB.value,
    @SerialName("logo_path") val logoPath: String? = null,
    @SerialName("logo_provider") val logoProvider: String = ArtworkProviderId.TMDB.value,
    @SerialName("updated_at_epoch_millis") val updatedAtEpochMillis: Long,
) {
    fun toModel() = ArtworkOverride(
        profileId = profileId,
        mediaKey = mediaKey,
        poster = posterPath.toAsset(posterProvider),
        backdrop = backdropPath.toAsset(backdropProvider),
        logo = logoPath.toAsset(logoProvider),
        updatedAtEpochMillis = updatedAtEpochMillis,
    )

    companion object {
        fun of(override: ArtworkOverride) = ArtworkOverrideRow(
            profileId = override.profileId,
            mediaKey = override.mediaKey,
            posterPath = override.poster?.reference,
            posterProvider = override.poster?.provider?.value ?: ArtworkProviderId.TMDB.value,
            backdropPath = override.backdrop?.reference,
            backdropProvider = override.backdrop?.provider?.value ?: ArtworkProviderId.TMDB.value,
            logoPath = override.logo?.reference,
            logoProvider = override.logo?.provider?.value ?: ArtworkProviderId.TMDB.value,
            updatedAtEpochMillis = override.updatedAtEpochMillis,
        )
    }
}

private fun String?.toAsset(provider: String): ArtworkAsset? =
    this?.trim()?.takeIf(String::isNotBlank)?.let { reference ->
        ArtworkProviderId.parseOrNull(provider)?.let { ArtworkAsset(it, reference) }
    }
