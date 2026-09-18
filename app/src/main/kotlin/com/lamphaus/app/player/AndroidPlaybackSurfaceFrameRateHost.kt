package com.lamphaus.app.player

import android.os.Build
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import androidx.annotation.RequiresApi
import androidx.media3.ui.PlayerView

/** Owns the explicit non-seamless surface vote used by the TV ALWAYS setting. */
internal class AndroidPlaybackSurfaceFrameRateHost : PlaybackSurfaceFrameRateHost, SurfaceHolder.Callback {
    private var holder: SurfaceHolder? = null
    private var requestedFrameRateHz = 0f

    fun attachPlayerView(playerView: View) {
        val nextHolder = ((playerView as? PlayerView)?.videoSurfaceView as? SurfaceView)?.holder
        if (holder === nextHolder) return
        holder?.let { previous ->
            previous.removeCallback(this)
            applyFrameRate(previous.surface, 0f)
        }
        holder = nextHolder
        nextHolder?.addCallback(this)
        nextHolder?.surface?.let { applyFrameRate(it, requestedFrameRateHz) }
    }

    override fun requestFrameRate(frameRateHz: Float) {
        requestedFrameRateHz = frameRateHz.takeIf { it.isFinite() && it > 0f } ?: 0f
        holder?.surface?.let { applyFrameRate(it, requestedFrameRateHz) }
    }

    override fun clearFrameRate() {
        requestedFrameRateHz = 0f
        holder?.surface?.let { applyFrameRate(it, 0f) }
    }

    fun release() {
        clearFrameRate()
        holder?.removeCallback(this)
        holder = null
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (holder === this.holder) applyFrameRate(holder.surface, requestedFrameRateHz)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) = Unit

    private fun applyFrameRate(surface: Surface, frameRateHz: Float) {
        if (!surface.isValid) return
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> applyFrameRateApi31(surface, frameRateHz)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> applyFrameRateApi30(surface, frameRateHz)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun applyFrameRateApi31(surface: Surface, frameRateHz: Float) {
        runCatching {
            surface.setFrameRate(
                frameRateHz,
                Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                Surface.CHANGE_FRAME_RATE_ALWAYS,
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun applyFrameRateApi30(surface: Surface, frameRateHz: Float) {
        // Android 11 has no non-seamless strategy argument. Retain the best
        // available seamless hint while preferredDisplayModeId handles modes
        // the display advertises explicitly.
        runCatching {
            surface.setFrameRate(frameRateHz, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
        }
    }
}
