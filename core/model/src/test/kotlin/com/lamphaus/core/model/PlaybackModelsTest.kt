package com.lamphaus.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackModelsTest {
    private val first = Episode("series:1:1", "Pilot", season = 1, episode = 1)
    private val second = Episode("series:1:2", "Second", season = 1, episode = 2)

    @Test
    fun `next episode is resolved in season and episode order`() {
        val unordered = listOf(second, first, Episode("series:2:1", "New season", season = 2, episode = 1))

        assertEquals(second, unordered.nextEpisodeAfter(first))
    }

    @Test
    fun `season transition returns the first episode of the next season`() {
        val nextSeason = Episode("series:2:1", "New season", season = 2, episode = 1)

        assertEquals(nextSeason, listOf(first, nextSeason).nextEpisodeAfter(first))
    }

    @Test
    fun `immediate unaired episode is offered without silently skipping ahead`() {
        val future = second.copy(releasedAtEpochMillis = 2_000)
        val later = Episode("series:1:3", "Third", season = 1, episode = 3)

        assertEquals(future, listOf(first, future, later).nextEpisodeAfter(first))
    }

    @Test
    fun `player episode list keeps a bounded window around the current episode`() {
        val episodes = (1..10).map { Episode("series:1:$it", "E$it", season = 1, episode = it) }

        val window = episodes.shuffled().playbackQueueAround(episodes[4], before = 2, after = 3)

        assertEquals(listOf(3, 4, 5, 6, 7, 8), window.map { it.episode })
        assertEquals(episodes, episodes.playbackQueueAround(episodes[0]))
        assertTrue(episodes.playbackQueueAround(Episode("unknown", "Unknown")).isEmpty())
    }

    @Test
    fun `still watching asks after three automatic starts in a row`() {
        var streak = 0
        repeat(3) { streak = AutoPlayPolicy.nextStreak(streak, automatic = true) }

        assertTrue(AutoPlayPolicy.shouldAskStillWatching(streak))
        assertFalse(AutoPlayPolicy.shouldAskStillWatching(2))
        assertEquals(0, AutoPlayPolicy.nextStreak(streak, automatic = false))
    }

    @Test
    fun `missing current episode has no implicit next episode`() {
        assertNull(listOf(first, second).nextEpisodeAfter(Episode("unknown", "Unknown")))
    }

    @Test
    fun `last episode has no next episode`() {
        assertNull(listOf(first, second).nextEpisodeAfter(second))
    }

    @Test
    fun `playback queue is ordered bounded and begins at current episode`() {
        val third = Episode("series:1:3", "Third", season = 1, episode = 3)

        assertEquals(listOf(second, third), listOf(third, first, second).playbackQueueFrom(second, maximumSize = 2))
    }

    @Test
    fun `release state determines aired status and unknown dates count as aired`() {
        val now = 10_000L
        assertTrue(first.hasAired(nowEpochMillis = now))
        assertTrue(first.copy(releasedAtEpochMillis = now).hasAired(nowEpochMillis = now))
        assertFalse(first.copy(releasedAtEpochMillis = now + 1).hasAired(nowEpochMillis = now))
        assertTrue(first.copy(releasedAtEpochMillis = null).hasAired(nowEpochMillis = now))
    }
}
