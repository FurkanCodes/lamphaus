package com.lamphaus.core.player

import android.app.ActivityManager
import android.content.Context
import java.io.File

/**
 * "Native memory buffer" sizing (PLY-NET-01): buffered media lives off the Java
 * heap, so a high-bitrate remux holds as many seconds as any other stream. The
 * RAM tiers are Nuvio's. Stock Media3 stops at a fixed 144 MiB, about 12 s of a
 * 100 Mbit/s remux; here time sets the buffer and RAM only caps it.
 */
object NativeMemoryPolicy {

    const val SEGMENT_BYTES = 64 * 1024

    /**
     * One target rather than a low and a high mark: loading tops up as the
     * buffer drains, so it never idles the socket and sags before refilling.
     */
    const val BUFFER_MS = 50_000
    const val BUFFER_FOR_PLAYBACK_MS = 2_000
    const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 3_000

    private const val BACK_BUFFER_MS = 15_000

    /** 50 s ahead plus 15 s behind of a 100 Mbit/s remux; smaller targets keep every byte ahead. */
    private const val BACK_BUFFER_MIN_TARGET_BYTES = 800 * 1024 * 1024

    /** A starting bandwidth estimate for adaptive streams, so HLS opens at a high rendition. */
    const val INITIAL_BITRATE_ESTIMATE = 50_000_000L

    private const val MIN_TARGET_MB = 25
    private const val MIB = 1024L * 1024L
    private const val GIB = 1024.0 * 1024.0 * 1024.0

    /** The buffer ceiling for this much physical RAM, in MiB. */
    fun safeLimitMb(totalRamBytes: Long): Int {
        if (totalRamBytes <= 0L) return 200
        val gib = totalRamBytes / GIB
        return when {
            gib < 1.15 -> 100
            gib < 2.3 -> 200
            gib < 3.2 -> 500
            gib < 4.8 -> 1_000
            gib < 6.8 -> 1_600
            else -> 2_000
        }
    }

    /** Keeps a rewind off the network, but only where it cannot cost the buffer ahead. */
    fun backBufferMs(targetBufferBytes: Int): Int =
        if (targetBufferBytes >= BACK_BUFFER_MIN_TARGET_BYTES) BACK_BUFFER_MS else StreamingPolicy.STOCK_BACK_BUFFER_MS

    /** The sample buffer's byte target once parallel downloads took their share. */
    fun targetBufferBytes(totalRamBytes: Long, parallelOverheadBytes: Long): Int {
        val overheadMb = ((parallelOverheadBytes + MIB - 1) / MIB).toInt()
        val targetMb = (safeLimitMb(totalRamBytes) - overheadMb).coerceAtLeast(MIN_TARGET_MB)
        return (targetMb * MIB).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun safeLimitBytes(totalRamBytes: Long): Long = safeLimitMb(totalRamBytes) * MIB

    @Volatile
    private var cachedRamBytes = 0L

    /** Physical RAM from ActivityManager, falling back to /proc/meminfo. */
    fun totalRamBytes(context: Context): Long {
        cachedRamBytes.takeIf { it > 0L }?.let { return it }
        val fromActivityManager = runCatching {
            val info = ActivityManager.MemoryInfo()
            context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(info)
            info.totalMem
        }.getOrDefault(0L)
        val ram = fromActivityManager.takeIf { it > 0L } ?: runCatching {
            File("/proc/meminfo").useLines { lines ->
                lines.firstOrNull()?.let { Regex("\\d+").find(it)?.value?.toLongOrNull() }?.times(1024L)
            } ?: 0L
        }.getOrDefault(0L)
        if (ram > 0L) cachedRamBytes = ram
        return ram
    }
}
