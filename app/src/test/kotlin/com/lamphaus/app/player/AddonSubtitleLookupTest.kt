package com.lamphaus.app.player

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** PLY-SUB-01: a stream never waits for the slowest add-on, and loses nothing to it. */
@OptIn(ExperimentalCoroutinesApi::class)
class AddonSubtitleLookupTest {
    private fun answer(afterMillis: Long, vararg tracks: String): suspend () -> List<String> = {
        delay(afterMillis)
        tracks.toList()
    }

    private val hung: suspend () -> List<String> = { awaitCancellation() }

    private fun TestScope.lookup(vararg lookups: suspend () -> List<String>) =
        AddonSubtitleLookup(backgroundScope, lookups.toList())

    @Test
    fun `returns as soon as every add-on answered`() = runTest {
        val tracks = lookup(answer(100, "a"), answer(300, "b")).await(3_000) { false }
        assertEquals(listOf("a", "b"), tracks)
        assertEquals(300, currentTime)
    }

    @Test
    fun `no add-ons means no wait`() = runTest {
        assertEquals(emptyList<String>(), lookup().await(3_000) { false })
        assertEquals(0, currentTime)
    }

    @Test
    fun `stops waiting the moment the wanted subtitle arrives`() = runTest {
        val tracks = lookup(answer(200, "tr"), answer(2_000, "en")).await(3_000) { "tr" in it }
        assertEquals(listOf("tr"), tracks)
        assertEquals(200, currentTime)
    }

    @Test
    fun `a hung add-on costs the budget, not the subtitles that did arrive`() = runTest {
        val tracks = lookup(answer(100, "a"), hung, answer(200, "b")).await(1_000) { false }
        assertEquals(listOf("a", "b"), tracks)
        assertEquals(1_000, currentTime)
    }

    @Test
    fun `a failing add-on counts as answered`() = runTest {
        val failing: suspend () -> List<String> = { error("bad add-on") }
        assertEquals(listOf("a"), lookup(failing, answer(100, "a")).await(3_000) { false })
        assertEquals(100, currentTime)
    }

    @Test
    fun `answers keep add-on order and late ones still arrive`() = runTest {
        val subtitles = lookup(answer(2_000, "slow"), answer(100, "fast"))
        assertEquals(listOf("fast"), subtitles.await(500) { false })
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(listOf("slow", "fast"), subtitles.arrived.first())
    }

    @Test
    fun `the wait follows whether subtitles are wanted`() {
        assertEquals(1_000L, AddonSubtitleLookup.waitMillis(wanted = false))
        assertEquals(3_000L, AddonSubtitleLookup.waitMillis(wanted = true))
    }
}
