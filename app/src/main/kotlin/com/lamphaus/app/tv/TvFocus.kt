package com.lamphaus.app.tv

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged

/**
 * Entry point of a horizontal strip of focusable items: a row of cards,
 * chips or actions (TV-NAV-05). Entering the strip lands on the item that
 * last had focus there, else on the item at [initialIndex]; never on
 * whatever happens to sit under the previous row's focus. Compose's own
 * `focusRestorer` did not restore these cards, so the strip tracks it.
 */
@Stable
internal class TvRowFocus(initialIndex: Int) {
    internal val entry = FocusRequester()
    internal var entryIndex by mutableIntStateOf(initialIndex)
}

@Composable
internal fun rememberTvRowFocus(initialIndex: Int = 0): TvRowFocus = remember { TvRowFocus(initialIndex) }

/**
 * Makes a strip enter at [row]'s entry item, and keeps Left and Right inside
 * it, so the end of a row cannot throw focus into the navigation or another
 * row. Apply it before a lazy row; for a plain `Row`, pass [plainRow] to add
 * the focus group the entry needs.
 */
internal fun Modifier.tvRowFocus(row: TvRowFocus, plainRow: Boolean = false): Modifier {
    val strip = focusProperties {
        onExit = {
            if (requestedFocusDirection == FocusDirection.Left || requestedFocusDirection == FocusDirection.Right) {
                cancelFocusChange()
            }
        }
    }.focusRestorer(row.entry)
    return if (plainRow) strip.focusGroup() else strip
}

/**
 * Marks the item at [index] of [row]. Only the items whose entry status
 * changes recompose, so focus moves stay cheap (QA-08).
 */
@Composable
internal fun Modifier.tvRowItem(row: TvRowFocus, index: Int): Modifier {
    val isEntry by remember(row, index) { derivedStateOf { row.entryIndex == index } }
    return (if (isEntry) focusRequester(row.entry) else this)
        .onFocusChanged { if (it.isFocused) row.entryIndex = index }
}

/**
 * Keeps focus inside a row or grid when its trailing "Load more"/"Retry"
 * action goes away while focused (TV-FOC-01). Removing a focused node sends
 * focus back to the top navigation, so the action stays composed until focus
 * has moved to the first newly loaded item, or to the last item when nothing
 * new arrived; only then does it disappear.
 */
@Stable
internal class TrailingActionFocus {
    internal val itemRequester = FocusRequester()
    internal var targetIndex by mutableIntStateOf(-1)
    internal var actionFocused by mutableStateOf(false)
    private var itemCountAtPress = -1

    /** Whether to compose the action: while it is wanted, or while it holds focus. */
    fun visible(showAction: Boolean): Boolean = showAction || actionFocused


    /** Call when the trailing action is pressed. */
    fun onPressed(itemCount: Int) {
        itemCountAtPress = itemCount
    }


    internal fun handOff(itemCount: Int) {
        targetIndex = if (itemCountAtPress in 0 until itemCount) itemCountAtPress else itemCount - 1
        itemCountAtPress = -1
    }
}

/** Marks the trailing action tracked by [focus]. */
internal fun Modifier.trailingAction(focus: TrailingActionFocus): Modifier =
    onFocusChanged { focus.actionFocused = it.isFocused }

/** Marks the item at [index] as a possible hand-off target for [focus]. */
internal fun Modifier.trailingItem(focus: TrailingActionFocus, index: Int): Modifier =
    if (index == focus.targetIndex) focusRequester(focus.itemRequester) else this

@Composable
internal fun rememberTrailingActionFocus(showAction: Boolean, itemCount: Int): TrailingActionFocus {
    val focus = remember { TrailingActionFocus() }
    LaunchedEffect(showAction, focus.actionFocused, itemCount) {
        if (!showAction && focus.actionFocused && itemCount > 0) focus.handOff(itemCount)
    }
    LaunchedEffect(focus.targetIndex) {
        if (focus.targetIndex < 0) return@LaunchedEffect
        withFrameNanos { }
        runCatching { focus.itemRequester.requestFocus() }
        focus.targetIndex = -1
    }
    return focus
}
