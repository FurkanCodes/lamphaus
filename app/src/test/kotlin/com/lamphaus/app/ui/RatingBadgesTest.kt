package com.lamphaus.app.ui

import com.lamphaus.core.model.RatingSourceScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RatingBadgesTest {

    private fun score(
        sourceId: String,
        value: Double,
        scale: Double = 10.0,
    ) = RatingSourceScore(
        sourceId = sourceId,
        displayName = sourceId,
        value = value,
        scale = scale,
    )

    @Test
    fun `metadata imdb score requires the imdb source`() {
        val score = metadataImdbScore(8.4, "imdb", "IMDb")
        assertEquals(8.4, score?.value)
        assertNull(metadataImdbScore(8.4, "provider", "IMDb"))
        assertNull(metadataImdbScore(8.4, null, "IMDb"))
    }

    @Test
    fun `metadata imdb score rejects missing and out of range values`() {
        assertNull(metadataImdbScore(null, "imdb", "IMDb"))
        assertNull(metadataImdbScore(10.1, "imdb", "IMDb"))
        assertNull(metadataImdbScore(-0.1, "imdb", "IMDb"))
    }

    @Test
    fun `ordered ratings give metadata the imdb slot and dedupe sources`() {
        val metadata = metadataImdbScore(8.1, "imdb", "IMDb")!!
        val enrichment = listOf(
            score("imdb", 7.9),
            score("tmdb", 7.2),
            score("tomatoes", 93.0, scale = 100.0),
            score("popcorn", 78.0, scale = 100.0),
            score("letterboxd", 3.8, scale = 5.0),
        )
        val ordered = orderedRatingScores(metadata, enrichment)

        assertEquals(listOf("imdb", "tomatoes", "popcorn", "letterboxd", "tmdb"), ordered.map { it.sourceId })
        assertEquals(8.1, ordered.first().value, 0.0)
    }

    @Test
    fun `ordered ratings keep unknown sources after known ones in arrival order`() {
        val ordered = orderedRatingScores(
            metadata = null,
            enrichment = listOf(score("letterboxd", 3.8, scale = 5.0), score("zompist", 1.0), score("tmdb", 7.2)),
        )
        assertEquals(listOf("letterboxd", "tmdb", "zompist"), ordered.map { it.sourceId })
    }

    @Test
    fun `rating value text follows the source scale`() {
        assertEquals("7.8", ratingValueText(score("imdb", 7.8)))
        assertEquals("3.8", ratingValueText(score("letterboxd", 3.8, scale = 5.0)))
        assertEquals("93%", ratingValueText(score("tomatoes", 93.0, scale = 100.0)))
        assertEquals("78%", ratingValueText(score("popcorn", 78.4, scale = 100.0)))
    }
}
