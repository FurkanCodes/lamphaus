package com.lamphaus.app.ui

import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.MediaType
import com.lamphaus.core.model.StreamCandidate
import org.junit.Assert.assertEquals
import org.junit.Test

class SourcePickerStateTest {
    private val media = MediaPreview("tt1", MediaType.MOVIE, "movie", "Movie")
    private val first = StreamCandidate("one", "First", url = "https://one.example/video.mp4")
    private val second = StreamCandidate("two", "Second", url = "https://two.example/video.mp4")

    @Test
    fun `all chip exposes every source`() {
        val picker = SourcePickerState(media = media, sources = listOf(first, second), loading = false)

        assertEquals(listOf(first, second), picker.visibleSources)
        assertEquals(listOf("one", "two"), picker.providerIds)
    }

    @Test
    fun `provider chip filters without mutating loaded sources`() {
        val picker = SourcePickerState(media = media, sources = listOf(first, second), loading = false)
            .selectProvider("two")

        assertEquals(listOf(second), picker.visibleSources)
        assertEquals(listOf(first, second), picker.sources)
    }
}
