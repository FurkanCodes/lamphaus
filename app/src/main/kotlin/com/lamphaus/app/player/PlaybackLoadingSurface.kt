package com.lamphaus.app.player

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.scale
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import coil3.size.Size
import coil3.toBitmap
import com.google.android.material.color.DynamicColorsOptions
import com.google.android.material.color.MaterialColors
import com.lamphaus.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.lamphaus.app.ui.rememberReducedMotion
import com.lamphaus.core.model.PlaybackRequest

/** Timing and geometry for the playback loading surface (PLY-IMM-03, TV-MOT-01). */
internal object PlaybackLoadingTokens {
    /** The backdrop pushes in slowly while the source prepares. */
    const val driftMillis = 14_000
    const val driftStartScale = 1.10f
    const val driftEndScale = 1.02f
    const val artworkFadeMillis = 600
    const val logoEnterMillis = 700
    const val sweepPeriodMillis = 2_600
    const val progressPeriodMillis = 1_500
    val light = Color(0xFF68D4E8)
    val blue = Color(0xFF4058D8)
    const val accentFadeMillis = 600
    const val paletteSampleSize = 96
}

/**
 * The glow and comet take their colour from the backdrop (SHR-PROD-08): a
 * small software decode seeds Material's content-based colour, whose dark-
 * scheme accent roles stay legible on the dark surface. One artwork source
 * drives both (MOB-CLR-07); failures keep the instrument-blue default.
 */
private data class LoadingAccent(val glow: Color, val comet: Color)

private val accentCache = android.util.LruCache<String, LoadingAccent>(16)

private suspend fun loadingAccentFor(context: android.content.Context, artwork: String): LoadingAccent? {
    accentCache.get(artwork)?.let { return it }
    return withContext(Dispatchers.Default) {
        runCatching {
            val result = SingletonImageLoader.get(context).execute(
                ImageRequest.Builder(context)
                    .data(artwork)
                    .size(Size(PlaybackLoadingTokens.paletteSampleSize, PlaybackLoadingTokens.paletteSampleSize))
                    .allowHardware(false)
                    .bitmapConfig(Bitmap.Config.ARGB_8888)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .build(),
            ) as? SuccessResult ?: return@runCatching null
            val sample = result.image.toBitmap()
                .scale(PlaybackLoadingTokens.paletteSampleSize, PlaybackLoadingTokens.paletteSampleSize)
            val seed = DynamicColorsOptions.Builder()
                .setContentBasedSource(sample)
                .build()
                .contentBasedSeedColor
                ?: return@runCatching null
            val roles = MaterialColors.getColorRoles(seed, false)
            // The light accent tone reads as a glow over a dimmed backdrop; the dark
            // container tone disappears into it.
            LoadingAccent(glow = Color(roles.accent), comet = Color(roles.accent))
        }.getOrNull()
    }?.also { accentCache.put(artwork, it) }
}

private val Emphasized = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/**
 * The surface shown while a title starts or, on TV, rebuffers for long: the
 * title's own backdrop drifting behind a vignette, its logo rising into a soft
 * bloom with a passing beam of light, and a thin comet of light as the only
 * progress cue. No metadata. Without a logo image the title is set as the
 * logo. Remove animations leaves a still backdrop and logo.
 */
@Composable
internal fun PlaybackLoadingSurface(
    request: PlaybackRequest,
    isTelevision: Boolean,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val artwork = request.artworkUrl
        ?: request.preview?.backgroundUrl
        ?: request.preview?.posterUrl
    val logo = request.preview?.logoUrl?.takeIf(String::isNotBlank)
    var logoFailed by remember(logo) { mutableStateOf(false) }

    val drift = remember { Animatable(if (reducedMotion) PlaybackLoadingTokens.driftEndScale else PlaybackLoadingTokens.driftStartScale) }
    val artworkAlpha = remember { Animatable(0f) }
    var artworkLoaded by remember(artwork) { mutableStateOf(false) }
    val enter = remember { Animatable(if (reducedMotion) 1f else 0f) }

    LaunchedEffect(reducedMotion) {
        if (!reducedMotion) {
            drift.animateTo(
                PlaybackLoadingTokens.driftEndScale,
                tween(PlaybackLoadingTokens.driftMillis, easing = LinearEasing),
            )
        }
    }
    LaunchedEffect(artworkLoaded) {
        if (artworkLoaded) {
            if (reducedMotion) artworkAlpha.snapTo(1f)
            else artworkAlpha.animateTo(1f, tween(PlaybackLoadingTokens.artworkFadeMillis))
        }
    }
    LaunchedEffect(Unit) {
        if (!reducedMotion) enter.animateTo(1f, tween(PlaybackLoadingTokens.logoEnterMillis, easing = Emphasized))
    }
    val context = LocalContext.current
    var accent by remember(artwork) { mutableStateOf<LoadingAccent?>(null) }
    LaunchedEffect(artwork) {
        if (!artwork.isNullOrBlank()) accent = loadingAccentFor(context, artwork)
    }
    val accentSpec = tween<Color>(if (reducedMotion) 0 else PlaybackLoadingTokens.accentFadeMillis)
    val glowColor by animateColorAsState(accent?.glow ?: PlaybackLoadingTokens.blue, accentSpec, label = "glow")
    val cometColor by animateColorAsState(accent?.comet ?: PlaybackLoadingTokens.light, accentSpec, label = "comet")
    val loop = rememberInfiniteTransition(label = "playback-loading")
    val sweep by loop.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(PlaybackLoadingTokens.sweepPeriodMillis, easing = LinearEasing)),
        label = "sweep",
    )
    val comet by loop.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(PlaybackLoadingTokens.progressPeriodMillis, easing = FastOutSlowInEasing),
        ),
        label = "comet",
    )
    val breathe by loop.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PlayerBackground)
            .testTag("playback-loading")
            .semantics { contentDescription = "Loading ${request.title}" },
        contentAlignment = Alignment.Center,
    ) {
        if (!artwork.isNullOrBlank()) {
            AsyncImage(
                model = artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onState = { if (it is AsyncImagePainter.State.Success) artworkLoaded = true },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = artworkAlpha.value
                        scaleX = drift.value
                        scaleY = drift.value
                    },
            )
        }
        // A vignette keeps the centre readable and lets the edges fall to black (PLY-IMM-03).
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(Color.Black.copy(alpha = 0.42f))
                    drawRect(
                        Brush.radialGradient(
                            0f to Color.Transparent,
                            0.55f to Color.Black.copy(alpha = 0.35f),
                            1f to Color.Black.copy(alpha = 0.85f),
                            center = center,
                            radius = size.maxDimension * 0.62f,
                        ),
                    )
                    // A soft instrument-blue bloom sits behind the logo.
                    val bloom = (if (reducedMotion) 1f else breathe) * enter.value
                    drawCircle(
                        Brush.radialGradient(
                            0f to glowColor.copy(alpha = 0.30f * bloom),
                            0.45f to glowColor.copy(alpha = 0.10f * bloom),
                            1f to Color.Transparent,
                            center = center,
                            radius = size.minDimension * 0.55f,
                        ),
                        radius = size.minDimension * 0.55f,
                    )
                },
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (isTelevision) 36.dp else 28.dp),
            modifier = Modifier
                .padding(horizontal = if (isTelevision) 64.dp else 28.dp)
                .graphicsLayer {
                    alpha = enter.value
                    translationY = (1f - enter.value) * 18.dp.toPx()
                    val scale = 0.96f + 0.04f * enter.value
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            val logoModifier = Modifier
                // Offscreen so the beam lights only the logo's own pixels.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    if (!reducedMotion) drawBeam(sweep)
                }
            when {
                logo != null && !logoFailed -> AsyncImage(
                    model = logo,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    onState = { if (it is AsyncImagePainter.State.Error) logoFailed = true },
                    modifier = logoModifier
                        .widthIn(max = if (isTelevision) 420.dp else 280.dp)
                        .heightIn(max = if (isTelevision) 170.dp else 120.dp),
                )
                request.title.isNotBlank() -> Text(
                    text = request.title,
                    color = PlayerOnSurface,
                    fontFamily = PlayerFont,
                    fontSize = if (isTelevision) 40.sp else 30.sp,
                    lineHeight = if (isTelevision) 46.sp else 36.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = logoModifier.widthIn(max = if (isTelevision) 640.dp else 320.dp),
                )
                else -> Image(
                    painter = painterResource(R.drawable.ic_lamphaus_foreground),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = logoModifier.size(if (isTelevision) 108.dp else 84.dp),
                )
            }
            if (!reducedMotion) {
                LoadingComet(
                    progress = comet,
                    color = cometColor,
                    modifier = Modifier
                        .width(if (isTelevision) 180.dp else 140.dp)
                        .height(4.dp),
                )
            }
        }
    }
}

/** A band of light crossing the logo, then a quiet gap before the next pass. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBeam(sweep: Float) {
    // The pass uses the first 55% of each period; the rest is rest.
    val pass = (sweep / 0.55f).coerceAtMost(1f)
    if (pass >= 1f) return
    val band = size.width * 0.35f
    val x = -band + (size.width + 2 * band) * FastOutSlowInEasing.transform(pass)
    drawRect(
        brush = Brush.linearGradient(
            0f to Color.Transparent,
            0.5f to Color.White.copy(alpha = 0.55f),
            1f to Color.Transparent,
            start = Offset(x - band / 2, 0f),
            end = Offset(x + band / 2, size.height * 0.35f),
        ),
        blendMode = BlendMode.SrcAtop,
    )
}

/** A faint track with a glowing comet gliding across it: activity without a promise of progress. */
@Composable
private fun LoadingComet(progress: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val y = size.height / 2
        drawLine(
            color = Color.White.copy(alpha = 0.14f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        // The comet fades in at the start of the track and out at its end.
        val presence = kotlin.math.sin(Math.PI * progress).toFloat()
        val headX = size.width * progress
        val tailX = (headX - size.width * 0.32f).coerceAtLeast(0f)
        drawLine(
            brush = Brush.horizontalGradient(
                0f to Color.Transparent,
                1f to color.copy(alpha = presence),
                startX = tailX,
                endX = headX.coerceAtLeast(tailX + 1f),
            ),
            start = Offset(tailX, y),
            end = Offset(headX, y),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(color.copy(alpha = 0.35f * presence), radius = 4.dp.toPx(), center = Offset(headX, y))
        drawCircle(Color.White.copy(alpha = presence), radius = 1.6.dp.toPx(), center = Offset(headX, y))
    }
}
