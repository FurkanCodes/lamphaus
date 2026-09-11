package com.lamphaus.app.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.lamphaus.app.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Update discovery repository (plan §4, SHR-ARC-02/04/11). Owns feed data
 * and eligibility decisions; activities own permission/installer launches
 * (SHR-ARC-03). Works signed out without backend auth.
 *
 * - Conditional GET (ETag/Last-Modified); cached metadata never authorizes
 *   an install after a failed revalidation.
 * - Signature verified over decoded bytes BEFORE parsing; unknown keys,
 *   schemas, duplicate/conflicting versions, oversized data, and off-repo
 *   APK URLs rejected.
 * - Highest accepted revision persisted; older rejected; equal must match.
 */
class UpdateRepository(
    private val context: Context,
    private val prefs: UpdatePreferences,
) {
    data class CheckResult(
        val payload: UpdateFeed.Payload?,
        val candidate: UpdateFeed.Release?,
        val status: Status,
        val waitingForStable: Boolean = false,
    )

    enum class Status { UP_TO_DATE, AVAILABLE, WAITING_FOR_STABLE, NO_COMPATIBLE, CHECK_FAILED }

    private val feedUrl: String get() = BuildConfig.UPDATE_FEED_URL

    suspend fun check(manual: Boolean = false): CheckResult = withContext(Dispatchers.IO) {
        if (!BuildConfig.UPDATES_ENABLED) {
            return@withContext CheckResult(null, null, Status.UP_TO_DATE)
        }
        val fetch = fetchFeed()
        if (fetch == null) {
            return@withContext CheckResult(null, null, Status.CHECK_FAILED)
        }
        val (payload, revisionChanged) = fetch
        if (!revisionChanged) {
            // Revalidate cached decision against stored payload is unnecessary:
            // 304 means the accepted revision still stands.
        }
        val installed = installedVersion()
        val candidate = UpdateFeed.selectCandidate(
            payload = payload,
            channel = prefs.channel,
            installedCode = installed.first,
            installedSemver = installed.second,
            deviceSdk = Build.VERSION.SDK_INT,
            deviceAbis = Build.SUPPORTED_ABIS.toSet(),
        )
        if (candidate != null) {
            return@withContext CheckResult(payload, candidate, Status.AVAILABLE)
        }
        if (UpdateFeed.stableWaiting(payload, installed.first, installed.second)) {
            return@withContext CheckResult(payload, null, Status.WAITING_FOR_STABLE, waitingForStable = true)
        }
        // Distinguish no-compatible from up-to-date: any newer code that was
        // filtered by SDK/ABI means NO_COMPATIBLE.
        val newerExists = payload.releases.any {
            it.versionCode > installed.first && it.versionCode !in payload.withdrawnVersionCodes
        }
        if (newerExists) {
            return@withContext CheckResult(payload, null, Status.NO_COMPATIBLE)
        }
        CheckResult(payload, null, Status.UP_TO_DATE)
    }

    /** Revalidate the feed before installing; block withdrawn artifacts. */
    suspend fun revalidate(selected: UpdateFeed.Release): Boolean = withContext(Dispatchers.IO) {
        val fetch = fetchFeed(force = true) ?: return@withContext false
        val (payload, _) = fetch
        val current = payload.releases.firstOrNull { it.versionCode == selected.versionCode }
            ?: return@withContext false
        if (selected.versionCode in payload.withdrawnVersionCodes) return@withContext false
        current.apk.sha256.equals(selected.apk.sha256, ignoreCase = true) &&
            current.apk.byteLength == selected.apk.byteLength
    }

    /** Returns (versionCode, versionName) of the installed package. */
    fun installedVersion(): Pair<Int, String> {
        val pm = context.packageManager
        val info: PackageInfo = pm.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION")
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else info.versionCode
        return code to (info.versionName ?: "0.0.0")
    }

    private fun fetchFeed(force: Boolean = false): Pair<UpdateFeed.Payload, Boolean>? {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(feedUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/json")
                if (!force) {
                    if (prefs.etag.isNotBlank()) setRequestProperty("If-None-Match", prefs.etag)
                    if (prefs.lastModified.isNotBlank()) setRequestProperty("If-Modified-Since", prefs.lastModified)
                }
            }
            if (conn.responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                // Cached revision stands; reconstruct minimal payload marker.
                // Caller treats null-payload + 304 as "no change": re-run
                // selection from last accepted state is impossible without the
                // bytes, so force a full fetch instead.
                return fetchFeed(force = true)
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = conn.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
            conn.headerFields["ETag"]?.firstOrNull()?.let { prefs.etag = it }
            conn.headerFields["Last-Modified"]?.firstOrNull()?.let { prefs.lastModified = it }
            when (val result = UpdateFeed.verifyEnvelope(body)) {
                is UpdateFeed.VerifyResult.Invalid -> return null
                is UpdateFeed.VerifyResult.Valid -> {
                    val payload = result.payload
                    val hash = sha256(result.rawBytes)
                    if (payload.revision < prefs.acceptedRevision) return null
                    if (payload.revision == prefs.acceptedRevision &&
                        prefs.acceptedPayloadHash.isNotBlank() &&
                        prefs.acceptedPayloadHash != hash
                    ) {
                        return null
                    }
                    prefs.acceptedRevision = payload.revision
                    prefs.acceptedPayloadHash = hash
                    prefs.consecutiveFailures = 0
                    return payload to true
                }
            }
        } catch (_: Exception) {
            return null
        } finally {
            conn?.disconnect()
        }
    }

    private fun sha256(bytes: ByteArray): String {
        val d = MessageDigest.getInstance("SHA-256").digest(bytes)
        return d.joinToString("") { "%02x".format(it) }
    }
}
