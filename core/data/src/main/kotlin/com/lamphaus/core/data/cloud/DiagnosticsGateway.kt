package com.lamphaus.core.data.cloud

import com.lamphaus.core.model.PlaybackReport

/** What became of one playback report handed to the reporting service (SHR-PROD-18). */
enum class DiagnosticsDelivery {
    /** Accepted; the service delivers it, retrying offline sends itself. */
    SENT,

    /** Reporting is not running now (no service configured, or the viewer has not opted in); try later. */
    UNAVAILABLE,
}

interface DiagnosticsGateway {
    suspend fun send(report: PlaybackReport): DiagnosticsDelivery
}

/** Builds without a reporting service keep reports on the device only. */
object LocalDiagnosticsGateway : DiagnosticsGateway {
    override suspend fun send(report: PlaybackReport) = DiagnosticsDelivery.UNAVAILABLE
}
