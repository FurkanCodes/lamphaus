package com.lamphaus.core.data.diagnostics

import com.lamphaus.core.data.cloud.DiagnosticsDelivery
import com.lamphaus.core.data.cloud.DiagnosticsGateway
import com.lamphaus.core.model.DiagnosticsConsent
import com.lamphaus.core.model.PlaybackReport
import com.lamphaus.core.model.PlaybackTotals
import com.lamphaus.core.model.ProcessExitFacts
import com.lamphaus.core.model.ReportDelivery
import com.lamphaus.core.model.ReportKind
import com.lamphaus.core.model.ReportOutcome
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** SHR-PROD-18: the journal keeps reports on the device and sends only consented bad playbacks. */
class PlaybackReportRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    private val now = 1_800_000_000_000L
    private val consented = DiagnosticsConsent(crashReports = true, performanceMetrics = true, updatedAtEpochMillis = now - 60_000)

    private class FakeGateway(var answer: DiagnosticsDelivery = DiagnosticsDelivery.SENT) : DiagnosticsGateway {
        val sent = mutableListOf<PlaybackReport>()
        override suspend fun send(report: PlaybackReport): DiagnosticsDelivery {
            if (answer == DiagnosticsDelivery.SENT) sent += report
            return answer
        }
    }

    private fun report(id: String, outcome: ReportOutcome? = ReportOutcome.FAILED, startedAt: Long = now - 1_000) =
        PlaybackReport(id = id, startedAtEpochMillis = startedAt, outcome = outcome, totals = PlaybackTotals(errors = 1))

    private fun TestScope.repository(
        gateway: DiagnosticsGateway,
        consent: DiagnosticsConsent = consented,
        store: PlaybackReportStore = PlaybackReportStore { folder.root },
    ): PlaybackReportRepository {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return PlaybackReportRepository(
            store = store,
            gateway = gateway,
            consent = { consent },
            scope = TestScope(dispatcher),
            clock = { now },
            io = dispatcher,
        )
    }

    @Test
    fun `SHR-PROD-18 a closed bad report is written and sent once`() = runTest {
        val gateway = FakeGateway()
        val repository = repository(gateway)

        repository.record(report("a", outcome = null), closed = false)
        repository.record(report("a"), closed = true)
        advanceUntilIdle()
        repository.sendPending()

        assertEquals(listOf("a"), gateway.sent.map { it.id })
        assertEquals(ReportDelivery.SENT, repository.reports().single().delivery)
        assertEquals(1, gateway.sent.single().recent?.bad)
    }

    @Test
    fun `SHR-PROD-18 nothing leaves the device without Performance metrics`() = runTest {
        val gateway = FakeGateway()
        repository(gateway, consent = DiagnosticsConsent(crashReports = true)).apply { record(report("a"), closed = true) }
        advanceUntilIdle()
        assertTrue(gateway.sent.isEmpty())
    }

    @Test
    fun `SHR-PROD-18 a device sends at most ten playbacks a day`() = runTest {
        val gateway = FakeGateway()
        val store = PlaybackReportStore { folder.root }
        repeat(14) { index -> store.write(report("r$index", startedAt = now - 10_000 + index)) }
        val repository = repository(gateway, store = store)

        assertEquals(PlaybackReportRepository.MAX_SENT_PER_DAY, repository.sendPending())
        assertEquals(0, repository.sendPending())
        assertEquals("r0", gateway.sent.first().id)
    }

    @Test
    fun `SHR-PROD-18 good playbacks stay on the device`() = runTest {
        val gateway = FakeGateway()
        val repository = repository(gateway)
        repository.record(report("a", outcome = ReportOutcome.ENDED).copy(totals = PlaybackTotals()), closed = true)
        advanceUntilIdle()
        assertEquals(0, repository.sendPending())
        assertEquals(1, repository.reports().size)
    }

    @Test
    fun `SHR-PROD-18 reports wait while reporting is not running`() = runTest {
        val gateway = FakeGateway(DiagnosticsDelivery.UNAVAILABLE)
        val repository = repository(gateway)
        repository.record(report("a"), closed = true)
        advanceUntilIdle()
        assertEquals(ReportDelivery.PENDING, repository.reports().single().delivery)

        gateway.answer = DiagnosticsDelivery.SENT
        assertEquals(1, repository.sendPending())
        assertEquals(ReportDelivery.SENT, repository.reports().single().delivery)
    }

    @Test
    fun `SHR-PROD-18 the journal keeps the newest thirty reports`() = runTest {
        val store = PlaybackReportStore { folder.root }
        val repository = repository(FakeGateway(DiagnosticsDelivery.UNAVAILABLE), store = store)
        repeat(35) { index -> store.write(report("r$index", startedAt = now - 100_000 + index)) }
        repository.record(report("last", startedAt = now), closed = true)
        advanceUntilIdle()

        val kept = repository.reports()
        assertEquals(PlaybackReportStore.MAX_REPORTS, kept.size)
        assertEquals("last", kept.first().id)
        assertTrue(kept.none { it.id == "r0" })
    }

    @Test
    fun `SHR-PROD-18 a report left open by a dead process is closed with its exit and sent`() = runTest {
        val gateway = FakeGateway()
        val store = PlaybackReportStore { folder.root }
        store.write(report("open", outcome = null).copy(pid = 11, totals = PlaybackTotals()))
        val repository = repository(gateway, store = store)

        repository.reconcileExits(
            readExits = { listOf(ProcessExitFacts(atEpochMillis = now, pid = 11, reason = "CRASH_NATIVE", status = 11)) },
            currentPid = 12,
            template = PlaybackReport(id = "", startedAtEpochMillis = 0),
            newId = { "crash" },
        )
        repository.sendPending()

        val reports = repository.reports().associateBy { it.id }
        assertEquals(ReportOutcome.PROCESS_DIED, reports.getValue("open").outcome)
        assertEquals("CRASH_NATIVE", reports.getValue("open").exit?.reason)
        // The crash itself stays in the journal; the reporting service's SDK sends crashes.
        assertEquals(ReportKind.CRASH, reports.getValue("crash").kind)
        assertEquals(listOf("open"), gateway.sent.map { it.id })
        assertEquals(now, store.exitWatermark())
    }
}
