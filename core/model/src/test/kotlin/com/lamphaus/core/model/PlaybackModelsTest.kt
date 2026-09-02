package com.lamphaus.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackModelsTest {
    private val first = Episode("series:1:1", "Pilot", season = 1, episode = 1)
    private val second = Episode("series:1:2", "Second", season = 1, episode = 2)

    @Test
    fun `next episode is resolved in season and episode order`() {
        val unordered = listOf(second, first, Episode("series:2:1", "New season", season = 2, episode = 1))

        assertEquals(second, unordered.nextEpisodeAfter(first, nowEpochMillis = 100))
    }

    @Test
    fun `unreleased episodes are not offered`() {
        val future = second.copy(releasedAtEpochMillis = 2_000)

        assertNull(listOf(first, future).nextEpisodeAfter(first, nowEpochMillis = 1_000))
    }

    @Test
    fun `missing current episode has no implicit next episode`() {
        assertNull(listOf(first, second).nextEpisodeAfter(Episode("unknown", "Unknown")))
    }

    @Test
    fun `playback queue is ordered bounded and begins at current episode`() {
        val third = Episode("series:1:3", "Third", season = 1, episode = 3)

        assertEquals(listOf(second, third), listOf(third, first, second).playbackQueueFrom(second, maximumSize = 2))
    }
}
