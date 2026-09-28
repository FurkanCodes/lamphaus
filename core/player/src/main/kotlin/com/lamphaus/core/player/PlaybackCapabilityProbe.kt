package com.lamphaus.core.player

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.view.Display
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioCapabilities
import com.lamphaus.core.model.PlaybackCapabilities
import com.lamphaus.core.model.VideoCodecFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads what this device and its current route can play, for predicting how
 * a source will play before it starts. Decoders never change while the app
 * runs and are read once; the screen and the audio route (an HDMI receiver,
 * a soundbar) can, so they are read on every call.
 */
class PlaybackCapabilityProbe(context: Context) {
    private val context = context.applicationContext
    private val decoders by lazy(::readDecoders)

    @OptIn(UnstableApi::class)
    suspend fun capabilities(): PlaybackCapabilities = withContext(Dispatchers.Default) {
        val hdrTypes = displayHdrTypes()
        val audio = AudioCapabilities.getCapabilities(context)
        PlaybackCapabilities(
            supportsDolbyVision = Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION in hdrTypes,
            supportsHdr10 = Display.HdrCapabilities.HDR_TYPE_HDR10 in hdrTypes,
            supportsHdr10Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS in hdrTypes,
            supportsHlg = Display.HdrCapabilities.HDR_TYPE_HLG in hdrTypes,
            supportsAc3Passthrough = audio.supportsEncoding(C.ENCODING_AC3),
            supportsEac3Passthrough = audio.supportsEncoding(C.ENCODING_E_AC3),
            supportsEac3JocPassthrough = audio.supportsEncoding(C.ENCODING_E_AC3_JOC),
            supportsTrueHdPassthrough = audio.supportsEncoding(C.ENCODING_DOLBY_TRUEHD),
            supportsDtsPassthrough = audio.supportsEncoding(C.ENCODING_DTS) ||
                audio.supportsEncoding(C.ENCODING_DTS_HD),
            maxPcmChannelCount = audio.maxChannelCount,
            hardwareVideoMaxHeight = decoders.maxHeights,
            dolbyVisionDecoderProfiles = decoders.dolbyVisionProfiles,
        )
    }

    @Suppress("DEPRECATION")
    private fun displayHdrTypes(): Set<Int> {
        val display = context.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY) ?: return emptySet()
        val types = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            display.mode.supportedHdrTypes
        } else {
            display.hdrCapabilities?.supportedHdrTypes
        }
        return types?.toSet().orEmpty()
    }

    private class Decoders(val maxHeights: Map<VideoCodecFamily, Int>, val dolbyVisionProfiles: Set<Int>)

    private fun readDecoders(): Decoders {
        val infos = runCatching { MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.toList() }
            .getOrDefault(emptyList())
            .filter { !it.isEncoder && it.isHardware() }
        val maxHeights = VIDEO_TYPES.mapNotNull { (family, mimeType) ->
            val height = infos.filter { mimeType in it.supportedTypes }
                .maxOfOrNull { info -> info.maxHeight(mimeType) } ?: 0
            (family to height).takeIf { height > 0 }
        }.toMap()
        // The player skips emulator Dolby Vision decoders (PlaybackEnginePolicy).
        val dolbyVisionProfiles = if (DeviceEnvironment.isAndroidEmulator()) {
            emptySet()
        } else {
            infos.filter { MimeTypes.VIDEO_DOLBY_VISION in it.supportedTypes }
                .flatMap { info ->
                    runCatching { info.getCapabilitiesForType(MimeTypes.VIDEO_DOLBY_VISION).profileLevels.toList() }
                        .getOrDefault(emptyList())
                }
                // Each DolbyVisionProfile* constant is 1 shl its profile number.
                .mapNotNull { level -> Integer.numberOfTrailingZeros(level.profile).takeIf { level.profile > 0 } }
                .toSet()
        }
        return Decoders(maxHeights, dolbyVisionProfiles)
    }

    private fun MediaCodecInfo.isHardware(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            isHardwareAccelerated
        } else {
            SOFTWARE_PREFIXES.none { name.startsWith(it, ignoreCase = true) }
        }

    private fun MediaCodecInfo.maxHeight(mimeType: String): Int {
        val video = runCatching { getCapabilitiesForType(mimeType).videoCapabilities }.getOrNull() ?: return 0
        return FRAME_SIZES.firstOrNull { (width, height) ->
            runCatching { video.isSizeSupported(width, height) }.getOrDefault(false)
        }?.second ?: 0
    }

    private companion object {
        val VIDEO_TYPES = listOf(
            VideoCodecFamily.AVC to MimeTypes.VIDEO_H264,
            VideoCodecFamily.HEVC to MimeTypes.VIDEO_H265,
            VideoCodecFamily.AV1 to MimeTypes.VIDEO_AV1,
            VideoCodecFamily.VP9 to MimeTypes.VIDEO_VP9,
        )
        val FRAME_SIZES = listOf(3840 to 2160, 2560 to 1440, 1920 to 1080, 1280 to 720, 854 to 480)
        val SOFTWARE_PREFIXES = listOf("OMX.google.", "c2.android.", "OMX.ffmpeg.", "c2.ffmpeg.")
    }
}
