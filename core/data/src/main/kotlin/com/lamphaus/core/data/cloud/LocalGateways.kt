package com.lamphaus.core.data.cloud

import com.lamphaus.core.model.DeviceGrant
import com.lamphaus.core.model.PairedDevice
import com.lamphaus.core.model.PairingSession
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LocalAccountGateway : AccountGateway {
    private val mutableState = MutableStateFlow<AccountState>(AccountState.SignedOut)
    override val state: StateFlow<AccountState> = mutableState.asStateFlow()

    fun openDevelopmentSession() {
        mutableState.value = AccountState.SignedIn("local-development", "Local viewer", null)
    }

    override suspend fun signInWithGoogleIdToken(idToken: String, nonce: String?) = Result.failure<Unit>(CloudNotConfiguredException())
    override suspend fun sendEmailLink(email: String) = Result.failure<Unit>(CloudNotConfiguredException())
    override suspend fun completeEmailLink(email: String, link: String) = Result.failure<Unit>(CloudNotConfiguredException())
    override suspend fun deleteAccount() = Result.failure<Unit>(CloudNotConfiguredException())
    override suspend fun signOut() { mutableState.value = AccountState.SignedOut }
}

class LocalPairingGateway : PairingGateway {
    override suspend fun createPairingSession(deviceLabel: String, deviceKey: String?): Result<PairingSession> {
        val id = UUID.randomUUID().toString()
        return Result.success(
            PairingSession(
                id = id,
                shortCode = id.take(6).uppercase(),
                qrPayload = "lamphaus://pair/$id",
                expiresAtEpochMillis = System.currentTimeMillis() + 5 * 60_000,
            ),
        )
    }

    override suspend fun claimPairingSession(shortCode: String) = Result.failure<Unit>(CloudNotConfiguredException())
    override suspend fun exchangeDeviceGrant(sessionId: String) = Result.failure<DeviceGrant>(CloudNotConfiguredException())
    override suspend fun registerDeviceSession(deviceId: String) = Result.success(Unit)
    override suspend fun listDevices() = Result.failure<List<PairedDevice>>(CloudNotConfiguredException())
    override suspend fun revokeDevice(deviceId: String) = Result.success(Unit)
}

class CloudNotConfiguredException : IllegalStateException("Cloud services are not configured for this build.")

class ArtworkKeysNotConfiguredException :
    IllegalStateException("Artwork keys are not configured.")

