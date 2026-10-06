package com.lamphaus.core.player.mpv

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import com.lamphaus.core.model.languageMatches
import com.lamphaus.core.model.sourceTrackId
import com.lamphaus.core.player.EngineHandoffState

/** One entry of mpv's `track-list`, the fields the player reads. */
internal data class MpvTrackEntry(
    /** "audio" or "sub". */
    val type: String,
    /** mpv's per-type track id, as a string ("3"). */
    val id: String,
    val language: String? = null,
    val title: String? = null,
    val codec: String? = null,
    val external: Boolean = false,
    val externalFilename: String? = null,
    val forced: Boolean = false,
    val default: Boolean = false,
    val selected: Boolean = false,
    val channelCount: Int = 0,
)

/** What to set on mpv for a set of Media3 track selection parameters. */
internal data class MpvTrackPlan(
    /** mpv audio id to select, or null to leave the current one. */
    val aid: String?,
    /** mpv subtitle id to select, "no" to turn subtitles off, or null to leave them. */
    val sid: String?,
    /** Comma-separated preferred audio languages, mpv's own pick before an override arrives. */
    val alang: String,
    val slang: String,
)

/**
 * Pure mapping between Media3's track model and mpv's (plan §1, §3): the
 * panels and the defaults policy speak Media3 overrides, and mpv selects by
 * its own per-type ids, which the engine exposes as track group ids
 * ("audio-3", "sub-5").
 */
@UnstableApi
internal object MpvTrackMapping {

    /** Add-on subtitles in the order mpv downloads them: preferred languages first, otherwise as given. */
    fun subtitleLoadOrder(
        subtitles: List<androidx.media3.common.MediaItem.SubtitleConfiguration>,
        preferredLanguages: List<String>,
    ): List<androidx.media3.common.MediaItem.SubtitleConfiguration> {
        val preferred = preferredLanguages.mapNotNull(::iso3Language).toSet()
        if (preferred.isEmpty()) return subtitles
        return subtitles.sortedBy { config ->
            val language = config.language?.let(::iso3Language)
            if (language != null && language in preferred) 0 else 1
        }
    }

    /** "en", "en-US", "eng", and "ENG" all become "eng"; bibliographic codes ("fre") become terminology ones ("fra"). */
    internal fun iso3Language(code: String): String? {
        val base = code.trim().lowercase(java.util.Locale.ROOT).substringBefore('-').substringBefore('_')
        val iso3 = when (base.length) {
            2 -> runCatching { java.util.Locale(base).isO3Language }.getOrNull()
            3 -> base
            else -> null
        } ?: return null
        return BIBLIOGRAPHIC_TO_TERMINOLOGY[iso3] ?: iso3.ifEmpty { null }
    }

    private val BIBLIOGRAPHIC_TO_TERMINOLOGY = mapOf(
        "alb" to "sqi", "arm" to "hye", "baq" to "eus", "bur" to "mya", "chi" to "zho", "cze" to "ces",
        "dut" to "nld", "fre" to "fra", "geo" to "kat", "ger" to "deu", "gre" to "ell", "ice" to "isl",
        "mac" to "mkd", "mao" to "mri", "may" to "msa", "per" to "fas", "rum" to "ron", "slo" to "slk",
        "tib" to "bod", "wel" to "cym",
    )
    const val AUDIO = "audio"
    const val SUBTITLE = "sub"

    fun groupId(type: String, mpvId: String): String = "$type-$mpvId"

    fun plan(parameters: TrackSelectionParameters): MpvTrackPlan = plan(
        overrides = parameters.overrides.values.map { override -> override.type to override.mediaTrackGroup.id },
        textDisabled = C.TRACK_TYPE_TEXT in parameters.disabledTrackTypes,
        preferredAudioLanguages = parameters.preferredAudioLanguages,
        preferredTextLanguages = parameters.preferredTextLanguages,
    )

    /** [overrides] are (track type, track group id) pairs, the part of an override mpv needs. */
    fun plan(
        overrides: List<Pair<Int, String>>,
        textDisabled: Boolean,
        preferredAudioLanguages: List<String>,
        preferredTextLanguages: List<String>,
    ): MpvTrackPlan {
        var aid: String? = null
        var sid: String? = null
        overrides.forEach { (type, groupId) ->
            when {
                type == C.TRACK_TYPE_AUDIO && groupId.startsWith("$AUDIO-") -> aid = groupId.removePrefix("$AUDIO-")
                type == C.TRACK_TYPE_TEXT && groupId.startsWith("$SUBTITLE-") -> sid = groupId.removePrefix("$SUBTITLE-")
            }
        }
        if (textDisabled) sid = "no"
        return MpvTrackPlan(
            aid = aid,
            sid = sid,
            alang = preferredAudioLanguages.joinToString(","),
            slang = preferredTextLanguages.joinToString(","),
        )
    }

    /** mpv codec names as Media3 mime types, so the panels name the real format (SRT, PGS, TrueHD). */
    fun mimeType(type: String, codec: String?): String {
        val name = codec?.lowercase().orEmpty()
        return if (type == AUDIO) {
            when {
                name == "aac" -> MimeTypes.AUDIO_AAC
                name == "ac3" -> MimeTypes.AUDIO_AC3
                name == "eac3" -> MimeTypes.AUDIO_E_AC3
                name == "truehd" || name == "mlp" -> MimeTypes.AUDIO_TRUEHD
                name == "dts" -> MimeTypes.AUDIO_DTS
                name == "flac" -> MimeTypes.AUDIO_FLAC
                name == "opus" -> MimeTypes.AUDIO_OPUS
                name == "mp3" -> MimeTypes.AUDIO_MPEG
                name == "vorbis" -> MimeTypes.AUDIO_VORBIS
                name.startsWith("pcm") -> MimeTypes.AUDIO_RAW
                else -> MimeTypes.AUDIO_AAC
            }
        } else {
            when (name) {
                "subrip", "srt" -> MimeTypes.APPLICATION_SUBRIP
                "ass", "ssa" -> MimeTypes.TEXT_SSA
                "hdmv_pgs_subtitle", "pgs" -> MimeTypes.APPLICATION_PGS
                "dvd_subtitle", "vobsub" -> MimeTypes.APPLICATION_VOBSUB
                "dvb_subtitle" -> MimeTypes.APPLICATION_DVBSUBS
                "webvtt", "vtt" -> MimeTypes.TEXT_VTT
                "ttml" -> MimeTypes.APPLICATION_TTML
                "mov_text" -> MimeTypes.APPLICATION_TX3G
                else -> MimeTypes.TEXT_SSA
            }
        }
    }

    fun selectionFlags(entry: MpvTrackEntry): Int =
        (if (entry.forced) C.SELECTION_FLAG_FORCED else 0) or (if (entry.default) C.SELECTION_FLAG_DEFAULT else 0)

    /**
     * The Format id an mpv track carries: an add-on subtitle keeps the id the
     * add-on gave it (matched by its URL), so its origin and remembered
     * choice survive the engine; every other track uses mpv's own id.
     */
    fun formatId(entry: MpvTrackEntry, addonSubtitleIds: Map<String, String>): String =
        entry.externalFilename?.takeIf { entry.external }?.let(addonSubtitleIds::get) ?: "${entry.type}_${entry.id}"

    /**
     * After a hand-off from Media3, the tracks the viewer had: the same
     * add-on subtitle by id, otherwise the same languages (plan §1). mpv
     * numbers tracks differently, so Media3's ids never transfer directly.
     */
    fun restoreSelection(
        entries: List<MpvTrackEntry>,
        state: EngineHandoffState,
        addonSubtitleIds: Map<String, String>,
    ): Pair<String?, String?> {
        val audio = entries.filter { it.type == AUDIO }
        val subtitles = entries.filter { it.type == SUBTITLE }
        val aid = state.audioLanguage?.let { language ->
            audio.firstOrNull { languageMatches(it.language, language) }?.id
        }
        val sid = when {
            state.subtitlesOff -> "no"
            else -> subtitles.firstOrNull { formatId(it, addonSubtitleIds) == sourceTrackId(state.subtitleTrackId) }?.id
                ?: state.subtitleLanguage?.let { language ->
                    subtitles.firstOrNull { languageMatches(it.language, language) && it.forced == state.subtitleForced }?.id
                        ?: subtitles.firstOrNull { languageMatches(it.language, language) }?.id
                }
        }
        return aid to sid
    }
}
