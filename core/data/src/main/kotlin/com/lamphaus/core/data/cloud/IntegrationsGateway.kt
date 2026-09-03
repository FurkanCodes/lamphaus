package com.lamphaus.core.data.cloud

import com.lamphaus.core.model.IntegrationStatus

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Third-party integrations whose credentials must never re-enter the client
 * (SHR-PROD-06). Unlike provider configs, list responses expose only
 * connection state and enabled sources — never the stored secret.
 */
interface IntegrationsGateway {
    suspend fun statuses(userId: String): Result<List<IntegrationStatus>>
    suspend fun saveCredential(userId: String, integration: String, credential: String): Result<Unit>
    suspend fun setEnabledSources(userId: String, integration: String, sources: List<String>): Result<Unit>
    suspend fun removeCredential(userId: String, integration: String): Result<Unit>
}

/** The server rejected the credential (MDBList key invalid). */
class IntegrationInvalidCredentialException :
    IllegalStateException("The integration credential was rejected.")

class SupabaseIntegrationsGateway(
    private val supabase: SupabaseClient,
    private val sessionRecovery: SupabaseSessionRecovery,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : IntegrationsGateway {

    override suspend fun statuses(userId: String): Result<List<IntegrationStatus>> = runCatching {
        sessionRecovery.withAuthRetry {
            val body = supabase.functions.buildEdgeFunction(FUNCTION_LIST_INTEGRATIONS)
                .invoke("{}") { contentType(ContentType.Application.Json) }
                .bodyOrThrow()
            json.decodeFromString<List<WireStatus>>(body).map(WireStatus::toModel)
        }
    }

    override suspend fun saveCredential(userId: String, integration: String, credential: String): Result<Unit> =
        runCatching {
            sessionRecovery.withAuthRetry {
                val response = supabase.functions.buildEdgeFunction(FUNCTION_SAVE_INTEGRATION_CREDENTIAL)
                    .invoke(json.encodeToString(WireCredentialSave(integration, credential))) {
                        contentType(ContentType.Application.Json)
                    }
                if (!response.status.isSuccess()) {
                    val body = response.bodyAsText()
                    val code = extractFunctionErrorCode(json, body)
                    if (response.status.value == 400 && code == CODE_INVALID_CREDENTIAL) {
                        throw IntegrationInvalidCredentialException()
                    }
                    throw SupabaseFunctionException(
                        statusCode = response.status.value,
                        responseCode = code,
                        message = "save integration returned ${response.status.value}: " +
                            CloudLog.clamp(CloudLog.sanitize(body)),
                    )
                }
            }
        }

    override suspend fun setEnabledSources(userId: String, integration: String, sources: List<String>): Result<Unit> =
        runCatching {
            sessionRecovery.withAuthRetry {
                supabase.functions.buildEdgeFunction(FUNCTION_SAVE_INTEGRATION_CREDENTIAL)
                    .invoke(json.encodeToString(WireSourcesUpdate(integration, sources))) {
                        contentType(ContentType.Application.Json)
                    }
                    .bodyOrThrow()
            }
        }

    override suspend fun removeCredential(userId: String, integration: String): Result<Unit> = runCatching {
        sessionRecovery.withAuthRetry {
            supabase.functions.buildEdgeFunction(FUNCTION_DELETE_INTEGRATION_CREDENTIAL)
                .invoke(json.encodeToString(WireIntegrationDelete(integration))) {
                    contentType(ContentType.Application.Json)
                }
                .bodyOrThrow()
        }
    }

    // ── wire format (contract with supabase/functions/*) ─────────────────

    @Serializable
    private data class WireStatus(
        val integration: String,
        val connected: Boolean = false,
        val valid: Boolean? = null,
        @SerialName("enabledSources") val enabledSources: List<String> = emptyList(),
    ) {
        fun toModel() = IntegrationStatus(
            integration = integration,
            connected = connected,
            valid = valid,
            enabledSources = enabledSources,
        )
    }

    @Serializable
    private data class WireCredentialSave(val integration: String, val credential: String)

    @Serializable
    private data class WireSourcesUpdate(val integration: String, val enabledSources: List<String>)

    @Serializable
    private data class WireIntegrationDelete(val integration: String)

    private suspend fun io.ktor.client.statement.HttpResponse.bodyOrThrow(): String {
        val body = bodyAsText()
        if (!status.isSuccess()) {
            throw SupabaseFunctionException(
                statusCode = status.value,
                responseCode = extractFunctionErrorCode(json, body),
                message = "edge function returned ${status.value}: ${CloudLog.clamp(CloudLog.sanitize(body))}",
            )
        }
        return body
    }

    private companion object {
        const val FUNCTION_LIST_INTEGRATIONS = "list-integrations"
        const val FUNCTION_SAVE_INTEGRATION_CREDENTIAL = "save-integration-credential"
        const val FUNCTION_DELETE_INTEGRATION_CREDENTIAL = "delete-integration-credential"
        const val CODE_INVALID_CREDENTIAL = "invalid_credential"
    }
}

private fun unexpectedSaveCancellation(error: Throwable): Boolean = error is CancellationException
