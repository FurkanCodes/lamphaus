package com.lamphaus.app.tv

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.google.android.gms.cast.tv.CastReceiverContext
import com.lamphaus.app.BuildConfig
import com.lamphaus.app.LamphausApplication
import com.lamphaus.app.isTelevision
import com.lamphaus.app.mobile.MobileActivity
import com.lamphaus.app.player.PlayerActivity
import com.lamphaus.app.ui.AppViewModel
import com.lamphaus.app.ui.isSafeExternalUri

class TvActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels {
        AppViewModel.factory((application as LamphausApplication).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!isTelevision()) {
            // Started on a non-TV device (e.g. Android Studio picked the wrong
            // target): hand off to the mobile experience instead of rendering
            // the ten-foot pairing screen on a touchscreen.
            startActivity(Intent(this, MobileActivity::class.java))
            finish()
            return
        }
        setContent {
            TvApp(
                viewModel = viewModel,
                initialSearch = intent?.getStringExtra(SearchManager.QUERY),
                onPlay = { startActivity(PlayerActivity.intent(this, it)) },
                onExternalPlay = ::openExternalPlayback,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        if (BuildConfig.CAST_APPLICATION_ID.isNotBlank()) runCatching { CastReceiverContext.getInstance().start() }
    }

    override fun onStop() {
        if (BuildConfig.CAST_APPLICATION_ID.isNotBlank()) runCatching { CastReceiverContext.getInstance().stop() }
        super.onStop()
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
        startActivity(Intent.createChooser(intent, "Open source"))
    }

}
