package com.lamphaus.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.lamphaus.app.R
import com.lamphaus.core.model.IntegrationStatus
import java.util.Date

/**
 * One line per Seekr allowance used up today, with the local time it comes
 * back (Seekr resets at midnight UTC). Shown on the Seekr card in Settings on
 * TV and mobile; the player stays silent (PLY-SEEK-01).
 */
@Composable
internal fun seekrLimitLines(seekr: IntegrationStatus?): List<String> {
    val context = LocalContext.current
    val timeFormat = remember(context) { android.text.format.DateFormat.getTimeFormat(context) }
    val now = System.currentTimeMillis()
    return seekr?.limits.orEmpty()
        .filter { it.untilEpochMillis > now }
        .sortedBy { it.scope }
        .map { limit ->
            val time = timeFormat.format(Date(limit.untilEpochMillis))
            when (limit.scope) {
                "movie" -> stringResource(R.string.seekr_limit_movies, time)
                "episode" -> stringResource(R.string.seekr_limit_episodes, time)
                else -> stringResource(R.string.seekr_limit_all, time)
            }
        }
}
