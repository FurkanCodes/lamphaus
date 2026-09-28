package com.lamphaus.app.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import com.lamphaus.app.R

/**
 * The Lamphaus wordmark, shared with the website: tracked capitals in Jost
 * Medium beside the house mark. Only the wordmark uses Jost; interface text
 * stays in Inter.
 */
internal val WordmarkFontFamily = FontFamily(Font(R.font.jost_medium, FontWeight.Medium))

/** Letter spacing of the wordmark, a fraction of its size (the site's 0.26em). */
internal const val WORDMARK_TRACKING_EM = 0.26f

internal fun wordmarkStyle(fontSize: TextUnit, tracking: Boolean = true) = TextStyle(
    fontFamily = WordmarkFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = fontSize,
    letterSpacing = if (tracking) WORDMARK_TRACKING_EM.em else 0.em,
)

@Composable
internal fun LamphausWordmark(fontSize: TextUnit, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.app_name).uppercase(),
        style = wordmarkStyle(fontSize),
        color = color,
        modifier = modifier,
    )
}
