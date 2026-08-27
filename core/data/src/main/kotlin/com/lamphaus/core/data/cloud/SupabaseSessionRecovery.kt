package com.lamphaus.core.data.cloud

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal enum class SessionRefreshResult {
    REFRESHED,
    RETRYABLE_FAILURE,
    TERMINAL_FAILURE,
}

private val TERMINAL_AUTH_ERRORS = setOf(
    AuthErrorCode.RefreshTokenNotFound,
    AuthErrorCode.RefreshTokenAlreadyUsed,
    AuthErrorCode.SessionExpired,
    AuthErrorCode.SessionNotFound,
    AuthErrorCode.UserNotFound,
    AuthErrorCode.UserBanned,
    AuthErrorCode.BadJwt,
)

internal fun isTerminalAuthError(code: AuthErrorCode?): Boolean = code in TERMINAL_AUTH_ERRORS
private val EXPIRED_JWT_TEXT = Regex(
    "\\b(?:expired|invalid)\\s+(?:jwt|token)\\b|\\b(?:jwt|token)\\s+(?:is\\s+)?(?:expired|invalid)\\b",
    RegexOption.IGNORE_CASE,
)
private val FUTURE_JWT_TEXT = Regex(
    "\\b(?:jwt|token)\\s+(?:was\\s+)?issued\\s+at\\s+future\\b|\\bissued\\s+at\\s+future\\b",
    RegexOption.IGNORE_CASE,
)

internal fun isExpiredPostgrestJwt(code: String?, text: String): Boolean =
    code == "PGRST303" && EXPIRED_JWT_TEXT.containsMatchIn(text)

internal fun isFuturePostgrestJwt(code: String?, text: String): Boolean =
    code == "PGRST303" && FUTURE_JWT_TEXT.containsMatchIn(text)

/**
 * The refresh-token exchange is serialized because Supabase refresh tokens are
 * single-use outside the configured reuse interval.
 */
class SupabaseSessionRecovery internal constructor(
    private val currentAccessToken: () -> String?,
    private val refreshCurrentSession: suspend () -> Unit,
    private val clearSession: suspend () -> Unit,
    private val delayMillis: suspend (Long) -> Unit,
    private val logger: (String) -> Unit,
) {
    constructor(supabase: SupabaseClient) : this(
        currentAccessToken = { supabase.auth.currentAccessTokenOrNull() },
        refreshCurrentSession = { supabase.auth.refreshCurrentSession() },
        clearSession = { supabase.auth.clearSession() },
        delayMillis = { delay(it) },
        logger = { CloudLog.d(it) },
    )

    private val mutex = Mutex()

    internal suspend fun refreshRestoredSession(storedAccessToken: String): SessionRefreshResult = mutex.withLock {
        val currentToken = currentAccessToken()
        when {
            currentToken == null -> {
                logger("auth.recovery restore refresh deferred: no current session")
                SessionRefreshResult.TERMINAL_FAILURE
            }
            currentToken != storedAccessToken -> {
                logger("auth.recovery restore refresh deferred: concurrent caller replaced token")
                SessionRefreshResult.REFRESHED
            }
            else -> refreshLocked()
        }
    }

    internal suspend fun <T> withAuthRetry(block: suspend () -> T): T {
        var accessTokenRetried = false
        var clockSkewRetries = 0
        while (true) {
            val requestAccessToken = currentAccessToken()
            try {
                return block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                if (error.isFutureJwtRejection() && clockSkewRetries < FUTURE_JWT_RETRY_ATTEMPTS - 1) {
                    val backoff = FUTURE_JWT_BACKOFF_MILLIS shl clockSkewRetries
                    clockSkewRetries++
                    logger("auth.recovery clock-skew retry in ${backoff}ms")
                    delayMillis(backoff)
                    continue
                }
                if (accessTokenRetried || !error.isAccessTokenRejection()) throw error

                accessTokenRetried = true
                logger("auth.recovery request rejected: refreshing session")
                when (refreshRestoredSession(requestAccessToken.orEmpty())) {
                    SessionRefreshResult.REFRESHED ->
                        logger("auth.recovery request retrying with refreshed session")
                    SessionRefreshResult.RETRYABLE_FAILURE -> {
                        logger("auth.recovery request refresh deferred")
                        throw error
                    }
                    SessionRefreshResult.TERMINAL_FAILURE -> {
                        logger("auth.recovery request refresh rejected: session cleared")
                        throw error
                    }
                }
            }
        }
    }

    private suspend fun refreshLocked(): SessionRefreshResult {
        logger("auth.recovery restore refresh started")
        return try {
            refreshCurrentSession()
            logger("auth.recovery restore refresh succeeded")
            SessionRefreshResult.REFRESHED
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            when (error.refreshClassification()) {
                SessionRefreshResult.TERMINAL_FAILURE -> {
                    logger("auth.recovery restore refresh rejected: clearing local session")
                    try {
                        clearSession()
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Throwable) {
                        // The session is terminal even if local storage cleanup fails.
                    }
                    SessionRefreshResult.TERMINAL_FAILURE
                }
                SessionRefreshResult.RETRYABLE_FAILURE -> {
                    logger("auth.recovery restore refresh deferred (${error.safeAuthFailure()})")
                    SessionRefreshResult.RETRYABLE_FAILURE
                }
                SessionRefreshResult.REFRESHED -> error("Unexpected refresh classification")
            }
        }
    }

    private fun Throwable.refreshClassification(): SessionRefreshResult {
        val authError = this as? AuthRestException
        return when {
            isTerminalAuthError(authError?.errorCode) -> SessionRefreshResult.TERMINAL_FAILURE
            authError?.errorCode == AuthErrorCode.RequestTimeout ||
                authError?.errorCode == AuthErrorCode.Conflict ||
                authError?.statusCode == HTTP_TOO_MANY_REQUESTS ||
                authError?.statusCode in HTTP_SERVER_ERROR_RANGE -> SessionRefreshResult.RETRYABLE_FAILURE
            this is RestException && statusCode in HTTP_SERVER_ERROR_RANGE -> SessionRefreshResult.RETRYABLE_FAILURE
            else -> SessionRefreshResult.RETRYABLE_FAILURE
        }
    }

    private fun Throwable.safeAuthFailure(): String = when (this) {
        is AuthRestException -> "code=${errorCode?.value ?: "unknown"} status=$statusCode"
        is RestException -> "status=$statusCode"
        else -> this::class.simpleName ?: "unknown"
    }

    private fun Throwable.isAccessTokenRejection(): Boolean = when (this) {
        is SupabaseFunctionException -> responseCode == UNAUTHORIZED_ASYMMETRIC_JWT
        is PostgrestRestException -> isExpiredPostgrestJwt(code, details?.toString() ?: description.orEmpty())
        is RestException -> statusCode == HTTP_UNAUTHORIZED && isStructuredAccessRejection()
        else -> false
    }

    private fun RestException.isStructuredAccessRejection(): Boolean {
        val body = error.trim()
        val parsed = runCatching { responseJson.parseToJsonElement(body).jsonObject }.getOrNull()
        val code = body.trim('"')
            .takeIf { it == BAD_JWT || it == UNAUTHORIZED_ASYMMETRIC_JWT || it == POSTGREST_JWT_ERROR }
            ?: parsed?.get("code")?.jsonPrimitive?.contentOrNull
        if (code == BAD_JWT || code == UNAUTHORIZED_ASYMMETRIC_JWT) return true
        val details = parsed?.get("details")?.jsonPrimitive?.contentOrNull
        return isExpiredPostgrestJwt(code, details.orEmpty()) ||
            EXPIRED_JWT_TEXT.containsMatchIn(description.orEmpty())
    }

    private fun Throwable.isFutureJwtRejection(): Boolean =
        this is PostgrestRestException && isFuturePostgrestJwt(code, details?.toString() ?: description.orEmpty())

    private companion object {
        val responseJson = Json { ignoreUnknownKeys = true }
        const val POSTGREST_JWT_ERROR = "PGRST303"
        const val UNAUTHORIZED_ASYMMETRIC_JWT = "UNAUTHORIZED_ASYMMETRIC_JWT"
        const val BAD_JWT = "bad_jwt"
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HTTP_SERVER_ERROR_START = 500
        const val HTTP_SERVER_ERROR_END = 599
        val HTTP_SERVER_ERROR_RANGE = HTTP_SERVER_ERROR_START..HTTP_SERVER_ERROR_END
        const val FUTURE_JWT_RETRY_ATTEMPTS = 3
        const val FUTURE_JWT_BACKOFF_MILLIS = 2_000L
    }
}

internal class SupabaseFunctionException(
    val statusCode: Int,
    val responseCode: String?,
    message: String,
) : Exception(message)

/** Edge Functions answer errors as non-2xx JSON with a machine-readable code/error field. */
internal fun extractFunctionErrorCode(json: Json, body: String): String? = runCatching {
    val response = json.parseToJsonElement(body).jsonObject
    response["code"]?.jsonPrimitive?.contentOrNull ?: response["error"]?.jsonPrimitive?.contentOrNull
}.getOrNull()
