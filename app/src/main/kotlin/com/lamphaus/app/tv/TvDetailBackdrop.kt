package com.lamphaus.app.tv

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.appcompat.content.res.AppCompatResources
import com.lamphaus.app.R
import androidx.tv.material3.MaterialTheme
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import coil3.size.Size
import coil3.toBitmap
import com.lamphaus.app.ui.LocalArtworkResolver
import com.lamphaus.app.ui.MediaArtwork
import com.lamphaus.core.model.MediaPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val BACKDROP_WIDTH = 1920
private const val BACKDROP_HEIGHT = 1080

private fun detailScrims(background: Color) = listOf(
    ScrimLayer(
        arrayOf(
            0f to background.copy(alpha = 0.98f),
            0.50f to background.copy(alpha = 0.82f),
            0.78f to background.copy(alpha = 0.20f),
        ),
        horizontal = true,
    ),
    ScrimLayer(
        arrayOf(
            0f to Color.Transparent,
            0.72f to Color.Transparent,
            1f to background,
        ),
        horizontal = false,
    ),
)

/**
 * The details backdrop (artwork plus its two legibility scrims, SHR-PROD-01).
 *
 * QA-08: drawn live, the artwork and scrims were three full-screen layers on
 * every frame, and on a Mali-G31 TV the details screen spent about 50 ms of
 * GPU time per frame. The same stack is baked off-thread into one opaque
 * bitmap at display resolution. Until it is ready, the live layers render the
 * identical image, so the swap is invisible.
 */
@Composable
internal fun TvDetailBackdrop(media: MediaPreview, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resolver = LocalArtworkResolver.current
    val background = MaterialTheme.colorScheme.background
    val source = remember(media, resolver) { selectArtworkSource(context, resolver.resolve(media)) }
    var baked by remember(source?.key, background) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(source?.key, background) {
        baked = withContext(Dispatchers.IO) {
            val artwork: Bitmap? = source?.let {
                val result = SingletonImageLoader.get(context).execute(
                    ImageRequest.Builder(context)
                        .data(it.data)
                        .size(Size(BACKDROP_WIDTH, BACKDROP_HEIGHT))
                        .allowHardware(false)
                        .bitmapConfig(Bitmap.Config.ARGB_8888)
                        // Only the baked result is kept in memory.
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .build(),
                )
                (result as? SuccessResult)?.image?.toBitmap() ?: return@withContext null
            }
            bakeArtwork(
                artwork = artwork,
                width = BACKDROP_WIDTH,
                height = BACKDROP_HEIGHT,
                background = background,
                imageAlpha = 1f,
                scrims = detailScrims(background),
                placeholder = if (artwork == null) {
                    AppCompatResources.getDrawable(context, R.drawable.ic_lamphaus_foreground)
                } else {
                    null
                },
            ).asImageBitmap()
        }
    }

    Box(modifier) {
        val image = baked
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            MediaArtwork(
                media = media,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                preferBackdrop = true,
            )
            val scrims = detailScrims(background)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.horizontalGradient(colorStops = scrims[0].stops))
                    .background(Brush.verticalGradient(colorStops = scrims[1].stops)),
            )
        }
    }
}
