package com.lamphaus.app.player

import com.lamphaus.core.model.StreamCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** SHR-PROD-12: the player's add-on tabs group sources like the details screen, in add-on order. */
class PlayerSourcesPanelTest {
    private fun option(providerId: String, name: String?, url: String) = PlayerSourceOption(
        stream = StreamCandidate(providerId = providerId, name = url, url = url),
        url = url,
        providerName = name,
    )

    @Test
    fun `sources group by add-on in the order the add-ons answered`() {
        val groups = listOf(
            option("embedded", null, "https://a/1"),
            option("torrentio", "Torrentio", "https://b/1"),
            option("torrentio", "Torrentio", "https://b/2"),
            option("comet", "Comet", "https://c/1"),
        ).sourceGroups()

        assertEquals(listOf("embedded", "torrentio", "comet"), groups.map { it.id })
        assertNull(groups.first().label)
        assertEquals(listOf(1, 2, 1), groups.map { it.options.size })
    }
}
