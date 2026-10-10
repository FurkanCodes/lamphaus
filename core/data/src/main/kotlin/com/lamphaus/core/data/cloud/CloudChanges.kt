package com.lamphaus.core.data.cloud

import com.lamphaus.core.data.preferences.SyncedSettings
import com.lamphaus.core.model.ArtworkOverride
import com.lamphaus.core.model.LibraryEntry
import com.lamphaus.core.model.Profile
import com.lamphaus.core.model.WatchProgress

/**
 * One pull from the account (push-to-pull sync): every row the account
 * changed after the device's cursor, or the whole account when [full].
 */
data class CloudChanges(
    /** The account's change number this pull reflects; the next pull starts here. */
    val cursor: Long,
    /** True when this is the whole account: absences mean deletions, and [deletions] is empty. */
    val full: Boolean,
    /** How many profiles the account has in the cloud, changed or not. */
    val profileCount: Int,
    /** Whether the account has a settings row at all, changed or not. */
    val hasSettings: Boolean,
    val profiles: List<Profile>,
    val library: List<LibraryEntry>,
    val progress: List<WatchProgress>,
    /** The settings row when it changed (or on a full pull), else null. */
    val settings: SyncedSettings?,
    val artworkOverrides: List<ArtworkOverride>,
    /** True when [artworkOverrides] lists every override the account has. */
    val artworkOverridesComplete: Boolean,
    val deletions: List<CloudDeletion>,
)

/** A row another device removed; each key lives within one profile. */
data class CloudDeletion(
    val collection: CloudCollection,
    val profileId: String,
    val key: String,
)

enum class CloudCollection(val wireName: String) {
    LIBRARY("library"),
    PROGRESS("progress"),
    ARTWORK_OVERRIDE("artwork_override"),
    ;

    companion object {
        fun fromWire(value: String): CloudCollection? = entries.firstOrNull { it.wireName == value }
    }
}
