package com.lamphaus.core.data.cloud

import com.lamphaus.core.model.DeviceGrant
import com.lamphaus.core.model.PairingSession
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Instant

/**
 * M4 pairing against the create/claim/exchange Edge Functions (plan F2).
 *
 * create/exchange are unauthenticated by design (rate-limited, single-use
 * codes server-side); claim relies on the SDK injecting the caller's bearer
 * token into the request pipeline. The grant returned by exchange is
 * single-use and must be consumed via Auth.verifyEmailOtp immediately.
 */
class SupabasePairingGateway(
    private val supabase: SupabaseClient,
) : PairingGateway {

    override suspend fun createPairingSession(deviceLabel: String): Result<PairingSession> =
        CloudLog.tracedResult("pairing.create", "label=$deviceLabel") {
            val body = invoke("create-pairing-session") {
                put("device_label", deviceLabel)
            }
            PairingSession(
                id = body.string("session_id"),
                shortCode = body.string("short_code"),
                qrPayload = body.string("qr_payload"),
                expiresAtEpochMillis = Instant.parse(body.string("expires_at")).toEpochMilli(),
            ).also {
                CloudLog.d(
                    "pairing.create ← session=${it.id} code=${it.shortCode} " +
                        "expiresIn=${it.expiresAtEpochMillis - System.currentTimeMillis()}ms",
                )
            }
        }

    override suspend fun claimPairingSession(shortCode: String): Result<Unit> =
        CloudLog.tracedResult("pairing.claim", "code=$shortCode") {
            invoke("claim-pairing-session") { put("code", shortCode) }
            Unit
        }

    override suspend fun exchangeDeviceGrant(sessionId: String): Result<DeviceGrant> =
        CloudLog.tracedResult("pairing.exchange", "session=$sessionId") {
            val body = invoke("exchange-device-grant") {
                put("session_id", sessionId)
            }
            when (body.string("status")) {
                "pending" -> DeviceGrant.Pending
                "granted" -> DeviceGrant.Granted(
                    email = body.string("email"),
                    otp = body.string("otp"),
                    deviceId = body.string("device_id"),
                ).also { CloudLog.d("pairing.exchange ← GRANTED device=${it.deviceId} email=${it.email}") }
                else -> error("grant_${body.string("status")}").also {
                    CloudLog.w("pairing.exchange ← unexpected status=${body.string("status")}")
                }
            }
        }

    // Revocation travels through its own endpoint in M6 (plan F6).
    override suspend fun revokeDevice(deviceId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Revoke ships with device management in M6."))

    private suspend fun invoke(
        functionName: String,
        block: JsonObjectBuilder.() -> Unit,
    ): JsonObject {
        val payload = buildJsonObject(block)
        val startedAt = android.os.SystemClock.elapsedRealtime()
        val response = supabase.functions.buildEdgeFunction(functionName).invoke(payload)
        val text = response.bodyAsText()
        CloudLog.d(
            "functions.$functionName ← HTTP ${response.status.value} " +
                "(${android.os.SystemClock.elapsedRealtime() - startedAt}ms) " +
                CloudLog.clamp(CloudLog.sanitize(text)),
        )
        return JSON.parseToJsonElement(text).jsonObject
    }

    private fun JsonObject.string(name: String): String =
        requireNotNull(get(name)?.jsonPrimitive?.contentOrNull) { "Missing '$name' in pairing response" }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
