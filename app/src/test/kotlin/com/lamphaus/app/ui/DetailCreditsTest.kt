package com.lamphaus.app.ui

import com.lamphaus.core.model.DetailEnrichment
import com.lamphaus.core.model.MediaDetail
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.MediaType
import com.lamphaus.core.model.PersonCredit
import org.junit.Assert.assertEquals
import org.junit.Test

class DetailCreditsTest {
    private val detail = MediaDetail(
        preview = MediaPreview(id = "tt1", type = MediaType.MOVIE, rawType = "movie", name = "Film"),
        cast = listOf("Actor One", "Actor Two"),
        directors = listOf("Provider Director"),
    )

    private fun credits(enrichment: DetailEnrichment?) =
        detailCredits(detail, enrichment, directorRole = "Director", plainName = { it })

    @Test
    fun `without enrichment the add-on's directors lead its cast`() {
        assertEquals(
            listOf(
                PersonCredit(name = "Provider Director", role = "Director"),
                PersonCredit(name = "Actor One"),
                PersonCredit(name = "Actor Two"),
            ),
            credits(null),
        )
    }

    @Test
    fun `TMDB directors lead TMDB cast and writers stay out`() {
        val director = PersonCredit(personId = "9", name = "Tmdb Director", role = "Director", profileUrl = "https://img/d")
        val actor = PersonCredit(personId = "1", name = "Tmdb Actor", role = "Hero")
        val enrichment = DetailEnrichment(
            mediaKey = "movie:tt1",
            cast = listOf(actor),
            crew = listOf(PersonCredit(personId = "8", name = "Tmdb Writer", role = "Writer"), director),
            fetchedAtEpochMillis = 0,
        )
        assertEquals(listOf(director, actor), credits(enrichment))
    }

    @Test
    fun `the add-on's directors fill in when TMDB names none`() {
        val actor = PersonCredit(personId = "1", name = "Tmdb Actor")
        val enrichment = DetailEnrichment(mediaKey = "movie:tt1", cast = listOf(actor), fetchedAtEpochMillis = 0)
        assertEquals(
            listOf(PersonCredit(name = "Provider Director", role = "Director"), actor),
            credits(enrichment),
        )
    }

    @Test
    fun `the localized role replaces TMDB's job`() {
        val enrichment = DetailEnrichment(
            mediaKey = "movie:tt1",
            crew = listOf(PersonCredit(personId = "9", name = "Tmdb Director", role = "Director")),
            fetchedAtEpochMillis = 0,
        )
        val result = detailCredits(detail, enrichment, directorRole = "Réalisation", plainName = { it })
        assertEquals("Réalisation", result.first().role)
    }
}
