package com.lamphaus.core.data.trailer

import com.lamphaus.core.model.TrailerSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrailerRepositoryTest {

    private class FakeTrailerExtractor(private val sources: Map<String, TrailerSource>) : TrailerExtractor {
        val requests = mutableListOf<String>()
        override suspend fun extract(videoId: String, maxHeight: Int): TrailerSource? {
            requests += videoId
            return sources[videoId]
        }
    }

    private var nowMillis = 1_000_000L

    @Test
    fun `falls through failing ids and reuses the resolved stream`() = runTest {
        val source = TrailerSource("https://v.googlevideo.com/videoplayback?expire=${nowMillis / 1000 + 21_600}")
        val extractor = FakeTrailerExtractor(mapOf("bbbbbbbbbbb" to source))
        val repository = TrailerRepository(extractor) { nowMillis }

        assertEquals(source, repository.source(listOf("aaaaaaaaaaa", "bbbbbbbbbbb"), maxHeight = 720))
        assertEquals(source, repository.source(listOf("aaaaaaaaaaa", "bbbbbbbbbbb"), maxHeight = 720))

        // The miss is remembered and the hit is cached: two extractions in total.
        assertEquals(listOf("aaaaaaaaaaa", "bbbbbbbbbbb"), extractor.requests)
    }

    @Test
    fun `expired streams and old misses are resolved again`() = runTest {
        val source = TrailerSource("https://v.googlevideo.com/videoplayback?expire=${nowMillis / 1000 + 600}")
        val extractor = FakeTrailerExtractor(mapOf("bbbbbbbbbbb" to source))
        val repository = TrailerRepository(extractor) { nowMillis }
        repository.source(listOf("aaaaaaaaaaa", "bbbbbbbbbbb"), maxHeight = 720)

        nowMillis += TrailerRepository.MISS_TTL_MILLIS + 1
        repository.source(listOf("aaaaaaaaaaa", "bbbbbbbbbbb"), maxHeight = 720)

        assertEquals(4, extractor.requests.size)
    }

    @Test
    fun `no ids resolves to nothing`() = runTest {
        val repository = TrailerRepository(FakeTrailerExtractor(emptyMap())) { nowMillis }

        assertNull(repository.source(emptyList(), maxHeight = 1080))
    }

    @Test
    fun `expiry comes from the signed url with a margin`() {
        assertEquals(
            2_000_000L - TrailerRepository.EXPIRY_MARGIN_MILLIS,
            TrailerRepository.expiresAtMillis("https://x.googlevideo.com/v?expire=2000", nowMillis = 0),
        )
        assertEquals(
            5L + TrailerRepository.DEFAULT_TTL_MILLIS,
            TrailerRepository.expiresAtMillis("https://manifest.googlevideo.com/hls", nowMillis = 5),
        )
    }
}
