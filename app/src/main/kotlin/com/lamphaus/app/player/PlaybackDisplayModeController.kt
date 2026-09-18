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
        HDR_UNAVAILABLE(R.string.playback_display_hdr_unavailable),
    }

    internal constructor(
        activity: Activity,
        configProvider: () -> DevicePlaybackConfig,
        onModeDecision: (DisplayModeDecision) -> Unit = {},
        surfaceFrameRateHost: PlaybackSurfaceFrameRateHost = NoOpPlaybackSurfaceFrameRateHost,
    ) : this(AndroidPlaybackDisplayHost(activity), configProvider, onModeDecision, surfaceFrameRateHost)

    // Zero means system-managed; restoring the observed physical ID would pin the window.
    private val originalPreferredModeId = output.preferredModeId
    private var pendingFormat: PlaybackVideoFormat? = null
    private var stableMillis = 0L
    private var evaluated = false
    private var evaluateImmediately = false
    private var matchingConfig: Pair<FrameRateMatching, ResolutionMatching>? = null
    private var requestedMode: PlaybackOutputMode? = null
    private var requestMillis = 0L
    private var outputLockedForItem = false

    internal fun onVideoFormat(
        width: Int,
        height: Int,
        frameRateHz: Float,
        hdrType: PlaybackHdrType? = null,
    ) {
        if (width <= 0 || height <= 0) return
        if (outputLockedForItem || requestedMode != null) return
        val format = PlaybackVideoFormat(
            width = width,
            height = height,
            frameRateHz = normalizeFrameRate(frameRateHz),
            hdrType = hdrType,
        )
        if (pendingFormat?.matches(format) != true) {
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
    fun prepareForPlayback(): Boolean {
        val format = pendingFormat ?: return false
        val config = configProvider()
        // Many containers omit FPS metadata. Do not settle startup against an
        // unknown cadence: the activity will decode a bounded, muted warm-up
        // behind the loading surface until VideoCadenceEstimator has a value.
        // Otherwise the same source becomes "new" about 2–3 seconds after it
        // is visible and causes a late, disruptive display-mode switch.
        if (config.frameRateMatching != FrameRateMatching.OFF && format.frameRateHz <= 0f) {
            return false
        }
        if (!evaluated && requestedMode == null) {
            stableMillis = STABILITY_MILLIS
            evaluateImmediately = true
        }
        return true
    }

    /** Called on the main thread while playback is active. */
    fun tick(deltaMillis: Long) {
        val config = configProvider()
        val settings = config.frameRateMatching to config.resolutionMatching
        if (matchingConfig != settings) {
            if (matchingConfig != null) restoreOutputPreferences()
            matchingConfig = settings
            evaluated = false
            outputLockedForItem = false
            stableMillis = if (evaluateImmediately) STABILITY_MILLIS else 0L
        }
        val currentMode = output.currentMode
        requestedMode?.let { requested ->
            requestMillis += deltaMillis
            if (currentMode?.id == requested.id) {
                onModeDecision(DisplayModeDecision(requested.candidate, DisplayModeReason.MATCHED))
                requestedMode = null
                outputLockedForItem = true
            } else if (requestMillis >= SWITCH_TIMEOUT_MILLIS) {
                onModeDecision(DisplayModeDecision(null, DisplayModeReason.NOT_APPLIED))
                requestedMode = null
                outputLockedForItem = true
            } else {
                return
            }
        }
        if (evaluated || outputLockedForItem) return
        val format = pendingFormat ?: return
        val width = format.width
        val height = format.height
        val frameRate = format.frameRateHz
        stableMillis += deltaMillis
        if (stableMillis < STABILITY_MILLIS) return
        evaluated = true
        evaluateImmediately = false
        if (settings.first == FrameRateMatching.ALWAYS && frameRate > 0f) {
            surfaceFrameRateHost.requestFrameRate(frameRate)
        } else {
            surfaceFrameRateHost.clearFrameRate()
        }
        if (settings.first == FrameRateMatching.OFF && settings.second == ResolutionMatching.OFF &&
            (format.hdrType == null || currentMode?.supports(format.hdrType) != false)
        ) {
            onModeDecision(DisplayModeDecision(null, DisplayModeReason.OFF))
            outputLockedForItem = true
            return
        }
        if (currentMode == null) {
            outputLockedForItem = true
            return
        }
        // Before API 34 Android exposes display-wide HDR capabilities, not the
        // HDR types for each physical mode. A guessed preferredDisplayModeId
        // can therefore drop the HDMI link back to SDR. Keep the current mode
        // and let the Surface frame-rate vote choose a compatible cadence.
        if (format.hdrType != null && !currentMode.hdrTypesAreModeSpecific) {
            onModeDecision(DisplayModeDecision(currentMode.candidate, DisplayModeReason.NO_BETTER_MODE))
            outputLockedForItem = true
            return
        }
        val current = currentMode.candidate
        val alternatives = currentMode.alternativeRefreshRates
        // Filter before ranking so an unavailable seamless choice cannot hide a valid one.
        // Resolution opt-in permits resizing at the same refresh rate; it does not
        // grant permission for an unadvertised non-seamless refresh-rate change.
        val modes = output.supportedModes.filter {
            (format.hdrType == null || it.supports(format.hdrType)) && (
                settings.first != FrameRateMatching.SEAMLESS_ONLY ||
                    isSeamlessDisplayMode(current, it.candidate, alternatives) ||
                    (settings.second == ResolutionMatching.MATCH_SOURCE &&
                        refreshRateMatches(current.refreshRateHz, it.candidate.refreshRateHz))
                )
        }
        if (format.hdrType != null && modes.isEmpty()) {
            onModeDecision(DisplayModeDecision(null, DisplayModeReason.HDR_UNAVAILABLE))
            outputLockedForItem = true
            return
        }
        val hdrUpgrade = format.hdrType
            ?.takeIf { !currentMode.supports(it) }
            ?.let { modes.firstOrNull { mode -> mode.candidate == current } }
        val wanted = selectDisplayMode(
            current, width, height, frameRate, modes.map { it.candidate },
            matchFrameRate = settings.first != FrameRateMatching.OFF,
            matchResolution = settings.second == ResolutionMatching.MATCH_SOURCE,
        )
        if (wanted == null && hdrUpgrade == null) {
            val reason = when {
                frameRate <= 0f && settings.first != FrameRateMatching.OFF ->
                    DisplayModeReason.UNKNOWN_FRAME_RATE
                settings.first == FrameRateMatching.SEAMLESS_ONLY ->
                    DisplayModeReason.NO_SEAMLESS_MODE
                else -> DisplayModeReason.NO_BETTER_MODE
            }
            onModeDecision(DisplayModeDecision(null, reason))
            outputLockedForItem = true
            return
        }
        val candidate = hdrUpgrade ?: modes.first { it.candidate == wanted }
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
        outputLockedForItem = false
    }

    private companion object {
        const val STABILITY_MILLIS = 2_000L
        const val SWITCH_TIMEOUT_MILLIS = 5_000L
    }
}

internal enum class PlaybackHdrType { HDR10, HLG, DOLBY_VISION }

private data class PlaybackVideoFormat(
    val width: Int,
    val height: Int,
    val frameRateHz: Float,
    val hdrType: PlaybackHdrType?,
) {
    fun matches(other: PlaybackVideoFormat): Boolean =
        width == other.width && height == other.height && hdrType == other.hdrType &&
            when {
                frameRateHz <= 0f || other.frameRateHz <= 0f -> frameRateHz == other.frameRateHz
                else -> refreshRateMatches(frameRateHz, other.frameRateHz) ||
                    kotlin.math.abs(frameRateHz - other.frameRateHz) < FRAME_RATE_JITTER_HZ
            }
}

private fun normalizeFrameRate(frameRateHz: Float): Float {
    if (!frameRateHz.isFinite() || frameRateHz <= 0f) return 0f
    return KNOWN_FRAME_RATES.firstOrNull {
        kotlin.math.abs(frameRateHz - it) < FRAME_RATE_JITTER_HZ
    } ?: frameRateHz
}

private val KNOWN_FRAME_RATES = floatArrayOf(
    24000f / 1001f,
    24f,
    25f,
    30000f / 1001f,
    30f,
    50f,
    60000f / 1001f,
    60f,
    100f,
    120000f / 1001f,
    120f,
)

private const val FRAME_RATE_JITTER_HZ = 0.01f

internal data class PlaybackOutputMode(
    val id: Int,
    val candidate: DisplayModeCandidate,
    val alternativeRefreshRates: List<Float> = emptyList(),
    val supportedHdrTypes: Set<PlaybackHdrType> = emptySet(),
    val hdrTypesAreModeSpecific: Boolean = true,
) {
    fun supports(hdrType: PlaybackHdrType): Boolean = hdrType in supportedHdrTypes
}

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
    override val currentMode get() = display?.let { it.mode.toOutputMode(it) }
    override val supportedModes get() = display?.let { target ->
        target.supportedModes.map { it.toOutputMode(target) }
    }.orEmpty()
    override var preferredModeId: Int
        get() = activity.window.attributes.preferredDisplayModeId
        set(value) {
            val layout = activity.window.attributes
            layout.preferredDisplayModeId = value
            activity.window.attributes = layout
        }

    @Suppress("DEPRECATION")
    private fun Display.Mode.toOutputMode(display: Display) = PlaybackOutputMode(
        modeId,
        DisplayModeCandidate(physicalWidth, physicalHeight, refreshRate),
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alternativeRefreshRates.toList() else emptyList(),
        supportedHdrTypes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            supportedHdrTypes.toPlaybackHdrTypes()
        } else {
            display.hdrCapabilities.supportedHdrTypes.toPlaybackHdrTypes()
        },
        hdrTypesAreModeSpecific = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
    )

    private fun IntArray.toPlaybackHdrTypes(): Set<PlaybackHdrType> = buildSet {
        for (type in this@toPlaybackHdrTypes) {
            when (type) {
                Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> add(PlaybackHdrType.DOLBY_VISION)
                Display.HdrCapabilities.HDR_TYPE_HDR10,
                Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS,
                -> add(PlaybackHdrType.HDR10)
                Display.HdrCapabilities.HDR_TYPE_HLG -> add(PlaybackHdrType.HLG)
            }
        }
    }
}
