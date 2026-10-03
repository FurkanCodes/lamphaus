package com.lamphaus.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** SHR-PROD-16: new-episode alerts announce only fresh, aired, unwatched episodes, once. */
class NewEpisodeAlertsTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day
    private val lastCheck = now - day / 2

    private fun series(key: String, vararg episodes: Episode) = FollowedSeries(
        MediaPreview(id = key, type = MediaType.SERIES, rawType = "series", name = key),
        episodes.toList(),
    )

    private fun episode(id: String, season: Int?, number: Int?, released: Long?) =
        Episode(id = id, title = id, season = season, episode = number, releasedAtEpochMillis = released)

    private fun select(
        vararg followed: FollowedSeries,
        watched: Set<String> = emptySet(),
        announced: Set<String> = emptySet(),
        lastChecked: Long? = lastCheck,
    ) = selectNewEpisodes(followed.toList(), watched, announced, lastChecked, now)

    @Test
    fun episodeThatAiredSinceLastCheckAlerts() {
        val result = select(series("a", episode("a1", 1, 1, now - 3 * day), episode("a2", 1, 2, now - day / 4)))
        assertEquals("a2", result.alerts.single().episode.id)
        assertTrue("a2" in result.announcedVideoIds)
    }

    @Test
    fun firstCheckOnlyRecordsTheMoment() {
        val result = select(series("a", episode("a2", 1, 2, now - day / 4)), lastChecked = null)
        assertTrue(result.alerts.isEmpty())
    }

    @Test
    fun unairedSpecialsUndatedWatchedAndAnnouncedNeverAlert() {
        val result = select(
            series(
                "a",
                episode("future", 1, 3, now + day),
                episode("special", 0, 1, now - day / 4),
                episode("undated", 1, 4, null),
                episode("watched", 1, 5, now - day / 4),
                episode("told", 1, 6, now - day / 4),
            ),
            watched = setOf("watched"),
            announced = setOf("told"),
        )
        assertTrue(result.alerts.isEmpty())
    }

    @Test
    fun longIdleDeviceDoesNotAnnounceABacklog() {
        val result = select(series("a", episode("old", 1, 1, now - 10 * day)), lastChecked = now - 30 * day)
        assertTrue(result.alerts.isEmpty())
    }

    @Test
    fun seasonDropIsOneAlertForTheEarliestEpisode() {
        val result = select(
            series(
                "a",
                episode("a3", 2, 3, now - day / 4),
                episode("a1", 2, 1, now - day / 4),
                episode("a2", 2, 2, now - day / 4),
            ),
        )
        val alert = result.alerts.single()
        assertEquals("a1", alert.episode.id)
        assertEquals(3, alert.newCount)
        assertEquals(setOf("a1", "a2", "a3"), result.announcedVideoIds)
    }

    @Test
    fun alertsAreCappedButAllAreMarkedAnnounced() {
        val followed = (1..8).map { series("s$it", episode("e$it", 1, 1, now - it * 1000L)) }
        val result = select(*followed.toTypedArray())
        assertEquals(NEW_EPISODE_MAX_ALERTS, result.alerts.size)
        assertEquals("e1", result.alerts.first().episode.id)
        assertEquals(8, result.announcedVideoIds.size)
    }

    @Test
    fun aSeriesFollowedTwiceAlertsOnce() {
        val show = series("a", episode("a2", 1, 2, now - day / 4))
        assertEquals(1, select(show, show).alerts.size)
    }
}
