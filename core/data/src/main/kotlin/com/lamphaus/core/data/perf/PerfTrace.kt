package com.lamphaus.core.data.perf

import android.os.Trace
import android.os.Build

/**
 * Fixed-name, cheap trace spans (PERF-02). Names are constants so tools can
 * match them across builds, spans carry no provider URLs, credentials,
 * queries, tokens, stream locations, or account identifiers (SHR-PROD-06),
 * and [android.os.Trace] is a no-op unless a tracing agent is active, so the
 * instrumentation is removable by simply not reading the trace.
 *
 * Use [span] for durations and [mark] for milestones whose timing is the
 * interesting part (first usable row, first video frame, return to browse).
 */
object PerfTrace {
    // Dependency and data lifetimes.
    const val DEPENDENCY_INIT = "lamphaus.dependency.init"
    const val REPOSITORY_HASH = "lamphaus.repository.hash"
    const val REPOSITORY_DECODE = "lamphaus.repository.decode"

    // Catalog and search readiness.
    const val HOME_WINDOW_LOAD = "lamphaus.home.window.load"
    const val HOME_FIRST_USABLE_ROW = "lamphaus.home.first.usable.row"
    const val SEARCH_QUERY_TO_FIRST_RESULT = "lamphaus.search.query.to.first.result"
    const val SOURCE_RESOLUTION = "lamphaus.source.resolution"

    // Startup readiness outcomes (PERF-02).
    const val STARTUP_USABLE_CONTENT = "lamphaus.startup.usable.content"
    const val STARTUP_SETTLED = "lamphaus.startup.settled"
    const val STARTUP_DEGRADED = "lamphaus.startup.degraded"

    /**
     * Duration from the host surface entering composition to usable content or
     * an actionable empty/error state. Macrobenchmark reads it as a
     * `TraceSectionMetric`, so time-to-usable-content is measured next to TTID
     * and TTFD instead of being inferred from them.
     */
    const val STARTUP_READINESS = "lamphaus.startup.readiness"

    private var startupCookie: Int? = null
    private val cookies = java.util.concurrent.atomic.AtomicInteger()

    // Platform async tracing requires API 29; older devices omit these spans.
    @PublishedApi
    internal fun beginAsync(name: String): Int {
        val cookie = cookies.incrementAndGet()
        if (Build.VERSION.SDK_INT >= 29) Trace.beginAsyncSection(name, cookie)
        return cookie
    }

    @PublishedApi
    internal fun endAsync(name: String, cookie: Int) {
        if (Build.VERSION.SDK_INT >= 29) Trace.endAsyncSection(name, cookie)
    }

    @Synchronized
    fun beginStartupSpan() {
        if (startupCookie == null) startupCookie = beginAsync(STARTUP_READINESS)
    }

    @Synchronized
    fun endStartupSpan() {
        startupCookie?.let { endAsync(STARTUP_READINESS, it) }
        startupCookie = null
    }

    // Playback continuity.
    const val CONTROLLER_CONNECT = "lamphaus.playback.controller.connect"
    const val FIRST_VIDEO_FRAME = "lamphaus.playback.first.video.frame"
    const val RETURN_TO_BROWSE = "lamphaus.playback.return.to.browse"

    inline fun <T> span(name: String, block: () -> T): T {
        Trace.beginSection(name)
        try {
            return block()
        } finally {
            Trace.endSection()
        }
    }

    /** Suspending variant for spans around catalog, repository, and resolution work. */
    suspend inline fun <T> spanSuspend(name: String, block: suspend () -> T): T {
        val cookie = beginAsync(name)
        try {
            return block()
        } finally {
            endAsync(name, cookie)
        }
    }

    /** A zero-length milestone slice with a fixed name. */
    fun mark(name: String) {
        Trace.beginSection(name)
        Trace.endSection()
    }
}
