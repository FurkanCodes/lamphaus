package com.lamphaus.app.ui

import com.lamphaus.core.model.Episode
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.MediaType
import com.lamphaus.core.model.WatchProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpNextTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_000L * day
    private val show = MediaPreview(id = "tt1", type = MediaType.SERIES, rawType = "series", name = "Show")

    private fun episode(number: Int, releasedDaysFromNow: Long?, season: Int = 1) = Episode(
        id = "tt1:$season:$number",
        title = "Episode $number",
        season = season,
        episode = number,
        releasedAtEpochMillis = releasedDaysFromNow?.let { now + it * day },
    )

    private fun finished(videoId: String, daysAgo: Long, media: MediaPreview = show) = WatchProgress(
        profileId = "p",
        mediaKey = media.stableKey,
        videoId = videoId,
        positionMillis = 2_500_000,
        durationMillis = 2_600_000,
        completed = true,
        updatedAtEpochMillis = now - daysAgo * day,
        preview = media,
    )

    @Test
    fun `next episode is up next, or new when it aired after the finish`() {
        val episodes = listOf(episode(1, -30), episode(2, -20), episode(3, -1))

        val binge = upNextAfter(show, finished("tt1:1:1", daysAgo = 5), episodes, setOf("tt1:1:1"), now)
        val caughtUp = upNextAfter(show, finished("tt1:1:2", daysAgo = 5), episodes, setOf("tt1:1:1", "tt1:1:2"), now)

        assertEquals("tt1:1:2", binge?.episode?.id)
        assertEquals(UpNextKind.NEXT, binge?.kind)
        assertEquals("tt1:1:3", caughtUp?.episode?.id)
        assertEquals(UpNextKind.NEW, caughtUp?.kind)
    }

    @Test
    fun `an unaired next episode counts down only within the window`() {
        val soon = listOf(episode(1, -7), episode(2, 3))
        val far = listOf(episode(1, -7), episode(2, 90))
        val row = finished("tt1:1:1", daysAgo = 1)

        assertEquals(UpNextKind.UPCOMING, upNextAfter(show, row, soon, setOf("tt1:1:1"), now)?.kind)
        assertNull(upNextAfter(show, row, far, setOf("tt1:1:1"), now))
    }

    @Test
    fun `specials, finished episodes and the series end give nothing`() {
        val episodes = listOf(episode(1, -30, season = 0), episode(1, -20), episode(2, -10))

        assertNull(upNextAfter(show, finished("tt1:1:2", 1), episodes, setOf("tt1:1:2"), now))
        assertNull(upNextAfter(show, finished("tt1:0:1", 1), episodes, emptySet(), now))
        assertEquals(
            null,
            upNextAfter(show, finished("tt1:1:1", 1), episodes, setOf("tt1:1:1", "tt1:1:2"), now),
        )
    }

    @Test
    fun `candidates are recent series finishes without a resumable episode or dismissal`() {
        val other = show.copy(id = "tt2", name = "Other")
        val movie = MediaPreview(id = "tt3", type = MediaType.MOVIE, rawType = "movie", name = "Film")
        val resuming = show.copy(id = "tt4", name = "Resuming")
        val progress = listOf(
            finished("tt1:1:1", daysAgo = 3),
            finished("tt1:1:2", daysAgo = 2),
            finished("tt2:1:1", daysAgo = 90, media = other),
            finished("tt3", daysAgo = 1, media = movie),
            finished("tt4:1:1", daysAgo = 1, media = resuming),
            finished("tt4:1:2", daysAgo = 0, media = resuming).copy(completed = false, positionMillis = 600_000),
        )

        val candidates = upNextCandidates(progress, dismissed = emptySet(), nowEpochMillis = now)

        assertEquals(listOf("tt1:1:2"), candidates.map(WatchProgress::videoId))
        assertTrue(upNextCandidates(progress, setOf("series:tt1|tt1:1:2"), now).isEmpty())
    }

    @Test
    fun `row keeps watchable cards by recency and upcoming ones last by air date`() {
        val resumable = show.copy(id = "tt5", name = "Resumable") to finished("tt5:1:1", 1).copy(completed = false)
        val new = UpNextItem(show, episode(3, -1), UpNextKind.NEW, finished("tt1:1:2", 0))
        val later = show.copy(id = "tt6", name = "Later")
        val sooner = show.copy(id = "tt7", name = "Sooner")
        val upcomingLater = UpNextItem(later, episode(2, 6), UpNextKind.UPCOMING, finished("tt6:1:1", 0, later))
        val upcomingSooner = UpNextItem(sooner, episode(2, 2), UpNextKind.UPCOMING, finished("tt7:1:1", 9, sooner))

        val row = withUpNext(listOf(resumable), listOf(upcomingLater, new, upcomingSooner))

        assertEquals(listOf("Show", "Resumable", "Sooner", "Later"), row.map { it.first.name })
    }

    @Test
    fun `an upcoming up-next menu cannot start playback`() {
        // The menu reads the real clock.
        val realNow = System.currentTimeMillis()
        val upcoming = ContentMenuTarget(
            show,
            episode = episode(2, null).copy(releasedAtEpochMillis = realNow + 3 * day),
            origin = ContentMenuOrigin.CONTINUE_WATCHING,
        )
        val aired = upcoming.copy(episode = episode(2, null).copy(releasedAtEpochMillis = realNow - 3 * day))

        assertTrue(ContentMenuAction.StartFromBeginning !in upcoming.menuActions())
        assertTrue(ContentMenuAction.StartFromBeginning in aired.menuActions())
        assertTrue(ContentMenuAction.RemoveFromContinueWatching in upcoming.menuActions())
    }
}
