package com.lamphaus.app.update

/**
 * Where the signed update feed is read from, freshest first. A
 * raw.githubusercontent.com branch URL also has a GitHub contents API form
 * that is not held behind raw's long CDN cache.
 */
internal object UpdateFeedSources {
    data class Source(val url: String, val accept: String)

    private val RAW = Regex("^https://raw\\.githubusercontent\\.com/([^/]+)/([^/]+)/([^/]+)/(.+)$")

    fun forFeedUrl(feedUrl: String): List<Source> {
        val raw = Source(feedUrl, "application/json")
        val match = RAW.matchEntire(feedUrl) ?: return listOf(raw)
        val (owner, repo, branch, path) = match.destructured
        val api = Source(
            url = "https://api.github.com/repos/$owner/$repo/contents/$path?ref=$branch",
            accept = "application/vnd.github.raw+json",
        )
        return listOf(api, raw)
    }
}
