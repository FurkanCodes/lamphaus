package com.lamphaus.core.data.cloud

import android.util.Log
import com.lamphaus.core.data.preferences.SyncedSettings
import com.lamphaus.core.model.LibraryEntry
import com.lamphaus.core.model.MediaPreview
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Cloud sync over Supabase Postgrest + Realtime.
 *
 * Reads use [selectAsFlow]: an initial fetch followed by live re-emission on
 * every postgres change, with channel lifecycle handled by the SDK. Writes are
 * last-writer-wins upserts keyed by [updated_at_epoch_millis] columns while Room
 * remains the on-device source of truth.
 *
 * Provider configuration stays Functions-only (deny-all RLS) until the Edge
 * Functions milestone, so those methods intentionally fail like Local mode.
 */
@OptIn(SupabaseExperimental::class)
class SupabaseCloudSyncGateway(
    private val supabase: SupabaseClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : CloudSyncGateway {

    override fun profiles(userId: String): Flow<List<Profile>> =
        supabase.from(TABLE_PROFILES)
            .selectAsFlow(ProfileRow::id, filter = FilterOperation("user_id", FilterOperator.EQ, userId))
            .retryOnFreshTokenRejection()
            .map { rows -> rows.map { it.toModel() } }
            .recoverWithEmpty()

    override fun library(userId: String, profileId: String): Flow<List<LibraryEntry>> =
        supabase.from(TABLE_LIBRARY)
            .selectAsFlow(
                listOf(LibraryEntryRow::profileId, LibraryEntryRow::mediaKey),
                filter = FilterOperation("profile_id", FilterOperator.EQ, profileId),
            )
            .retryOnFreshTokenRejection()
            .map { rows -> rows.map { it.toModel(json) } }
            .recoverWithEmpty()

    override fun progress(userId: String, profileId: String): Flow<List<WatchProgress>> =
        supabase.from(TABLE_PROGRESS)
            .selectAsFlow(
                listOf(WatchProgressRow::profileId, WatchProgressRow::videoId),
                filter = FilterOperation("profile_id", FilterOperator.EQ, profileId),
            )
            .retryOnFreshTokenRejection()
            .map { rows -> rows.map { it.toModel() } }
            .recoverWithEmpty()

    override suspend fun saveProfile(userId: String, profile: Profile): Result<Unit> = runCatching {
        withFreshTokenRetry {
            supabase.from(TABLE_PROFILES)
                .upsert(listOf(ProfileRow.of(userId, profile))) { onConflict = "id" }
        }
    }

    override suspend fun saveLibrary(userId: String, entry: LibraryEntry): Result<Unit> = runCatching {
        withFreshTokenRetry {
            supabase.from(TABLE_LIBRARY)
                .upsert(listOf(LibraryEntryRow.of(userId, entry, json))) { onConflict = "profile_id,media_key" }
        }
    }

    override suspend fun saveProgress(userId: String, progress: WatchProgress): Result<Unit> = runCatching {
        withFreshTokenRetry {
            supabase.from(TABLE_PROGRESS)
                .upsert(listOf(WatchProgressRow.of(userId, progress))) { onConflict = "profile_id,video_id" }
        }
    }

    override fun settings(userId: String): Flow<SyncedSettings?> =
        supabase.from(TABLE_USER_SETTINGS)
            .selectAsFlow(UserSettingsRow::userId, filter = FilterOperation("user_id", FilterOperator.EQ, userId))
            .retryOnFreshTokenRejection()
            .map { rows -> rows.firstOrNull()?.toModel(json) }
            .recoverWithNull()

    override suspend fun saveSettings(userId: String, settings: SyncedSettings): Result<Unit> = runCatching {
        withFreshTokenRetry {
            supabase.from(TABLE_USER_SETTINGS)
                .upsert(listOf(UserSettingsRow.of(userId, settings, json))) { onConflict = "user_id" }
        }
    }

    // ── fresh-token rejection retry ──────────────────────────────────────
    // During platform incidents (supabase#48123) the Data API's JWT validator
    // clock trails Auth by more than its 30s skew allowance, so tokens minted
    // seconds earlier are rejected with PGRST303 ("JWT issued at future").
    // Only young tokens fail; brief spaced retries heal every affected call
    // while unrelated errors still surface immediately.

    private fun Throwable.isFreshTokenRejection(): Boolean =
        message?.contains("PGRST303", ignoreCase = true) == true

    private suspend fun <T> withFreshTokenRetry(block: suspend () -> T): T {
        var backoffMillis = FRESH_TOKEN_RETRY_BACKOFF_MILLIS
        repeat(FRESH_TOKEN_RETRY_ATTEMPTS - 1) {
            try {
                return block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (!error.isFreshTokenRejection()) throw error
                delay(backoffMillis)
                backoffMillis *= 2
            }
        }
        return block()
    }

    private fun <T> Flow<T>.retryOnFreshTokenRejection(): Flow<T> = retryWhen { cause, attempt ->
        val willRetry = cause.isFreshTokenRejection() && attempt < FRESH_TOKEN_RETRY_ATTEMPTS - 1L
        if (willRetry) delay(FRESH_TOKEN_RETRY_BACKOFF_MILLIS * (attempt + 1))
        willRetry
    }

    // ── Provider configs travel through Edge Functions (plan D4/F5): the
    // table is deny-all RLS, so Postgrest/realtime never see it. Pull-based
    // by necessity — deletions converge at the next session start.
    override suspend fun saveProvider(userId: String, provider: ProviderSubscription): Result<Unit> = runCatching {
        withFreshTokenRetry {
            supabase.functions.buildEdgeFunction(FUNCTION_SAVE_PROVIDER_CONFIG)
                .invoke(json.encodeToString(ProviderConfigUpsert.of(provider))) {
                    contentType(ContentType.Application.Json)
                }
                .bodyOrThrow()
            Unit
        }
    }

    override suspend fun deleteProvider(userId: String, providerId: String): Result<Unit> = runCatching {
        withFreshTokenRetry {
            supabase.functions.buildEdgeFunction(FUNCTION_DELETE_PROVIDER_CONFIG)
                .invoke(json.encodeToString(ProviderConfigDelete(providerId))) {
                    contentType(ContentType.Application.Json)
                }
                .bodyOrThrow()
            Unit
        }
    }

    override suspend fun providers(userId: String): Result<List<ProviderSubscription>> = runCatching {
        withFreshTokenRetry {
            val body = supabase.functions.buildEdgeFunction(FUNCTION_LIST_PROVIDER_CONFIGS)
                .invoke("{}") { contentType(ContentType.Application.Json) }
                .bodyOrThrow()
            json.decodeFromString<ProviderConfigsResponse>(body).configs.map { it.toModel() }
        }
    }

    /** Edge Functions answer errors as non-2xx JSON; surface them loudly. */
    private suspend fun HttpResponse.bodyOrThrow(): String {
        val body = bodyAsText()
        if (!status.isSuccess()) error("edge function returned ${status.value}: ${CloudLog.clamp(CloudLog.sanitize(body))}")
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
        private const val FRESH_TOKEN_RETRY_ATTEMPTS = 3
        private const val FRESH_TOKEN_RETRY_BACKOFF_MILLIS = 2_000L
        private const val TABLE_PROFILES = "profiles"
        private const val TABLE_LIBRARY = "library_entries"
        private const val TABLE_PROGRESS = "watch_progress"
        private const val TABLE_USER_SETTINGS = "user_settings"
        private const val FUNCTION_SAVE_PROVIDER_CONFIG = "save-provider-config"
        private const val FUNCTION_DELETE_PROVIDER_CONFIG = "delete-provider-config"
        private const val FUNCTION_LIST_PROVIDER_CONFIGS = "list-provider-configs"

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
