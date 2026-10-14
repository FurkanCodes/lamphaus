package com.lamphaus.core.data.diagnostics

import com.lamphaus.core.data.cloud.DiagnosticsDelivery
import com.lamphaus.core.data.cloud.DiagnosticsGateway
import com.lamphaus.core.model.DiagnosticsConsent
import com.lamphaus.core.model.PlaybackReport
import com.lamphaus.core.model.PlaybackReportPolicy
import com.lamphaus.core.model.ProcessExitFacts
import com.lamphaus.core.model.ReportDelivery
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Owns the playback journal and its delivery (SHR-PROD-18, SHR-ARC-05).
 * Reports are written in the order they arrive on one serial dispatcher.
 * Bad playbacks leave the device only when Performance metrics was on as
 * they began (see [PlaybackReportPolicy.mayBeSent]), at most
 * [MAX_SENT_PER_DAY] a day; crashes go through the reporting service's own SDK.
 */
class PlaybackReportRepository(
    private val store: PlaybackReportStore,
    private val gateway: DiagnosticsGateway,
    private val consent: suspend () -> DiagnosticsConsent,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
) {
    private val sending = Mutex()

    /** Queues [report] for writing; a closed bad report is sent right after. */
    fun record(report: PlaybackReport, closed: Boolean) {
        scope.launch(io) {
            store.write(report)
            if (closed) store.prune()
        }
        if (closed && PlaybackReportPolicy.isBad(report)) scope.launch { sendPending() }
    }

    /** Writes at once on the calling thread: for the crash handler only. */
    fun writeNow(report: PlaybackReport) = store.write(report)

    suspend fun reports(): List<PlaybackReport> = withContext(io) { store.all() }

    /**
     * Closes reports a dead process left open and records crashes nothing
     * caught, from the exits [readExits] returns newer than the last handled.
     */
    suspend fun reconcileExits(
        readExits: (sinceEpochMillis: Long) -> List<ProcessExitFacts>,
        currentPid: Int,
        template: PlaybackReport,
        newId: () -> String,
    ) = withContext(io) {
        val watermark = store.exitWatermark()
        val result = PlaybackReportPolicy.reconcileExits(
            reports = store.all(),
            exits = runCatching { readExits(watermark) }.getOrDefault(emptyList()),
            currentPid = currentPid,
            watermark = watermark,
            template = template,
            newId = newId,
        )
        (result.updated + result.created).forEach(store::write)
        if (result.watermark != watermark) store.setExitWatermark(result.watermark)
        store.prune()
    }

    /** Sends what may be sent; returns how many reports were handed over. */
    suspend fun sendPending(): Int = sending.withLock {
        val consent = consent()
        if (!consent.performanceMetrics) return 0
        val now = clock()
        val all = withContext(io) { store.all() }
        val recent = PlaybackReportPolicy.recent(all, now)
        // The reporting service's free plan is shared by every opted-in device.
        val sentToday = all.count {
            it.delivery == ReportDelivery.SENT && now - it.startedAtEpochMillis < DAY_MILLIS
        }
        val room = (MAX_SENT_PER_DAY - sentToday).coerceAtLeast(0)
        var sent = 0
        for (report in all.filter { PlaybackReportPolicy.mayBeSent(it, consent, now) }.sortedBy { it.startedAtEpochMillis }.take(room)) {
            if (gateway.send(report.copy(recent = recent)) != DiagnosticsDelivery.SENT) break
            sent++
            // Re-read so a write that landed meanwhile is not undone.
            withContext(io) {
                val latest = store.all().firstOrNull { it.id == report.id } ?: report
                store.write(latest.copy(delivery = ReportDelivery.SENT))
            }
        }
        sent
    }

    companion object {
        const val MAX_SENT_PER_DAY = 10
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
