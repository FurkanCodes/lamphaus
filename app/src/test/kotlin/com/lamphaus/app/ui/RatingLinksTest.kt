package com.lamphaus.app.ui

import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.MediaType
import com.lamphaus.core.model.RatingSourceScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RatingLinksTest {

    private fun score(sourceId: String, detailsUrl: String? = null) = RatingSourceScore(
        sourceId = sourceId,
        displayName = sourceId,
        value = 7.0,
        detailsUrl = detailsUrl,
    )

    private fun media(id: String, type: MediaType = MediaType.MOVIE, name: String = "The Thing") = MediaPreview(
        id = id,
        type = type,
        rawType = if (type == MediaType.SERIES) "series" else "movie",
        name = name,
    )

    @Test
    fun `imdb ids lead to exact pages`() {
        val movie = media("tt0084787")
        assertEquals("https://www.imdb.com/title/tt0084787/", ratingDetailsUrl(score("imdb"), movie))
        assertEquals("https://letterboxd.com/imdb/tt0084787/", ratingDetailsUrl(score("letterboxd"), movie))
        assertEquals("https://trakt.tv/search/imdb/tt0084787", ratingDetailsUrl(score("trakt"), movie))
    }

    @Test
    fun `tmdb ids lead to the typed tmdb page`() {
        assertEquals("https://www.themoviedb.org/movie/1091", ratingDetailsUrl(score("tmdb"), media("tmdb:1091")))
        assertEquals(
            "https://www.themoviedb.org/tv/1399",
            ratingDetailsUrl(score("tmdb"), media("tmdb:series:1399", MediaType.SERIES)),
        )
    }

    @Test
    fun `sources without an id lookup search for the title`() {
        val movie = media("tt0084787", name = "Amélie & Co")
        assertEquals(
            "https://www.rottentomatoes.com/search?search=Am%C3%A9lie+%26+Co",
            ratingDetailsUrl(score("tomatoes"), movie),
        )
        assertEquals(
            "https://www.metacritic.com/search/Am%C3%A9lie%20%26%20Co/",
            ratingDetailsUrl(score("metacritic"), movie),
        )
    }

    @Test
    fun `provider scoped ids never enter a link`() {
        val providerMedia = media("kitsu:42", name = "Secret Show")
        val url = ratingDetailsUrl(score("imdb"), providerMedia)
        assertEquals("https://www.imdb.com/find/?q=Secret+Show", url)
    }

    @Test
    fun `letterboxd has no series pages so series search films`() {
        val series = media("tt0903747", MediaType.SERIES, name = "Breaking Bad")
        assertEquals("https://letterboxd.com/search/films/Breaking%20Bad/", ratingDetailsUrl(score("letterboxd"), series))
    }

    @Test
    fun `server supplied https link wins and other schemes are ignored`() {
        val movie = media("tt0084787")
        assertEquals(
            "https://www.themoviedb.org/movie/1091",
            ratingDetailsUrl(score("tmdb", "https://www.themoviedb.org/movie/1091"), movie),
        )
        assertEquals(
            "https://www.imdb.com/title/tt0084787/",
            ratingDetailsUrl(score("imdb", "javascript:alert(1)"), movie),
        )
    }

    @Test
    fun `unknown sources and nameless titles have no link`() {
        assertNull(ratingDetailsUrl(score("somewhere"), media("tt0084787")))
        assertNull(ratingDetailsUrl(score("tomatoes"), media("x:1", name = " ")))
    }
}
