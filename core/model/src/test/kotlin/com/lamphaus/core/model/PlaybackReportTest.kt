package com.lamphaus.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** SHR-PROD-18: playback reports record what happened, judge drops, and keep sources out. */
class PlaybackReportTest {
    private val day = 24L * 60 * 60 * 1000
    private val epoch = 1_800_000_000_000L

    private fun builder(start: Long = 1_000L) = PlaybackReportBuilder(
        id = "r1",
        startedAtEpochMillis = epoch,
        startedAtElapsed = start,
        pid = 42,
        app = AppFacts("1.0.0", 1, "release"),
        device = DeviceFacts(model = "Box", sdk = 30, television = true),
        playback = PlaybackFacts(engineSetting = "AUTO", startEngine = "MEDIA3", streamType = "progressive"),
    )

    private fun PlaybackReportBuilder.started(at: Long = 3_000L) = apply {
        onPhase(EnginePhase.BUFFERING, 1_100L, 0, 0, null)
        onPhase(EnginePhase.READY, at, 0, 20_000, null)
        onFirstFrame(at + 100, 0)
        onPlaying(true, at + 100)
    }

    @Test
    fun `SHR-PROD-18 startup time, rebuffers, and play time are measured`() {
        val report = builder().started().apply {
            onPhase(EnginePhase.BUFFERING, 60_000L, 56_000, 0, 4_000_000)
            onPlaying(false, 60_000L)
            onPhase(EnginePhase.READY, 66_000L, 56_000, 15_000, null)
            onPlaying(true, 66_000L)
        }.close(now = 100_100L, nowEpochMillis = epoch + 99_000, reason = "back")

        assertEquals(2_100L, report.totals.startupMillis)
        assertEquals(1, report.totals.rebuffers)
        assertEquals(6_000L, report.totals.rebufferMillis)
        assertEquals(56_900L + 34_100L, report.totals.playedMillis)
        assertEquals(ReportOutcome.CLOSED, report.outcome)
        assertEquals("back", report.closeReason)
        assertEquals("bw=4.0Mbps", report.events.single { it.type == ReportEventType.BUFFERING }.detail)
        assertFalse(PlaybackReportPolicy.isBad(report))
    }

    @Test
    fun `SHR-PROD-18 buffering after a seek is not a rebuffer`() {
        val report = builder().started().apply {
            onSeek(10_000L, 5_000, 600_000)
            onPhase(EnginePhase.BUFFERING, 10_010L, 600_000, 0, null)
            onPhase(EnginePhase.READY, 12_000L, 600_000, 9_000, null)
        }.close(20_000L, epoch, null)

        assertEquals(0, report.totals.rebuffers)
        assertEquals("seek", report.events.single { it.type == ReportEventType.BUFFERING }.detail)
    }

    @Test
    fun `SHR-PROD-18 outcome follows where the session stood when it closed`() {
        assertEquals(
            ReportOutcome.CLOSED_WHILE_LOADING,
            builder().close(20_000L, epoch, "back").outcome,
        )
        assertEquals(
            ReportOutcome.CLOSED_WHILE_BUFFERING,
            builder().started().apply { onPhase(EnginePhase.BUFFERING, 9_000L, 1, 0, null) }
                .close(20_000L, epoch, "back").outcome,
        )
        assertEquals(
            ReportOutcome.FAILED,
            builder().started().apply { onError(9_000L, 5, 2001, "IO_NETWORK_CONNECTION_FAILED") }
                .close(20_000L, epoch, "back").outcome,
        )
        assertEquals(
            ReportOutcome.ENDED,
            builder().started().apply { onPhase(EnginePhase.ENDED, 9_000L, 90, 0, null) }
                .close(20_000L, epoch, "end").outcome,
        )
    }

    @Test
    fun `SHR-PROD-18 a recovered error clears the error but still marks the playback bad`() {
        val report = builder().started().apply {
            onError(9_000L, 5, 2001, "IO_NETWORK_CONNECTION_FAILED")
            note(ReportEventType.RETRY, 9_000L, detail = "REPREPARE")
            onPhase(EnginePhase.BUFFERING, 10_500L, 5, 0, null)
            onPhase(EnginePhase.READY, 11_000L, 5, 8_000, null)
        }.close(20_000L, epoch, "back")

        assertEquals(ReportOutcome.CLOSED, report.outcome)
        assertEquals(1, report.totals.retries)
        assertTrue(PlaybackReportPolicy.isBad(report))
    }

    @Test
    fun `SHR-PROD-18 dense frame drops are stutter, sparse ones are not`() {
        val sparse = builder().started().apply {
            onDroppedFrames(600_000L, 50, 590_000)
        }.close(3_600_000L, epoch, null)
        assertTrue(sparse.events.none { it.type == ReportEventType.DROPPED_FRAMES })
        assertFalse(PlaybackReportPolicy.isBad(sparse))

        val stutter = builder().started().apply {
            repeat(4) { onDroppedFrames(10_000L + it * 3_000L, 50, 3_000) }
        }.close(70_000L, epoch, null)
        assertEquals(4, stutter.events.count { it.type == ReportEventType.DROPPED_FRAMES })
        assertTrue(PlaybackReportPolicy.isBad(stutter))
    }

    @Test
    fun `SHR-PROD-18 noisy events are capped while totals keep counting`() {
        val report = builder().started().apply {
            repeat(500) { onSeek(10_000L + it, it.toLong(), it + 1L) }
        }.close(20_000L, epoch, null)

        assertEquals(500, report.totals.seeks)
        assertEquals(40, report.events.count { it.type == ReportEventType.SEEK })
        assertTrue(report.omittedEvents >= 460)
        assertTrue(report.events.size <= PlaybackReportBuilder.MAX_EVENTS)
        assertEquals(ReportEventType.CLOSE, report.events.last().type)
    }

    @Test
    fun `SHR-PROD-18 a slow start the viewer gave up on is bad, a quick back-out is not`() {
        assertFalse(PlaybackReportPolicy.isBad(builder().close(4_000L, epoch, "back")))
        assertTrue(PlaybackReportPolicy.isBad(builder().close(21_000L, epoch, "back")))
    }

    @Test
    fun `SHR-PROD-18 only finished bad reports begun after consent, within a week, are sent`() {
        val bad = builder().started().apply { onError(9_000L, 5, 2001, "x") }.close(20_000L, epoch, null)
        val consent = DiagnosticsConsent(performanceMetrics = true, updatedAtEpochMillis = epoch - 1)

        assertTrue(PlaybackReportPolicy.mayBeSent(bad, consent, epoch + day))
        assertFalse(PlaybackReportPolicy.mayBeSent(bad, consent.copy(performanceMetrics = false), epoch))
        assertFalse(PlaybackReportPolicy.mayBeSent(bad, consent.copy(crashReports = true, performanceMetrics = false), epoch))
        assertFalse(PlaybackReportPolicy.mayBeSent(bad, consent.copy(updatedAtEpochMillis = epoch + 1), epoch))
        assertFalse(PlaybackReportPolicy.mayBeSent(bad, consent, epoch + 8 * day))
        assertFalse(PlaybackReportPolicy.mayBeSent(bad.copy(delivery = ReportDelivery.SENT), consent, epoch))
        assertFalse(PlaybackReportPolicy.mayBeSent(bad.copy(outcome = null), consent, epoch))
        val good = builder().started().close(20_000L, epoch, null)
        assertFalse(PlaybackReportPolicy.mayBeSent(good, consent, epoch))
    }

    @Test
    fun `SHR-PROD-18 crashes never leave through the journal`() {
        val crash = PlaybackReport(id = "c", kind = ReportKind.CRASH, startedAtEpochMillis = epoch, outcome = ReportOutcome.PROCESS_DIED)
        val consent = DiagnosticsConsent(crashReports = true, performanceMetrics = true, updatedAtEpochMillis = epoch - 1)
        assertFalse(PlaybackReportPolicy.mayBeSent(crash, consent, epoch))
    }

    @Test
    fun `SHR-PROD-18 a bad playback is grouped by its first error, death, or symptom`() {
        val failed = builder().started().apply {
            onError(9_000L, 5, 2004, "ERROR_CODE_IO_BAD_HTTP_STATUS http=403 InvalidResponseCodeException")
            onError(9_500L, 5, 2001, "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED")
        }.close(20_000L, epoch, null)
        assertEquals("IO_BAD_HTTP_STATUS http=403", PlaybackReportPolicy.cause(failed))

        val died = builder().started().snapshot(epoch, 10_000L)
            .let { PlaybackReportPolicy.closeAfterExit(it, ProcessExitFacts(pid = 42, reason = "CRASH_NATIVE")) }
        assertEquals("process died: CRASH_NATIVE", PlaybackReportPolicy.cause(died))

        val stalled = builder().started().apply { onPhase(EnginePhase.BUFFERING, 9_000L, 1, 0, null) }
            .close(20_000L, epoch, "viewer")
        assertEquals("rebuffering", PlaybackReportPolicy.cause(stalled))
        assertEquals("slow start", PlaybackReportPolicy.cause(builder().close(21_000L, epoch, "viewer")))
    }

    @Test
    fun `SHR-PROD-18 a dead process closes its open playback with the exit Android recorded`() {
        val open = builder().started().snapshot(epoch + 60_000, 61_000L)
        val anr = ProcessExitFacts(atEpochMillis = epoch + 70_000, pid = 42, reason = "ANR", mainThread = listOf("state Blocked"))
        val lowMemoryElsewhere = ProcessExitFacts(atEpochMillis = epoch + 80_000, pid = 7, reason = "LOW_MEMORY")
        val result = PlaybackReportPolicy.reconcileExits(
            reports = listOf(open),
            exits = listOf(lowMemoryElsewhere, anr),
            currentPid = 99,
            watermark = 0,
            template = PlaybackReport(id = "", startedAtEpochMillis = 0),
            newId = { "new" },
        )

        val closed = result.updated.single()
        assertEquals(ReportOutcome.PROCESS_DIED, closed.outcome)
        assertEquals("ANR", closed.exit?.reason)
        assertTrue(PlaybackReportPolicy.isBad(closed))
        // The ANR also becomes a crash report; a low-memory kill does not.
        assertEquals(listOf("ANR"), result.created.map { it.exit?.reason })
        assertEquals(ReportKind.CRASH, result.created.single().kind)
        assertEquals(epoch + 80_000, result.watermark)
    }

    @Test
    fun `SHR-PROD-18 a paused player killed in the background closed normally`() {
        val open = builder().started().apply {
            onPlaying(false, 9_000L)
            onBackground(true, 9_100L)
        }.snapshot(epoch, 10_000L)
        val closed = PlaybackReportPolicy.closeAfterExit(open, ProcessExitFacts(pid = 42, reason = "LOW_MEMORY"))
        assertEquals(ReportOutcome.CLOSED_IN_BACKGROUND, closed.outcome)
        assertFalse(PlaybackReportPolicy.isBad(closed))

        val swiped = PlaybackReportPolicy.closeAfterExit(
            builder().started().snapshot(epoch, 10_000L),
            ProcessExitFacts(pid = 42, reason = "USER_REQUESTED"),
        )
        assertEquals(ReportOutcome.PROCESS_DIED, swiped.outcome)
        assertFalse(PlaybackReportPolicy.isBad(swiped))
    }

    @Test
    fun `SHR-PROD-18 the current process's reports and handled exits are left alone`() {
        val open = builder().started().snapshot(epoch, 10_000L)
        val result = PlaybackReportPolicy.reconcileExits(
            reports = listOf(open),
            exits = listOf(ProcessExitFacts(atEpochMillis = epoch, pid = 5, reason = "CRASH_NATIVE")),
            currentPid = 42,
            watermark = epoch,
            template = PlaybackReport(id = "", startedAtEpochMillis = 0),
            newId = { "new" },
        )
        assertTrue(result.updated.isEmpty())
        assertTrue(result.created.isEmpty())
    }

    @Test
    fun `SHR-PROD-06 text in reports never carries addresses, emails, or tokens`() {
        val cleaned = DiagnosticText.clean(
            "failed https://debrid.example/dl/abc?token=1 from 192.168.1.20:8080 for me@mail.com key " +
                "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0",
        )!!
        assertFalse(cleaned.contains("debrid"))
        assertFalse(cleaned.contains("192.168"))
        assertFalse(cleaned.contains("mail.com"))
        assertFalse(cleaned.contains("a1b2c3"))
        assertEquals("failed <url> from <ip> for <email> key <token>", cleaned)
        assertNull(DiagnosticText.clean("  "))
    }

    @Test
    fun `SHR-PROD-06 exception facts keep classes and frames, never messages`() {
        val error = IllegalStateException("https://secret.host/stream", java.io.IOException("token=abc"))
        val facts = DiagnosticText.exceptions(error)
        assertEquals(listOf("java.lang.IllegalStateException", "java.io.IOException"), facts.map { it.type })
        assertTrue(facts.first().frames.first().contains("PlaybackReportTest"))
        assertTrue(facts.none { fact -> fact.frames.any { it.contains("secret") || it.contains("token") } })
    }

    @Test
    fun `SHR-PROD-18 an ANR trace keeps only the main thread`() {
        val trace = """
            ----- pid 42 at 2026-10-13 -----
            "Signal Catcher" daemon prio=10 tid=2 Runnable
              at dalvik.system.VMStack.getThreadStackTrace(Native method)

            "main" prio=5 tid=1 Blocked
              | group="main" sCount=1 ucsCount=0 flags=1 obj=0x72
              at com.lamphaus.core.player.mpv.MpvLibrary.command(MpvLibrary.kt:120)
              - waiting to lock <0x0a1b> (a java.lang.Object) held by thread 31
              at com.lamphaus.core.player.mpv.MpvPlayer.load(MpvPlayer.kt:188)

            "OkHttp https://host.example/x" prio=5 tid=31 Native
              at okhttp3.internal.Thing.run(Thing.kt:1)
        """.trimIndent()

        assertEquals(
            listOf(
                "state Blocked",
                "at com.lamphaus.core.player.mpv.MpvLibrary.command(MpvLibrary.kt:120)",
                "- waiting to lock <0x0a1b> (a java.lang.Object) held by thread 31",
                "at com.lamphaus.core.player.mpv.MpvPlayer.load(MpvPlayer.kt:188)",
            ),
            DiagnosticText.anrMainThread(trace),
        )
    }

    @Test
    fun `SHR-PROD-18 recent playbacks give each bad report a denominator`() {
        val good = builder().started().close(20_000L, epoch, null)
        val bad = good.copy(id = "b", outcome = ReportOutcome.FAILED)
        val old = good.copy(id = "o", startedAtEpochMillis = epoch - 9 * day)
        val open = good.copy(id = "p", outcome = null)
        assertEquals(
            RecentPlaybacks(days = 7, playbacks = 2, bad = 1),
            PlaybackReportPolicy.recent(listOf(good, bad, old, open), epoch + day),
        )
    }

    @Test
    fun `SHR-PROD-18 formats read as one short line`() {
        assertEquals(
            "hvc1.2.4.L153 3840x2160 23.976fps HDR10 18.4Mbps",
            DecodedTrack(mime = "video/hevc", codecs = "hvc1.2.4.L153", width = 3840, height = 2160, frameRate = 23.976f, bitrate = 18_400_000, hdr = "HDR10").describe(),
        )
        assertEquals("eac3 6ch 640kbps", DecodedTrack(mime = "audio/eac3", channels = 6, bitrate = 640_000).describe())
        assertEquals("avc 24fps", DecodedTrack(mime = "video/avc", frameRate = 24f).describe())
    }
}
