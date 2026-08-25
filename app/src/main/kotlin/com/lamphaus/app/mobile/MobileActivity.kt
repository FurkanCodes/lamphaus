package com.lamphaus.app.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.lamphaus.app.BuildConfig
import com.lamphaus.app.LamphausApplication
import com.lamphaus.app.ui.AppViewModel
import com.lamphaus.app.ui.isSafeExternalUri
import com.lamphaus.app.player.PlayerActivity
import android.content.Intent
import androidx.core.content.edit
import androidx.core.net.toUri
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.launch
import androidx.credentials.exceptions.NoCredentialException

class MobileActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels {
        AppViewModel.factory((application as LamphausApplication).container)
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIncomingIntent(intent)
        setContent {
            MobileApp(
                viewModel = viewModel,
                widthSizeClass = calculateWindowSizeClass(this).widthSizeClass,
                onGoogleSignIn = ::requestGoogleSignIn,
                onEmailLink = ::sendEmailLink,
                onPlay = { startActivity(PlayerActivity.intent(this, it)) },
                onExternalPlay = ::openExternalPlayback,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun requestGoogleSignIn() {
        if (BuildConfig.WEB_CLIENT_ID.isBlank()) {
            viewModel.reportMessage("Google sign-in needs the production web client ID.")
            return
        }
        lifecycleScope.launch {
            try {
                // Supabase verifies the token against the hashed nonce; the raw
                // value travels only with the sign-in request.
                val rawNonce = UUID.randomUUID().toString()
                val hashedNonce = MessageDigest.getInstance("SHA-256")
                    .digest(rawNonce.toByteArray())
                    .joinToString("") { "%02x".format(it) }

                val option = GetGoogleIdOption.Builder()
                    .setServerClientId(BuildConfig.WEB_CLIENT_ID)
                    .setFilterByAuthorizedAccounts(false)
                    .setAutoSelectEnabled(true)
                    .setNonce(hashedNonce)
                    .build()
                val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                val credential = CredentialManager.create(this@MobileActivity)
                    .getCredential(this@MobileActivity, request)
                    .credential
                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    viewModel.signInWithGoogleToken(token, rawNonce)
                } else {
                    viewModel.reportMessage("That credential cannot be used here.")
                }
            } catch (_: NoCredentialException) {
                viewModel.reportMessage("No saved Google account is available on this device.")
            } catch (_: Exception) {
                viewModel.reportMessage("Google sign-in was cancelled or unavailable.")
            }
        }
    }

    private fun sendEmailLink(email: String) {
        getSharedPreferences("pending_auth", MODE_PRIVATE).edit { putString("email", email.trim()) }
        viewModel.sendEmailLink(email)
    }

    private fun finishPendingEmailLink() {
        val link = intent?.data?.toString() ?: return
        val email = getSharedPreferences("pending_auth", MODE_PRIVATE).getString("email", null) ?: return
        viewModel.completeEmailLink(email, link)
    }

    private fun handleIncomingIntent(incoming: Intent?) {
        val data = incoming?.data ?: return
        val scheme = data.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            if (data.host.equals("pair", ignoreCase = true)) {
                val code = data.getQueryParameter("code") ?: data.pathSegments.firstOrNull()
                if (code.isNullOrBlank()) viewModel.reportMessage("That pairing link is incomplete.")
                else viewModel.claimPairingSession(code)
            } else {
                viewModel.addProvider(data.toString())
            }
            return
        }
        finishPendingEmailLink()
    }

    private fun openExternalPlayback(url: String) {
        val uri = runCatching { url.toUri() }.getOrNull()
        if (uri == null || !isSafeExternalUri(url)) {
            viewModel.reportMessage("Lamphaus refused an unsafe external source.")
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
        if (intent.resolveActivity(packageManager) == null) {
            viewModel.reportMessage("No installed app can open this source.")
            return
        }
        startActivity(Intent.createChooser(intent, "Open with"))
    }
}
