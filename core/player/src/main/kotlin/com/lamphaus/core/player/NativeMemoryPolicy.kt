package com.lamphaus.core.player

import android.app.ActivityManager
import android.content.Context
import java.io.File

/**
 * "Native memory buffer" sizing (PLY-NET-01), Nuvio's ExoPlayer native memory
 * values: buffered media lives off the Java heap, so the byte target follows
 * the device's physical RAM instead of the app's heap limit.
 */
object NativeMemoryPolicy {

    const val SEGMENT_BYTES = 64 * 1024
    const val MIN_BUFFER_MS = 15_000
    const val MAX_BUFFER_MS = 45_000
    const val BUFFER_FOR_PLAYBACK_MS = 3_000
    const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 3_000

    /** Wanted back buffer; it gives way first, so it never exceeds half of [MIN_BUFFER_MS]. */
    private const val BACK_BUFFER_MS = 15_000
    val backBufferMs: Int = BACK_BUFFER_MS.coerceAtMost(MIN_BUFFER_MS / 2)

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
