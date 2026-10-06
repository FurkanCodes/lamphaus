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
