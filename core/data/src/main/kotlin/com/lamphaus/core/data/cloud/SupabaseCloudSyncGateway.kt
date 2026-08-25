package com.lamphaus.core.data.cloud

import android.util.Log
import com.lamphaus.core.model.LibraryEntry
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.Profile
import com.lamphaus.core.model.ProfileKind
import com.lamphaus.core.model.ProviderSubscription
import com.lamphaus.core.model.WatchProgress
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.filter.FilterOperation
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.selectAsFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

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
            .map { rows -> rows.map { it.toModel() } }
            .recoverWithEmpty()

    override fun library(userId: String, profileId: String): Flow<List<LibraryEntry>> =
        supabase.from(TABLE_LIBRARY)
            .selectAsFlow(
                listOf(LibraryEntryRow::profileId, LibraryEntryRow::mediaKey),
                filter = FilterOperation("profile_id", FilterOperator.EQ, profileId),
            )
            .map { rows -> rows.map { it.toModel(json) } }
            .recoverWithEmpty()

    override fun progress(userId: String, profileId: String): Flow<List<WatchProgress>> =
        supabase.from(TABLE_PROGRESS)
            .selectAsFlow(
                listOf(WatchProgressRow::profileId, WatchProgressRow::videoId),
                filter = FilterOperation("profile_id", FilterOperator.EQ, profileId),
            )
            .map { rows -> rows.map { it.toModel() } }
            .recoverWithEmpty()

    override suspend fun saveProfile(userId: String, profile: Profile): Result<Unit> = runCatching {
        supabase.from(TABLE_PROFILES)
            .upsert(listOf(ProfileRow.of(userId, profile))) { onConflict = "id" }
    }

    override suspend fun saveLibrary(userId: String, entry: LibraryEntry): Result<Unit> = runCatching {
        supabase.from(TABLE_LIBRARY)
            .upsert(listOf(LibraryEntryRow.of(userId, entry, json))) { onConflict = "profile_id,media_key" }
    }

    override suspend fun saveProgress(userId: String, progress: WatchProgress): Result<Unit> = runCatching {
        supabase.from(TABLE_PROGRESS)
            .upsert(listOf(WatchProgressRow.of(userId, progress))) { onConflict = "profile_id,video_id" }
    }

    // ── Provider configs travel through Edge Functions from M5 (deny-all RLS).
    override suspend fun saveProvider(userId: String, provider: ProviderSubscription): Result<Unit> =
        Result.failure(UnsupportedOperationException(PROVIDERS_DEFERRED))

    override suspend fun deleteProvider(userId: String, providerId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException(PROVIDERS_DEFERRED))

    override suspend fun providers(userId: String): Result<List<ProviderSubscription>> =
        Result.failure(UnsupportedOperationException(PROVIDERS_DEFERRED))

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

    companion object {
        private const val TAG = "SupabaseSync"
        private const val TABLE_PROFILES = "profiles"
        private const val TABLE_LIBRARY = "library_entries"
        private const val TABLE_PROGRESS = "watch_progress"
        private const val PROVIDERS_DEFERRED = "Provider configuration moves to Edge Functions in M5."

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
    }
}
