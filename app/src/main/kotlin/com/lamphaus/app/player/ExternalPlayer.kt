package com.lamphaus.app.player

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.activity.result.contract.ActivityResultContract
import com.lamphaus.core.model.PlaybackSegment
import com.lamphaus.core.model.PlaybackSegmentType

/** A stream handed to another player app (PLY-EXT-01). */
internal data class ExternalPlayback(
    val uri: String,
    val title: String?,
    val headers: Map<String, String>,
    val resumePositionMillis: Long,
    /** Subtitles to load alongside, preferred language first. */
    val subtitles: List<ExternalSubtitle> = emptyList(),
    /** Intro and outro timestamps for players that skip them (mpvNova reads them). */
    val skipSegments: List<PlaybackSegment> = emptyList(),
)

internal data class ExternalSubtitle(val uri: String, val name: String, val language: String?)

/** What the other app reported when it returned; many report nothing. */
internal data class ExternalPlaybackResult(
    val positionMillis: Long,
    val durationMillis: Long?,
    /** The app said playback reached the end (MX Player, Just Player, mpvNova, mpv at EOF). */
    val completed: Boolean,
)

/**
 * Opens a stream in another video player with the extras the common
 * players read (MX Player, Just Player, VLC, mpv-android, Vimu): title,
 * request headers, resume position, subtitles, and a request to report
 * where playback stopped. Players differ in what they honour; unknown extras
 * are ignored. The type is `video/` so video players, not browsers, answer.
 */
internal class ExternalPlayerContract : ActivityResultContract<ExternalPlayback, ExternalPlaybackResult?>() {

    override fun createIntent(context: Context, input: ExternalPlayback): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(input.uri.toUri(), "video/*")
            input.title?.let { title ->
                putExtra("title", title)
                putExtra(Intent.EXTRA_TITLE, title)
                putExtra("forcename", title)
            }
            if (input.headers.isNotEmpty()) {
                // MX Player's convention, also read by Just Player: alternating names and values.
                putExtra("headers", input.headers.flatMap { listOf(it.key, it.value) }.toTypedArray())
            }
            if (input.resumePositionMillis > 0) {
                putExtra("position", input.resumePositionMillis.toInt())
                putExtra("extra_position", input.resumePositionMillis)
                putExtra("startfrom", input.resumePositionMillis.toInt())
                putExtra("forceresume", true)
                putExtra("from_start", false)
            } else {
                putExtra("from_start", true)
            }
            putExtra("return_result", true)
            if (input.skipSegments.isNotEmpty()) putExtra("skip_segments", skipSegmentsJson(input.skipSegments))
            if (input.subtitles.isNotEmpty()) putSubtitles(input.subtitles)
        }

    private fun Intent.putSubtitles(subtitles: List<ExternalSubtitle>) {
        val uris = subtitles.map { it.uri.toUri() }
        // Content URIs in extras are only readable with a grant through ClipData.
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData("subtitles", arrayOf("application/x-subrip", "text/vtt"), ClipData.Item(uris.first())).apply {
            uris.drop(1).forEach { addItem(ClipData.Item(it)) }
        }
        val names = subtitles.map { it.name }.toTypedArray()
        putExtra("subs", uris.toTypedArray())
        putExtra("subs.name", names)
        putExtra("subs.filename", subtitles.map { "${it.language ?: "und"}_${it.name}.srt" }.toTypedArray())
        putExtra("subs.enable", arrayOf(uris.first()))
        putExtra("subtitle_uri", uris.toTypedArray())
        putExtra("subtitle_name", names)
        putExtra("subtitles_location", uris.first().toString())
        putExtra("forcedsrt", subtitles.first().uri)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): ExternalPlaybackResult? {
        val data = intent ?: return null
        val extras = data.extras?.let { bundle -> bundle.keySet().associateWith { key -> @Suppress("DEPRECATION") bundle.get(key) } }
            .orEmpty()
        return ExternalPlayerResults.parse(resultCode == Activity.RESULT_OK, data.action, extras)
    }

    companion object {
        /** `[{"type":"intro","start":12.0,"end":84.5}]`, times in seconds. */
        fun skipSegmentsJson(segments: List<PlaybackSegment>): String = segments
            .filter { it.type != PlaybackSegmentType.POST_CREDITS && it.endMillis != null }
            .joinToString(prefix = "[", postfix = "]") { segment ->
                val type = when (segment.type) {
                    PlaybackSegmentType.INTRO -> "intro"
                    PlaybackSegmentType.RECAP -> "recap"
                    else -> "outro"
                }
                "{\"type\":\"$type\",\"start\":${segment.startMillis / 1000.0},\"end\":${segment.endMillis!! / 1000.0}}"
            }
    }
}

/**
 * Reads what players report on return. MX Player, Just Player, and mpvNova
 * send `position`/`duration` (ms, Int) and `end_by`; VLC sends
 * `extra_position`/`extra_duration` (ms, Long); vanilla mpv-android returns
 * no extras at all when a file ends.
 */
internal object ExternalPlayerResults {
    private const val MPV_RESULT_ACTION = "is.xyz.mpv.MPVActivity.result"

    fun parse(resultOk: Boolean, action: String?, extras: Map<String, Any?>): ExternalPlaybackResult? {
        val position = positive(extras, "extra_position", "position")
        val duration = positive(extras, "extra_duration", "duration")
        val endedNaturally = extras["end_by"] == "playback_completion"
        val mpvEnded = resultOk && action == MPV_RESULT_ACTION && position == null && duration == null && extras["end_by"] == null
        if (position == null && !endedNaturally && !mpvEnded) return null
        // Players that report the end often reset the position to 0; the end counts as watched.
        return ExternalPlaybackResult(
            positionMillis = position ?: duration ?: 0L,
            durationMillis = duration,
            completed = endedNaturally || mpvEnded,
        )
    }

    private fun positive(extras: Map<String, Any?>, vararg keys: String): Long? = keys.firstNotNullOfOrNull { key ->
        when (val value = extras[key]) {
            is Long -> value.takeIf { it > 0 }
            is Int -> value.toLong().takeIf { it > 0 }
            else -> null
        }
    }
}
