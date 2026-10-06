package com.lamphaus.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lamphaus.app.R
import com.lamphaus.core.model.PlaybackSettings

/**
 * The four automatic-skip switches (PLY-SKIP-01), shared by TV and mobile
 * Settings, which draw each [row] in their own style. Intro and recap need
 * Skip intro; outro and end credits need Skip ending (MOB-SET-05).
 */
@Composable
internal fun AutoSkipRows(
    playback: PlaybackSettings,
    onUpdate: (PlaybackSettings) -> Unit,
    row: @Composable (title: String, description: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) -> Unit,
) {
    val introOn = playback.skipIntroEnabled
    val endingOn = playback.skipEndingEnabled
    val needsIntro = stringResource(R.string.auto_skip_requires_skip_intro)
    val needsEnding = stringResource(R.string.auto_skip_requires_skip_ending)
    row(
        stringResource(R.string.auto_skip_intro),
        if (introOn) stringResource(R.string.auto_skip_intro_description) else needsIntro,
        playback.autoSkipIntro,
        introOn,
    ) { onUpdate(playback.copy(autoSkipIntro = it)) }
    row(
        stringResource(R.string.auto_skip_recap),
        if (introOn) stringResource(R.string.auto_skip_recap_description) else needsIntro,
        playback.autoSkipRecap,
        introOn,
    ) { onUpdate(playback.copy(autoSkipRecap = it)) }
    row(
        stringResource(R.string.auto_skip_outro),
        if (endingOn) stringResource(R.string.auto_skip_outro_description) else needsEnding,
        playback.autoSkipOutro,
        endingOn,
    ) { onUpdate(playback.copy(autoSkipOutro = it)) }
    row(
        stringResource(R.string.auto_skip_credits),
        if (endingOn) stringResource(R.string.auto_skip_credits_description) else needsEnding,
        playback.autoSkipCredits,
        endingOn,
    ) { onUpdate(playback.copy(autoSkipCredits = it)) }
}
