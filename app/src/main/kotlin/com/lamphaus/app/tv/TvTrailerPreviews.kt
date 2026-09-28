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
 * Spotlight trailer previews, available only while previews are on and the
 * TV activity is started. Each trailer gets its own muted player, released
 * the moment the trailer stops: a player kept across cards could get stuck
 * and leave every later card silent until previews were switched off and on,
 * and a released player also gives playback and other apps the decoder back.
 */
@Stable
internal class TvTrailerPreviews(
    private val context: Context,
    private val resolve: suspend (MediaPreview) -> TrailerSource?,
    private val forget: (TrailerSource) -> Unit,
) {
    suspend fun source(media: MediaPreview): TrailerSource? = try {
        resolve(media)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    fun newPlayer(): TrailerPlayer =
        TrailerPlayer(context, TvTrailerPreviewTokens.maxVideoHeight, withSound = false)

    /** A stream that failed is not reused; the next focus extracts it again. */
    fun failed(source: TrailerSource) = forget(source)
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
    forget: (TrailerSource) -> Unit,
): TvTrailerPreviews? {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentResolve by rememberUpdatedState(resolve)
    val currentForget by rememberUpdatedState(forget)
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
    return remember(context) {
        TvTrailerPreviews(
            context,
            resolve = { media -> currentResolve(media, TvTrailerPreviewTokens.maxVideoHeight) },
            forget = { source -> currentForget(source) },
        )
    }
}

/** The trailer inside a focused Spotlight card; it fades in on its first frame. */
@Composable
internal fun TvSpotlightTrailer(
    previews: TvTrailerPreviews,
    source: TrailerSource,
    onEnded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val trailerPlayer = remember(previews, source) { previews.newPlayer() }
    var firstFrame by remember(source) { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (firstFrame) 1f else 0f,
        animationSpec = tween(TvTrailerPreviewTokens.fadeInMillis),
        label = "spotlight trailer fade",
    )
    DisposableEffect(trailerPlayer) {
        trailerPlayer.play(source)
        onDispose(trailerPlayer::release)
    }
    TrailerVideo(
        player = trailerPlayer.player,
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha },
        crop = true,
        onFirstFrame = { firstFrame = true },
        onEnded = onEnded,
        onError = {
            previews.failed(source)
            onEnded()
        },
    )
}
