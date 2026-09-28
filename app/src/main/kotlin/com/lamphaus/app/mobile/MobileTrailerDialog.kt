package com.lamphaus.app.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lamphaus.app.R
import com.lamphaus.app.ui.TrailerVideo
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.TrailerSource
import com.lamphaus.core.player.trailer.TrailerPlayer
import kotlin.coroutines.cancellation.CancellationException

/** The phone never needs more than 1080p for a trailer (QA-08). */
private const val TRAILER_MAX_HEIGHT = 1080

private enum class TrailerStatus { LOADING, PLAYING, FAILED }

/**
 * A full-screen trailer with sound, opened from the title page's Trailer
 * action. Close and system Back dismiss it (MOB-NAV-06); the standard
 * Media3 controls keep pause and scrubbing reachable without gestures, and
 * everything stays clear of the system bars and cutouts (MOB-SYS-03). A
 * failure stays inside the dialog with a retry (SHR-PROD-04).
 */
@Composable
internal fun MobileTrailerDialog(
    media: MediaPreview,
    resolve: suspend (media: MediaPreview, maxHeight: Int, refresh: Boolean) -> TrailerSource?,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val context = LocalContext.current
        val trailerPlayer = remember(context) { TrailerPlayer(context, TRAILER_MAX_HEIGHT, withSound = true) }
        var status by remember { mutableStateOf(TrailerStatus.LOADING) }
        var attempt by remember { mutableIntStateOf(0) }
        DisposableEffect(trailerPlayer) {
            onDispose(trailerPlayer::release)
        }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        DisposableEffect(lifecycle, trailerPlayer) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) trailerPlayer.player.pause()
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
        LaunchedEffect(media.stableKey, attempt) {
            status = TrailerStatus.LOADING
            val source = try {
                resolve(media, TRAILER_MAX_HEIGHT, attempt > 0)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
            if (source == null) {
                status = TrailerStatus.FAILED
            } else {
                trailerPlayer.play(source)
                status = TrailerStatus.PLAYING
            }
        }
        val title = stringResource(R.string.trailer_title_format, media.name)
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .semantics { paneTitle = title },
        ) {
            val content = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
            when (status) {
                TrailerStatus.LOADING -> Box(content, contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
                TrailerStatus.PLAYING -> TrailerVideo(
                    player = trailerPlayer.player,
                    modifier = content,
                    showControls = true,
                    onEnded = onDismiss,
                    onError = {
                        trailerPlayer.stop()
                        status = TrailerStatus.FAILED
                    },
                )
                TrailerStatus.FAILED -> Column(
                    modifier = content.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(R.string.trailer_unavailable),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    OutlinedButton(onClick = { attempt++ }) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(4.dp),
            ) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.close), tint = Color.White)
            }
        }
    }
}
