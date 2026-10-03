package com.lamphaus.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import com.lamphaus.app.LamphausApplication
import com.lamphaus.app.R
import com.lamphaus.app.mobile.MobileActivity
import com.lamphaus.app.mobile.TitleLinks
import com.lamphaus.app.ui.computeUpNext
import com.lamphaus.core.data.cloud.AccountState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Continue watching on the home screen (MOB-WGT-01..06): one glanceable
 * use case with a useful default and no configuration. Layout changes at
 * breakpoints instead of stretching (MOB-WGT-03): one card at 2×2, a row
 * of cards at 4×2, a headed list at 4×3. It fills its cell with the system
 * corner radius (MOB-WGT-02), uses Material roles with dynamic color on
 * Android 12+ and Lamphaus' static scheme before (MOB-WGT-04), and every
 * card opens its title's details (MOB-NOT-05).
 */
internal class ContinueWatchingWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SINGLE, ROW, LIST))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val content = loadContent(context)
        val artwork = (content as? WidgetContent.Cards)
            ?.cards
            ?.mapNotNull { card -> card.artworkUrl?.let { url -> loadArtwork(context, url)?.let { card.mediaKey to it } } }
            ?.toMap()
            .orEmpty()
        provideContent {
            GlanceTheme(colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) GlanceTheme.colors else LamphausWidgetColors) {
                WidgetBody(content, artwork)
            }
        }
    }

    private suspend fun loadContent(context: Context): WidgetContent {
        val container = (context.applicationContext as LamphausApplication).container
        if (container.accountGateway.state.value is AccountState.SignedOut) return WidgetContent.SignedOut
        val settings = container.preferences.current()
        val profileId = settings.activeProfileId
            ?: container.libraryRepository.profiles().first().firstOrNull()?.id
            ?: return WidgetContent.SignedOut
        val progress = container.libraryRepository.progress(profileId).first()
        // Up next asks add-ons through the shared cache; a slow add-on only
        // drops its series from this refresh, never the whole widget.
        val upNext = withTimeoutOrNull(UP_NEXT_TIMEOUT_MILLIS) {
            computeUpNext(progress, settings.upNextDismissed, System.currentTimeMillis()) { media ->
                container.providerMetadataRepository.getSeriesEpisodes(media)
            }
        }.orEmpty()
        val cards = widgetCards(progress, upNext)
        return if (cards.isEmpty()) WidgetContent.Empty else WidgetContent.Cards(cards)
    }

    private suspend fun loadArtwork(context: Context, url: String): Bitmap? = runCatching {
        val result = SingletonImageLoader.get(context).execute(
            ImageRequest.Builder(context)
                .data(url)
                .size(Size(ARTWORK_WIDTH_PX, ARTWORK_HEIGHT_PX))
                .allowHardware(false)
                .build(),
        )
        (result as? SuccessResult)?.image?.toBitmap()
    }.onFailure { if (it is CancellationException) throw it }.getOrNull()

    companion object {
        private val SINGLE = DpSize(110.dp, 110.dp)
        private val ROW = DpSize(250.dp, 110.dp)
        private val LIST = DpSize(250.dp, 220.dp)
        private const val UP_NEXT_TIMEOUT_MILLIS = 8_000L
        private const val ARTWORK_WIDTH_PX = 480
        private const val ARTWORK_HEIGHT_PX = 270

        /** Redraws every placed widget, for example after playback saved progress. */
        suspend fun refresh(context: Context) {
            runCatching { ContinueWatchingWidget().updateAll(context) }
                .onFailure { if (it is CancellationException) throw it }
        }
    }
}

class ContinueWatchingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ContinueWatchingWidget()
}

/** Lamphaus' static scheme where wallpaper color is unavailable (MOB-CLR-02, MOB-CLR-09). */
private val LamphausWidgetColors = ColorProviders(
    light = lightColorScheme(
        primary = Color(0xFF2B5EA7),
        onPrimary = Color.White,
        surface = Color(0xFFF8F9FF),
        onSurface = Color(0xFF191C20),
        onSurfaceVariant = Color(0xFF43474E),
        surfaceVariant = Color(0xFFDFE2EB),
    ),
    dark = darkColorScheme(
        primary = Color(0xFFA8C8FF),
        onPrimary = Color(0xFF003062),
        surface = Color(0xFF101218),
        onSurface = Color(0xFFF2F4F8),
        onSurfaceVariant = Color(0xFF9AA1AF),
        surfaceVariant = Color(0xFF181B23),
    ),
)

@Composable
private fun WidgetBody(content: WidgetContent, artwork: Map<String, Bitmap>) {
    val context = LocalContext.current
    Box(
        GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.surface)
            // The launcher's own radius on Android 12+, via values-v31 (MOB-WGT-02).
            .cornerRadius(R.dimen.widget_background_radius),
    ) {
        when (content) {
            WidgetContent.SignedOut -> WidgetMessage(context.getString(R.string.widget_signed_out))
            WidgetContent.Empty -> WidgetMessage(context.getString(R.string.widget_empty))
            is WidgetContent.Cards -> {
                val size = LocalSize.current
                when {
                    size.height >= 220.dp && size.width >= 250.dp -> WidgetList(content.cards, artwork)
                    size.width >= 250.dp -> WidgetRow(content.cards.take(3), artwork)
                    else -> WidgetCardView(content.cards.first(), artwork, GlanceModifier.fillMaxSize())
                }
            }
        }
    }
}

/** Empty and signed-out states open the app rather than dead-ending (MOB-WGT-05). */
@Composable
private fun WidgetMessage(text: String) {
    val context = LocalContext.current
    Box(
        GlanceModifier
            .fillMaxSize()
            .padding(16.dp)
            .clickable(actionStartActivity(android.content.Intent(context, MobileActivity::class.java))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
        )
    }
}

@Composable
private fun WidgetRow(cards: List<WidgetCard>, artwork: Map<String, Bitmap>) {
    Row(GlanceModifier.fillMaxSize().padding(8.dp)) {
        cards.forEachIndexed { index, card ->
            if (index > 0) Spacer(GlanceModifier.width(8.dp))
            WidgetCardView(card, artwork, GlanceModifier.defaultWeight().fillMaxHeight())
        }
    }
}

@Composable
private fun WidgetList(cards: List<WidgetCard>, artwork: Map<String, Bitmap>) {
    val context = LocalContext.current
    Column(GlanceModifier.fillMaxSize().padding(12.dp)) {
        Text(
            context.getString(R.string.continue_watching),
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium),
            modifier = GlanceModifier.padding(start = 4.dp, bottom = 8.dp),
        )
        cards.take(3).forEachIndexed { index, card ->
            if (index > 0) Spacer(GlanceModifier.height(8.dp))
            WidgetCardView(card, artwork, GlanceModifier.fillMaxWidth().defaultWeight())
        }
    }
}

/** A card: artwork under a scrim, the title, its episode or time left, and progress when resuming. */
@Composable
private fun WidgetCardView(card: WidgetCard, artwork: Map<String, Bitmap>, modifier: GlanceModifier) {
    val context = LocalContext.current
    val episode = listOfNotNull(
        if (card.episodeSeason != null && card.episodeNumber != null) {
            context.getString(R.string.episode_format, card.episodeSeason, card.episodeNumber)
        } else {
            null
        },
        card.episodeTitle,
    ).joinToString(" · ").takeIf(String::isNotBlank)
    val status = when (card.kind) {
        WidgetCard.Kind.NEW_EPISODE -> context.getString(R.string.new_episode)
        WidgetCard.Kind.UP_NEXT -> context.getString(R.string.up_next)
        WidgetCard.Kind.RESUME -> card.minutesLeft?.let {
            context.resources.getQuantityString(R.plurals.widget_minutes_left, it, it)
        }
    }
    val label = listOfNotNull(card.title, episode, status).joinToString(", ")
    Box(
        modifier
            .cornerRadius(16.dp)
            .background(GlanceTheme.colors.surfaceVariant)
            .clickable(actionStartActivity(TitleLinks.intent(context, card.mediaKey)))
            .semantics { contentDescription = label },
        contentAlignment = Alignment.BottomStart,
    ) {
        artwork[card.mediaKey]?.let { bitmap ->
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize(),
            )
            // Legibility scrim behind the text (PLY-IMM-03 spirit, MOB-CLR-07).
            Image(
                provider = ImageProvider(R.drawable.widget_scrim),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = GlanceModifier.fillMaxSize(),
            )
        }
        Column(GlanceModifier.fillMaxWidth().padding(10.dp)) {
            val onArt = artwork.containsKey(card.mediaKey)
            val primary = if (onArt) WidgetOnArt else GlanceTheme.colors.onSurface
            val secondary = if (onArt) WidgetOnArtMuted else GlanceTheme.colors.onSurfaceVariant
            status?.let {
                Text(it, style = TextStyle(color = secondary, fontSize = 12.sp), maxLines = 1)
            }
            Text(
                card.title,
                style = TextStyle(color = primary, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            episode?.let { Text(it, style = TextStyle(color = secondary, fontSize = 12.sp), maxLines = 1) }
            card.progress?.let { fraction ->
                Spacer(GlanceModifier.height(6.dp))
                LinearProgressIndicator(
                    progress = fraction,
                    modifier = GlanceModifier.fillMaxWidth().height(3.dp),
                    color = GlanceTheme.colors.primary,
                    backgroundColor = WidgetTrack,
                )
            }
        }
    }
}

private val WidgetOnArt = androidx.glance.unit.ColorProvider(Color.White)
private val WidgetOnArtMuted = androidx.glance.unit.ColorProvider(Color(0xDDFFFFFF))
private val WidgetTrack = androidx.glance.unit.ColorProvider(Color(0x33FFFFFF))
