package com.lamphaus.core.data.diagnostics

import com.lamphaus.core.model.PlaybackReport
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The device's playback journal (SHR-PROD-18): one JSON file per report,
 * named so the newest sort first, and only the newest [MAX_REPORTS] kept.
 * Writes replace the file atomically and are synchronous, so the crash
 * handler can leave the last report behind before the process dies; every
 * other caller writes off the main thread.
 */
class PlaybackReportStore(directory: () -> File) {
    private val directory by lazy(directory)

    @Synchronized
    fun write(report: PlaybackReport) {
        val folder = directory.apply { mkdirs() }
        val target = File(folder, fileName(report))
        val temp = File(folder, "${target.name}.tmp")
        temp.writeText(JSON.encodeToString(PlaybackReport.serializer(), report))
        if (!temp.renameTo(target)) {
            target.delete()
            temp.renameTo(target)
        }
    }

    /** Every readable report, newest first. */
    @Synchronized
    fun all(): List<PlaybackReport> = reportFiles().mapNotNull { file ->
        runCatching { JSON.decodeFromString(PlaybackReport.serializer(), file.readText()) }.getOrNull()
    }

    @Synchronized
    fun prune(keep: Int = MAX_REPORTS) {
        reportFiles().drop(keep).forEach(File::delete)
        directory.listFiles { file -> file.name.endsWith(".tmp") }?.forEach(File::delete)
    }

    /** The newest process exit already reconciled. */
    @Synchronized
    fun exitWatermark(): Long = runCatching { File(directory, WATERMARK).readText().trim().toLong() }.getOrDefault(0L)

    @Synchronized
    fun setExitWatermark(value: Long) {
        directory.mkdirs()
        File(directory, WATERMARK).writeText(value.toString())
    }

    private fun reportFiles(): List<File> =
        directory.listFiles { file -> file.name.endsWith(".json") }
            .orEmpty()
            .sortedByDescending { it.name }

    // Zero-padded so names sort by start time.
    private fun fileName(report: PlaybackReport) = "%013d-%s.json".format(report.startedAtEpochMillis, report.id)

    companion object {
        const val MAX_REPORTS = 30
        private const val WATERMARK = "exit-watermark"

        /** Compact and tolerant: unset fields stay out, unknown fields from newer builds are ignored. */
        val JSON = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            explicitNulls = false
        }
    }
}
