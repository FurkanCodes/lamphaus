package com.lamphaus.core.data.security

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lamphaus.core.model.ArtworkProviderId
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.artworkKeyDataStore by preferencesDataStore("lamphaus_local_artwork_keys")

/** Device-local artwork credentials. The DataStore value is encrypted by Android Keystore. */
class LocalArtworkKeyStore(
    private val context: Context,
    private val cipher: StringCipher = AndroidKeystoreStringCipher(),
    private val json: Json = Json,
) {
    suspend fun get(userId: String, provider: ArtworkProviderId): String? =
        read()[key(userId, provider)]

    suspend fun has(userId: String, provider: ArtworkProviderId): Boolean =
        get(userId, provider) != null

    suspend fun save(userId: String, provider: ArtworkProviderId, apiKey: String) {
        update { it[key(userId, provider)] = apiKey }
    }

    suspend fun delete(userId: String, provider: ArtworkProviderId) {
        update { it.remove(key(userId, provider)) }
    }

    suspend fun all(userId: String): Map<ArtworkProviderId, String> =
        read()
            .filterKeys { it.startsWith("$userId:") }
            .mapNotNull { (key, value) ->
                ArtworkProviderId.parseOrNull(key.removePrefix("$userId:"))?.let { it to value }
            }
            .toMap()

    suspend fun clearUser(userId: String) {
        update { values -> values.keys.filter { it.startsWith("$userId:") }.forEach(values::remove) }
    }

    private suspend fun read(): MutableMap<String, String> {
        val encrypted = context.artworkKeyDataStore.data.first()[KEY] ?: return mutableMapOf()
        return runCatching {
            json.decodeFromString<Map<String, String>>(cipher.decrypt(encrypted)).toMutableMap()
        }.getOrDefault(mutableMapOf())
    }

    private suspend fun update(change: (MutableMap<String, String>) -> Unit) {
        context.artworkKeyDataStore.edit { preferences ->
            val values = preferences[KEY]?.let {
                runCatching {
                    json.decodeFromString<Map<String, String>>(cipher.decrypt(it)).toMutableMap()
                }.getOrDefault(mutableMapOf())
            } ?: mutableMapOf()
            change(values)
            preferences[KEY] = cipher.encrypt(json.encodeToString(values))
        }
    }

    private fun key(userId: String, provider: ArtworkProviderId): String = "$userId:${provider.value}"

    private companion object {
        val KEY = stringPreferencesKey("encrypted_artwork_keys")
    }
}
