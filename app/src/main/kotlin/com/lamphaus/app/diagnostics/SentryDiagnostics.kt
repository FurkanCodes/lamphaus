package com.lamphaus.app.diagnostics

import android.content.Context
import com.lamphaus.app.BuildConfig
import com.lamphaus.core.data.cloud.DiagnosticsDelivery
import com.lamphaus.core.data.cloud.DiagnosticsGateway
import com.lamphaus.core.data.diagnostics.PlaybackReportStore
import com.lamphaus.core.model.DiagnosticText
import com.lamphaus.core.model.DiagnosticsConsent
import com.lamphaus.core.model.PlaybackReport
import com.lamphaus.core.model.PlaybackReportPolicy
import com.lamphaus.core.model.ReportDelivery
import com.lamphaus.core.model.ReportEventType
import com.lamphaus.core.model.ReportOutcome
import io.sentry.Attachment
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import io.sentry.android.core.SentryAndroidOptions
import io.sentry.protocol.Message
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.util.Date

/**
 * Sentry, for viewers who opt in (SHR-PROD-18). It runs only while Crash
 * reports or Performance metrics is on: Crash reports turns on its Java,
 * native, and ANR crash capture; Performance metrics lets bad playback
 * reports through. Every event is scrubbed before it leaves (SHR-PROD-06):
 * no exception messages, no addresses in thread names or breadcrumbs, no
 * device name, IP address, screenshots, tracing, sessions, or replays.
 */
object SentryDiagnostics {
    private const val PLAYBACK_TAG = "report"
    private const val PLAYBACK = "playback"
    private const val MAX_BREADCRUMBS = 100
    private val CONTEXT_KEYS = listOf("closeReason", "totals", "state", "playback", "media", "exit", "recent", "omittedEvents")

    val configured: Boolean get() = BuildConfig.SENTRY_DSN.isNotBlank()

    @Volatile private var consent = DiagnosticsConsent()

    /** The switches Sentry was started with; null while it is stopped. */
    private var running: DiagnosticsConsent? = null
    private var taggedReport: String? = null
    private var taggedEngine: String? = null

    /** Starts, restarts, or stops Sentry to match [next]. Called off the main thread. */
    @Synchronized
    fun apply(context: Context, next: DiagnosticsConsent) {
        consent = next
        if (!configured) return
        val current = running
        if (current != null &&
            current.crashReports == next.crashReports &&
            current.performanceMetrics == next.performanceMetrics
        ) {
            return
        }
        if (current != null) {
            Sentry.close()
            running = null
            taggedReport = null
            taggedEngine = null
        }
        if (!next.crashReports && !next.performanceMetrics) return
        SentryAndroid.init(context.applicationContext) { options -> configure(options, next) }
        running = next
    }

    /**
     * Tags the open playback on Sentry's scope, so a crash or ANR during it
     * names the report and engine; native crashes and ANRs read the scope
     * Sentry keeps on disk.
     */
    @Synchronized
    fun onPlaybackReport(report: PlaybackReport, closed: Boolean) {
        if (running == null) return
        val id = report.id.takeUnless { closed }
        val engine = report.state.engine.takeUnless { closed }
        if (id != taggedReport) {
            if (id == null) Sentry.removeTag("playback_report") else Sentry.setTag("playback_report", id)
            taggedReport = id
        }
        if (engine != taggedEngine) {
            if (engine == null) Sentry.removeTag("playback_engine") else Sentry.setTag("playback_engine", engine)
            taggedEngine = engine
        }
    }

    /** Sends a bad playback as an event, grouped by what went wrong, with the full report attached. */
    fun capturePlayback(report: PlaybackReport): Boolean {
        if (!Sentry.isEnabled() || !consent.performanceMetrics) return false
        val outcome = report.outcome?.name ?: "OPEN"
        val cause = PlaybackReportPolicy.cause(report)
        // Through kotlinx-serialization, so names survive R8 (Sentry's own
        // reflection would send minified field names).
        val tree = PlaybackReportStore.JSON.encodeToJsonElement(
            PlaybackReport.serializer(),
            report.copy(delivery = ReportDelivery.PENDING, pid = 0),
        ).jsonObject
        val event = SentryEvent(Date(report.updatedAtEpochMillis)).apply {
            level = if (report.outcome == ReportOutcome.FAILED || report.outcome == ReportOutcome.PROCESS_DIED) {
                SentryLevel.ERROR
            } else {
                SentryLevel.WARNING
            }
            message = Message().apply { formatted = "Playback ${outcome.lowercase().replace('_', ' ')}: $cause" }
            fingerprints = listOf(PLAYBACK, outcome, cause)
            logger = PLAYBACK
            setTag(PLAYBACK_TAG, PLAYBACK)
            setTag("playback_report", report.id)
            setTag("outcome", outcome)
            setTag("cause", cause)
            report.state.engine?.let { setTag("engine", it) }
            report.playback?.let { playback ->
                setTag("start_engine", playback.startEngine)
                setTag("stream_type", playback.streamType)
                playback.settings["dolbyVision"]?.let { setTag("dolby_vision", it) }
            }
            report.media?.video?.let { video ->
                (video.mime ?: video.codecs)?.let { setTag("video_codec", it.substringAfter('/')) }
                video.height?.let { setTag("video_height", it.toString()) }
                video.hdr?.let { setTag("hdr", it) }
            }
            report.media?.audio?.mime?.let { setTag("audio_codec", it.substringAfter('/')) }
            report.exit?.reason?.let { setTag("exit_reason", it) }
            setTag("television", report.device.television.toString())
            contexts.put(
                PLAYBACK,
                CONTEXT_KEYS.mapNotNull { key -> tree[key]?.let { key to it.toPlain() } }.toMap(),
            )
            breadcrumbs = report.events.takeLast(MAX_BREADCRUMBS).map { event ->
                Breadcrumb(Date(report.startedAtEpochMillis + event.at)).apply {
                    category = PLAYBACK
                    message = listOfNotNull(event.type.name, event.detail).joinToString(" ")
                    level = when (event.type) {
                        ReportEventType.ERROR, ReportEventType.RECOVERY_GAVE_UP -> SentryLevel.ERROR
                        ReportEventType.LOAD_ERROR, ReportEventType.CODEC_ERROR, ReportEventType.RETRY,
                        ReportEventType.STALL_SKIP, ReportEventType.STALL_GAVE_UP, ReportEventType.ENGINE_FALLBACK,
                        ReportEventType.DOLBY_VISION_BASE_LAYER, ReportEventType.BUFFERING,
                        ReportEventType.DROPPED_FRAMES, ReportEventType.AUDIO_UNDERRUN, ReportEventType.LOW_MEMORY,
                        -> SentryLevel.WARNING
                        else -> SentryLevel.INFO
                    }
                    event.position?.let { setData("position_ms", it) }
                    event.value?.let { setData("value", it) }
                }
            }
        }
        Sentry.captureEvent(
            event,
            Hint.withAttachment(Attachment(tree.toString().toByteArray(), "playback-report.json", "application/json")),
        )
        return true
    }

    private fun configure(options: SentryAndroidOptions, consent: DiagnosticsConsent) {
        options.dsn = BuildConfig.SENTRY_DSN
        options.environment = BuildConfig.BUILD_TYPE
        options.isSendDefaultPii = false
        options.isAttachServerName = false
        // Crash capture follows Crash reports; playback events alone need none of it.
        options.isEnableUncaughtExceptionHandler = consent.crashReports
        options.isAnrEnabled = consent.crashReports
        options.isEnableNdk = consent.crashReports
        // Reports only: no sessions, tracing, profiling, screenshots, or view trees.
        options.isEnableAutoSessionTracking = false
        options.tracesSampleRate = null
        options.isEnableFramesTracking = false
        options.isEnableUserInteractionBreadcrumbs = false
        options.isEnableUserInteractionTracing = false
        options.isAttachScreenshot = false
        options.isAttachViewHierarchy = false
        options.beforeSend = SentryOptions.BeforeSendCallback { event, _ -> scrub(event) }
        options.beforeBreadcrumb = SentryOptions.BeforeBreadcrumbCallback { breadcrumb, _ -> scrub(breadcrumb) }
    }

    private fun scrub(event: SentryEvent): SentryEvent? {
        val current = consent
        val playback = event.getTag(PLAYBACK_TAG) == PLAYBACK
        if (playback && !current.performanceMetrics) return null
        // Crashes and ANRs, including ones Sentry kept from before the switch went on.
        if (!playback && (!current.crashReports || (event.timestamp?.time ?: 0L) < current.updatedAtEpochMillis)) return null
        // Messages can quote hosts, files, or a stream's own titles; types and frames stay.
        event.exceptions?.forEach { it.value = null }
        event.threads?.forEach { thread -> thread.name = DiagnosticText.clean(thread.name, 80) }
        event.breadcrumbs?.forEach(::scrub)
        event.user?.ipAddress = null
        event.request = null
        event.serverName = null
        event.contexts.device?.name = null
        return event
    }

    private fun JsonElement.toPlain(): Any? = when (this) {
        is JsonNull -> null
        is JsonPrimitive -> if (isString) content else booleanOrNull ?: longOrNull ?: doubleOrNull ?: content
        is JsonObject -> mapValues { (_, value) -> value.toPlain() }
        is JsonArray -> map { it.toPlain() }
    }

    private fun scrub(breadcrumb: Breadcrumb): Breadcrumb {
        breadcrumb.message = DiagnosticText.clean(breadcrumb.message, 200)
        breadcrumb.data.keys.toList().forEach { key ->
            (breadcrumb.data[key] as? String)?.let { value -> breadcrumb.setData(key, DiagnosticText.clean(value, 200) ?: "") }
        }
        return breadcrumb
    }
}

/** Hands bad playbacks to Sentry; reports wait on the device while it is not running. */
object SentryDiagnosticsGateway : DiagnosticsGateway {
    override suspend fun send(report: PlaybackReport): DiagnosticsDelivery =
        if (SentryDiagnostics.capturePlayback(report)) DiagnosticsDelivery.SENT else DiagnosticsDelivery.UNAVAILABLE
}
