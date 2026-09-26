package com.lamphaus.core.player

import androidx.media3.common.MimeTypes
import com.lamphaus.core.model.DolbyVisionHandling

/**
 * When to play a Dolby Vision stream as its backward-compatible base layer
 * (Nuvio's HDR10 fallback). Media3 already does this for profiles 4, 8, and 9
 * on non-DV displays; profile 7 (UHD Blu-ray remuxes) it leaves to a decoder
 * that often cannot take it, and playback fails. The base layer of 7 is plain
 * HDR10, so it plays on the ordinary HEVC decoder.
 */
object DolbyVisionPolicy {

    fun shouldPlayBaseLayer(handling: DolbyVisionHandling, nativeDecodeSupported: Boolean): Boolean =
        when (handling) {
            DolbyVisionHandling.NATIVE_ONLY -> false
            DolbyVisionHandling.HDR10_BASE_LAYER, DolbyVisionHandling.DISABLED -> true
            // Profile 7 → 8.1 conversion needs libdovi, which is not bundled;
            // until then conversion behaves like AUTO.
            DolbyVisionHandling.AUTO, DolbyVisionHandling.CONVERT_PROFILE7_TO_81 -> !nativeDecodeSupported
        }

    /**
     * The decodable base layer for a DV `codecs` string such as `dvhe.07.06`,
     * or null when there is none: profile 5 carries IPT colour with no
     * backward-compatible layer and would render green and purple.
     */
    fun baseLayerMimeType(codecs: String?): String? {
        val parts = codecs?.lowercase()?.split('.') ?: return MimeTypes.VIDEO_H265
        val profile = parts.getOrNull(1)?.toIntOrNull()
        if (profile == 5) return null
        return when (parts.first()) {
            "dvav", "dva1" -> MimeTypes.VIDEO_H264
            "dav1" -> MimeTypes.VIDEO_AV1
            else -> MimeTypes.VIDEO_H265
        }
    }
}
