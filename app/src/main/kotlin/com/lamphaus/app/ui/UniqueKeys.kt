package com.lamphaus.app.ui

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.runtime.Composable

/**
 * Keys for [count] lazy items that never repeat (SHR-ARC-18). Compose throws
 * on a repeated key, and add-ons can send the same id twice, so the first item
 * keeps its key and each repeat takes the next free "#2", "#3"…, as Nuvio does.
 */
internal fun uniqueKeys(count: Int, key: (index: Int) -> Any): List<Any> {
    val used = HashSet<Any>(count * 2)
    return List(count) { index ->
        val base = key(index)
        if (used.add(base)) {
            base
        } else {
            generateSequence(2) { it + 1 }.map { "$base#$it" }.first(used::add)
        }
    }
}

internal fun <T> List<T>.uniqueKeys(key: (item: T) -> Any): List<Any> = uniqueKeys(size) { key(this[it]) }

/** [LazyListScope.items] with keys that never repeat (SHR-ARC-18). */
internal inline fun <T> LazyListScope.uniqueItems(
    items: List<T>,
    noinline key: (item: T) -> Any,
    noinline contentType: (item: T) -> Any? = { null },
    crossinline itemContent: @Composable LazyItemScope.(item: T) -> Unit,
) {
    val keys = items.uniqueKeys(key)
    items(
        count = items.size,
        key = { index -> keys[index] },
        contentType = { index -> contentType(items[index]) },
    ) { index -> itemContent(items[index]) }
}

/** [LazyListScope.itemsIndexed] with keys that never repeat (SHR-ARC-18). */
internal inline fun <T> LazyListScope.uniqueItemsIndexed(
    items: List<T>,
    noinline key: (index: Int, item: T) -> Any,
    noinline contentType: (index: Int, item: T) -> Any? = { _, _ -> null },
    crossinline itemContent: @Composable LazyItemScope.(index: Int, item: T) -> Unit,
) {
    val keys = uniqueKeys(items.size) { key(it, items[it]) }
    items(
        count = items.size,
        key = { index -> keys[index] },
        contentType = { index -> contentType(index, items[index]) },
    ) { index -> itemContent(index, items[index]) }
}

/** [LazyGridScope.items] with keys that never repeat (SHR-ARC-18). */
internal inline fun <T> LazyGridScope.uniqueItems(
    items: List<T>,
    noinline key: (item: T) -> Any,
    noinline contentType: (item: T) -> Any? = { null },
    crossinline itemContent: @Composable LazyGridItemScope.(item: T) -> Unit,
) {
    val keys = items.uniqueKeys(key)
    items(
        count = items.size,
        key = { index -> keys[index] },
        contentType = { index -> contentType(items[index]) },
    ) { index -> itemContent(items[index]) }
}

/** [LazyGridScope.itemsIndexed] with keys that never repeat (SHR-ARC-18). */
internal inline fun <T> LazyGridScope.uniqueItemsIndexed(
    items: List<T>,
    noinline key: (index: Int, item: T) -> Any,
    noinline contentType: (index: Int, item: T) -> Any? = { _, _ -> null },
    crossinline itemContent: @Composable LazyGridItemScope.(index: Int, item: T) -> Unit,
) {
    val keys = uniqueKeys(items.size) { key(it, items[it]) }
    items(
        count = items.size,
        key = { index -> keys[index] },
        contentType = { index -> contentType(index, items[index]) },
    ) { index -> itemContent(index, items[index]) }
}
