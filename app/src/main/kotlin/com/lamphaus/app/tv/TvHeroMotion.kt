package com.lamphaus.app.tv

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lamphaus.app.ui.KenBurnsArtwork
import com.lamphaus.core.model.MediaPreview

@Composable
internal fun TvHeroArtwork(
    media: MediaPreview,
    userEnabled: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    KenBurnsArtwork(
        media = media,
        enabled = userEnabled,
        reducedMotion = reducedMotion,
        modifier = modifier,
    )
}
