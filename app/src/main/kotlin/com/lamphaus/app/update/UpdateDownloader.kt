package com.lamphaus.app.update

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import androidx.core.net.toUri
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * DownloadManager transfers for user-requested updates (plan §4).
 * Staging lives in an app-specific directory with a staging filename — never
 * public Downloads. Notification taps route to the updater, not direct install.
 */
class UpdateDownloader(private val context: Context, private val prefs: UpdatePreferences) {

    data class Progress(val downloadedBytes: Long, val totalBytes: Long, val status: Int)

    /** Enqueue exactly one operation; repeated taps reuse the active download. */
    fun enqueue(url: String, versionCode: Int): Long {
        existingActive()?.let { return it }
        val dm = context.getSystemService(DownloadManager::class.java)
        val staging = stagingFile(versionCode)
        if (staging.exists()) staging.delete()
        val request = DownloadManager.Request(url.toUri()).apply {
            setTitle("Lamphaus update")
            setDescription("Downloading update")
            setDestinationUri(Uri.fromFile(staging))
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            // Truthful metered-data policy: no roaming, metered only with consent.
            setAllowedOverRoaming(false)
            setAllowedOverMetered(false)
            setVisibleInDownloadsUi(false)
        }
        val id = dm.enqueue(request)
        prefs.downloadId = id
        prefs.selectedVersionCode = versionCode
        return id
    }

    fun setMeteredAllowed(downloadId: Long, allowed: Boolean) {
        // DownloadManager has no per-download metered toggle post-enqueue on all
        // API levels; callers re-enqueue with consent instead. Kept explicit so
        // metered use is always explained first (plan §4).
    }

    fun query(downloadId: Long): Progress? {
        val dm = context.getSystemService(DownloadManager::class.java)
        val c: Cursor = dm.query(DownloadManager.Query().setFilterById(downloadId))
        c.use {
            if (!it.moveToFirst()) return null
            val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            return Progress(downloaded, total, status)
        }
    }

    fun existingActive(): Long? {
        val id = prefs.downloadId
        if (id < 0) return null
        val p = query(id) ?: return null
        return when (p.status) {
            DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED -> id
            else -> null
        }
    }

    fun cancel() {
        val id = prefs.downloadId
        if (id >= 0) {
            runCatching { context.getSystemService(DownloadManager::class.java).remove(id) }
        }
        prefs.downloadId = -1
        cleanupStale(listOf(prefs.selectedVersionCode))
    }

    /** Copy completed bytes into private staging and verify length + SHA-256. */
    suspend fun copyAndVerify(versionCode: Int, expectedBytes: Long, expectedSha256: String): File? =
        withContext(Dispatchers.IO) {
            val src = stagingFile(versionCode)
            if (!src.exists() || src.length() != expectedBytes) return@withContext null
            val digest = MessageDigest.getInstance("SHA-256")
            src.inputStream().use { input ->
                val buf = ByteArray(256 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    digest.update(buf, 0, n)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(expectedSha256, ignoreCase = true)) {
                src.delete()
                return@withContext null
            }
            val dst = privateFile(versionCode)
            src.copyTo(dst, overwrite = true)
            dst
        }

    /** Remove cancelled, corrupt, withdrawn, superseded, installed artifacts. */
    fun cleanupStale(keepCodes: List<Int>) {
        stagingDir().listFiles()?.forEach { file ->
            val keep = keepCodes.any { file.name.contains(it.toString()) }
            if (!keep) runCatching { file.delete() }
        }
        filesDir().listFiles { f -> f.name.startsWith(PRIVATE_PREFIX) }?.forEach { file ->
            val keep = keepCodes.any { file.name.contains(it.toString()) }
            if (!keep) runCatching { file.delete() }
        }
    }

    fun stagingFile(versionCode: Int): File = File(stagingDir(), "$STAGING_PREFIX$versionCode.apk")
    fun privateFile(versionCode: Int): File = File(filesDir(), "$PRIVATE_PREFIX$versionCode.apk")

    private fun stagingDir(): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir()
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun filesDir(): File = File(context.filesDir, "updates").apply { if (!exists()) mkdirs() }

    private companion object {
        const val STAGING_PREFIX = "lamphaus-update-staging-"
        const val PRIVATE_PREFIX = "lamphaus-update-"
    }
}
