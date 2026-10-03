package com.lamphaus.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** MOB-SRCH-01: recent searches stay short, unique, and newest first. */
class SearchHistoryTest {
    @Test
    fun newestQueryLeads() {
        assertEquals(listOf("dune", "alien"), recordSearch(listOf("alien"), "dune"))
    }

    @Test
    fun repeatedQueryMovesToFrontWithNewestSpelling() {
        assertEquals(listOf("Dune", "alien"), recordSearch(listOf("alien", "dune"), "  Dune "))
    }

    @Test
    fun innerWhitespaceCollapses() {
        assertEquals(listOf("the bear"), recordSearch(emptyList(), "the   bear"))
    }

    @Test
    fun blankAndOverlongQueriesAreIgnored() {
        val history = listOf("alien")
        assertEquals(history, recordSearch(history, "   "))
        assertEquals(history, recordSearch(history, "x".repeat(101)))
    }

    @Test
    fun historyIsCapped() {
        val history = (1..SEARCH_HISTORY_LIMIT).map { "q$it" }
        val updated = recordSearch(history, "new")
        assertEquals(SEARCH_HISTORY_LIMIT, updated.size)
        assertEquals("new", updated.first())
        assertEquals("q${SEARCH_HISTORY_LIMIT - 1}", updated.last())
    }

    @Test
    fun forgetRemovesIgnoringCase() {
        assertEquals(listOf("alien"), forgetSearch(listOf("Dune", "alien"), "dune"))
    }
}
