package com.lamphaus.core.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderUrlPolicyTest {
    @Test
    fun `production accepts HTTPS and canonicalizes manifest path`() {
        val policy = ProviderUrlPolicy(allowDebugLocalhost = false)

        assertEquals(
            "https://media.example.org/provider/manifest.json",
            policy.normalizeManifestUrl(" https://media.example.org/provider/ "),
        )
        assertEquals(
            "https://media.example.org/provider/manifest.json?language=en",
            policy.normalizeManifestUrl("https://media.example.org/provider/manifest.json?language=en#ignored"),
        )
    }

    @Test
    fun `production rejects cleartext credentials and malformed addresses`() {
        val policy = ProviderUrlPolicy(allowDebugLocalhost = false)

        assertNull(policy.normalizeManifestUrl("http://media.example.org"))
        assertNull(policy.normalizeManifestUrl("https://user:secret@media.example.org"))
        assertNull(policy.normalizeManifestUrl("not an address"))
    }

    @Test
    fun `debug permits only explicit emulator loopback hosts over cleartext`() {
        val policy = ProviderUrlPolicy(allowDebugLocalhost = true)

        assertEquals(
            "http://10.0.2.2:8080/manifest.json",
            policy.normalizeManifestUrl("http://10.0.2.2:8080"),
        )
        assertNull(policy.normalizeManifestUrl("http://192.168.1.5:8080"))
    }

    @Test
    fun `neutral add-on links normalize to HTTPS without retaining custom scheme`() {
        val policy = ProviderUrlPolicy(allowDebugLocalhost = false)

        assertEquals(
            "https://provider.example/configured/manifest.json?token=abc",
            policy.normalizeManifestUrl("lamphaus://provider.example/configured/manifest.json?token=abc"),
        )
    }

    @Test
    fun `compatible custom install schemes normalize generically`() {
        val policy = ProviderUrlPolicy(allowDebugLocalhost = false)

        assertEquals(
            "https://provider.example/configured/manifest.json?token=abc",
            policy.normalizeManifestUrl("compatible://provider.example/configured/manifest.json?token=abc"),
        )
    }
}
