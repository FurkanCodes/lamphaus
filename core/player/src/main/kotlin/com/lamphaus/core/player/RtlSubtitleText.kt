package com.lamphaus.core.player

import android.text.SpannableStringBuilder
import android.text.Spanned
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi

/**
 * Right-to-left subtitle lines (Arabic, Hebrew, Persian, Urdu) render inside
 * Media3's left-to-right caption view, so a line that opens with a dialogue
 * dash or ends in punctuation lands on the wrong side. Wrapping each such line
 * in a right-to-left embedding (RLE … PDF) fixes it, as Nuvio does for Arabic.
 * Lines without RTL letters are untouched, and the wrap is idempotent.
 */
object RtlSubtitleText {

    fun embed(text: CharSequence): CharSequence {
        val insertions = insertions(text)
        if (insertions.isEmpty()) return text
        // Insert back to front so earlier indices stay valid; spans survive.
        return if (text is Spanned) {
            SpannableStringBuilder(text).apply {
                insertions.asReversed().forEach { (index, mark) -> insert(index, mark.toString()) }
            }
        } else {
            StringBuilder(text).apply {
                insertions.asReversed().forEach { (index, mark) -> insert(index, mark) }
            }.toString()
        }
    }

    @UnstableApi
    fun embed(group: CueGroup): CueGroup {
        if (group.cues.none { cue -> cue.text?.let(::insertions)?.isNotEmpty() == true }) return group
        val cues = group.cues.map { cue ->
            cue.text?.let { cue.buildUpon().setText(embed(it)).build() } ?: cue
        }
        return CueGroup(cues, group.presentationTimeUs)
    }

    /** Ascending (index, mark) insertions that wrap every unwrapped RTL line. */
    internal fun insertions(text: CharSequence): List<Pair<Int, Char>> {
        val result = mutableListOf<Pair<Int, Char>>()
        var lineStart = 0
        while (lineStart <= text.length) {
            val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
            val line = text.subSequence(lineStart, lineEnd)
            if (line.firstOrNull() != RLE && line.any(::isRtlLetter)) {
                // A trailing CR stays outside the embedding so the PDF still closes it.
                val contentEnd = if (line.lastOrNull() == '\r') lineEnd - 1 else lineEnd
                result += lineStart to RLE
                result += contentEnd to PDF
            }
            lineStart = lineEnd + 1
        }
        return result
    }

    private fun isRtlLetter(char: Char): Boolean = when (Character.getDirectionality(char)) {
        Character.DIRECTIONALITY_RIGHT_TO_LEFT, Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> true
        else -> false
    }

    private const val RLE = '‫'
    private const val PDF = '‬'
}
