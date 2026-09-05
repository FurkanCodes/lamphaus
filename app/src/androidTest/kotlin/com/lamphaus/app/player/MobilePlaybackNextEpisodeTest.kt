package com.lamphaus.app.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.espresso.Espresso
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lamphaus.core.model.Episode
import com.lamphaus.core.model.PlaybackRequest
import com.lamphaus.core.model.PlaybackSegment
import com.lamphaus.core.model.PlaybackSegmentType
import com.lamphaus.core.model.PlaybackSettings
import com.lamphaus.core.model.PlaybackSource
import com.lamphaus.core.model.SpoilerProtectionSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Card timing and dismissal at the screen level: hidden without a trigger,
 * shown at the exact ending timestamp, and dismissed by Back before controls
 * or exit (PLY-IMM-02, PLY-PIP-03).
 */
@RunWith(AndroidJUnit4::class)
class MobilePlaybackNextEpisodeTest {
    @get:Rule
    val compose = createComposeRule()

    private val request = PlaybackRequest(
        mediaKey = "series:1",
        videoId = "series:1:1",
        title = "Series",
        subtitle = "S1 · E1",
        source = PlaybackSource(uri = "https://example.invalid/video.m3u8"),
        episode = Episode("series:1:1", "Pilot", season = 1, episode = 1),
        nextEpisode = Episode("series:1:2", "Next", season = 1, episode = 2),
    )

    @Test
    fun cardAppearsAtTheEndingTimestampAndBackDismissesItBeforeControls() {
        var dismissed = 0
        var exits = 0
        var endingDataAvailable by mutableStateOf(false)
        compose.setContent {
            PlaybackScreen(
                request = request,
                player = null,
                isTelevision = false,
                settings = PlaybackSettings(nextEpisodeEnabled = true),
                segments = if (endingDataAvailable) {
                    listOf(
                        PlaybackSegment(
                            type = PlaybackSegmentType.ENDING,
                            startMillis = 0,
                        ),
                    )
                } else {
                    emptyList()
                },
                nextEpisodeLoading = false,
                nextEpisodeMessage = null,
                spoilerProtection = SpoilerProtectionSettings(),
                nextEpisodeDismissed = false,
                onExit = { exits++ },
                onOpenExternally = {},
                onNextEpisode = {},
                onDismissNextEpisodeMessage = {},
                onDismissNextEpisodeCard = {
                    dismissed++
                    endingDataAvailable = false
                },
                onPlayerViewLayout = {},
                onEnterPictureInPicture = {},
                pictureInPictureAvailable = false,
                inPictureInPicture = false,
                subtitleStyle = com.lamphaus.core.model.SubtitleStyle(),
                subtitleDelayMillis = 0L,
                audioDelayMillis = 0L,
                streamInfo = null,
                onSubtitleDelay = {},
                onAudioDelay = {},
                onSubtitleStyle = {},
                onLoadSidecarCues = { callback -> callback(emptyList()) },
                onApplySyncByLine = { _, _ -> },
            )
        }

        // Without ending data and without duration the card stays hidden.
        compose.onNodeWithText("Up next").assertDoesNotExist()

        endingDataAvailable = true
        compose.waitForIdle()
        compose.onNodeWithText("Up next").assertIsDisplayed()

        Espresso.pressBack()
        compose.waitForIdle()

        assertEquals(1, dismissed)
        assertEquals(0, exits)
        compose.onNodeWithText("Up next").assertDoesNotExist()
    }
}
