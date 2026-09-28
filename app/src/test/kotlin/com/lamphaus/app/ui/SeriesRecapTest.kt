package com.lamphaus.app.ui

import com.lamphaus.core.model.Episode
import com.lamphaus.core.model.WatchProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeriesRecapTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_000L * day
    private val episodes = (1..4).map { number ->
        Episode(
            id = "tt1:1:$number",
            title = "Episode $number",
            season = 1,
            episode = number,
            overview = "What happens in episode $number.",
            releasedAtEpochMillis = 0,
        )
    }

    private fun row(number: Int, daysAgo: Long, completed: Boolean = true, position: Long = 2_700_000) = WatchProgress(
        profileId = "p",
        mediaKey = "series:tt1",
        videoId = "tt1:1:$number",
        positionMillis = if (completed) 2_700_000 else position,
        durationMillis = 2_700_000,
        completed = completed,
        updatedAtEpochMillis = now - daysAgo * day,
    )

    @Test
    fun `returning after three weeks recaps the last finished episode`() {
        val progress = listOf(row(1, daysAgo = 40), row(2, daysAgo = 30))
        assertEquals(episodes[1], seriesRecap(episodes, progress, emptySet(), now))
    }

    @Test
    fun `a recent visit needs no recap`() {
        val progress = listOf(row(1, daysAgo = 40), row(2, daysAgo = 5))
        assertNull(seriesRecap(episodes, progress, emptySet(), now))
    }

    @Test
    fun `resuming an episode recaps the finished one before it`() {
        val progress = listOf(row(2, daysAgo = 31), row(3, daysAgo = 30, completed = false, position = 600_000))
        assertEquals(episodes[1], seriesRecap(episodes, progress, emptySet(), now))
    }

    @Test
    fun `a finished series has nothing to continue`() {
        val progress = (1..4).map { row(it, daysAgo = 60) }
        assertNull(seriesRecap(episodes, progress, emptySet(), now))
    }

    @Test
    fun `an episode without a synopsis is not recapped`() {
        val silent = episodes.map { if (it.episode == 2) it.copy(overview = " ") else it }
        assertNull(seriesRecap(silent, listOf(row(2, daysAgo = 30)), emptySet(), now))
    }

    @Test
    fun `nothing watched means no recap`() {
        assertNull(seriesRecap(episodes, emptyList(), emptySet(), now))
    }
}
