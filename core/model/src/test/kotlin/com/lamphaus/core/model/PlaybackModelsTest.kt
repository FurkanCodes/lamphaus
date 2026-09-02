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
