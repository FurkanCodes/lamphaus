package com.lamphaus.app.update

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Signed release-feed contracts (plan §3). Shared with
 * scripts/release/feed.py and web/src/lib/updates.ts — keep all three in
 * sync (SHR-ARC-14, QA-01).
 *
 * Envelope: { keyId, payloadBase64, signatureBase64 }. The signature covers
 * the decoded payload bytes (SHA256withRSA) and is verified BEFORE parsing.
 * Schema version and revision live inside the signed payload. Decoded payload
 * bound: 1 MiB, enforced here and in the publisher.
 *
 * Pure JVM logic (java.util.Base64, no Android APIs) so contracts stay unit
 * testable on the JVM (SHR-ARC-15).
 */
object UpdateFeed {
    const val SCHEMA_VERSION = 1
    const val MAX_PAYLOAD_BYTES = 1024 * 1024
    const val EXPECTED_PACKAGE = "com.lamphaus.app"
    const val EXPECTED_REPO_MARKER = "github.com/furkancodes/lamphaus/releases/download/"
    val REQUIRED_ABIS = setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Serializable
    private data class Envelope(
        val keyId: String = "",
        val payloadBase64: String = "",
        val signatureBase64: String = "",
    )

    /** Trusted metadata signing keys: primary + offline recovery (plan §3). */
    object Keys {
        const val PRIMARY_ID = "lamphaus-metadata-1"
        const val RECOVERY_ID = "lamphaus-metadata-recovery-1"
        // SPKI DER, Base64. Public only; private keys stay on the owner's Mac
        // + encrypted offline backup. Rotation: add new ID, ship app+site with
        // both, then retire the old ID after propagation (see runbook).
        const val PRIMARY_SPKI_BASE64 =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA5k9HxpKP3WxJr8fbfd+CibyiF9l+cbaqhXRMiPE6tzdhWS5XC4zIfeIxdXebZdIPTHcObUCqwzwKqxzmiZ9w2hDuIO2SsGk2xDMuD1rkDQkDiu88KqsH0xDTTqgs8duaOaEpEAv9nznFfdK73Inf31UEKXpHQzRlJQjIjyw1HdPnkroRZeUqlM4TD2klzOu4nCsPdOZvsyTtQ45J44IiFJbknO3g3CGPTZ61O6J4IsdM07+r+jFNCnVkCQRRbVftQBQRmNbuny5aEbURwTDzHyaWvyl/NEZB6eGM2slNXOWQPCuvuSeslQ7D6Mp12ZzKMiOjv8Ey5aVOfeOntyoZ2wIDAQAB"
        const val RECOVERY_SPKI_BASE64 =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA5bDScNmr8IEldGY53Ovgx+FSUvYwxTMCcP+D/SvKvGUXMHNkz16GWSOsLj0Rxghwr2RSp4XSeNvma1B2uVuIyt57cXeYC3ZR2AGPhzYUWtuW9EnSJLRZJ44Wa8UhuT6vDxQNFJxugi13hr4DPIiQ09OYgnN5iKNUAa/+T9FqmFj6bY51ZHWezGYSQKiF1CDavYwtogsOm/UWY5rut4QEajslImvG/mmRg5dmFvCfbGaYniwBIoblNWaPpOk4QfztVcO/vg3Zep2rlNL8UUDQD5BT7flRTmTXhB2RCkCAdIkGcWeVSxkZ817/Y3IZ113aM7vcdhMl8+4o3+MejLvrrwIDAQAB"

        fun publicKeyFor(keyId: String): java.security.PublicKey? {
            val b64 = when (keyId) {
                PRIMARY_ID -> PRIMARY_SPKI_BASE64
                RECOVERY_ID -> RECOVERY_SPKI_BASE64
                else -> return null
            }
            val der = java.util.Base64.getMimeDecoder().decode(b64)
            return KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(der))
        }
    }

    @Serializable
    data class Apk(
        val packageId: String = "",
        val url: String = "",
        val abis: List<String> = emptyList(),
        val byteLength: Long = 0,
        val sha256: String = "",
        val signerFingerprint: String = "",
    )

    @Serializable
    data class Release(
        val channel: String = "",
        val versionCode: Int = 0,
        val versionName: String = "",
        val tag: String = "",
        val commit: String = "",
        val publishedAt: String = "",
        val minSdk: Int = 0,
        val changelog: String = "",
        val releasePageUrl: String = "",
        val apk: Apk = Apk(),
    )

    @Serializable
    data class Payload(
        val schemaVersion: Int = 0,
        val revision: Int = 0,
        val publishedAt: String = "",
        val releases: List<Release> = emptyList(),
        val withdrawnVersionCodes: List<Int> = emptyList(),
    )

    sealed interface VerifyResult {
        data class Valid(val payload: Payload, val rawBytes: ByteArray) : VerifyResult
        data class Invalid(val reason: String) : VerifyResult
    }

    private val sha256Re = Regex("^[0-9a-fA-F]{64}$")
    private val fingerprintRe = Regex("^([0-9A-Fa-f]{2}:)+[0-9A-Fa-f]{2}$")
    private val semverRe = Regex("^\\d+\\.\\d+\\.\\d+(-[0-9A-Za-z.-]+)?$")

    /** Verify envelope signature over decoded bytes, then validate payload. */
    fun verifyEnvelope(envelopeJson: String): VerifyResult {
        val envelope = runCatching {
            json.decodeFromString<Envelope>(envelopeJson)
        }.getOrElse { return VerifyResult.Invalid("malformed envelope") }
        if (envelope.keyId.isBlank() || envelope.keyId.length > 128) {
            return VerifyResult.Invalid("missing keyId")
        }
        if (envelope.payloadBase64.isBlank()) return VerifyResult.Invalid("missing payload")
        if (envelope.signatureBase64.isBlank()) return VerifyResult.Invalid("missing signature")
        val decoder = java.util.Base64.getMimeDecoder()
        val raw = runCatching { decoder.decode(envelope.payloadBase64) }.getOrElse {
            return VerifyResult.Invalid("bad payload encoding")
        }
        val sig = runCatching { decoder.decode(envelope.signatureBase64) }.getOrElse {
            return VerifyResult.Invalid("bad signature encoding")
        }
        if (raw.isEmpty() || raw.size > MAX_PAYLOAD_BYTES) {
            return VerifyResult.Invalid("payload size out of bounds")
        }
        val key = Keys.publicKeyFor(envelope.keyId) ?: return VerifyResult.Invalid("unknown key")
        val ok = runCatching {
            val s = Signature.getInstance("SHA256withRSA")
            s.initVerify(key)
            s.update(raw)
            s.verify(sig)
        }.getOrDefault(false)
        if (!ok) return VerifyResult.Invalid("invalid signature")
        val payload = runCatching {
            json.decodeFromString<Payload>(raw.toString(Charsets.UTF_8))
        }.getOrElse { return VerifyResult.Invalid("malformed payload") }
        return validatePayload(payload, raw)
    }

    fun validatePayload(payload: Payload, raw: ByteArray? = null): VerifyResult {
        if (payload.schemaVersion != SCHEMA_VERSION) return VerifyResult.Invalid("unsupported schema")
        if (payload.revision < 1) return VerifyResult.Invalid("bad revision")
        if (payload.publishedAt.isBlank()) return VerifyResult.Invalid("missing publishedAt")
        if (payload.releases.size > 64) return VerifyResult.Invalid("too many releases")
        val codes = mutableSetOf<Int>()
        val tags = mutableSetOf<String>()
        for (rel in payload.releases) {
            validateRelease(rel)?.let { return VerifyResult.Invalid(it) }
            if (!codes.add(rel.versionCode)) return VerifyResult.Invalid("duplicate version")
            if (!tags.add(rel.tag)) return VerifyResult.Invalid("duplicate tag")
            if (rel.versionCode in payload.withdrawnVersionCodes) {
                return VerifyResult.Invalid("release withdrawn")
            }
        }
        val ordered = payload.releases.map { it.versionCode }
        if (ordered != ordered.sorted()) return VerifyResult.Invalid("unordered releases")
        return VerifyResult.Valid(payload, raw ?: ByteArray(0))
    }

    private fun validateRelease(rel: Release): String? {
        if (rel.channel != "beta" && rel.channel != "stable") return "bad channel"
        if (rel.versionCode < 1) return "bad versionCode"
        if (!semverRe.matches(rel.versionName)) return "bad versionName"
        if (rel.tag.isBlank() || rel.commit.isBlank() || rel.publishedAt.isBlank()) return "missing identity"
        if (rel.minSdk < 26) return "bad minSdk"
        if (rel.changelog.length > 8000) return "changelog too long"
        val lower = rel.changelog.lowercase()
        if ("<script" in lower || "<img" in lower) return "unsafe changelog"
        if (rel.releasePageUrl.isBlank()) return "missing release page"
        val apk = rel.apk
        if (apk.packageId != EXPECTED_PACKAGE) return "wrong package"
        if (!apk.url.startsWith("https://")) return "bad apk url"
        if (EXPECTED_REPO_MARKER !in apk.url) return "apk outside repository"
        if (!sha256Re.matches(apk.sha256)) return "bad sha256"
        if (!fingerprintRe.matches(apk.signerFingerprint)) return "bad fingerprint"
        if (apk.byteLength < 1) return "bad byte length"
        if (apk.abis.toSet() != REQUIRED_ABIS) return "bad abis"
        return null
    }

    /** Newest eligible release, or null. Never downgrades (plan §4). */
    fun selectCandidate(
        payload: Payload,
        channel: UpdateChannel,
        installedCode: Int,
        installedSemver: String,
        deviceSdk: Int,
        deviceAbis: Set<String>,
    ): Release? {
        return payload.releases
            .filter { it.versionCode !in payload.withdrawnVersionCodes }
            .filter { it.versionCode > installedCode }
            .filter { !semverRegressed(installedSemver, it.versionName) }
            .filter { deviceSdk >= it.minSdk }
            .filter { deviceAbis.intersect(it.apk.abis.toSet()).isNotEmpty() }
            .filter { channel == UpdateChannel.BETA || it.channel == "stable" }
            .maxByOrNull { it.versionCode }
    }

    /** True when a newer-beta install waits on a lower-semver stable offer. */
    fun stableWaiting(payload: Payload, installedCode: Int, installedSemver: String): Boolean {
        val stables = payload.releases.filter {
            it.channel == "stable" && it.versionCode !in payload.withdrawnVersionCodes
        }
        val newest = stables.maxByOrNull { it.versionCode } ?: return false
        return semverRegressed(installedSemver, newest.versionName) &&
            newest.versionCode <= installedCode
    }

    private fun semverBase(v: String): Triple<Int, Int, Int> {
        val base = v.substringBefore("-").split(".")
        return Triple(
            base.getOrNull(0)?.toIntOrNull() ?: 0,
            base.getOrNull(1)?.toIntOrNull() ?: 0,
            base.getOrNull(2)?.toIntOrNull() ?: 0,
        )
    }

    fun semverRegressed(installed: String, candidate: String): Boolean {
        val (a1, b1, c1) = semverBase(installed)
        val (a2, b2, c2) = semverBase(candidate)
        if (a2 != a1) return a2 < a1
        if (b2 != b1) return b2 < b1
        return c2 < c1
    }
}

enum class UpdateChannel { BETA, STABLE }
