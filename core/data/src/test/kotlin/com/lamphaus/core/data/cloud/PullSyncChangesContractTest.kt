package com.lamphaus.core.data.cloud

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the app to pull_sync_changes' response (supabase/migrations/20261010120000_push_to_pull_sync.sql). */
class PullSyncChangesContractTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `SHR-ARC-13 an incremental pull decodes rows, settings, overrides and removals`() {
        val changes = SupabaseCloudSyncGateway.decodeCloudChanges(json, INCREMENTAL)

        assertEquals(10L, changes.cursor)
        assertFalse(changes.full)
        assertEquals(1, changes.profileCount)
        assertTrue(changes.hasSettings)
        assertEquals(listOf("Home"), changes.profiles.map { it.name })
        assertEquals(listOf("movie:tt2"), changes.library.map { it.mediaKey })
        assertEquals("Two again", changes.library.single().preview.name)
        assertEquals(9000L, changes.progress.single().positionMillis)
        assertEquals("S1 E2", changes.progress.single().episodeLabel)
        assertEquals(1234L, changes.settings?.updatedAtEpochMillis)
        assertEquals("/p.jpg", changes.artworkOverrides.single().poster?.reference)
        assertFalse(changes.artworkOverridesComplete)
        assertEquals(
            listOf(
                CloudDeletion(CloudCollection.LIBRARY, PROFILE, "movie:tt2"),
                CloudDeletion(CloudCollection.PROGRESS, PROFILE, "tt1"),
                CloudDeletion(CloudCollection.ARTWORK_OVERRIDE, PROFILE, "movie:tt1"),
            ),
            changes.deletions,
        )
    }

    @Test
    fun `SHR-ARC-13 an empty account pull is full and has nothing to seed from`() {
        val changes = SupabaseCloudSyncGateway.decodeCloudChanges(
            json,
            """{"cursor":0,"full":true,"profile_count":0,"has_settings":false,"profiles":[],"library":[],
               "progress":[],"settings":null,"overrides_complete":true,"overrides":[],"deleted":[]}""",
        )

        assertTrue(changes.full)
        assertEquals(0, changes.profileCount)
        assertFalse(changes.hasSettings)
        assertNull(changes.settings)
        assertTrue(changes.artworkOverridesComplete)
    }

    @Test(expected = IllegalStateException::class)
    fun `SHR-ARC-13 a signed-out pull is a failure, never an empty account`() {
        SupabaseCloudSyncGateway.decodeCloudChanges(json, "null")
    }

    private companion object {
        const val PROFILE = "aaaaaaaa-0000-0000-0000-000000000001"
        const val USER = "11111111-1111-1111-1111-111111111111"
        val INCREMENTAL = """
            {
              "cursor": 10, "full": false, "profile_count": 1, "has_settings": true,
              "profiles": [{"id": "$PROFILE", "user_id": "$USER", "name": "Home", "avatar_key": "a", "kind": "ADULT",
                            "has_pin": false, "hide_unrated": false, "updated_at_epoch_millis": 3}],
              "library": [{"user_id": "$USER", "profile_id": "$PROFILE", "media_key": "movie:tt2",
                           "preview": {"id": "tt2", "type": "movie", "rawType": "movie", "name": "Two again"},
                           "added_at_epoch_millis": 4, "updated_at_epoch_millis": 4}],
              "progress": [{"user_id": "$USER", "profile_id": "$PROFILE", "media_key": "series:tt9", "video_id": "tt9:1:2",
                            "position_millis": 9000, "duration_millis": 20000, "completed": false,
                            "updated_at_epoch_millis": 9, "preview": null, "episode_label": "S1 E2"}],
              "settings": {"user_id": "$USER", "payload": {}, "updated_at_epoch_millis": 1234},
              "overrides_complete": false,
              "overrides": [{"profile_id": "$PROFILE", "media_key": "movie:tt1", "poster_path": "/p.jpg",
                             "poster_provider": "tmdb", "backdrop_path": null, "logo_path": null,
                             "updated_at_epoch_millis": 5}],
              "deleted": [
                {"c": "library", "p": "$PROFILE", "k": "movie:tt2"},
                {"c": "progress", "p": "$PROFILE", "k": "tt1"},
                {"c": "artwork_override", "p": "$PROFILE", "k": "movie:tt1"},
                {"c": "something_newer", "p": "$PROFILE", "k": "ignored"}
              ]
            }
        """.trimIndent()
    }
}
