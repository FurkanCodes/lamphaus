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

/** The tracks the policy may choose from, keyed by a stable "group:track" id. */
internal data class TrackCandidates(
    val audio: List<Pair<AudioTrackInfo, TrackRef>>,
    val subtitles: List<Pair<SubtitleTrackInfo, TrackRef>>,
)

/** What the defaults policy decided; a null subtitle means subtitles off. */
internal data class TrackDefaultsDecision(
    val audio: TrackRef?,
    val subtitle: TrackRef?,
)

/**
 * Maps engine tracks into the policy's track model. Tracks the device cannot
 * play are left out, so a default never lands on an undecodable stream; when
 * nothing of a type is playable, every track of that type stays eligible.
 */
internal fun Tracks.trackCandidates(): TrackCandidates {
    fun refs(type: Int): List<Pair<TrackRef, Boolean>> = groups
        .filter { it.type == type }
        .flatMap { group ->
            (0 until group.length).map { index ->
                TrackRef(group.mediaTrackGroup, index, group.getTrackFormat(index)) to group.isTrackSupported(index)
            }
        }
    fun playable(type: Int): List<TrackRef> {
        val all = refs(type)
        return all.filter { it.second }.ifEmpty { all }.map { it.first }
    }
    val audio = playable(C.TRACK_TYPE_AUDIO).mapIndexed { index, ref -> ref.format.audioInfo("a$index") to ref }
    val subtitles = playable(C.TRACK_TYPE_TEXT).mapIndexed { index, ref -> ref.format.subtitleInfo("s$index") to ref }
    return TrackCandidates(audio, subtitles)
}

private fun Format.roles(): Set<TrackRole> {
    val roles = subtitleRoles(label, isForcedFlag = selectionFlags and C.SELECTION_FLAG_FORCED != 0).toMutableSet()
    if (roleFlags and (C.ROLE_FLAG_CAPTION or C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND) != 0) roles += TrackRole.SDH
    if (roleFlags and C.ROLE_FLAG_COMMENTARY != 0) roles += TrackRole.COMMENTARY
    if (roleFlags and C.ROLE_FLAG_DESCRIBES_VIDEO != 0) roles += TrackRole.AUDIO_DESCRIPTION
    return roles
}

private fun Format.audioInfo(id: String) = AudioTrackInfo(
    id = id,
    languageTag = language,
    label = label,
    channelCount = channelCount.coerceAtLeast(0),
    isDefault = selectionFlags and C.SELECTION_FLAG_DEFAULT != 0,
    roles = roles(),
)

private fun Format.subtitleInfo(id: String) = SubtitleTrackInfo(
    id = id,
    languageTag = language,
    label = label,
    isDefault = selectionFlags and C.SELECTION_FLAG_DEFAULT != 0,
    isTextual = sourceMimeType() !in BITMAP_SUBTITLE_MIME_TYPES,
    roles = roles(),
)

private val BITMAP_SUBTITLE_MIME_TYPES = setOf(MimeTypes.APPLICATION_PGS, MimeTypes.APPLICATION_VOBSUB, MimeTypes.APPLICATION_DVBSUBS)

/**
 * The profile's defaults, a title's remembered choice first, applied to the
 * engine's real tracks (plan §3): one policy for Media3 and MPV, embedded and
 * add-on tracks alike.
 */
internal fun decideTrackDefaults(
    candidates: TrackCandidates,
    profile: ProfilePlaybackPreferences,
    remembered: MediaPlaybackSelection?,
    deviceLanguageTag: String,
): TrackDefaultsDecision {
    val audio = selectAudioTrack(
        tracks = candidates.audio.map { it.first },
        sessionSelectionTrackId = null,
        sourceSelectionTrackId = null,
        semantic = remembered,
        profile = profile,
        deviceLanguageTag = deviceLanguageTag,
    )
    val subtitleId = selectSubtitleTrack(
        tracks = candidates.subtitles.map { it.first },
        sessionSelectionTrackId = null,
        sourceSelectionTrackId = null,
        semantic = remembered,
        profile = profile,
        deviceLanguageTag = deviceLanguageTag,
    )
    return TrackDefaultsDecision(
        audio = audio?.let { chosen -> candidates.audio.firstOrNull { it.first.id == chosen.id }?.second },
        subtitle = subtitleId?.let { id -> candidates.subtitles.firstOrNull { it.first.id == id }?.second },
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
        val decision = decideTrackDefaults(tracks.trackCandidates(), profile, remembered, deviceLanguageTag())
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
internal fun Format.rememberedSelection(
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
