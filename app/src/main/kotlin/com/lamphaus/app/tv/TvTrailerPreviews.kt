package com.lamphaus.app.tv

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lamphaus.app.ui.TrailerVideo
import com.lamphaus.app.ui.rememberReducedMotion
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.TrailerSource
import com.lamphaus.core.player.trailer.TrailerPlayer
import kotlin.coroutines.cancellation.CancellationException

/** Spotlight trailer previews (TV-CNT-04). */
internal object TvTrailerPreviewTokens {
    /** Focus must rest this long on a card before its trailer starts. */
    const val startDelayMillis = 3_000L
    const val fadeInMillis = 400

    /** The card is 411×231dp; 720p covers it at 4K without decoding more. */
    const val maxVideoHeight = 720
}

/**
 * One muted trailer player shared by every Spotlight card. It exists only
 * while previews are on and the TV activity is started, so playback and
 * other apps get the decoders back as soon as Home stops.
 */
@Stable
internal class TvTrailerPreviews(
    private val context: Context,
    private val resolve: suspend (MediaPreview) -> TrailerSource?,
) {
    private var trailerPlayer: TrailerPlayer? = null

    suspend fun source(media: MediaPreview): TrailerSource? = try {
        resolve(media)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    fun player(): TrailerPlayer =
        trailerPlayer ?: TrailerPlayer(context, TvTrailerPreviewTokens.maxVideoHeight, withSound = false)
            .also { trailerPlayer = it }

    fun release() {
        trailerPlayer?.release()
        trailerPlayer = null
    }
}

internal val LocalTvTrailerPreviews = staticCompositionLocalOf<TvTrailerPreviews?> { null }

/**
 * Previews while [enabled], the activity is started, and animations are on:
 * a trailer is motion, so Remove animations turns previews off (TV-MOT-01).
 */
@Composable
internal fun rememberTvTrailerPreviews(
    enabled: Boolean,
    resolve: suspend (MediaPreview, Int) -> TrailerSource?,
): TvTrailerPreviews? {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentResolve by rememberUpdatedState(resolve)
    val reducedMotion = rememberReducedMotion()
    var started by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> started = true
                Lifecycle.Event.ON_STOP -> started = false
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    if (!enabled || !started || reducedMotion) return null
    val previews = remember(context) {
        TvTrailerPreviews(context) { media -> currentResolve(media, TvTrailerPreviewTokens.maxVideoHeight) }
    }
    DisposableEffect(previews) {
        onDispose(previews::release)
    }
    return previews
}

/** The trailer inside a focused Spotlight card; it fades in on its first frame. */
@Composable
internal fun TvSpotlightTrailer(
    previews: TvTrailerPreviews,
    source: TrailerSource,
    onEnded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val trailerPlayer = remember(previews) { previews.player() }
    var firstFrame by remember(source) { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (firstFrame) 1f else 0f,
        animationSpec = tween(TvTrailerPreviewTokens.fadeInMillis),
        label = "spotlight trailer fade",
    )
    DisposableEffect(trailerPlayer, source) {
        trailerPlayer.play(source)
        onDispose(trailerPlayer::stop)
    }
    TrailerVideo(
        player = trailerPlayer.player,
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha },
        crop = true,
        onFirstFrame = { firstFrame = true },
        onEnded = onEnded,
    )
}
