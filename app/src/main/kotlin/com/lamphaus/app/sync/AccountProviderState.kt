package com.lamphaus.app.sync

import com.lamphaus.core.data.preferences.UserPreferences
import com.lamphaus.core.model.ArtworkProviderStatus

/**
 * Where this device stands with the account's add-ons and artwork keys.
 * They are encrypted and arrive only through Edge Functions, so the device
 * notes when a pull reported a change and when it last fetched them, and
 * keeps the artwork-key status it last saw for key-backed UI at launch.
 */
interface AccountProviderState {
    suspend fun changedAt(userId: String): Long?

    suspend fun setChangedAt(userId: String, cursor: Long)

    suspend fun fetchedAt(userId: String): Long?

    suspend fun setFetchedAt(userId: String, cursor: Long)

    suspend fun artworkStatuses(userId: String): List<ArtworkProviderStatus>?

    suspend fun setArtworkStatuses(userId: String, statuses: List<ArtworkProviderStatus>)
}

class PreferenceAccountProviderState(private val preferences: UserPreferences) : AccountProviderState {
    override suspend fun changedAt(userId: String): Long? = preferences.providersChangedAt(userId)

    override suspend fun setChangedAt(userId: String, cursor: Long) = preferences.setProvidersChangedAt(userId, cursor)

    override suspend fun fetchedAt(userId: String): Long? = preferences.providersFetchedAt(userId)

    override suspend fun setFetchedAt(userId: String, cursor: Long) = preferences.setProvidersFetchedAt(userId, cursor)

    override suspend fun artworkStatuses(userId: String): List<ArtworkProviderStatus>? =
        preferences.artworkStatuses(userId)

    override suspend fun setArtworkStatuses(userId: String, statuses: List<ArtworkProviderStatus>) =
        preferences.setArtworkStatuses(userId, statuses)
}
