package com.lamphaus.app.player

import android.graphics.Bitmap
import android.os.Looper
import android.app.UiAutomation
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.media3.common.*
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.lamphaus.core.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** QA-01/06/07: real Media3 state changes, layer dismissal, and remote focus restoration. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackChromeTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var player: FakeChromePlayer
    private var exits = 0
    private var delayMillis = 0L

    private fun show(tv: Boolean, fontScale: Float? = null) {
        compose.runOnUiThread { player = FakeChromePlayer() }
        compose.setContent {
            var style by remember { mutableStateOf(SubtitleStyle()) }
            var subtitleDelay by remember { mutableLongStateOf(0L) }
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,
                fontScale ?: LocalDensity.current.fontScale)) {
            PlaybackScreen(
                request = PlaybackRequest(mediaKey = "movie:fixture", videoId = "fixture", title = "The quiet earth",
                    subtitle = "A journey beyond the familiar", source = PlaybackSource("https://example.invalid/fixture")),
                player = player, isTelevision = tv, settings = PlaybackSettings(), segments = emptyList(),
                nextEpisodeLoading = false, nextEpisodeMessage = null, onExit = { exits++ },
                onOpenExternally = {}, onNextEpisode = {}, onDismissNextEpisodeMessage = {},
                spoilerProtection = SpoilerProtectionSettings(), nextEpisodeDismissed = false,
                onDismissNextEpisodeCard = {}, onPlayerViewLayout = {}, onEnterPictureInPicture = {},
                pictureInPictureAvailable = !tv, inPictureInPicture = false, subtitleStyle = style,
                subtitleDelayMillis = subtitleDelay, audioDelayMillis = 0, streamInfo = "1920 × 1080 · 24 fps",
                onSubtitleDelay = { subtitleDelay = it; delayMillis = it }, onAudioDelay = {},
                onSubtitleStyle = { style = it }, onLoadSidecarCues = { it(emptyList()) },
                onApplySyncByLine = { _, _ -> },
            )
            }
        }
        compose.waitForIdle()
    }

    @Test fun TV_FOC_01_audioSelectionAndBackRestoreOrigin() {
        show(true)
        compose.onNodeWithContentDescription("Play").assertIsFocused()
        screenshot("tv-controls")
        compose.onNodeWithContentDescription("Audio").performClick()
        compose.onNodeWithText("Automatic").assertIsDisplayed()
        compose.onNodeWithText("French").performClick()
        compose.runOnIdle { assertEquals("audio-fr", player.trackSelectionParameters.overrides.keys.single().id) }
        screenshot("tv-audio")
        pressBack()
        compose.onNodeWithContentDescription("Audio").assertIsFocused()
        assertEquals(0, exits)
    }

    @Test fun TV_NAV_02_subtitleEditorReturnsToAppearanceAndTrackOffWorks() {
        show(true)
        compose.onNodeWithContentDescription("Subtitles").performClick()
        compose.onNodeWithText("English").performClick()
        compose.onNodeWithText("English captions").performClick()
        compose.runOnIdle { assertEquals("text-en", player.trackSelectionParameters.overrides.keys.single().id) }
        screenshot("tv-subtitles")
        compose.onAllNodesWithText("Subtitle appearance").filter(hasClickAction()).onFirst().performClick()
        compose.onNodeWithText("Subtitle appearance").assertIsDisplayed()
        screenshot("tv-appearance")
        pressBack()
        compose.onAllNodesWithText("Subtitle appearance").filter(hasClickAction()).onFirst().assertIsFocused()
        compose.onNodeWithText("Off").performClick()
        compose.runOnIdle { assertTrue(C.TRACK_TYPE_TEXT in player.trackSelectionParameters.disabledTrackTypes) }
        pressBack()
        compose.onNodeWithContentDescription("Subtitles").assertIsFocused()
    }

    @Test fun TV_NAV_05_remoteTraversesControlsAndMoreBackUnwindsOneLayer() {
        show(true)
        compose.onNodeWithContentDescription("Play").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithContentDescription("Rewind 10 seconds").assertIsFocused()
        compose.onNodeWithContentDescription("Rewind 10 seconds").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithContentDescription("Playback position").assertIsFocused()
        compose.onNodeWithContentDescription("Playback position").performKeyInput { pressKey(Key.DirectionRight) }
        compose.runOnIdle { assertEquals(130_000L, player.currentPosition) }
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Playback speed").performClick()
        compose.onNodeWithText("1.5×").performClick()
        compose.runOnIdle { assertEquals(1.5f, player.playbackParameters.speed) }
        pressBack()
        compose.onNodeWithText("Display mode").assertIsDisplayed()
        pressBack()
        compose.onNodeWithContentDescription("More options").assertIsFocused()
    }

    @Test fun MOB_CMP_03_audioAndSubtitleSheetsApplySelections() {
        show(false)
        screenshot("mobile-controls")
        compose.onNodeWithText("Audio").performClick()
        compose.onNodeWithText("French").performClick()
        compose.runOnIdle { assertEquals("audio-fr", player.trackSelectionParameters.overrides.keys.single().id) }
        screenshot("mobile-audio")
        compose.onNodeWithContentDescription("Done").performClick()
        compose.onNodeWithText("Subtitles").performClick()
        compose.onNodeWithText("English captions").performClick()
        screenshot("mobile-subtitles")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Appearance · 100%", substring = true))
        compose.onNodeWithText("Appearance · 100%", substring = true).performClick()
        compose.onNodeWithText("Subtitle appearance").assertIsDisplayed()
        screenshot("mobile-appearance")
        compose.onNodeWithContentDescription("Done").performClick()
        compose.onNodeWithText("Off").performClick()
        compose.runOnIdle { assertTrue(C.TRACK_TYPE_TEXT in player.trackSelectionParameters.disabledTrackTypes) }
    }

    @Test fun MOB_A11Y_03_seekExposesRangeAction() {
        show(false)
        compose.onNodeWithContentDescription("Playback position")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(300_000f) }
        compose.runOnIdle { assertEquals(300_000L, player.currentPosition) }
    }

    @Test fun MOB_TYP_03_largeTextKeepsTrackAndAppearanceControlsReachable() {
        show(false, fontScale = 2f)
        compose.onNodeWithText("Subtitles").performClick()
        compose.onNodeWithText("English captions").performScrollTo().performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Appearance · 100%", substring = true))
        compose.onNodeWithText("Appearance · 100%", substring = true).performClick()
        compose.onNodeWithText("Subtitle appearance").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Text color"))
        compose.onNodeWithText("Text color").assertIsDisplayed()
        screenshot("mobile-large-text")
    }

    @Test fun MOB_LAY_16_landscapeUsesScrollableSidePane() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        try {
            check(automation.setRotation(UiAutomation.ROTATION_FREEZE_90))
            compose.waitUntil(5_000) {
                instrumentation.targetContext.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            }
            show(false)
            screenshot("mobile-landscape-controls")
            compose.onNodeWithText("Audio").performClick()
            compose.onNodeWithText("French").performScrollTo().performClick()
            screenshot("mobile-landscape-audio")
            compose.onNodeWithContentDescription("Done").performClick()
            compose.onNodeWithText("Subtitles").performClick()
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Appearance · 100%", substring = true))
        compose.onNodeWithText("Appearance · 100%", substring = true).performClick()
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Text color"))
        compose.onNodeWithText("Text color").assertIsDisplayed()
            screenshot("mobile-landscape-appearance")
        } finally {
            automation.setRotation(UiAutomation.ROTATION_UNFREEZE)
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(650) // Allow system rotation/window animations to settle before capture.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "player-review").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private class FakeChromePlayer : SimpleBasePlayer(Looper.getMainLooper()) {
    private var parameters = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT
    private var position = 120_000L
    private var speed = PlaybackParameters.DEFAULT
    private var playing = false
    private val groups = listOf(
        TrackGroup("audio-en", Format.Builder().setLabel("English").setLanguage("en").setSampleMimeType("audio/aac").setChannelCount(6).build()),
        TrackGroup("audio-fr", Format.Builder().setLabel("French").setLanguage("fr").setSampleMimeType("audio/aac").setChannelCount(2).build()),
        TrackGroup("text-en", Format.Builder().setLabel("English captions").setLanguage("en").setSampleMimeType("text/vtt").build()),
        TrackGroup("text-tr", Format.Builder().setLabel("Turkish captions").setLanguage("tr").setSampleMimeType("text/vtt").build()),
    )
    override fun getState(): State {
        val tracks = Tracks(groups.map { group ->
            val selected = if (group.type in parameters.disabledTrackTypes) false
                else parameters.overrides[group]?.trackIndices?.contains(0)
                    ?: (group.id == "audio-en" && parameters.overrides.keys.none { it.type == C.TRACK_TYPE_AUDIO })
            Tracks.Group(group, false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(selected))
        })
        return State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAll(Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM, Player.COMMAND_GET_TIMELINE, Player.COMMAND_GET_TRACKS,
                Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS, Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_FORWARD, Player.COMMAND_SET_SPEED_AND_PITCH).build())
            .setPlaylist(listOf(MediaItemData.Builder("fixture").setMediaItem(MediaItem.fromUri("https://example.invalid/video"))
                .setDurationUs(3_600_000_000L).setIsSeekable(true).setTracks(tracks).build()))
            .setCurrentMediaItemIndex(0).setContentPositionMs(position).setContentBufferedPositionMs(PositionSupplier { 600_000L })
            .setPlaybackState(Player.STATE_READY).setPlaybackParameters(speed)
            .setPlayWhenReady(playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setTrackSelectionParameters(parameters).build()
    }
    override fun handleSetTrackSelectionParameters(trackSelectionParameters: TrackSelectionParameters): ListenableFuture<*> {
        parameters = trackSelectionParameters
        return Futures.immediateVoidFuture()
    }
    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        position = positionMs
        return Futures.immediateVoidFuture()
    }
    override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> {
        speed = playbackParameters
        return Futures.immediateVoidFuture()
    }
    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        playing = playWhenReady
        return Futures.immediateVoidFuture()
    }
}
