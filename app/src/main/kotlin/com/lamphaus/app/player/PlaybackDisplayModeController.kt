package com.lamphaus.app.player

import android.app.Activity
import android.os.Build
import android.view.Display
import com.lamphaus.core.model.DevicePlaybackConfig
import com.lamphaus.core.model.DisplayModeCandidate
import com.lamphaus.core.model.FrameRateMatching
import com.lamphaus.core.model.ResolutionMatching
import com.lamphaus.core.model.selectDisplayMode

/**
 * TV frame-rate and resolution matching (plan §2). Applies the matched
 * display mode only after the video format has remained stable for two
 * seconds, ignores adaptive-bitrate representation changes, and restores the
 * original mode when playback ends, fails, or the Activity closes.
 *
 * Decision math lives in [selectDisplayMode] (unit-tested); this controller
 * owns the Android mechanics: mode switching through
 * `WindowManager.LayoutParams.preferredDisplayModeId` and the seamless-only
 * gate via `Display.Mode.alternativeRefreshRates`.
 */
class PlaybackDisplayModeController(
    private val activity: Activity,
    private val configProvider: () -> DevicePlaybackConfig,
    /** Reports the applied mode or why a requested mode was not applied (plan §2 stream info). */
    private val onModeDecision: (DisplayModeDecision) -> Unit = {},
) {
    data class DisplayModeDecision(val appliedMode: DisplayModeCandidate?, val reason: String)

    @Suppress("DEPRECATION")
    private val display: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        activity.display
    } else {
        activity.windowManager.defaultDisplay
    }
    private val originalModeId = display?.mode?.modeId

    private var pendingFormat: Triple<Int, Int, Float>? = null
    private var stableSinceElapsedMillis = 0L
    private var appliedModeId: Int? = null

    /**
     * Feeds a newly observed video format. Adaptive-bitrate changes that keep
     * the same (width, height, fps) bucket never restart the stability timer.
     */
    fun onVideoFormat(width: Int, height: Int, frameRateHz: Float) {
        val format = Triple(width, height, frameRateHz)
        val previous = pendingFormat
        val previousBucket = previous?.let { (w, h, f) -> "${w}x${h}@${f.toInt()}" }
        val newBucket = "${width}x${height}@${frameRateHz.toInt()}"
        if (previous == null || previousBucket != newBucket) {
            pendingFormat = format
            stableSinceElapsedMillis = 0L
        }
    }

    /** Drives the two-second stability gate; call from the progress pulse. */
    fun tick(deltaMillis: Long) {
        if (appliedModeId != null || pendingFormat == null || display == null) return
        stableSinceElapsedMillis += deltaMillis
        if (stableSinceElapsedMillis < STABILITY_MILLIS) return
        val (width, height, frameRateHz) = pendingFormat ?: return
        val config = configProvider()
        if (config.frameRateMatching == FrameRateMatching.OFF && config.resolutionMatching == ResolutionMatching.OFF) {
            return
        }
        val modes = display.supportedModes.map { DisplayModeCandidate(it.physicalWidth, it.physicalHeight, it.refreshRate) }
        val current = display.mode.let { DisplayModeCandidate(it.physicalWidth, it.physicalHeight, it.refreshRate) }
        val wantsResolution = config.resolutionMatching == ResolutionMatching.MATCH_SOURCE
        val wanted = selectDisplayMode(current, width, height, frameRateHz, modes)
        if (wanted == null) {
            onModeDecision(DisplayModeDecision(null, "current mode already matches the source"))
            return
        }
        if (!wantsResolution && (wanted.width != current.width || wanted.height != current.height)) {
            // Frame-rate-only matching may not change physical resolution.
            val sameResolution = modes.filter { it.width == current.width && it.height == current.height }
            val rateOnly = selectDisplayMode(current, width, height, frameRateHz, sameResolution)
            applyOrReport(rateOnly, current, "resolution matching is off")
            return
        }
        applyOrReport(wanted, current, if (wantsResolution) "" else "resolution matching is off")
    }

    private fun applyOrReport(
        mode: DisplayModeCandidate?,
        current: DisplayModeCandidate,
        skipReason: String,
    ) {
        if (mode == null) {
            onModeDecision(DisplayModeDecision(null, skipReason.ifEmpty { "no matching mode" }))
            return
        }
        val layout = activity.window.attributes
        val candidate = display?.supportedModes?.firstOrNull {
            it.physicalWidth == mode.width && it.physicalHeight == mode.height &&
                kotlin.math.abs(it.refreshRate - mode.refreshRateHz) < 0.01f
        }
        if (candidate == null) {
            onModeDecision(DisplayModeDecision(null, "requested mode not offered by the display"))
            return
        }
        if (configProvider().frameRateMatching == FrameRateMatching.SEAMLESS_ONLY &&
            candidate.modeId != current.let { display?.mode?.modeId } &&
            !isSeamless(display!!, current, mode)
        ) {
            onModeDecision(DisplayModeDecision(null, "non-seamless switch skipped (seamless only)"))
            return
        }
        layout.preferredDisplayModeId = candidate.modeId
        activity.window.attributes = layout
        appliedModeId = candidate.modeId
        onModeDecision(DisplayModeDecision(mode, "matched"))
    }

    private fun isSeamless(display: Display, current: DisplayModeCandidate, target: DisplayModeCandidate): Boolean {
        // Same resolution is always seamless; different resolutions are only
        // seamless when the display advertises the rate as an alternative.
        if (current.width == target.width && current.height == target.height) return true
        val currentMode = display.supportedModes.firstOrNull {
            it.physicalWidth == current.width && it.physicalHeight == current.height &&
                kotlin.math.abs(it.refreshRate - current.refreshRateHz) < 0.01f
        } ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return currentMode.alternativeRefreshRates.any {
            kotlin.math.abs(it - target.refreshRateHz) < 0.01f
        }
    }

    /** Reports why matching was skipped without changing the display mode. */
    fun reportSkipped(reason: String) {
        onModeDecision(DisplayModeDecision(null, reason))
    }

    /** Restores the original display mode; call on end, failure, and destroy. */
    fun restore() {
        val originalId = originalModeId ?: return
        val layout = activity.window.attributes
        if (layout.preferredDisplayModeId != originalId) {
            layout.preferredDisplayModeId = originalId
            activity.window.attributes = layout
        }
        appliedModeId = null
        pendingFormat = null
        stableSinceElapsedMillis = 0L
    }

    private companion object {
        const val STABILITY_MILLIS = 2_000L
    }
}
