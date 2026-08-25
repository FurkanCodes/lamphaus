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

    override suspend fun createPairingSession(deviceLabel: String): Result<PairingSession> = runCatching {
        val body = invoke("create-pairing-session") {
            put("device_label", deviceLabel)
        }
        PairingSession(
            id = body.string("session_id"),
            shortCode = body.string("short_code"),
            qrPayload = body.string("qr_payload"),
            expiresAtEpochMillis = Instant.parse(body.string("expires_at")).toEpochMilli(),
        )
    }

    override suspend fun claimPairingSession(shortCode: String): Result<Unit> = runCatching {
        invoke("claim-pairing-session") { put("code", shortCode) }
        Unit
    }

    override suspend fun exchangeDeviceGrant(sessionId: String): Result<DeviceGrant> = runCatching {
        val body = invoke("exchange-device-grant") {
            put("session_id", sessionId)
        }
        when (body.string("status")) {
            "pending" -> DeviceGrant.Pending
            "granted" -> DeviceGrant.Granted(
                email = body.string("email"),
                otp = body.string("otp"),
                deviceId = body.string("device_id"),
            )
            else -> error("grant_${body.string("status")}")
        }
    }

    // Revocation travels through its own endpoint in M6 (plan F6).
    override suspend fun revokeDevice(deviceId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Revoke ships with device management in M6."))

    private suspend fun invoke(
        functionName: String,
        block: JsonObjectBuilder.() -> Unit,
    ): JsonObject {
        val response = supabase.functions.buildEdgeFunction(functionName)
            .invoke(buildJsonObject(block))
        return JSON.parseToJsonElement(response.bodyAsText()).jsonObject
    }

    private fun JsonObject.string(name: String): String =
        requireNotNull(get(name)?.jsonPrimitive?.contentOrNull) { "Missing '$name' in pairing response" }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
