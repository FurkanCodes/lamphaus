package com.lamphaus.core.data.cloud

import com.lamphaus.core.model.DeviceGrant
import com.lamphaus.core.model.PairedDevice
import com.lamphaus.core.model.PairingSession
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
    private val sessionRecovery: SupabaseSessionRecovery,
) : PairingGateway {

    override suspend fun createPairingSession(deviceLabel: String, deviceKey: String?): Result<PairingSession> =
        CloudLog.tracedResult("pairing.create", "label=$deviceLabel") {
            val body = invoke("create-pairing-session") {
                put("device_label", deviceLabel)
                if (!deviceKey.isNullOrBlank()) put("device_key", deviceKey)
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
            sessionRecovery.withAuthRetry {
                invoke("claim-pairing-session") { put("code", shortCode) }
            }
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
                ).also { CloudLog.d("pairing.exchange ← GRANTED device=${it.deviceId}") }
                else -> {
                    CloudLog.w("pairing.exchange ← unexpected status=${body.string("status")}")
                    error("grant_${body.string("status")}")
                }
            }
        }.mapTerminalPairingFailures()

    /**
     * The server answers terminal pairing states as 409/410; surface them as
     * values so the poll loop can react immediately instead of polling a dead
     * session until the local timer expires. Everything else stays a failure
     * (network hiccups keep polling, as before).
     */
    private fun Result<DeviceGrant>.mapTerminalPairingFailures(): Result<DeviceGrant> =
        recoverCatching { error ->
            when ((error as? SupabaseFunctionException)?.statusCode) {
                HTTP_GONE -> DeviceGrant.Expired
                HTTP_CONFLICT -> DeviceGrant.Consumed
                else -> throw error
            }
        }

    override suspend fun registerDeviceSession(deviceId: String): Result<Unit> =
        CloudLog.tracedResult("devices.register", "device=$deviceId") {
            sessionRecovery.withAuthRetry {
                supabase.postgrest.rpc(
                    "register_device_session",
                    buildJsonObject { put("p_device_id", deviceId) },
                )
            }
            Unit
        }

    override suspend fun listDevices(): Result<List<PairedDevice>> =
        CloudLog.tracedResult("devices.list") {
            sessionRecovery.withAuthRetry {
                supabase.from("devices")
                    .select()
                    .decodeList<DeviceRow>()
                    .filter { !it.revoked }
                    .map { it.toModel() }
                    .also { CloudLog.d("devices.list ← ${it.size} paired") }
            }
        }

    override suspend fun revokeDevice(deviceId: String): Result<Unit> =
        CloudLog.tracedResult("devices.revoke", "device=$deviceId") {
            sessionRecovery.withAuthRetry {
                invoke("revoke-device") { put("device_id", deviceId) }
            }
            Unit
        }

    private suspend fun invoke(
        functionName: String,
        block: JsonObjectBuilder.() -> Unit,
    ): JsonObject {
        // EdgeFunction.invoke hands raw bodies to Ktor without a serializer or
        // Content-Type ("Fail to prepare request body"), so we pre-serialize
        // ourselves and declare the type explicitly.
        val payload = buildJsonObject(block).toString()
        val startedAt = android.os.SystemClock.elapsedRealtime()
        val response = supabase.functions.buildEdgeFunction(functionName).invoke(payload) {
            contentType(ContentType.Application.Json)
        }
        val text = response.bodyAsText()
        CloudLog.d(
            "functions.$functionName ← HTTP ${response.status.value} " +
                "(${android.os.SystemClock.elapsedRealtime() - startedAt}ms) " +
                CloudLog.clamp(CloudLog.sanitize(text)),
        )
        if (!response.status.isSuccess()) {
            val responseCode = extractFunctionErrorCode(json, text)
            throw SupabaseFunctionException(
                statusCode = response.status.value,
                responseCode = responseCode,
                message = "edge function returned ${response.status.value}: ${CloudLog.clamp(CloudLog.sanitize(text))}",
            )
        }
        return json.parseToJsonElement(text).jsonObject
    }

    private fun JsonObject.string(name: String): String =
        requireNotNull(get(name)?.jsonPrimitive?.contentOrNull) { "Missing '$name' in pairing response" }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        const val HTTP_CONFLICT = 409
        const val HTTP_GONE = 410
    }
}

@Serializable
internal data class DeviceRow(
    @SerialName("id") val id: String,
    @SerialName("label") val label: String,
    @SerialName("platform") val platform: String = "android-tv",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("revoked") val revoked: Boolean = false,
) {
    fun toModel() = PairedDevice(id = id, label = label, platform = platform, createdAt = createdAt)
}
