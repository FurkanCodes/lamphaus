package com.lamphaus.core.data.cloud

import com.lamphaus.core.data.diagnostics.PlaybackReportStore
import com.lamphaus.core.model.PlaybackReport
import com.lamphaus.core.model.ReportDelivery
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** What became of one report sent to the backend (SHR-PROD-18). */
enum class DiagnosticsDelivery {
    SENT,

    /** The backend refused this report for good (too large, malformed); it is not tried again. */
    REJECTED,

    /** The account sent its daily share; try again on a later launch. */
    RATE_LIMITED,

    /** No cloud, no network, or no session right now; try again later. */
    UNAVAILABLE,
}

interface DiagnosticsGateway {
    suspend fun send(userId: String, report: PlaybackReport): DiagnosticsDelivery
}

/** Builds without a cloud keep reports on the device only. */
object LocalDiagnosticsGateway : DiagnosticsGateway {
    override suspend fun send(userId: String, report: PlaybackReport) = DiagnosticsDelivery.UNAVAILABLE
}

/**
 * Inserts reports into the write-only `diagnostic_reports` table. The client
 * can add rows for its own account and read nothing back; the database caps
 * each account's daily reports and deletes them after 30 days.
 */
class SupabaseDiagnosticsGateway(
    private val supabase: SupabaseClient,
    private val sessionRecovery: SupabaseSessionRecovery,
    private val installationId: String,
) : DiagnosticsGateway {

    override suspend fun send(userId: String, report: PlaybackReport): DiagnosticsDelivery = try {
        CloudLog.traced("diagnostics.send", "kind=${report.kind} outcome=${report.outcome}") {
            sessionRecovery.withAuthRetry {
                supabase.from(TABLE).insert(listOf(DiagnosticReportRow.of(userId, installationId, report)))
            }
        }
        DiagnosticsDelivery.SENT
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: PostgrestRestException) {
        when {
            // Already there: an earlier send landed but its answer did not.
            error.code == UNIQUE_VIOLATION -> DiagnosticsDelivery.SENT
            error.statusCode == RATE_LIMITED_STATUS -> DiagnosticsDelivery.RATE_LIMITED
            // No session yet, or a backend without the table: keep the report for later.
            error.statusCode == 401 || error.statusCode == 403 || error.statusCode == 404 -> DiagnosticsDelivery.UNAVAILABLE
            error.statusCode in 400..499 -> DiagnosticsDelivery.REJECTED
            else -> DiagnosticsDelivery.UNAVAILABLE
        }
    } catch (error: RestException) {
        if (error.statusCode == RATE_LIMITED_STATUS) DiagnosticsDelivery.RATE_LIMITED else DiagnosticsDelivery.UNAVAILABLE
    } catch (_: Exception) {
        DiagnosticsDelivery.UNAVAILABLE
    }

    @Serializable
    private data class DiagnosticReportRow(
        val id: String,
        @SerialName("user_id") val userId: String,
        @SerialName("installation_id") val installationId: String,
        val kind: String,
        val outcome: String?,
        @SerialName("app_version") val appVersion: String,
        @SerialName("device_model") val deviceModel: String,
        val sdk: Int,
        val television: Boolean,
        @SerialName("started_at_epoch_millis") val startedAtEpochMillis: Long,
        val payload: JsonElement,
    ) {
        companion object {
            fun of(userId: String, installationId: String, report: PlaybackReport) = DiagnosticReportRow(
                id = report.id,
                userId = userId,
                installationId = installationId,
                kind = report.kind.name.lowercase(),
                outcome = report.outcome?.name?.lowercase(),
                appVersion = report.app.versionName,
                deviceModel = "${report.device.manufacturer} ${report.device.model}".trim(),
                sdk = report.device.sdk,
                television = report.device.television,
                startedAtEpochMillis = report.startedAtEpochMillis,
                // Delivery state and the process id only mean something on the device.
                payload = report.copy(delivery = ReportDelivery.PENDING, pid = 0).fittingPayload(),
            )
        }
    }

    internal companion object {
        /**
         * Below the table's 32 KB check with room to spare: Postgres prints
         * jsonb with a space after every colon and comma.
         */
        const val MAX_PAYLOAD_BYTES = 26_000

        const val TABLE = "diagnostic_reports"
        const val UNIQUE_VIOLATION = "23505"

        /** The table's daily cap raises SQLSTATE PT429, which PostgREST answers with 429. */
        const val RATE_LIMITED_STATUS = 429
    }
}

/**
 * The report as JSON, with events dropped from the middle until it fits
 * [maxBytes]: the eventful playbacks are the ones worth sending.
 */
internal fun PlaybackReport.fittingPayload(maxBytes: Int = SupabaseDiagnosticsGateway.MAX_PAYLOAD_BYTES): JsonElement {
    var report = this
    while (true) {
        val element = PlaybackReportStore.JSON.encodeToJsonElement(PlaybackReport.serializer(), report)
        if (element.toString().encodeToByteArray().size <= maxBytes || report.events.size <= 2) return element
        val keepFirst = minOf(40, report.events.size / 4)
        val drop = maxOf(1, (report.events.size - keepFirst) / 4)
        report = report.copy(
            events = report.events.take(keepFirst) + report.events.drop(keepFirst + drop),
            omittedEvents = report.omittedEvents + drop,
        )
    }
}
