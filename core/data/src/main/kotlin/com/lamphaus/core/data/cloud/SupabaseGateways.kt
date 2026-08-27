package com.lamphaus.core.data.cloud

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionSource
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
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
    private val sessionRecovery: SupabaseSessionRecovery,
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
                        if (status.source is SessionSource.Storage) {
                            when (sessionRecovery.refreshRestoredSession(status.session.accessToken)) {
                                SessionRefreshResult.REFRESHED ->
                                    CloudLog.d("auth.restore refresh accepted; waiting for SDK refresh state")
                                SessionRefreshResult.RETRYABLE_FAILURE -> {
                                    mutableState.value = status.session.toAccountState()
                                    CloudLog.w("auth.restore refresh deferred; keeping stored account state")
                                }
                                SessionRefreshResult.TERMINAL_FAILURE ->
                                    CloudLog.i("auth.restore refresh terminal; waiting for NotAuthenticated")
                            }
                        } else {
                            mutableState.value = status.session.toAccountState()
                                .also { CloudLog.i("auth.state → ${it::class.simpleName}") }
                        }
                    }
                    SessionStatus.Initializing -> {
                        CloudLog.d("auth.status ← Initializing")
                        mutableState.value = AccountState.Loading
                    }
                    is SessionStatus.NotAuthenticated -> {
                        CloudLog.i("auth.status ← NotAuthenticated (${status::class.simpleName}) → SignedOut")
                        mutableState.value = AccountState.SignedOut
                    }
                    is SessionStatus.RefreshFailure -> {
                        CloudLog.w("auth.status ← RefreshFailure (${status.cause::class.simpleName}); keeping last account state")
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
            sessionRecovery.withAuthRetry { invokeDeleteAccount() }
        }

    private suspend fun invokeDeleteAccount() {
        supabase.functions.buildEdgeFunction("delete-account").invoke("{}") {
            contentType(ContentType.Application.Json)
        }
        // Account deletion is an explicit destructive action; remote sign-out
        // remains intentional here rather than part of automatic recovery.
        supabase.auth.signOut()
    }
    override suspend fun signOut() {
        runCatching { supabase.auth.signOut() }
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
