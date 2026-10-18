package com.lamphaus.app.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** SHR-ARC-18: lazy list keys never repeat, whatever an add-on sends. */
class UniqueKeysTest {
    @Test
    fun `distinct keys are kept as they are`() {
        assertEquals(listOf("a", "b", 3), listOf("a", "b", 3).uniqueKeys { it })
    }

    @Test
    fun `a repeated id gets the next free suffix`() {
        assertEquals(
            listOf("movie:tt1", "series:tt2", "movie:tt1#2", "movie:tt1#3"),
            listOf("movie:tt1", "series:tt2", "movie:tt1", "movie:tt1").uniqueKeys { it },
        )
    }

    @Test
    fun `a suffix never takes a key an item already has`() {
        val keys = listOf("a", "a#2", "a").uniqueKeys { it }

        assertEquals(listOf("a", "a#2", "a#3"), keys)
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `the first of each id keeps its key so focus and scroll stay put`() {
        val before = listOf("x", "y").uniqueKeys { it }
        val after = listOf("x", "y", "x").uniqueKeys { it }

        assertEquals(before, after.take(2))
    }

    @Test
    fun `no lazy list in the app keys items without the unique helpers`() {
        // Compose throws on a repeated key, and these lists show add-on data.
        // Fixed-count lists (skeletons) key by position and are fine.
        val sources = listOf(File("src/main/kotlin"), File("app/src/main/kotlin")).first(File::isDirectory)
        val rawKeyedCall = Regex("""\b(items|itemsIndexed)\((?!\s*(count\s*=\s*)?\d)[^\n]*\bkey\s*=""")
        val offenders = sources.walkTopDown()
            .filter { it.extension == "kt" && it.name != "UniqueKeys.kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    "${file.relativeTo(sources)}:${index + 1}".takeIf { rawKeyedCall.containsMatchIn(line) }
                }
            }
            .toList()

        assertTrue("Use uniqueItems/uniqueItemsIndexed: $offenders", offenders.isEmpty())
    }
}
