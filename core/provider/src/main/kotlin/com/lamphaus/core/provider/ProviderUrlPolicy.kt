package com.lamphaus.core.provider

import java.util.Locale
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Liberal input handling for add-on addresses, validated by fetching rather
 * than by policing the string: real-world installs arrive as paste jobs and
 * share-sheet deep links carrying invisible characters, bare domains, custom
 * schemes, and underscore hostnames the platform's strict URI parser refuses.
 * Everything becomes HTTPS here; the only hard rules are transport-level —
 * no cleartext outside debug loopback hosts, no embedded credentials.
 */
class ProviderUrlPolicy(
    private val allowDebugLocalhost: Boolean,
) {
    fun normalizeManifestUrl(input: String): String? =
        normalize(input)?.withManifestPath()

    fun normalizeCatalogUrl(input: String): String? = normalize(input)?.toString()

    private fun normalize(raw: String): HttpUrl? {
        val cleaned = raw.sanitize()
        if (cleaned.isEmpty()) return null
        // Bare domains and custom app schemes (addon://, …) all ride
        // HTTPS on the wire; only explicit http(s) is taken as written so the
        // cleartext rule below stays in charge.
        val candidate = if (cleaned.startsWith("http://", true) || cleaned.startsWith("https://", true)) {
            cleaned
        } else {
            "https://" + cleaned.substringAfter("://")
        }
        val url = candidate.toHttpUrlOrNull() ?: return null
        val cleartextOk = allowDebugLocalhost &&
            url.scheme.equals("http", ignoreCase = true) &&
            url.host.lowercase(Locale.US) in DEBUG_LOOPBACK_HOSTS
        if (!url.scheme.equals("https", ignoreCase = true) && !cleartextOk) return null
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        return url
    }

    /** Add-on convention: a base address addresses its own /manifest.json. */
    private fun HttpUrl.withManifestPath(): String {
        val builder = newBuilder().fragment(null)
        val path = encodedPath
        if (!path.endsWith(MANIFEST_SUFFIX) && !path.endsWith(JSON_SUFFIX)) {
            builder.encodedPath("${path.trimEnd('/')}$MANIFEST_SUFFIX")
        }
        return builder.build().toString()
    }

    private companion object {
        const val MANIFEST_SUFFIX = "/manifest.json"
        const val JSON_SUFFIX = ".json"
        val DEBUG_LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "10.0.2.2")

        /**
         * Removes what copy/paste adds and nothing else: surrounding whitespace
         * and quotes, sentence punctuation after the address, and invisible
         * characters (NBSP variants, zero-width, bidi marks) that phones insert
         * and java.net.URI chokes on.
         */
        fun String.sanitize(): String =
            trim()
                .filterNot { it in INVISIBLE }
                .trim('\'', '"', '`')
                .trimEnd('.', ',', ';', '!', '?')

        val INVISIBLE = charArrayOf(
            '\u00A0', '\u2007', '\u202F', // regular/thin/narrow no-break spaces
            '\u200B', '\u200C', '\u200D', '\uFEFF', // zero-width space, joiners, BOM
            '\u200E', '\u200F', '\u202A', '\u202B', '\u202C', '\u202D', '\u202E', // bidi controls
        )
    }
}
