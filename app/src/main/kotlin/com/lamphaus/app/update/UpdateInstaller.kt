package com.lamphaus.app.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

/**
 * PackageInstaller sessions with explicit user confirmation (plan §4).
 * Bytes written into the session are re-verified against the signed hash
 * before commit; never installs from externally mutable staging files.
 * The callback uses an explicitly targeted intent with the PendingIntent
 * mutability required by the target SDK.
 */
class UpdateInstaller(private val context: Context) {

    companion object {
        const val ACTION_SESSION_CALLBACK = "com.lamphaus.app.update.SESSION_CALLBACK"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_VERSION_CODE = "version_code"
        private const val SESSION_APK = "base.apk"
        private const val SESSION_DEX_METADATA = "base.dm"
    }

    data class Session(val sessionId: Int)

    fun createSession(totalLength: Long): Session {
        val pi = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setSize(totalLength)
            if (Build.VERSION.SDK_INT >= 31) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }
        return Session(pi.createSession(params))
    }

    /**
     * Stream the verified private file into the session, verifying again.
     * [dexMetadata], already verified against the signed feed, goes in beside
     * it as `base.dm`, the way Google Play installs: Android then compiles the
     * app with its profile during the install instead of days later.
     */
    fun writeSession(sessionId: Int, apk: File, expectedSha256: String, dexMetadata: ByteArray? = null): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        return try {
            val pi = context.packageManager.packageInstaller
            pi.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite(SESSION_APK, 0, apk.length()).use { out ->
                        val buf = ByteArray(256 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            digest.update(buf, 0, n)
                        }
                        session.fsync(out)
                    }
                }
                // The name pairs it with the APK; Android drops a .dm it cannot pair.
                dexMetadata?.let { bytes ->
                    session.openWrite(SESSION_DEX_METADATA, 0, bytes.size.toLong()).use { out ->
                        out.write(bytes)
                        session.fsync(out)
                    }
                }
            }
            val valid = digest.digest().joinToString("") { "%02x".format(it) }
                .equals(expectedSha256, ignoreCase = true)
            if (!valid) abandon(sessionId)
            valid
        } catch (_: Exception) {
            abandon(sessionId)
            false
        }
    }

    fun commit(sessionId: Int, versionCode: Int) {
        val pi = context.packageManager.packageInstaller
        val intent = Intent(context, UpdateInstallReceiver::class.java).apply {
            action = ACTION_SESSION_CALLBACK
            putExtra(EXTRA_SESSION_ID, sessionId)
            putExtra(EXTRA_VERSION_CODE, versionCode)
            // Explicitly targeted callback receiver (installer contract).
            `package` = context.packageName
        }
        val flags = if (Build.VERSION.SDK_INT >= 31) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val sender = PendingIntent.getBroadcast(context, sessionId, intent, flags)
        pi.openSession(sessionId).use { session ->
            session.commit(sender.intentSender)
        }
    }

    fun abandon(sessionId: Int) {
        if (sessionId < 0) return
        runCatching { context.packageManager.packageInstaller.abandonSession(sessionId) }
    }

    /** Confirm success from the installed package on next launch (plan §4). */
    fun installedVersionCode(): Int {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else info.versionCode
    }

    /** minSdk 26 guarantees PackageManager#canRequestPackageInstalls. */
    fun canRequestInstalls(): Boolean = context.packageManager.canRequestPackageInstalls()
}
