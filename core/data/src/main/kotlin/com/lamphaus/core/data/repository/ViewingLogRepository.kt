package com.lamphaus.core.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lamphaus.core.model.viewingMonthKey
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.viewingLogStore by preferencesDataStore("viewing_log")

/**
 * Playing time per title per month for each profile, the source of the
 * monthly recap (SHR-PROD-17). Device-local, never synced or logged
 * (SHR-PROD-06), kept for thirteen months, and cleared with the account's
 * local data. Its own small file, so frequent playback writes never rewrite
 * the main preferences.
 */
class ViewingLogRepository(
    private val context: Context,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** One month's playing time per media key, for the active profile. */
    fun getMonthStream(profileId: String, month: YearMonth): Flow<Map<String, Long>> =
        context.viewingLogStore.data.map { decode(it[key(profileId, month.toString())]) }

    suspend fun addWatchTime(profileId: String, mediaKey: String, millis: Long, atEpochMillis: Long) {
        if (millis <= 0) return
        val month = viewingMonthKey(atEpochMillis, zone())
        val oldest = YearMonth.parse(month).minusMonths(RETAINED_MONTHS)
        context.viewingLogStore.edit { values ->
            val entry = key(profileId, month)
            val updated = decode(values[entry]).toMutableMap()
            updated[mediaKey] = (updated[mediaKey] ?: 0L) + millis
            values[entry] = json.encodeToString(MAP_SERIALIZER, updated)
            values.asMap().keys
                .filter { stored ->
                    val storedMonth = stored.name.substringAfterLast(SEPARATOR, "")
                    runCatching { YearMonth.parse(storedMonth) < oldest }.getOrDefault(false)
                }
                .toList()
                .forEach { values.remove(it) }
        }
    }

    /** Leaving an account forgets every profile's viewing (SHR-PROD-06). */
    suspend fun clear() {
        context.viewingLogStore.edit { it.clear() }
    }

    private fun key(profileId: String, month: String) = stringPreferencesKey("$profileId$SEPARATOR$month")

    private fun decode(raw: String?): Map<String, Long> =
        raw?.let { runCatching { json.decodeFromString(MAP_SERIALIZER, it) }.getOrNull() }.orEmpty()

    private companion object {
        const val SEPARATOR = "|"
        const val RETAINED_MONTHS = 13L
        val MAP_SERIALIZER = MapSerializer(String.serializer(), Long.serializer())
    }
}
