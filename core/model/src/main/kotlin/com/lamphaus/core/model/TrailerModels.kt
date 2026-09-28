package com.lamphaus.core.model

/**
 * A playable trailer resolved from a YouTube id. Adaptive YouTube streams
 * carry video and audio separately, so [audioUrl] is merged under
 * [videoUrl] when present; an HLS manifest or a muxed progressive stream
 * plays on its own.
 */
data class TrailerSource(
    val videoUrl: String,
    val audioUrl: String? = null,
) {
    val isHls: Boolean
        get() = "/manifest/hls" in videoUrl || videoUrl.substringBefore('?').endsWith(".m3u8")
}
