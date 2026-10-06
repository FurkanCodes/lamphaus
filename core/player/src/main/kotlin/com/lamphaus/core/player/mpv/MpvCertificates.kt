package com.lamphaus.core.player.mpv

import android.content.Context
import android.util.Base64
import java.io.File
import java.security.KeyStore
import java.security.cert.X509Certificate

/**
 * The device's trusted certificate authorities as one PEM bundle, for mpv's
 * TLS (FFmpeg over mbedtls reads a CA file rather than Android's store), so
 * HTTPS streams are verified as they are in Media3 (PLY-ENG-01).
 */
internal object MpvCertificates {
    private const val FILE_NAME = "mpv-ca-bundle.pem"
    private const val MAX_AGE_MILLIS = 7L * 24 * 60 * 60 * 1000

    /** The bundle's path, rebuilt weekly so installed or removed authorities follow; null if none could be read. */
    fun bundle(context: Context): String? = runCatching {
        val file = File(context.noBackupFilesDir, FILE_NAME)
        if (!file.exists() || System.currentTimeMillis() - file.lastModified() > MAX_AGE_MILLIS) {
            val store = KeyStore.getInstance("AndroidCAStore").apply { load(null) }
            val pem = buildString {
                store.aliases().toList().forEach { alias ->
                    val certificate = store.getCertificate(alias) as? X509Certificate ?: return@forEach
                    append("-----BEGIN CERTIFICATE-----\n")
                    append(Base64.encodeToString(certificate.encoded, Base64.DEFAULT))
                    append("-----END CERTIFICATE-----\n")
                }
            }
            if (pem.isEmpty()) return@runCatching null
            val partial = File(context.noBackupFilesDir, "$FILE_NAME.tmp")
            partial.writeText(pem)
            partial.renameTo(file)
        }
        file.absolutePath
    }.getOrNull()
}
