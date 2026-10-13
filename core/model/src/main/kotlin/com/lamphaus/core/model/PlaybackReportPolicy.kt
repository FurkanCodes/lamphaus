package com.lamphaus.core.model

/**
 * Which reports count as a drop, which may leave the device, and how reports
 * left open by a process that died are closed (SHR-PROD-18).
 */
object PlaybackReportPolicy {

    /** Reports older than this are never sent. */
    const val SEND_WINDOW_MILLIS = 7L * 24 * 60 * 60 * 1000

    /** Exits that are the viewer's or the system's ordinary doing, not a failure. */
    private val ORDINARY_EXITS = setOf(
        "USER_REQUESTED",
        "USER_STOPPED",
        "PACKAGE_UPDATED",
        "PACKAGE_STATE_CHANGE",
        "PERMISSION_CHANGE",
        "EXIT_SELF",
    )

    /** Exits that are the app failing, whatever it was doing at the time. */
    private val FAILED_EXITS = setOf("CRASH", "CRASH_NATIVE", "ANR", "INITIALIZATION_FAILURE")

    /** Failed, stalled, stuttered, or died: the playbacks worth a closer look. */
    fun isBad(report: PlaybackReport): Boolean {
        if (report.kind == ReportKind.CRASH) return true
        val totals = report.totals
        val outcomeBad = when (report.outcome) {
            ReportOutcome.FAILED, ReportOutcome.CLOSED_WHILE_BUFFERING -> true
            ReportOutcome.PROCESS_DIED -> report.exit?.reason !in ORDINARY_EXITS
            ReportOutcome.CLOSED_WHILE_LOADING -> totals.openMillis >= SLOW_START_MILLIS
            else -> false
        }
        val played = totals.playedMillis.coerceAtLeast(60_000L)
        return outcomeBad ||
            totals.errors > 0 ||
            totals.retries > 0 ||
            totals.engineFallbacks > 0 ||
            totals.rebuffers >= REBUFFERS_BAD ||
            totals.rebufferMillis >= REBUFFER_MILLIS_BAD ||
            totals.audioUnderruns >= UNDERRUNS_BAD ||
            (totals.droppedFrames >= DROPPED_FRAMES_BAD && totals.droppedFrames * 60_000L / played >= DROPPED_PER_MINUTE_BAD)
    }

    /**
     * Whether [report] may be sent now: a finished bad playback from the last
     * week, begun after Performance metrics was turned on. Crashes never
     * leave through the journal: the reporting service's SDK sends those,
     * with native stacks, under Crash reports.
     */
    fun mayBeSent(report: PlaybackReport, consent: DiagnosticsConsent, nowEpochMillis: Long): Boolean {
        if (report.kind != ReportKind.PLAYBACK || report.outcome == null) return false
        if (report.delivery != ReportDelivery.PENDING) return false
        if (nowEpochMillis - report.startedAtEpochMillis > SEND_WINDOW_MILLIS) return false
        return consent.performanceMetrics &&
            report.startedAtEpochMillis >= consent.updatedAtEpochMillis &&
            isBad(report)
    }

    /**
     * What went wrong, in a few words, for grouping bad playbacks: the first
     * error's code (with its HTTP status), else why the process died, else
     * the symptom.
     */
    fun cause(report: PlaybackReport): String {
        val totals = report.totals
        val firstError = report.events.firstOrNull { it.type == ReportEventType.ERROR }?.detail
            ?.split(' ')
            ?.filter { it.startsWith("ERROR_CODE_") || it.startsWith("http=") || it.startsWith("mpv=") }
            ?.joinToString(" ")
            ?.removePrefix("ERROR_CODE_")
            ?.ifEmpty { null }
        val played = totals.playedMillis.coerceAtLeast(60_000L)
        return when {
            report.outcome == ReportOutcome.PROCESS_DIED -> "process died: ${report.exit?.reason ?: "unknown"}"
            firstError != null -> firstError
            report.outcome == ReportOutcome.CLOSED_WHILE_LOADING -> "slow start"
            report.outcome == ReportOutcome.CLOSED_WHILE_BUFFERING ||
                totals.rebuffers >= REBUFFERS_BAD || totals.rebufferMillis >= REBUFFER_MILLIS_BAD -> "rebuffering"
            totals.droppedFrames >= DROPPED_FRAMES_BAD &&
                totals.droppedFrames * 60_000L / played >= DROPPED_PER_MINUTE_BAD -> "dropped frames"
            totals.audioUnderruns >= UNDERRUNS_BAD -> "audio underruns"
            totals.engineFallbacks > 0 -> "engine fallback"
            totals.retries > 0 -> "recovered error"
            else -> "other"
        }
    }

    /** Closes a playback whose process ended while it was open. */
    fun closeAfterExit(report: PlaybackReport, exit: ProcessExitFacts?): PlaybackReport {
        val failed = report.crash != null || exit?.reason in FAILED_EXITS
        val outcome = if (!failed && report.state.background && !report.state.playing) {
            ReportOutcome.CLOSED_IN_BACKGROUND
        } else {
            ReportOutcome.PROCESS_DIED
        }
        return report.copy(outcome = outcome, exit = exit ?: report.exit)
    }

    /**
     * Matches Android's exit records to reports a previous process left open,
     * and turns crashes nothing recorded (native crashes, ANRs) into crash
     * reports. [watermark] is the newest exit already handled.
     */
    fun reconcileExits(
        reports: List<PlaybackReport>,
        exits: List<ProcessExitFacts>,
        currentPid: Int,
        watermark: Long,
        template: PlaybackReport,
        newId: () -> String,
    ): ExitReconciliation {
        val updated = mutableListOf<PlaybackReport>()
        reports.filter { it.pid != currentPid }.forEach { report ->
            val exit = exits.firstOrNull { it.pid == report.pid && it.pid != 0 }
            when {
                report.kind == ReportKind.PLAYBACK && report.outcome == null ->
                    updated += closeAfterExit(report, exit)
                report.kind == ReportKind.CRASH && report.exit == null && exit != null ->
                    updated += report.copy(exit = exit)
            }
        }
        val knownPids = reports.filter { it.kind == ReportKind.CRASH }.map { it.pid }.toSet()
        val created = exits
            .filter { it.atEpochMillis > watermark && it.pid != currentPid }
            .filter { it.reason in FAILED_EXITS && it.pid !in knownPids }
            .map { exit ->
                template.copy(
                    id = newId(),
                    kind = ReportKind.CRASH,
                    startedAtEpochMillis = exit.atEpochMillis,
                    updatedAtEpochMillis = exit.atEpochMillis,
                    pid = exit.pid,
                    outcome = ReportOutcome.PROCESS_DIED,
                    exit = exit,
                )
            }
        return ExitReconciliation(
            updated = updated,
            created = created,
            watermark = maxOf(watermark, exits.maxOfOrNull { it.atEpochMillis } ?: watermark),
        )
    }

    /** How this device's playbacks went over the last [days], as a denominator for one bad report. */
    fun recent(reports: List<PlaybackReport>, nowEpochMillis: Long, days: Int = 7): RecentPlaybacks {
        val since = nowEpochMillis - days * 24L * 60 * 60 * 1000
        val finished = reports.filter {
            it.kind == ReportKind.PLAYBACK && it.outcome != null && it.startedAtEpochMillis >= since
        }
        return RecentPlaybacks(days = days, playbacks = finished.size, bad = finished.count(::isBad))
    }

    private const val SLOW_START_MILLIS = 15_000L
    private const val REBUFFERS_BAD = 3
    private const val REBUFFER_MILLIS_BAD = 30_000L
    private const val UNDERRUNS_BAD = 5
    private const val DROPPED_FRAMES_BAD = 120L
    private const val DROPPED_PER_MINUTE_BAD = 6L
}

data class ExitReconciliation(
    val updated: List<PlaybackReport>,
    val created: List<PlaybackReport>,
    val watermark: Long,
)

/**
 * Text that may enter a report: masked of anything that could locate a
 * stream or identify a person (SHR-PROD-06), and bounded.
 */
object DiagnosticText {
    private val URL = Regex("""\b[a-zA-Z][a-zA-Z0-9+.-]*://\S+""")
    private val EMAIL = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val IPV4 = Regex("""\b\d{1,3}(?:\.\d{1,3}){3}(?::\d+)?\b""")
    private val LONG_TOKEN = Regex("""\b[A-Za-z0-9_\-]{32,}\b""")
    private val ANR_FRAME = Regex("""^\s*(at |- |native: )""")

    fun clean(text: String?, max: Int = 160): String? {
        if (text.isNullOrBlank()) return null
        val masked = text
            .replace(URL, "<url>")
            .replace(EMAIL, "<email>")
            .replace(IPV4, "<ip>")
            .replace(LONG_TOKEN, "<token>")
            .replace(Regex("\\s+"), " ")
            .trim()
        return if (masked.length <= max) masked else masked.take(max - 1) + "…"
    }

    /** Class names and frames of [error] and its causes; never their messages, which can carry addresses. */
    fun exceptions(error: Throwable, maxCauses: Int = 5, maxFrames: Int = 24): List<ExceptionFacts> =
        generateSequence(error) { it.cause.takeIf { cause -> cause !== it } }
            .take(maxCauses)
            .mapIndexed { index, throwable ->
                ExceptionFacts(
                    type = throwable.javaClass.name,
                    frames = throwable.stackTrace
                        .take(if (index == 0) maxFrames else maxFrames / 3)
                        .map { frame -> "${frame.className}.${frame.methodName}(${frame.fileName ?: "?"}:${frame.lineNumber})" },
                )
            }
            .toList()

    /** The main thread's state and frames from an ANR trace; other threads are left out. */
    fun anrMainThread(trace: String, maxFrames: Int = 30): List<String> {
        val lines = trace.lineSequence().toList()
        val start = lines.indexOfFirst { it.startsWith("\"main\"") }
        if (start < 0) return emptyList()
        val header = lines[start].substringAfterLast(' ').trim()
        val frames = lines.drop(start + 1)
            .takeWhile { it.isNotBlank() }
            .filter { ANR_FRAME.containsMatchIn(it) }
            .take(maxFrames)
            .mapNotNull { clean(it.trim(), 200) }
        return listOf("state $header") + frames
    }
}
