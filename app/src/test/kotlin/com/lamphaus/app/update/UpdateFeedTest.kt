package com.lamphaus.app.update

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Feed contracts mirrored from scripts/release/feed.py + release/fixtures
 * (plan §8, QA-01, SHR-ARC-15). Rule changes here MUST update test_feed.py
 * and web updates.test.ts with the same case.
 */
class UpdateFeedTest {

    private fun loadPayload(): UpdateFeed.Payload {
        // Shared fixture: release/fixtures/feed_valid.json (repo root).
        val candidates = listOf(
            File("../release/fixtures/feed_valid.json"),
            File("release/fixtures/feed_valid.json"),
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("shared feed fixture missing")
        val text = file.readText()
        return UpdateFeed.json.decodeFromString(UpdateFeed.Payload.serializer(), text)
    }
    @Test
    fun `unknown key rejected before parsing`() {
        val raw = java.util.Base64.getEncoder().encodeToString("{}".toByteArray())
        val sig = java.util.Base64.getEncoder().encodeToString(ByteArray(256))
        val envelope = """{"keyId":"nope","payloadBase64":"$raw","signatureBase64":"$sig"}"""
        val result = UpdateFeed.verifyEnvelope(envelope)
        assertTrue(result is UpdateFeed.VerifyResult.Invalid)
    }


    @Test
    fun `duplicate version codes rejected`() {
        val payload = loadPayload()
        val dup = payload.copy(releases = payload.releases + payload.releases.first())
        assertTrue(UpdateFeed.validatePayload(dup) is UpdateFeed.VerifyResult.Invalid)
    }

    @Test
    fun `off-repo apk url rejected`() {
        val payload = loadPayload()
        val tampered = payload.copy(
            releases = payload.releases.mapIndexed { i, r ->
                if (i == 0) r.copy(apk = r.apk.copy(url = "https://evil.example/l.apk")) else r
            },
        )
        assertTrue(UpdateFeed.validatePayload(tampered) is UpdateFeed.VerifyResult.Invalid)
    }

    @Test
    fun `beta accepts newer beta and stable`() {
        val payload = loadPayload()
        val got = UpdateFeed.selectCandidate(
            payload, UpdateChannel.BETA, 1, "0.9.0", 34, setOf("arm64-v8a"),
        )
        assertEquals(3, got?.versionCode)
    }

    @Test
    fun `stable-only ignores beta`() {
        val payload = loadPayload().copy(
            releases = listOf(loadPayload().releases.first()),
        )
        val got = UpdateFeed.selectCandidate(
            payload, UpdateChannel.STABLE, 1, "0.9.0", 34, setOf("arm64-v8a"),
        )
        assertNull(got)
    }

    @Test
    fun `equal or lower code never downgrades`() {
        val payload = loadPayload()
        val got = UpdateFeed.selectCandidate(
            payload, UpdateChannel.BETA, 3, "1.0.0", 34, setOf("arm64-v8a"),
        )
        assertNull(got)
    }

    @Test
    fun `semantic regression rejected`() {
        val payload = loadPayload()
        val got = UpdateFeed.selectCandidate(
            payload, UpdateChannel.BETA, 1, "2.0.0", 34, setOf("arm64-v8a"),
        )
        assertNull(got)
    }

    @Test
    fun `incompatible sdk has no candidate`() {
        val payload = loadPayload()
        val got = UpdateFeed.selectCandidate(
            payload, UpdateChannel.BETA, 1, "0.9.0", 21, setOf("arm64-v8a"),
        )
        assertNull(got)
    }

    @Test
    fun `newer beta install waits for stable`() {
        val payload = loadPayload()
        assertTrue(UpdateFeed.stableWaiting(payload, 5, "1.1.0-beta.2"))
    }

    @Test
    fun `unordered releases rejected`() {
        val payload = loadPayload()
        val reversed = payload.copy(releases = payload.releases.reversed())
        assertNotNull(reversed)
        assertTrue(UpdateFeed.validatePayload(reversed) is UpdateFeed.VerifyResult.Invalid)
    }
}
