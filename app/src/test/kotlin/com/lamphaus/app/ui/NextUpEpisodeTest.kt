package com.lamphaus.app.ui

import com.lamphaus.core.model.Episode
import com.lamphaus.core.model.WatchProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextUpEpisodeTest {
    private val special = Episode("s0e1", "Special", season = 0, episode = 1)
    private val e11 = Episode("s1e1", "One", season = 1, episode = 1)
    private val e12 = Episode("s1e2", "Two", season = 1, episode = 2)
    private val e21 = Episode("s2e1", "Three", season = 2, episode = 1)
    private val unaired = Episode("s2e2", "Four", season = 2, episode = 2, releasedAtEpochMillis = 2_000L)
    private val episodes = listOf(e21, special, e12, e11, unaired)

    @Test
    fun `nothing watched starts at the first aired regular episode`() {
        val next = nextUpEpisode(episodes, emptyList(), emptySet(), nowEpochMillis = 1_000L)!!

        assertEquals(e11, next.episode)
        assertEquals(NextUpKind.START, next.kind)
    }

    @Test
    fun `a partly watched episode is resumed`() {
        val rows = listOf(
            progress("s1e1", completed = true, updatedAt = 10),
            progress("s2e1", position = 600_000, updatedAt = 20),
        )

        val next = nextUpEpisode(episodes, rows, setOf("s1e1"), nowEpochMillis = 1_000L)!!

        assertEquals(e21, next.episode)
        assertEquals(NextUpKind.RESUME, next.kind)
        assertEquals("s2e1", next.progress?.videoId)
    }

    @Test
    fun `after a finished episode the next unwatched one follows, across seasons`() {
        val rows = listOf(progress("s1e2", completed = true, updatedAt = 30))

        val next = nextUpEpisode(episodes, rows, setOf("s1e2"), nowEpochMillis = 1_000L)!!

        assertEquals(e21, next.episode)
        assertEquals(NextUpKind.NEXT, next.kind)
    }

    @Test
    fun `unaired episodes and specials are skipped`() {
        val rows = listOf(progress("s2e1", completed = true, updatedAt = 30))

        val next = nextUpEpisode(episodes, rows, setOf("s2e1"), nowEpochMillis = 1_000L)!!

        assertEquals(e11, next.episode)
        assertEquals(NextUpKind.START, next.kind)
    }

    @Test
    fun `no episodes means no next up`() {
        assertNull(nextUpEpisode(emptyList(), emptyList(), emptySet()))
    }

    private fun progress(
        videoId: String,
        position: Long = 0,
        completed: Boolean = false,
        updatedAt: Long,
    ) = WatchProgress(
        profileId = "p",
        mediaKey = "series:tt1",
        videoId = videoId,
        positionMillis = if (completed) 3_000_000 else position,
        durationMillis = 3_000_000,
        completed = completed,
        updatedAtEpochMillis = updatedAt,
    )
}
