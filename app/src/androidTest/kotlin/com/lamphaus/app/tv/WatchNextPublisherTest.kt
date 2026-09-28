package com.lamphaus.app.tv

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tvprovider.media.tv.TvContractCompat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** TV-HOME-01: cards reach the system Watch Next table and leave it again. */
@RunWith(AndroidJUnit4::class)
class WatchNextPublisherTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun publishedKeys(): List<String> = context.contentResolver.query(
        TvContractCompat.WatchNextPrograms.CONTENT_URI,
        arrayOf(TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID),
        null,
        null,
        null,
    )?.use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }.orEmpty()

    private fun entry(key: String, engagedAt: Long) = WatchNextEntry(
        mediaKey = key,
        kind = WatchNextEntry.Kind.CONTINUE,
        isEpisode = false,
        title = "Check $key",
        episodeTitle = null,
        season = null,
        episode = null,
        positionMillis = 600_000,
        durationMillis = 6_000_000,
        engagedAtMillis = engagedAt,
        artUrl = "https://example.com/art.jpg",
        artIsPoster = false,
    )

    @Test
    fun publishesUpdatesAndRemovesCards() = runBlocking {
        assumeTrue(context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK))
        val publisher = WatchNextPublisher(context)
        publisher.clear()

        publisher.publish(listOf(entry("movie:a", 1), entry("movie:b", 2)))
        assertEquals(setOf("movie:a", "movie:b"), publishedKeys().toSet())

        publisher.publish(listOf(entry("movie:b", 3)))
        assertEquals(listOf("movie:b"), publishedKeys())

        publisher.clear()
        assertEquals(emptyList<String>(), publishedKeys())
    }
}
