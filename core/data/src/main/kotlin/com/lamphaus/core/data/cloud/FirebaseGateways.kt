package com.lamphaus.core.data.cloud

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.actionCodeSettings
import com.google.firebase.functions.FirebaseFunctions
import com.lamphaus.core.model.PairingSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

class FirebaseAccountGateway(
    private val auth: FirebaseAuth,
    private val emailLinkDomain: String,
) : AccountGateway {
    private val mutableState = MutableStateFlow(auth.currentUser.toAccountState())
    override val state: StateFlow<AccountState> = mutableState.asStateFlow()

    private val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        mutableState.value = firebaseAuth.currentUser.toAccountState()
    }

    init {
        auth.addAuthStateListener(listener)
    }

    override suspend fun signInWithGoogleIdToken(idToken: String): Result<Unit> = runCatching {
        require(idToken.isNotBlank())
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        Unit
    }

    override suspend fun sendEmailLink(email: String): Result<Unit> = runCatching {
        val settings = actionCodeSettings {
            url = "https://$emailLinkDomain/__/auth/links?email=${email.encodeForLink()}"
            handleCodeInApp = true
            setAndroidPackageName("com.lamphaus.app", true, "23")
        }
        auth.sendSignInLinkToEmail(email, settings).await()
        Unit
    }

    override suspend fun completeEmailLink(email: String, link: String): Result<Unit> = runCatching {
        require(auth.isSignInWithEmailLink(link))
        auth.signInWithEmailLink(email, link).await()
        Unit
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    private fun com.google.firebase.auth.FirebaseUser?.toAccountState(): AccountState =
        this?.let { AccountState.SignedIn(it.uid, it.displayName, it.email) } ?: AccountState.SignedOut

    private fun String.encodeForLink(): String = java.net.URLEncoder.encode(this, Charsets.UTF_8.name())
}

class FirebasePairingGateway(
    private val functions: FirebaseFunctions,
) : PairingGateway {
    override suspend fun createPairingSession(deviceLabel: String): Result<PairingSession> = runCatching {
        val data = functions.getHttpsCallable("createPairingSession")
            .call(mapOf("deviceLabel" to deviceLabel.take(80)))
            .await().data.asMap()
        PairingSession(
            id = data.string("sessionId"),
            shortCode = data.string("shortCode"),
            qrPayload = data.string("qrPayload"),
            expiresAtEpochMillis = data.number("expiresAtEpochMillis"),
        )
    }

    override suspend fun claimPairingSession(shortCode: String): Result<Unit> = callUnit(
        "claimPairingSession",
        mapOf("shortCode" to shortCode.filter(Char::isLetterOrDigit).uppercase().take(8)),
    )

    override suspend fun exchangeDeviceGrant(sessionId: String): Result<String> = runCatching {
        val data = functions.getHttpsCallable("exchangeDeviceGrant")
            .call(mapOf("sessionId" to sessionId))
            .await().data.asMap()
        data.string("customToken")
    }

    override suspend fun revokeDevice(deviceId: String): Result<Unit> = callUnit(
        "revokeDevice",
        mapOf("deviceId" to deviceId),
    )

    private suspend fun callUnit(name: String, data: Map<String, Any?>): Result<Unit> = runCatching {
        functions.getHttpsCallable(name).call(data).await()
        Unit
    }

    @Suppress("UNCHECKED_CAST")
    private fun Any?.asMap(): Map<String, Any?> = this as? Map<String, Any?> ?: error("Invalid server response")
    private fun Map<String, Any?>.string(key: String): String = this[key] as? String ?: error("Missing $key")
    private fun Map<String, Any?>.number(key: String): Long = (this[key] as? Number)?.toLong() ?: error("Missing $key")
}

