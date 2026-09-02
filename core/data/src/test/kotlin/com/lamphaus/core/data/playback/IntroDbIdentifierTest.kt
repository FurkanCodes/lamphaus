package com.lamphaus.core.data.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IntroDbIdentifierTest {
    @Test
    fun `extracts Cinemeta IMDb ids`() {
        assertEquals(IntroDbIdentifier.Imdb("tt0903747"), IntroDbIdentifier.from("tt0903747"))
        assertEquals(IntroDbIdentifier.Imdb("tt0903747"), IntroDbIdentifier.from("series/tt0903747:1:2"))
    }

    @Test
    fun `extracts explicitly namespaced TMDB ids`() {
        assertEquals(IntroDbIdentifier.Tmdb("1396"), IntroDbIdentifier.from("tmdb:tv:1396"))
        assertEquals(IntroDbIdentifier.Tmdb("550"), IntroDbIdentifier.from("catalog/tmdb/movie/550"))
    }

    @Test
    fun `extracts explicitly namespaced TVDB ids`() {
        assertEquals(IntroDbIdentifier.Tvdb("81189"), IntroDbIdentifier.from("tvdb:81189"))
    }

    @Test
    fun `does not guess provider-specific numeric ids`() {
        assertNull(IntroDbIdentifier.from("1396"))
        assertNull(IntroDbIdentifier.from("kitsu:1396"))
    }
}
