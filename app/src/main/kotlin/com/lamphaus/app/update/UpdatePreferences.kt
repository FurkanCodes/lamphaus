package com.lamphaus.app.update

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Durable updater state (plan §4, SHR-ARC-05). Android's actual
 * download/session/package state is authoritative during reconciliation;
 * these persisted IDs are hints, not proof of completion.
 *
 * Diagnostics record only version, stage, and error category — never
 * provider credentials, URLs, tokens, or media data (SHR-PROD-06).
 */
class UpdatePreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("lamphaus_updates", Context.MODE_PRIVATE)

    var channel: UpdateChannel
        get() = if (read(KEY_CHANNEL, "beta") { getString(KEY_CHANNEL, "beta") } == "stable") {
            UpdateChannel.STABLE
        } else {
            UpdateChannel.BETA
        }
        set(value) = prefs.edit { putString(KEY_CHANNEL, if (value == UpdateChannel.STABLE) "stable" else "beta") }

    /** Highest accepted feed revision; older revisions are rejected. */
    var acceptedRevision: Int
        get() = read(KEY_REVISION, 0) { getInt(KEY_REVISION, 0) }
        set(value) = prefs.edit { putInt(KEY_REVISION, value) }

    var acceptedPayloadHash: String
        get() = read(KEY_PAYLOAD_HASH, "") { getString(KEY_PAYLOAD_HASH, "") ?: "" }
        set(value) = prefs.edit { putString(KEY_PAYLOAD_HASH, value) }

    /** Deferred versionCode → wall-clock deadline millis. */
    fun reminderDeadline(versionCode: Int): Long =
        read("$KEY_REMINDER$versionCode", 0L) { getLong("$KEY_REMINDER$versionCode", 0L) }

    fun defer(versionCode: Int, deadlineMillis: Long) {
        prefs.edit { putLong("$KEY_REMINDER$versionCode", deadlineMillis) }
    }

    fun clearReminder(versionCode: Int) {
        prefs.edit { remove("$KEY_REMINDER$versionCode") }
    }

    var downloadId: Long
        get() = read(KEY_DOWNLOAD_ID, -1L) { getLong(KEY_DOWNLOAD_ID, -1L) }
        set(value) = prefs.edit { putLong(KEY_DOWNLOAD_ID, value) }

    var installerSessionId: Int
        get() = read(KEY_SESSION_ID, -1) { getInt(KEY_SESSION_ID, -1) }
        set(value) = prefs.edit { putInt(KEY_SESSION_ID, value) }

    var selectedVersionCode: Int
        get() = read(KEY_SELECTED_CODE, -1) { getInt(KEY_SELECTED_CODE, -1) }
        set(value) = prefs.edit { putInt(KEY_SELECTED_CODE, value) }

    var selectedSha256: String
        get() = read(KEY_SELECTED_SHA, "") { getString(KEY_SELECTED_SHA, "") ?: "" }
        set(value) = prefs.edit { putString(KEY_SELECTED_SHA, value) }

    var lastAutoCheckMillis: Long
        get() = read(KEY_LAST_CHECK, 0L) { getLong(KEY_LAST_CHECK, 0L) }
        set(value) = prefs.edit { putLong(KEY_LAST_CHECK, value) }

    var consecutiveFailures: Int
        get() = read(KEY_FAILURES, 0) { getInt(KEY_FAILURES, 0) }
        set(value) = prefs.edit { putInt(KEY_FAILURES, value) }

    var etag: String
        get() = read(KEY_ETAG, "") { getString(KEY_ETAG, "") ?: "" }
        set(value) = prefs.edit { putString(KEY_ETAG, value) }

    var lastModified: String
        get() = read(KEY_LAST_MOD, "") { getString(KEY_LAST_MOD, "") ?: "" }
        set(value) = prefs.edit { putString(KEY_LAST_MOD, value) }

    /** Package replacement preserves this file, including stale or damaged value types. */
    private inline fun <T> read(key: String, default: T, value: SharedPreferences.() -> T): T =
        try {
            prefs.value()
        } catch (_: ClassCastException) {
            prefs.edit { remove(key) }
            default
        }

    private companion object {
        const val KEY_CHANNEL = "channel"
        const val KEY_REVISION = "accepted_revision"
        const val KEY_PAYLOAD_HASH = "accepted_payload_hash"
        const val KEY_REMINDER = "reminder_until_"
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_SESSION_ID = "installer_session_id"
        const val KEY_SELECTED_CODE = "selected_version_code"
        const val KEY_SELECTED_SHA = "selected_sha256"
        const val KEY_LAST_CHECK = "last_auto_check"
        const val KEY_FAILURES = "consecutive_failures"
        const val KEY_ETAG = "feed_etag"
        const val KEY_LAST_MOD = "feed_last_modified"
    }
}
