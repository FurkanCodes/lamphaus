package com.lamphaus.core.data.cloud

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.lamphaus.core.model.LibraryEntry
import com.lamphaus.core.model.Profile
import com.lamphaus.core.model.ProviderSubscription
import com.lamphaus.core.model.WatchProgress
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class FirebaseCloudSyncGateway(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : CloudSyncGateway {
    override fun profiles(userId: String): Flow<List<Profile>> = observePayloads(
        "users/$userId/profiles",
    ) { json.decodeFromString<Profile>(it) }

    override fun library(userId: String, profileId: String): Flow<List<LibraryEntry>> = observePayloads(
        "users/$userId/profiles/$profileId/library",
    ) { json.decodeFromString<LibraryEntry>(it) }

    override fun progress(userId: String, profileId: String): Flow<List<WatchProgress>> = observePayloads(
        "users/$userId/profiles/$profileId/progress",
    ) { json.decodeFromString<WatchProgress>(it) }

    override suspend fun saveProfile(userId: String, profile: Profile): Result<Unit> = savePayload(
        "users/$userId/profiles/${profile.id}",
        json.encodeToString(profile),
        profile.updatedAtEpochMillis,
    )

    override suspend fun saveLibrary(userId: String, entry: LibraryEntry): Result<Unit> = savePayload(
        "users/$userId/profiles/${entry.profileId}/library/${entry.mediaKey.safeDocumentId()}",
        json.encodeToString(entry),
        entry.updatedAtEpochMillis,
    )

    override suspend fun saveProgress(userId: String, progress: WatchProgress): Result<Unit> = savePayload(
        "users/$userId/profiles/${progress.profileId}/progress/${progress.videoId.safeDocumentId()}",
        json.encodeToString(progress),
        progress.updatedAtEpochMillis,
    )

    override suspend fun saveProvider(userId: String, provider: ProviderSubscription): Result<Unit> = runCatching {
        functions.getHttpsCallable("saveProviderConfiguration").call(
            mapOf(
                "providerId" to provider.id,
                "manifestUrl" to provider.manifestUrl,
                "displayName" to provider.displayName,
                "enabled" to provider.enabled,
                "sortOrder" to provider.sortOrder,
            ),
        ).await()
        Unit
    }

    override suspend fun providers(userId: String): Result<List<ProviderSubscription>> = runCatching {
        @Suppress("UNCHECKED_CAST")
        val root = functions.getHttpsCallable("listProviderConfigurations").call().await().data as? Map<String, Any?>
            ?: error("Invalid provider response")
        @Suppress("UNCHECKED_CAST")
        val rows = root["providers"] as? List<Map<String, Any?>> ?: emptyList()
        rows.map { row ->
            ProviderSubscription(
                id = row.string("providerId"),
                manifestUrl = row.string("manifestUrl"),
                displayName = row.string("displayName"),
                enabled = row["enabled"] as? Boolean ?: true,
                sortOrder = (row["sortOrder"] as? Number)?.toInt() ?: 0,
                updatedAtEpochMillis = (row["updatedAtEpochMillis"] as? Number)?.toLong() ?: 0,
            )
        }
    }

    private fun <T> observePayloads(path: String, decode: (String) -> T): Flow<List<T>> = callbackFlow {
        val registration = firestore.collection(path).addSnapshotListener { snapshots, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(
                snapshots?.documents.orEmpty().mapNotNull { document ->
                    document.getString("payload")?.let { runCatching { decode(it) }.getOrNull() }
                },
            )
        }
        awaitClose { registration.remove() }
    }

    private suspend fun savePayload(path: String, payload: String, updatedAt: Long): Result<Unit> = runCatching {
        firestore.document(path).set(mapOf("payload" to payload, "updatedAtEpochMillis" to updatedAt)).await()
        Unit
    }

    private fun String.safeDocumentId(): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(toByteArray())
        .joinToString("") { "%02x".format(it) }

    private fun Map<String, Any?>.string(key: String): String = this[key] as? String ?: error("Missing $key")
}

