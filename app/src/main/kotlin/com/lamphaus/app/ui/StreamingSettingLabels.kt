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
