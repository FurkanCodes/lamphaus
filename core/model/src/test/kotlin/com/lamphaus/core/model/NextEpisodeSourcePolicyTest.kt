package com.lamphaus.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextEpisodeSourcePolicyTest {
    private fun stream(provider: String, name: String = provider, binge: String? = null) =
        StreamCandidate(providerId = provider, name = name, url = "https://$provider.invalid/v", bingeGroup = binge)

    private fun select(candidates: List<StreamCandidate>, provider: String? = null, binge: String? = null) =
        NextEpisodeSourcePolicy.select(candidates, { it }, provider, binge)

    @Test
    fun `binge group match wins over provider order`() {
        val first = stream("a")
        val match = stream("b", binge = "hd-web")
        assertEquals(match, select(listOf(first, match), provider = "a", binge = "hd-web"))
    }

    @Test
    fun `without a binge match the current provider wins`() {
        val first = stream("a")
        val same = stream("b")
        assertEquals(same, select(listOf(first, same), provider = "b", binge = "other"))
    }

    @Test
    fun `otherwise the first stream in provider order`() {
        val first = stream("a")
        assertEquals(first, select(listOf(first, stream("b")), provider = "z"))
        assertNull(select(emptyList()))
    }

    @Test
    fun `blank binge groups never match`() {
        assertEquals(false, NextEpisodeSourcePolicy.isBingeMatch(stream("a", binge = " "), " "))
    }

    @Test
    fun `source name uses the stream's first line, then the provider`() {
        assertEquals("Torrentio", NextEpisodeSourcePolicy.sourceName(stream("a", name = "Torrentio\n4k"), "Provider"))
        assertEquals("Provider", NextEpisodeSourcePolicy.sourceName(stream("a", name = " "), "Provider"))
    }
}
