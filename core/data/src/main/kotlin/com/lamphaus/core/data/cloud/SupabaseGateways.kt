package com.lamphaus.core.data.cloud

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Supabase-backed account state. Mirrors GoTrue session status into [AccountState]
 * and persists sessions across launches through the platform session manager.
 */
class SupabaseAccountGateway(
    private val supabase: SupabaseClient,
) : AccountGateway {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow<AccountState>(AccountState.Loading)
    override val state: StateFlow<AccountState> = mutableState.asStateFlow()

    init {
        scope.launch {
            supabase.auth.sessionStatus.collect { status ->
                // Never log $status directly — UserSession.toString() carries tokens.
                when (status) {
                    is SessionStatus.Authenticated -> {
                        CloudLog.i("auth.status ← Authenticated user=${status.session.user?.id}")
                        mutableState.value = status.session.toAccountState()
                            .also { CloudLog.i("auth.state → ${it::class.simpleName}") }
                        validateRestoredSessionOnce(status.session.accessToken)
                    }
                    SessionStatus.Initializing -> {
                        CloudLog.d("auth.status ← Initializing")
                        mutableState.value = AccountState.Loading
                    }
                    is SessionStatus.NotAuthenticated -> {
                        CloudLog.i("auth.status ← NotAuthenticated (${status::class.simpleName}) → SignedOut")
                        mutableState.value = AccountState.SignedOut
                    }
                    // A failed refresh keeps the last known state: transient network
                    // issues must not sign the user out. But a SERVER rejection
                    // means the session is gone (revoked from another device,
                    // plan F3) — drop to SignedOut so the TV shows its QR again.
                    is SessionStatus.RefreshFailure -> {
                        val detail = when (val reason = status.cause) {
                            is RefreshFailureCause.NetworkError ->
                                "network ${reason.exception::class.simpleName}: ${reason.exception.message}"
                            else -> reason::class.simpleName
                        }
                        CloudLog.w("auth.status ← RefreshFailure ($detail)")
                        if (status.cause !is RefreshFailureCause.NetworkError) {
                            mutableState.value = AccountState.SignedOut
                            CloudLog.i("auth.state → SignedOut (session rejected server-side)")
                        }
                    }
                }
            }
        }
    }

    override suspend fun signInWithGoogleIdToken(idToken: String, nonce: String?): Result<Unit> =
        CloudLog.tracedResult("auth.signInWithGoogle", "idToken=<${idToken.length} chars> hasNonce=${nonce != null}") {
            require(idToken.isNotBlank())
            supabase.auth.signInWith(IDToken) {
                this.idToken = idToken
                provider = Google
                nonce?.let { this.nonce = it }
            }
            Unit
        }

    override suspend fun sendEmailLink(email: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Magic link sign-in lands in milestone M7."))

    override suspend fun completeEmailLink(email: String, link: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Magic link sign-in lands in milestone M7."))

    override suspend fun deleteAccount(): Result<Unit> =
        CloudLog.tracedResult("account.delete") {
            supabase.functions.buildEdgeFunction("delete-account").invoke("{}") {
                contentType(ContentType.Application.Json)
            }
            supabase.auth.signOut()
            Unit
        }

    override suspend fun signOut() {
        runCatching { supabase.auth.signOut() }
    }

    /**
     * Restored sessions are trusted only after GoTrue confirms the user
     * still exists: a deleted account leaves behind tokens that look valid
     * for up to an hour, and storage restore reports Authenticated without
     * asking the server. One probe per process; a SERVER rejection clears
     * the dead session immediately, while a NETWORK error keeps the state
     * untouched (plan F3 — offline must never sign anyone out).
     */
    private var startupValidationStarted = false
    private fun validateRestoredSessionOnce(accessToken: String) {
        if (startupValidationStarted) return
        startupValidationStarted = true
        scope.launch {
            try {
                supabase.auth.retrieveUser(accessToken)
                CloudLog.d("auth.restore validated with server")
            } catch (e: RestException) {
                CloudLog.w("auth.restore rejected by server (${e::class.simpleName}) → clearing dead session")
                mutableState.value = AccountState.SignedOut
                    .also { CloudLog.i("auth.state → SignedOut") }
                runCatching { supabase.auth.signOut() }
            } catch (e: Exception) {
                CloudLog.d("auth.restore validation deferred (${e::class.simpleName})")
            }
        }
    }

    private fun UserSession.toAccountState(): AccountState {
        val user = user ?: return AccountState.SignedOut
        return AccountState.SignedIn(
            userId = user.id,
            displayName = user.displayName(),
            email = user.email,
        )
    }

    private fun UserInfo.displayName(): String? {
        val metadata = userMetadata.orEmpty()
        return metadata["name"]?.jsonPrimitive?.contentOrNull
            ?: metadata["full_name"]?.jsonPrimitive?.contentOrNull
            ?: metadata["given_name"]?.jsonPrimitive?.contentOrNull
    }
}
