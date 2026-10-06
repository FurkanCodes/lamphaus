package com.lamphaus.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lamphaus.app.R

/** "512 KB" or "16 MB" for a parallel download chunk size (PLY-NET-01). */
@Composable
internal fun chunkSizeLabel(kilobytes: Int): String =
    if (kilobytes < 1024) {
        stringResource(R.string.size_kilobytes, kilobytes)
    } else {
        stringResource(R.string.size_megabytes, kilobytes / 1024)
    }

/** "Stereo", "Quad (4.0)", or "5.1" for a downmix speaker layout (PLY-AUD-01). */
@Composable
internal fun downmixLayoutLabel(channels: Int): String = stringResource(
    when (channels) {
        4 -> R.string.downmix_layout_quad
        6 -> R.string.downmix_layout_51
        else -> R.string.downmix_layout_stereo
    },
)

/** The Default player choice's label (PLY-EXT-01). */
@Composable
internal fun defaultPlayerLabel(player: com.lamphaus.core.model.DefaultPlayer): String = stringResource(
    when (player) {
        com.lamphaus.core.model.DefaultPlayer.INTERNAL -> R.string.default_player_internal
        com.lamphaus.core.model.DefaultPlayer.EXTERNAL -> R.string.default_player_external
        com.lamphaus.core.model.DefaultPlayer.ASK -> R.string.default_player_ask
    },
)

/** The playback engine choice's label (PLY-ENG-01). */
@Composable
internal fun playbackEngineLabel(engine: com.lamphaus.core.model.PlaybackEngineKind): String = stringResource(
    when (engine) {
        com.lamphaus.core.model.PlaybackEngineKind.AUTO -> R.string.playback_engine_auto
        com.lamphaus.core.model.PlaybackEngineKind.MEDIA3 -> R.string.playback_engine_exoplayer
        com.lamphaus.core.model.PlaybackEngineKind.MPV -> R.string.playback_engine_mpv
    },
)
