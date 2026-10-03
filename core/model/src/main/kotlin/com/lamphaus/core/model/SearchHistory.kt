package com.lamphaus.core.model

/** Recent searches kept per profile (MOB-SRCH-01). */
const val SEARCH_HISTORY_LIMIT = 10

/** Longest query worth remembering; longer input is pasted text, not a search. */
private const val SEARCH_HISTORY_MAX_QUERY_LENGTH = 100

/**
 * [history] with [query] moved to the front: trimmed, deduplicated ignoring
 * case (the newest spelling wins), and capped at [limit]. Blank or overlong
 * queries leave the history unchanged.
 */
fun recordSearch(history: List<String>, query: String, limit: Int = SEARCH_HISTORY_LIMIT): List<String> {
    val trimmed = query.trim().replace(Regex("\\s+"), " ")
    if (trimmed.isEmpty() || trimmed.length > SEARCH_HISTORY_MAX_QUERY_LENGTH) return history
    return (listOf(trimmed) + history.filterNot { it.equals(trimmed, ignoreCase = true) }).take(limit)
}

/** [history] without [query], compared ignoring case. */
fun forgetSearch(history: List<String>, query: String): List<String> =
    history.filterNot { it.equals(query.trim(), ignoreCase = true) }
