package com.lamphaus.app.ui

import com.lamphaus.core.model.Episode
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.MediaType
import com.lamphaus.core.model.WatchProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentMenuActionsTest {
    @Test
    fun `QA-01 completed movie poster exposes unwatched without continue-watching removal`() {
        val target = ContentMenuTarget(movie, progress = progress(completed = true))
        val actions = target.menuActions()

        assertTrue(ContentMenuAction.MarkUnwatched in actions)
        assertFalse(ContentMenuAction.MarkWatched in actions)
        assertTrue(ContentMenuAction.StartFromBeginning in actions)
        assertFalse(ContentMenuAction.RemoveFromContinueWatching in actions)
    }

    @Test
    fun `QA-01 continue-watching series exposes row actions`() {
        val target = ContentMenuTarget(
            media = series,
            progress = progress(videoId = "episode-1"),
            origin = ContentMenuOrigin.CONTINUE_WATCHING,
        )

        assertEquals(
            listOf(
                ContentMenuAction.ViewDetails,
                ContentMenuAction.ToggleLibrary,
                ContentMenuAction.MarkWatched,
                ContentMenuAction.StartFromBeginning,
                ContentMenuAction.RemoveFromContinueWatching,
            ),
            target.menuActions(),
        )
    }

    @Test
    fun `QA-01 series poster has no whole-series watched action`() {
        assertEquals(
            listOf(ContentMenuAction.ViewDetails, ContentMenuAction.ToggleLibrary),
            ContentMenuTarget(series).menuActions(),
        )
    }

    @Test
    fun `QA-01 episode without progress can be marked watched but not started over`() {
        val actions = ContentMenuTarget(
            media = series,
            episode = Episode(id = "episode-1", title = "Pilot"),
            origin = ContentMenuOrigin.EPISODE,
        ).menuActions()

        assertTrue(ContentMenuAction.MarkWatched in actions)
        assertFalse(ContentMenuAction.StartFromBeginning in actions)
        assertFalse(ContentMenuAction.RemoveFromContinueWatching in actions)
    }

    private fun progress(videoId: String = movie.id, completed: Boolean = false) = WatchProgress(
        profileId = "profile",
        mediaKey = movie.stableKey,
        videoId = videoId,
        positionMillis = 60_000,
        durationMillis = 100_000,
        completed = completed,
        updatedAtEpochMillis = 1,
        preview = movie,
    )

    private companion object {
        val movie = MediaPreview("movie-1", MediaType.MOVIE, "movie", "Movie")
        val series = MediaPreview("series-1", MediaType.SERIES, "series", "Series")
    }
}
