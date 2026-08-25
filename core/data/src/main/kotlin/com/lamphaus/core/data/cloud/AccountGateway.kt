package com.lamphaus.core.data.cloud

import com.lamphaus.core.model.DeviceGrant
import com.lamphaus.core.model.PairingSession
import kotlinx.coroutines.flow.StateFlow

sealed interface AccountState {
    data object Loading : AccountState
    data object SignedOut : AccountState
    data class SignedIn(val userId: String, val displayName: String?, val email: String?) : AccountState
}

interface AccountGateway {
    val state: StateFlow<AccountState>

    /**
     * Exchanges a Google ID token for a platform session. The [nonce] must be the
     * raw random string whose SHA-256 hash was passed to the credential request.
     */
    suspend fun signInWithGoogleIdToken(idToken: String, nonce: String? = null): Result<Unit>
    suspend fun sendEmailLink(email: String): Result<Unit>
    suspend fun completeEmailLink(email: String, link: String): Result<Unit>
    suspend fun signOut()
}

interface PairingGateway {
    suspend fun createPairingSession(deviceLabel: String): Result<PairingSession>
    suspend fun claimPairingSession(shortCode: String): Result<Unit>
    suspend fun exchangeDeviceGrant(sessionId: String): Result<DeviceGrant>
    suspend fun revokeDevice(deviceId: String): Result<Unit>
}

