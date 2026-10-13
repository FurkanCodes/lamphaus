package com.lamphaus.core.data.cloud

import com.lamphaus.core.model.PlaybackReport
import com.lamphaus.core.model.ReportEvent
import com.lamphaus.core.model.ReportEventType
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** SHR-PROD-18: an eventful report is trimmed to fit the table instead of being refused. */
class DiagnosticsPayloadTest {
    private fun report(events: Int) = PlaybackReport(
        id = "r",
        startedAtEpochMillis = 1,
        events = List(events) { index ->
            ReportEvent(at = index.toLong(), type = ReportEventType.LOAD_ERROR, detail = "media SocketTimeoutException after=8000ms #$index")
        },
    )

    @Test
    fun `SHR-PROD-18 large reports keep their start and end within the payload limit`() {
        val payload = report(600).fittingPayload()
        val size = payload.toString().encodeToByteArray().size
        assertTrue("payload was $size bytes", size <= SupabaseDiagnosticsGateway.MAX_PAYLOAD_BYTES)
        val events = payload.jsonObject.getValue("events").jsonArray
        assertEquals(0, events.first().jsonObject.getValue("at").jsonPrimitive.int)
        assertEquals(599, events.last().jsonObject.getValue("at").jsonPrimitive.int)
        assertEquals(600 - events.size, payload.jsonObject.getValue("omittedEvents").jsonPrimitive.int)
    }

    @Test
    fun `SHR-PROD-18 small reports are sent whole`() {
        val payload = report(10).fittingPayload()
        assertEquals(10, payload.jsonObject.getValue("events").jsonArray.size)
    }
}
