package com.lamphaus.app.tv

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import com.lamphaus.app.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TvDetailMetadataTest {
    @get:Rule
    val compose = createComposeRule()

    private val longPlot = buildString {
        repeat(40) { sentence ->
            append("Sentence number $sentence describes more of the story. ")
        }
    }

    @Test
    fun plotExpandsWhileFocusedAndCollapsesWhenFocusLeaves() {
        val plotRequester = FocusRequester()
        val afterRequester = FocusRequester()

        compose.setContent {
            LamphausTvTheme {
                TvDetailTestSurface {
                    Column(Modifier.width(520.dp)) {
                        TvExpandableText(
                            text = longPlot,
                            collapsedLines = 2,
                            modifier = Modifier
                                .focusRequester(plotRequester)
                                .testTag("plot"),
                        )
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .height(24.dp)
                                .focusRequester(afterRequester)
                                .focusable()
                                .testTag("after"),
                        )
                    }
                }
            }
        }

        compose.waitForIdle()
        val collapsedHeight = heightOf("plot")

        compose.runOnIdle { plotRequester.requestFocus() }
        compose.waitForIdle()
        val expandedHeight = heightOf("plot")
        assertTrue("focused plot should reveal the full text", expandedHeight > collapsedHeight)

        compose.runOnIdle { afterRequester.requestFocus() }
        compose.waitForIdle()
        val recollapsedHeight = heightOf("plot")
        assertTrue("blurred plot should collapse again", recollapsedHeight < expandedHeight)
        assertTrue(recollapsedHeight <= collapsedHeight + 1f)
    }

    @Test
    fun focusTraversalSkipsShortPlot() {
        val beforeRequester = FocusRequester()

        compose.setContent {
            LamphausTvTheme {
                TvDetailTestSurface {
                    Column(Modifier.width(520.dp)) {
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .height(24.dp)
                                .focusRequester(beforeRequester)
                                .focusable()
                                .testTag("before"),
                        )
                        TvExpandableText(
                            text = "Short plot.",
                            collapsedLines = 2,
                            modifier = Modifier.width(520.dp).testTag("plot"),
                        )
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .height(24.dp)
                                .focusable()
                                .testTag("after"),
                        )
                    }
                }
            }
        }

        compose.waitForIdle()
        compose.runOnIdle { beforeRequester.requestFocus() }
        compose.waitForIdle()
        compose.onNodeWithTag("before").assertIsFocused()

        compose.onNodeWithTag("before").performKeyInput {
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
        }
        compose.waitForIdle()

        // The short plot fits its collapsed lines, so focus must jump past it.
        compose.onNodeWithTag("after").assertIsFocused()
    }

    @Test
    fun downFromMetadataReturnsToTheOriginatingButton() {
        val beforeRequester = FocusRequester()

        compose.setContent {
            LamphausTvTheme {
                TvDetailTestSurface {
                    Column(Modifier.width(520.dp)) {
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .height(24.dp)
                                .focusRequester(beforeRequester)
                                .focusable()
                                .testTag("before"),
                        )
                        TvExpandableText(
                            text = longPlot,
                            collapsedLines = 2,
                            returnFocusProvider = { beforeRequester },
                            modifier = Modifier.testTag("plot"),
                        )
                    }
                }
            }
        }

        compose.waitForIdle()
        compose.runOnIdle { beforeRequester.requestFocus() }
        compose.waitForIdle()

        // Down into the metadata peek…
        compose.onNodeWithTag("before").performKeyInput {
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("plot").assertIsFocused()

        // …and pressing down again returns to the originating button.
        compose.onNodeWithTag("plot").performKeyInput {
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("before").assertIsFocused()
    }

    @Test
    fun peopleSectionGrowsIntoFullChipListWhileFocused() {
        // More than six names, and long enough that the collapsed two-line
        // summary truncates at the test width — both expansion triggers.
        val cast = List(24) { index -> "Actor With A Distinctly Long Name $index" }
        val castRequester = FocusRequester()

        compose.setContent {
            LamphausTvTheme {
                TvDetailTestSurface {
                    Column(Modifier.width(520.dp)) {
                        TvExpandablePeopleSection(
                            labelRes = R.string.cast,
                            people = cast,
                            modifier = Modifier
                                .focusRequester(castRequester)
                                .testTag("cast"),
                        )
                    }
                }
            }
        }

        compose.waitForIdle()
        val collapsedHeight = heightOf("cast")

        compose.runOnIdle { castRequester.requestFocus() }
        compose.waitForIdle()
        val expandedHeight = heightOf("cast")

        assertTrue("focused cast section should show the full list", expandedHeight > collapsedHeight)
    }

    private fun heightOf(tag: String): Float =
        compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.height
}

@Composable
private fun TvDetailTestSurface(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        colors = SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ),
    ) {
        content()
    }
}
