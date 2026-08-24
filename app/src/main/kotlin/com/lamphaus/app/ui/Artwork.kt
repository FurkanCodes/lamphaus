package com.lamphaus.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import com.lamphaus.app.R
import com.lamphaus.core.model.MediaPreview

@Composable
fun MediaArtwork(
    media: MediaPreview,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    preferBackdrop: Boolean = false,
) {
    val local = when (media.id) {
        "fixture:aurora" -> R.drawable.poster_aurora
        "fixture:glass" -> R.drawable.poster_glass
        else -> null
    }
    val remote = if (preferBackdrop) media.backgroundUrl ?: media.posterUrl else media.posterUrl ?: media.backgroundUrl
    when {
        local != null -> Image(
            painter = painterResource(local),
            contentDescription = media.name,
            modifier = modifier,
            contentScale = contentScale,
        )
        !remote.isNullOrBlank() -> AsyncImage(
            model = remote,
            contentDescription = media.name,
            modifier = modifier,
            contentScale = contentScale,
        )
        else -> Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_lamphaus_foreground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(0.28f),
            )
        }
    }
}
