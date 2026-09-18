package com.lamphaus.app.player

import android.app.Activity
import android.os.Build
import android.view.Display
import com.lamphaus.app.R
import com.lamphaus.core.model.DevicePlaybackConfig
import com.lamphaus.core.model.DisplayModeCandidate
import com.lamphaus.core.model.FrameRateMatching
import com.lamphaus.core.model.ResolutionMatching
import com.lamphaus.core.model.isSeamlessDisplayMode
import com.lamphaus.core.model.selectDisplayMode
import com.lamphaus.core.model.refreshRateMatches

/** TV output matching after a stable video format; preserves window policy on exit (PLY-IMM-04). */
class PlaybackDisplayModeController internal constructor(
    private val output: PlaybackDisplayHost,
    private val configProvider: () -> DevicePlaybackConfig,
    private val onModeDecision: (DisplayModeDecision) -> Unit = {},
    private val surfaceFrameRateHost: PlaybackSurfaceFrameRateHost = NoOpPlaybackSurfaceFrameRateHost,
) {
    data class DisplayModeDecision(val appliedMode: DisplayModeCandidate?, val reason: DisplayModeReason)

    enum class DisplayModeReason(val stringRes: Int) {
        MATCHED(R.string.playback_display_matched),
        NOT_APPLIED(R.string.playback_display_not_applied),
        OFF(R.string.playback_display_off),
        UNKNOWN_FRAME_RATE(R.string.playback_display_unknown_rate),
        NO_SEAMLESS_MODE(R.string.playback_display_no_seamless_mode),
        NO_BETTER_MODE(R.string.playback_display_no_better_mode),
        REQUESTED(R.string.playback_display_requested),
    }

    internal constructor(
        activity: Activity,
        configProvider: () -> DevicePlaybackConfig,
        onModeDecision: (DisplayModeDecision) -> Unit = {},
        surfaceFrameRateHost: PlaybackSurfaceFrameRateHost = NoOpPlaybackSurfaceFrameRateHost,
    ) : this(AndroidPlaybackDisplayHost(activity), configProvider, onModeDecision, surfaceFrameRateHost)

    // Zero means system-managed; restoring the observed physical ID would pin the window.
    private val originalPreferredModeId = output.preferredModeId
    private var pendingFormat: Triple<Int, Int, Float>? = null
    private var stableMillis = 0L
    private var evaluated = false
    private var evaluateImmediately = false
    private var matchingConfig: Pair<FrameRateMatching, ResolutionMatching>? = null
    private var requestedMode: PlaybackOutputMode? = null
    private var requestMillis = 0L

    fun onVideoFormat(width: Int, height: Int, frameRateHz: Float) {
        if (width <= 0 || height <= 0) return
        val rate = frameRateHz.takeIf { it.isFinite() && it > 0f } ?: 0f
        val format = Triple(width, height, rate)
        if (pendingFormat != format) {
            pendingFormat = format
            stableMillis = 0L
            evaluated = false
        }
    }

    /**
     * Allows the initial format to be evaluated before playback starts. During
     * normal playback we still wait for [STABILITY_MILLIS] so an adaptive
     * source can settle; startup already waited for Media3 to reach READY.
     */
    fun prepareForPlayback() {
        if (pendingFormat != null && !evaluated && requestedMode == null) {
            stableMillis = STABILITY_MILLIS
            evaluateImmediately = true
        }
    }

    /** Called on the main thread while playback is active. */
    fun tick(deltaMillis: Long) {
        val config = configProvider()
        val settings = config.frameRateMatching to config.resolutionMatching
        if (matchingConfig != settings) {
            if (matchingConfig != null) restoreOutputPreferences()
            matchingConfig = settings
            evaluated = false
            stableMillis = if (evaluateImmediately) STABILITY_MILLIS else 0L
        }
        val currentMode = output.currentMode
        requestedMode?.let { requested ->
            requestMillis += deltaMillis
            if (currentMode?.id == requested.id) {
                onModeDecision(DisplayModeDecision(requested.candidate, DisplayModeReason.MATCHED))
                requestedMode = null
            } else if (requestMillis >= SWITCH_TIMEOUT_MILLIS) {
                onModeDecision(DisplayModeDecision(null, DisplayModeReason.NOT_APPLIED))
                requestedMode = null
            } else {
                return
            }
        }
        if (evaluated) return
        val (width, height, frameRate) = pendingFormat ?: return
        stableMillis += deltaMillis
        if (stableMillis < STABILITY_MILLIS) return
        evaluated = true
        evaluateImmediately = false
        if (settings.first == FrameRateMatching.ALWAYS && frameRate > 0f) {
            surfaceFrameRateHost.requestFrameRate(frameRate)
        } else {
            surfaceFrameRateHost.clearFrameRate()
        }
        if (settings.first == FrameRateMatching.OFF && settings.second == ResolutionMatching.OFF) {
            onModeDecision(DisplayModeDecision(null, DisplayModeReason.OFF))
            return
        }
        if (currentMode == null) return
        val current = currentMode.candidate
        val alternatives = currentMode.alternativeRefreshRates
        // Filter before ranking so an unavailable seamless choice cannot hide a valid one.
        // Resolution opt-in permits resizing at the same refresh rate; it does not
        // grant permission for an unadvertised non-seamless refresh-rate change.
        val modes = output.supportedModes.filter {
            settings.first != FrameRateMatching.SEAMLESS_ONLY ||
                isSeamlessDisplayMode(current, it.candidate, alternatives) ||
                (settings.second == ResolutionMatching.MATCH_SOURCE &&
                    refreshRateMatches(current.refreshRateHz, it.candidate.refreshRateHz))
        }
        val wanted = selectDisplayMode(
            current, width, height, frameRate, modes.map { it.candidate },
            matchFrameRate = settings.first != FrameRateMatching.OFF,
            matchResolution = settings.second == ResolutionMatching.MATCH_SOURCE,
        )
        if (wanted == null) {
            val reason = when {
                frameRate <= 0f && settings.first != FrameRateMatching.OFF ->
                    DisplayModeReason.UNKNOWN_FRAME_RATE
                settings.first == FrameRateMatching.SEAMLESS_ONLY ->
                    DisplayModeReason.NO_SEAMLESS_MODE
                else -> DisplayModeReason.NO_BETTER_MODE
            }
            onModeDecision(DisplayModeDecision(null, reason))
            return
        }
        val candidate = modes.first { it.candidate == wanted }
        output.preferredModeId = candidate.id
        requestedMode = candidate
        requestMillis = 0L
        onModeDecision(DisplayModeDecision(null, DisplayModeReason.REQUESTED))
    }

    private fun restoreWindowPreference() {
        if (output.preferredModeId != originalPreferredModeId) {
            output.preferredModeId = originalPreferredModeId
        }
        requestedMode = null
        requestMillis = 0L
    }

    private fun restoreOutputPreferences() {
        restoreWindowPreference()
        surfaceFrameRateHost.clearFrameRate()
    }

    /** Restores system policy and clears the previous source on end, failure, or replacement. */
    fun restore() {
        restoreOutputPreferences()
        pendingFormat = null
        evaluated = false
        stableMillis = 0L
        evaluateImmediately = false
        matchingConfig = null
    }

    private companion object {
        const val STABILITY_MILLIS = 2_000L
        const val SWITCH_TIMEOUT_MILLIS = 5_000L
    }
}

internal data class PlaybackOutputMode(
    val id: Int,
    val candidate: DisplayModeCandidate,
    val alternativeRefreshRates: List<Float> = emptyList(),
)

/** Small platform boundary so switching, restoration, and confirmation can be regression tested. */
internal interface PlaybackDisplayHost {
    val currentMode: PlaybackOutputMode?
    val supportedModes: List<PlaybackOutputMode>
    var preferredModeId: Int
}

internal interface PlaybackSurfaceFrameRateHost {
    fun requestFrameRate(frameRateHz: Float)
    fun clearFrameRate()
}

private object NoOpPlaybackSurfaceFrameRateHost : PlaybackSurfaceFrameRateHost {
    override fun requestFrameRate(frameRateHz: Float) = Unit
    override fun clearFrameRate() = Unit
}

private class AndroidPlaybackDisplayHost(private val activity: Activity) : PlaybackDisplayHost {
    @Suppress("DEPRECATION")
    private val display: Display? get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        activity.display
    } else {
        activity.windowManager.defaultDisplay
    }
    override val currentMode get() = display?.mode?.toOutputMode()
    override val supportedModes get() = display?.supportedModes?.map { it.toOutputMode() }.orEmpty()
    override var preferredModeId: Int
        get() = activity.window.attributes.preferredDisplayModeId
        set(value) {
            val layout = activity.window.attributes
            layout.preferredDisplayModeId = value
            activity.window.attributes = layout
        }

    private fun Display.Mode.toOutputMode() = PlaybackOutputMode(
        modeId,
        DisplayModeCandidate(physicalWidth, physicalHeight, refreshRate),
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alternativeRefreshRates.toList() else emptyList(),
    )
}
