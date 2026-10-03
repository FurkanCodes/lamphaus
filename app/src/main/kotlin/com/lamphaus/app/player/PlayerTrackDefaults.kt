package com.lamphaus.app.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import com.lamphaus.core.model.AudioTrackInfo
import com.lamphaus.core.model.MediaPlaybackSelection
import com.lamphaus.core.model.ProfilePlaybackPreferences
import com.lamphaus.core.model.SubtitleTrackInfo
import com.lamphaus.core.model.TrackRole
import com.lamphaus.core.model.selectAudioTrack
import com.lamphaus.core.model.selectSubtitleTrack
import com.lamphaus.core.model.subtitleRoles

/** One track inside the engine's [Tracks], addressable for an override. */
internal data class TrackRef(val group: TrackGroup, val trackIndex: Int, val format: Format)

/**
 * What the policy reads from a track. Plain values rather than a Media3
 * [Format], so the policy runs (and is tested) without the Android framework.
 */
internal data class TrackFacts(
    val language: String? = null,
    val label: String? = null,
    /** The original format's mime type (SRT, PGS, …), not Media3's parsed-cue type. */
    val mimeType: String? = null,
    val channelCount: Int = 0,
    val selectionFlags: Int = 0,
    val roleFlags: Int = 0,
)

internal fun Format.facts(): TrackFacts =
    TrackFacts(language, label, sourceMimeType(), channelCount, selectionFlags, roleFlags)

/** A track the policy may choose: its facts, whether the device can play it, and the engine [handle] to select it. */
internal data class TrackCandidate<T>(val facts: TrackFacts, val supported: Boolean, val handle: T)

/** What the defaults policy decided; a null subtitle means subtitles off. */
internal data class TrackDefaultsDecision<T>(
    val audio: T?,
    val subtitle: T?,
)

/** The engine's audio or subtitle tracks as candidates, in the engine's order. */
internal fun Tracks.trackCandidates(trackType: Int): List<TrackCandidate<TrackRef>> = groups
    .filter { it.type == trackType }
    .flatMap { group ->
        (0 until group.length).map { index ->
            val format = group.getTrackFormat(index)
            TrackCandidate(format.facts(), group.isTrackSupported(index), TrackRef(group.mediaTrackGroup, index, format))
        }
    }

/**
 * Tracks the device cannot play are left out, so a default never lands on an
 * undecodable stream; when nothing of a type is playable, all stay eligible.
 */
private fun <T> List<TrackCandidate<T>>.playable(): List<TrackCandidate<T>> =
    filter { it.supported }.ifEmpty { this }

private fun TrackFacts.roles(): Set<TrackRole> {
    val roles = subtitleRoles(label, isForcedFlag = selectionFlags and C.SELECTION_FLAG_FORCED != 0).toMutableSet()
    if (roleFlags and (C.ROLE_FLAG_CAPTION or C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND) != 0) roles += TrackRole.SDH
    if (roleFlags and C.ROLE_FLAG_COMMENTARY != 0) roles += TrackRole.COMMENTARY
    if (roleFlags and C.ROLE_FLAG_DESCRIBES_VIDEO != 0) roles += TrackRole.AUDIO_DESCRIPTION
    return roles
}

private fun TrackFacts.audioInfo(id: String) = AudioTrackInfo(
    id = id,
    languageTag = language,
    label = label,
    channelCount = channelCount.coerceAtLeast(0),
    isDefault = selectionFlags and C.SELECTION_FLAG_DEFAULT != 0,
    roles = roles(),
)

private fun TrackFacts.subtitleInfo(id: String) = SubtitleTrackInfo(
    id = id,
    languageTag = language,
    label = label,
    isDefault = selectionFlags and C.SELECTION_FLAG_DEFAULT != 0,
    isTextual = mimeType !in BITMAP_SUBTITLE_MIME_TYPES,
    roles = roles(),
)

private val BITMAP_SUBTITLE_MIME_TYPES = setOf(MimeTypes.APPLICATION_PGS, MimeTypes.APPLICATION_VOBSUB, MimeTypes.APPLICATION_DVBSUBS)

/**
 * The profile's defaults, a title's remembered choice first, applied to the
 * engine's real tracks (plan §3): one policy for Media3 and MPV, embedded and
 * add-on tracks alike.
 */
internal fun <T> decideTrackDefaults(
    audioTracks: List<TrackCandidate<T>>,
    subtitleTracks: List<TrackCandidate<T>>,
    profile: ProfilePlaybackPreferences,
    remembered: MediaPlaybackSelection?,
    deviceLanguageTag: String,
): TrackDefaultsDecision<T> {
    val audio = audioTracks.playable()
    val subtitles = subtitleTracks.playable()
    val audioPick = selectAudioTrack(
        tracks = audio.mapIndexed { index, track -> track.facts.audioInfo("a$index") },
        sessionSelectionTrackId = null,
        sourceSelectionTrackId = null,
        semantic = remembered,
        profile = profile,
        deviceLanguageTag = deviceLanguageTag,
    )
    val subtitlePick = selectSubtitleTrack(
        tracks = subtitles.mapIndexed { index, track -> track.facts.subtitleInfo("s$index") },
        sessionSelectionTrackId = null,
        sourceSelectionTrackId = null,
        semantic = remembered,
        profile = profile,
        deviceLanguageTag = deviceLanguageTag,
    )
    return TrackDefaultsDecision(
        audio = audioPick?.id?.removePrefix("a")?.toIntOrNull()?.let { audio.getOrNull(it)?.handle },
        subtitle = subtitlePick?.removePrefix("s")?.toIntOrNull()?.let { subtitles.getOrNull(it)?.handle },
    )
}

/**
 * Applies [decideTrackDefaults] whenever the engine reports a new set of
 * tracks: when the item loads, when add-on subtitles join, and after an
 * engine hand-off. Once the viewer picks a track of a type, that type is
 * theirs for the rest of this playback and is never overridden again.
 */
internal class PlayerTrackDefaults(
    private val deviceLanguageTag: () -> String,
) : Player.Listener {
    private var player: Player? = null
    private var profile = ProfilePlaybackPreferences()
    private var remembered: MediaPlaybackSelection? = null
    private var appliedAudioKey: String? = null
    private var appliedTextKey: String? = null
    private var viewerChoseAudio = false
    private var viewerChoseText = false

    /** True while audio follows the defaults rather than the viewer's pick. */
    val audioFollowsDefaults: Boolean get() = !viewerChoseAudio

    fun attach(player: Player) {
        this.player?.removeListener(this)
        this.player = player
        player.addListener(this)
        apply(force = true)
    }

    fun detach() {
        player?.removeListener(this)
        player = null
    }

    /** New profile defaults or a remembered per-title choice: re-decide what the viewer has not picked. */
    fun update(profile: ProfilePlaybackPreferences, remembered: MediaPlaybackSelection?) {
        this.profile = profile
        this.remembered = remembered
        apply(force = true)
    }

    /** The viewer picked a track of [trackType] (or turned subtitles off). */
    fun onViewerChoice(trackType: Int) {
        if (trackType == C.TRACK_TYPE_AUDIO) viewerChoseAudio = true
        if (trackType == C.TRACK_TYPE_TEXT) viewerChoseText = true
    }

    /** "Automatic" in the panel: hand [trackType] back to the defaults. */
    fun restoreDefaults(trackType: Int) {
        if (trackType == C.TRACK_TYPE_AUDIO) viewerChoseAudio = false
        if (trackType == C.TRACK_TYPE_TEXT) viewerChoseText = false
        apply(force = true)
    }

    override fun onTracksChanged(tracks: Tracks) {
        apply(force = false)
    }

    private fun apply(force: Boolean) {
        val player = player ?: return
        val tracks = player.currentTracks
        val audioKey = tracks.groupKey(C.TRACK_TYPE_AUDIO)
        val textKey = tracks.groupKey(C.TRACK_TYPE_TEXT)
        val applyAudio = !viewerChoseAudio && audioKey.isNotEmpty() && (force || audioKey != appliedAudioKey)
        val applyText = !viewerChoseText && (audioKey.isNotEmpty() || textKey.isNotEmpty()) &&
            (force || textKey != appliedTextKey)
        if (!applyAudio && !applyText) return
        val decision = decideTrackDefaults(
            audioTracks = tracks.trackCandidates(C.TRACK_TYPE_AUDIO),
            subtitleTracks = tracks.trackCandidates(C.TRACK_TYPE_TEXT),
            profile = profile,
            remembered = remembered,
            deviceLanguageTag = deviceLanguageTag(),
        )
        val builder = player.trackSelectionParameters.buildUpon()
        if (applyAudio) {
            appliedAudioKey = audioKey
            decision.audio?.let { builder.setOverrideForType(TrackSelectionOverride(it.group, it.trackIndex)) }
        }
        if (applyText) {
            appliedTextKey = textKey
            val subtitle = decision.subtitle
            if (subtitle == null) {
                builder.clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            } else {
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(TrackSelectionOverride(subtitle.group, subtitle.trackIndex))
            }
        }
        player.trackSelectionParameters = builder.build()
    }
}

/** A key that changes when the set of groups of [type] changes, not when selection does. */
private fun Tracks.groupKey(type: Int): String = groups
    .filter { it.type == type }
    .joinToString("|") { group ->
        (0 until group.length).joinToString(",") { index -> group.getTrackFormat(index).id.orEmpty() }
    }

/**
 * Subtitles turned off by the viewer: remembered as forced-only with no
 * language, so the title keeps only forced (foreign-dialogue) subtitles.
 */
internal fun MediaPlaybackSelection?.withSubtitlesOff(nowMillis: Long): MediaPlaybackSelection =
    (this ?: MediaPlaybackSelection()).copy(
        subtitleLanguageTag = null,
        subtitlesForcedOnly = true,
        updatedAtEpochMillis = nowMillis,
    )

/** The language and kind of a track the viewer picked, remembered for the title (plan §3). */
internal fun TrackFacts.rememberedSelection(
    trackType: Int,
    current: MediaPlaybackSelection?,
    nowMillis: Long,
): MediaPlaybackSelection {
    val base = current ?: MediaPlaybackSelection()
    val language = language?.takeUnless { it.isBlank() || it == C.LANGUAGE_UNDETERMINED }
    return when (trackType) {
        C.TRACK_TYPE_AUDIO -> base.copy(audioLanguageTag = language, updatedAtEpochMillis = nowMillis)
        else -> base.copy(
            subtitleLanguageTag = language,
            subtitlesForcedOnly = TrackRole.FORCED in roles(),
            updatedAtEpochMillis = nowMillis,
        )
    }
}
